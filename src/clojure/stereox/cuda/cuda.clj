(ns stereox.cuda.cuda
  (:require [taoensso.timbre :as log]
            [clojure.java.io :as io])
  (:import (java.util.function Supplier)
           (org.bytedeco.cuda.cudart CUctx_st CUfunc_st CUmod_st)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacpp BytePointer))
  (:use [stereox.utils.cmacros])
  (:gen-class))

(declare status)
(declare devices*)
(declare context-create)

(def ^:private T 1024)
(def ^:private E 32)

(def ^:private devices
  (ThreadLocal/withInitial
    (sup-> (let [device (int-array [0])]
             (status (cudart/cuInit 0))
             (status (cudart/cuDeviceGet device 0))
             device))))

(def ^:private context
  (ThreadLocal/withInitial
    (sup-> (let [ctx (CUctx_st.)
                 res (cudart/cuDevicePrimaryCtxRetain ctx (aget (devices*) 0))]
             (if (= res cudart/CUDA_SUCCESS)
               (log/info (.getName (Thread/currentThread))
                         "CUDA context initialization SUCCESS")
               (throw (Exception. (str "CUDA initialization status: ERROR [" res "]"))))
             ctx))))

(def ^:private local-context
  (ThreadLocal.))

(defn status [s]
  (if (not= s cudart/CUDA_SUCCESS)
    (throw (Exception. (str "CUDA execution: ERROR [" s "]")))))

(defn- even
  "Returns 'v' if even, otherwise '(+ v 1)'"
  [v]
  (if (not= (mod v 2) 0)
    (+ v 1)
    v))

(defn context-create
  "Creates new CUDA context for first device in provided int array"
  {:static true
   :tag    CUctx_st}
  [^ints device]
  (let [ctx (CUctx_st.)
        res (cudart/cuCtxCreate ctx 0 (aget device 0))]
    (if (= res cudart/CUDA_SUCCESS)
      (log/info (.getName (Thread/currentThread))
                "CUDA context initialization SUCCESS")
      (throw (Exception. (str "CUDA initialization status: ERROR [" res "]"))))
    (.set local-context ctx)
    ctx))

(defn devices*
  "Returns thread local int array of gpu devices"
  {:static true
   :tag    ints}
  []
  (.get ^ThreadLocal devices))

(defn context*
  "Returns thread local primary CUDA context"
  {:static true
   :tag    CUctx_st}
  []
  (.get ^ThreadLocal context))

(defn context-local*
  "Returns thread local CUDA context (might be nil)"
  {:static true
   :tag    CUctx_st}
  []
  (.get ^ThreadLocal local-context))

(defn with-context
  "Execute with CUDA context.
  Two argument version returns execution time (nano sec).
  Expects:
    (fn [^CUctx_st context ^ints devices] ... )"
  ([func]
   (let [d (devices*)
         c (context*)]
     (func c d)))
  ([func timer]
   (let [t0 (System/nanoTime)
         r (with-context func)]
     (timer (- (System/nanoTime) t0))
     r)))

(defn load-func [file ^String name]
  (with-context
    (fn [_ _]
      (let [module (CUmod_st.)
            function (CUfunc_st.)
            file (-> file (.replaceAll ".cu" ".ptx") (io/resource) (.getPath))]
        (status (cudart/cuModuleLoad module (BytePointer. ^String file)))
        (status (cudart/cuModuleGetFunction function module name))
        function))))


(defn optimal-bt
  "Find optimal B,T ratio, where:
    B - Number of blocks
    T - Number of threads per block
  Prefers lower number of blocks (B).
  Returns:
    [[B,T]...]"
  [& arr]
  (map (fn [N]
         (if (= N T)
           [1 N]
           (let [gamma (-> (/ N E) Math/ceil int)
                 betta (-> gamma even (/ 2) int)
                 t (-> (* betta E) (min T) int)
                 b (-> (/ N t) Math/ceil int)]
             [b t])))
       (flatten arr)))
