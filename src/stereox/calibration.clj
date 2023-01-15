(ns stereox.calibration
  (:require
    [clojure.java.io :as io])
  (:import (java.io File))
  (:gen-class))

(defn prep-dirs [^File dir]
  (if (not (.exists dir))
    (.mkdir dir)))

(defn calibrate [& {:keys [rows columns square-size output-folder] :as all}]
  (println (pr-str all))
  (prep-dirs output-folder)

  )
