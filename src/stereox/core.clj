(ns stereox.core
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]
    [stereox.calibration :as cbr]
    [clojure.tools.cli :as cli])
  (:gen-class))


(def cli-options
  [["-r" "--rows ROWS" "Number of rows"
    :default 8
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-c" "--columns COLUMNS" "Number of columns"
    :default 6
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-s" "--square-size SIZE" "Square size in cm"
    :default 2
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--show-chessboards" "Show detected chessboard"]

   ["-o" "--output-folder FOLDER_NAME" "Folder to write calibration files to"
    :parse-fn #(io/file %)
    :missing "Output folder option is required"
    :validate [#(or (not (.exists %)) (.isDirectory %)) "File should be DIRECTORY or not exist"]
    ]

   ["-h" "--help"]])

(defn error-msg [errors]
  (str " - " (string/join "\n - " errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn usage [options-summary]
  (->> ["StereoX - stereo vision" "Options:" options-summary]
       (string/join \newline)))

(defn validate-args [args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options :in-order true)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (str "ERROR:\n" (error-msg errors) "\n\n" (usage summary))}

      :else
      {:options options})))


(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (cbr/calibrate options))))