(ns stereox.cuda.cuda-kernel
  (:require [clojure.java.io :as io])
  (:import (java.nio.file Files Paths)
           (org.bytedeco.cuda.cudart CUctx_st)
           (org.bytedeco.cuda.global cudart))
  (:gen-class))

(def ^:private T 1024)
(def ^:private E 32)
(def ^:private *cache (atom {}))

(defn- even
  "Returns 'v' if even, otherwise '(+ v 1)'"
  [v]
  (if (not= (mod v 2) 0)
    (+ v 1)
    v))

(defn file*
  "Load CUDA kernel"
  {:static true}
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