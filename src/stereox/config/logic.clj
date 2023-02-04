(ns stereox.config.logic
  (:require [taoensso.timbre :as log])
  (:gen-class))

(defn configure [args]
  (log/info (pr-str args))
  )