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
    :validate [#(< 5 % 0x10000) "Must be a number between 5 and 65536"]]

   ["-c" "--columns COLUMNS" "Number of columns"
    :default 6
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 5 % 0x10000) "Must be a number between 5 and 65536"]]

   ["-s" "--square-size SIZE" "Square size in cm"
    :default 2.
    :parse-fn #(Float/parseFloat %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-n" "--images-number NUMBER" "Number of images"
    :default 4
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 3 % 256) "Must be a number between 3 and 256"]]

   ["-i" "--camera-id ID..." "Camera ID (one or more)"
    :multi true
    :missing "Must be at least one camera ID"
    :update-fn #(conj (vec %1) %2)]

   ["-w" "--width WIDTH" "Frame width in PX"
    :default 640
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-h" "--height HEIGHT" "Frame height in PX"
    :default 480
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--codec CODEC" "Camera output codec"
    :default [\Y \U \Y \V]
    :default-desc "YUYV"
    :parse-fn #(-> (str %) .trim .toUpperCase .toCharArray seq vec)
    :validate [#(= 4 (count %)) "Must be 4 chars codec code, eg. MJPG"]]

   ["-z" "--buffer-size NUMBER" "Buffer size (frames)"
    :default 2
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 0 %) "Must be a positive integer"]]

   ["-f" "--fps FPS" "Camera FPS"
    :default 30
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 300) "Must be a number between 0 and 300"]]

   ["-e" "--exposure EXPOSURE" "Camera exposure"
    :default nil
    :default-desc ""
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-g" "--gain GAIN" "Camera gain"
    :default nil
    :default-desc ""
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-m" "--gamma GAMMA" "Camera gamma"
    :default nil
    :default-desc ""
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-b" "--brightness BRIGHTNESS" "Camera brightness"
    :default nil
    :default-desc ""
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--show-chessboard" "Show detected chessboard"]

   ["-o" "--output-folder FOLDER_NAME" "Folder to write calibration files to"
    :parse-fn #(io/file %)
    :missing "Output folder option is required"
    :validate [#(or (not (.exists %)) (.isDirectory %)) "File should be DIRECTORY or not exist"]]

   [nil "--help"]])

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
      (cbr/calibrate options)
      )))