(ns stereox.config.cli
  (:gen-class)
  (:require [clojure.string :as string]
            [stereox.config.view :as view]
            ))

(def cli-options
  [["-h" "--help"]])

(defn error-msg [errors]
  (str " - " (string/join "\n - " errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn pattern-matching [& args]
  (println "PATTERN MATCHING")
  (view/start-gui args)
  ; TODO
  )