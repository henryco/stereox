(ns stereox.config.cli
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [stereox.config.view :as view])
  (:gen-class))

(def ^:private cli-options
  [["-i" "--camera-id ID..." "Camera ID (one or more)"
    :id :ids
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

   ["-z" "--buffer-size NUMBER" "Buffer size (frames)"
    :id :buffer
    :default 2
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 0 %) "Must be a positive integer"]]

   ["-f" "--fps FPS" "Camera FPS"
    :default 30
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 300) "Must be a number between 0 and 300"]]

   ["-m" "--matcher MATCHER" "Stereo matcher algorithm [ cpu-bm | cpu-sgbm | gpu-sgbm ]"
    :default :cpu-bm
    :default-desc "cpu-bm"
    :parse-fn #(-> % (str) (.trim) (.toLowerCase) (keyword))
    :validate [#(some (fn [v] (= % v)) [:cpu-bm :cpu-sgbm :cuda-bm :cuda-sgbm])
               "Must be one of: [ cpu-bm | cpu-sgbm | cuda-bm | cuda-sgbm ]"]]

   [nil "--config FOLDER_NAME" "Folder with configuration files"
    :id :config-folder
    :default (io/file "config")
    :parse-fn #(io/file %)
    :validate [#(or (not (.exists %)) (.isDirectory %)) "File should be DIRECTORY or not exist"]]

   [nil "--codec CODEC" "Camera output codec"
    :default [\M \J \P \G]
    :default-desc "MJPG"
    :parse-fn #(-> (str %) .trim .toUpperCase .toCharArray seq vec)
    :validate [#(= 4 (count %)) "Must be 4 chars codec code, eg. MJPG"]]

   [nil "--help"]])

(defn- usage [options-summary]
  (->> ["StereoX - Stereo Vision, CONFIGURATION module."
        ""
        "For proper configuration first check your camera allowed properties: "
        "[  $ v4l2-ctl -d \"/dev/video${ID}\" --list-formats-ext  ]"
        ""
        "Options:" options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str " - " (string/join "\n - " errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn- validate-args [args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options :in-order true)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (str "ERROR:\n" (error-msg errors) "\n\n" (usage summary))}

      :else
      {:options options})))

(defn pattern-matching [args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (view/start-gui options))))