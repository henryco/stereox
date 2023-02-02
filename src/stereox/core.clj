(ns stereox.core
  (:require
    [stereox.calibration.cli :as cbr]
    [stereox.pmc.cli :as pmc]
    [clojure.string :as string]
    [clojure.tools.cli :as cli])
  (:gen-class))

(def cli-options
  [["-h" "--help"]])

(defn error-msg [errors]
  (str " - " (string/join "\n - " errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn usage [options-summary]
  (->> ["StereoX - stereo vision modules."
        "Usage: module [options...]"
        ""
        "Options:"
        options-summary
        ""
        "Modules:"
        "  calibration"
        "  vision"
        "  pmc"]
       (string/join \newline)))

(defn validate-args [args]
  (let [{:keys [arguments options errors summary]} (cli/parse-opts args cli-options :in-order true)]
    (cond
      (or (nil? (first arguments))
          (:help options))
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (str "ERROR:\n" (error-msg errors) "\n\n" (usage summary))}

      :else
      {:action (first arguments) :options options})))

(defn -main [& args]
  (let [{:keys [action exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "calibration" (cbr/calibration (drop 1 args))
        "vision" (throw (Exception. "TODO"))
        "pmc" (pmc/pattern-matching (drop 1 args))
        (exit 1 "Try to use --help")))))