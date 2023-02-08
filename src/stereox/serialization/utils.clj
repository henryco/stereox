(ns stereox.serialization.utils
  (:import (clojure.lang PersistentVector)
           (java.io File FileNotFoundException FilenameFilter)
           (java.text SimpleDateFormat)
           (java.util Date))
  (:gen-class))

(def CALIB_POSTFIX ".sxpcd")
(def CALIB_SOLO_POSTFIX ".sxscd")

(defn prep-dirs
  "Creates directory if not exists"
  {:static true}
  [^File dir]
  (if (not (.exists dir))
    (.mkdirs dir)))

(defn prepare-dir-name
  "Creates directory name for configuration data"
  {:static true}
  [width height ids]
  (reduce #(str %1 "_" %2)
          (str (int width) "x" (int height))
          ids))

(defn prepare-calib-name
  "Creates file name for calibration data"
  {:static true}
  [ids]
  (str
    (reduce #(str %1 "_" %2)
            (.format (SimpleDateFormat. "yyyyMMddHHmmSSS")
                     (Date.))
            ids)
    CALIB_POSTFIX))

(defn check-dir-exists
  "Checks if directory exists"
  {:static true}
  [^File file]
  (if (nil? file)
    (throw (FileNotFoundException. "Directory not provided!")))
  (if (not (.exists file))
    (throw (FileNotFoundException. (str "Directory "
                                        (.getAbsolutePath file)
                                        "does not exists!")))))

(defn lex-cont-count
  {:static true
   :tag    Integer}
  [^String s words]
  (reduce (fn [o n]
            (if (.contains s (str n))
              (+ o 1)
              o)
            ) 0 words))

(defn list-candidates
  "Returns file candidates sorted lexically descend."
  {:static true
   :tag    PersistentVector}
  [^File parent width height postfix & ids]
  (check-dir-exists parent)
  (let [id_list (map #(str "_" %) (flatten ids))
        files (.listFiles parent (reify FilenameFilter
                                   (accept [_ _ name]
                                     (.endsWith name
                                                postfix))))
        files (sort (fn [^File a ^File b]
                      (let [an (.getName a)
                            bn (.getName b)
                            anc (lex-cont-count an id_list)
                            bnc (lex-cont-count bn id_list)
                            p_r (Integer/compare bnc anc)]
                        (if (= 0 p_r)
                          (.compareTo b a)
                          p_r)))
                    files)
        ]
    (if (> (count files) 0)
      files
      (let [a (File. parent (str width "x" height (apply str id_list)))
            b (File. parent (str width "x" height (apply str (reverse id_list))))
            next (if (.exists a) a (if (.exists b) b nil))]
        (if (nil? next)
          []
          (recur next width height postfix (flatten ids)))))))