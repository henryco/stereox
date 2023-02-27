(ns stereox.cuda.cuda-kernel
  (:require [clojure.java.io :as io])
  (:import (java.nio.file Files Paths)
           (org.bytedeco.cuda.cudart CUctx_st)
           (org.bytedeco.cuda.global cudart))
  (:gen-class))

(def ^:private *cache (atom {}))

(defn file*
  "Load CUDA kernel"
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
