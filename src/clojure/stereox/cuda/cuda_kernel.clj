(ns stereox.cuda.cuda-kernel
  (:require [clojure.java.io :as io])
  (:import (java.nio.file Files Paths))
  (:gen-class))

(def ^:private *cache (atom {}))

(defn load*
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

(defn best-grid-dim [& [x y z w]]
  ; TODO
  (println x y z w)
  )