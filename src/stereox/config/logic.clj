(ns stereox.config.logic
  (:require [taoensso.timbre :as log]
            [stereox.serialization.utils :as su]
            [stereox.serialization.calibration :as sc])
  (:gen-class))

(def ^:private *calibration
  "Atom[CalibrationData]"
  (atom nil))

(defn- read-calibration-data [& {:keys [config-folder
                                        width height ids]}]
  (sc/calibration-from-file
    (first (su/list-candidates config-folder width height
                               su/CALIB_POSTFIX ids))))

(defn configure [& {:as args}]
  (log/info (pr-str args))
  (reset! *calibration (read-calibration-data args))

  )