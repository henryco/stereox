(ns stereox.core
  (:require
    [stereox.cv.stereo-camera :as camera]
    [stereox.config.cli :as config]
    [stereox.calibration.cli :as cbr]
    [clojure.string :as string]
    [clojure.tools.cli :as cli])
  (:gen-class)
  (:import (org.bytedeco.javacpp Loader)
           (org.bytedeco.cuda.presets cudart)
           (org.bytedeco.opencv opencv_java)))

(Loader/load ^Class cudart)
(Loader/load ^Class opencv_java)

(def cli-options
  [["-h" "--help"]

   [nil "--auto-exposure-on VALUE" "Value for camera auto exposure ON property"
    :default 3
    :parse-fn #(Integer/parseInt %)
    :validate [#(< % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--auto-exposure-off VALUE" "Value for camera auto exposure OFF property"
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [#(< % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--auto-exposure-force" "Disable manual exposure"]])

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
        "  config"
        "  vision"]
       (string/join \newline)))

(defn validate-args [args]
  (let [{:keys [arguments options errors summary]} (cli/parse-opts args cli-options :in-order true)]
    (let [{:keys [auto-exposure-on auto-exposure-off auto-exposure-force]} options]
      (if (some? auto-exposure-on)
        (camera/set-auto-exposure-on-value auto-exposure-on))
      (if (some? auto-exposure-off)
        (camera/set-auto-exposure-off-value auto-exposure-off))
      (if auto-exposure-force
        (do (println "[WARN]: FORCING CAMERA AUTO EXPOSURE")
            (camera/force-auto-exposure true))))
    (cond
      (or (nil? (first arguments))
          (:help options))
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (str "ERROR:\n" (error-msg errors) "\n\n" (usage summary))}

      :else
      {:action (first arguments) :options options :arguments (drop 1 arguments)})))

(defn -main [& args]
  (let [{:keys [arguments action exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "calibration" (cbr/calibration arguments)
        "config" (config/pattern-matching arguments)
        "vision" (throw (Exception. "TODO"))
        (exit 1 "Try to use --help")))))