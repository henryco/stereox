(ns stereox.cuda.cuda-kernel
  (:use [stereox.utils.cmacros])
  (:require [clojure.java.io :as io]
            [stereox.cuda.cuda :as cuda]
            )
  (:import (clojure.lang IFn)
           (java.nio.file Files Paths)
           (org.bytedeco.javacpp Pointer PointerPointer)
           (org.bytedeco.cuda.cudart CUctx_st CUfunc_st)
           (org.bytedeco.cuda.global cudart))
  (:gen-class))

(def ^:private *cache (atom {}))

(def ^:private T 1024)
(def ^:private E 32)

(defn- even
  "Returns 'v' if even, otherwise '(+ v 1)'"
  [v]
  (if (not= (mod v 2) 0)
    (+ v 1)
    v))

(defn launch-kernel-function
  "Launch cuda kernel function"
  {:static true}
  [^CUfunc_st function
   ^"[Lorg.bytedeco.javacpp.Pointer;" params
   ^Integer b_x
   ^Integer t_x
   ^Integer b_y
   ^Integer t_y]
  (cuda/status
    (cudart/cuLaunchKernel
      function
      b_x b_y 1
      t_x t_y 1
      0 nil
      (PointerPointer. params)
      nil))
  (cuda/status
    (cudart/cuCtxSynchronize)))

(defn file*
  "Load CUDA kernel file"
  {:static true
   :tag    bytes}
  [^String kernel-file]
  (let [name (delay (.replaceAll kernel-file ".cu" ".ptx"))
        file (delay (-> @name io/resource .toURI Paths/get Files/readAllBytes))
        first (delay (get @*cache kernel-file))
        second (delay (get @*cache @name))]
    (if (some? @first)
      @@first
      (if (some? @second)
        @@second
        (do (swap! *cache assoc kernel-file file)
            (swap! *cache assoc @name file)
            @file)))))

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

(defmacro parameters
  "Creates Function that transform CUDA kernel parameters into Pointer.
  When calling result function instead of array itself you should pass its pointer!

  Arguments:
    TYPE of kernel parameters.

  Example:
    (def parameters (builder shorts shorts int float))
    (parameters ptr_1 ptr_2 127 256)"
  {:static true
   :tag    IFn}
  [& arguments]
  (let [params (gensym 'params)
        args (gensym 'args)]
    `(fn [& ~params]
       (let [~args (vec (flatten ~params))]
         (into-array
           ~'org.bytedeco.javacpp.Pointer
           [~@(map-indexed
                (fn [i a]
                  (if (in (eval a) bytes shorts ints longs floats doubles)
                    `(new ~'org.bytedeco.javacpp.LongPointer
                          (long-array [(get ~args ~i)]))
                    `(new ~(symbol (str "org.bytedeco.javacpp."
                                        (.toUpperCase (.substring (str a) 0 1))
                                        (.substring (str a) 1 (.length (str a)))
                                        "Pointer"))
                          (~(symbol (str a "-array")) [(get ~args ~i)]))))
                (flatten arguments))])))))

(defmacro kernel-function
  "Creates function that executes CUDA kernel.
  When calling result function instead of array itself you should pass its pointer!

  Arguments:
    file      - kernel file (*.ptx)
    name      - kernel function name
    width     - image width
    height    - image height
    arg_types - function arguments type

  Example:
    (def function
      (kernel-function \"kernel.ptx\"
                       \"blend\"
                       640
                       480
                       shorts
                       shorts
                       int
                       float))
    (function ptr_1 ptr_2 10 0.5)"
  {:static true
   :tag    IFn}
  [file name width height & args_type]
  (let [function (gensym 'function_)
        params (gensym 'params_)
        args (gensym 'args_)
        dim (gensym 'dim_)
        b_x (gensym 'b_x_)
        b_y (gensym 'b_y_)
        t_x (gensym 't_x_)
        t_y (gensym 't_y_)]
    `(let [~function (~'stereox.cuda.cuda/load-func ~file ~name)
           ~dim (stereox.cuda.cuda-kernel/optimal-bt ~width ~height)
           ~params ~(macroexpand `(stereox.cuda.cuda-kernel/parameters ~@args_type))
           ~b_x (-> ~dim first first int)
           ~b_y (-> ~dim first last int)
           ~t_x (-> ~dim last first int)
           ~t_y (-> ~dim last last int)]
       (fn [& ~args]
         (stereox.cuda.cuda-kernel/launch-kernel-function
           ~function
           (~params ~args)
           ~b_x ~t_x ~b_y ~t_y)))))