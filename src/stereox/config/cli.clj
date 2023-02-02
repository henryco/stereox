(ns stereox.config.cli
  (:gen-class)
  (:require [clojure.string :as string]))

(def cli-options
  [["-h" "--help"]])

(defn error-msg [errors]
  (str " - " (string/join "\n - " errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn pattern-matching [args]
  (println "PATTERN MATCHING")
  (exit 0 "")
  ; TODO
  )