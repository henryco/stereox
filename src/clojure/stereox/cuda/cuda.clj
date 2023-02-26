(ns stereox.cuda.cuda
  (:import (java.util.function Supplier)
           (org.bytedeco.cuda.cudart CUctx_st)
           (org.bytedeco.cuda.global cudart))
  (:use [stereox.utils.cmacros])
  (:gen-class))

(declare devices*)

(def ^:private devices
  (ThreadLocal/withInitial
    (sup-> (let [device (int-array [0])]
             (cudart/cuInit 0)
             (cudart/cuDeviceGet device 0)
             device))))

(def ^:private context
  (ThreadLocal/withInitial
    (sup-> (let [ctx (CUctx_st.)]
             (cudart/cuDevicePrimaryCtxRetain ctx (aget (devices*) 0))
             ctx))))

(def ^:private local-context
  (ThreadLocal.))

(defn context-create
  "Creates new CUDA context for first device in provided int array"
  {:static true
   :tag    CUctx_st}
  [^ints device]
  (let [ctx (CUctx_st.)]
    (cudart/cuCtxCreate ctx 0 (aget device 0))
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
   (let [t0 (System/nanoTime)]
     (with-context func)
     (timer (- (System/nanoTime) t0)))))

(defn launch []
  ;(cudart/cuLaunchKer)
  ;(cudart/)
  )