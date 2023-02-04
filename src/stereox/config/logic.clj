(ns stereox.config.logic
  (:require [taoensso.timbre :as log]
            [stereox.cv.stereo-camera :as camera]
            [stereox.cv.block-matching :as bm]
            [stereox.serialization.utils :as su]
            [stereox.serialization.calibration :as sc])
  (:gen-class)
  (:import (clojure.lang Atom)
           (stereox.cv.block_matching BlockMatcher)
           (stereox.cv.stereo_camera StereoCamera)
           (stereox.serialization.calibration CalibrationData)))


(defrecord LogicState
  [^CalibrationData calibration
   ^BlockMatcher block-matcher
   ^StereoCamera camera])

(defprotocol ConfigurationLogic
  "Configuration logic interface"
  ;TODO
  (state [_]
    "Returns internal state (copy)")
  )

(deftype ConfigLogic [^Atom *state]
  ConfigurationLogic
  ; TODO

  (state [_]
    (map->LogicState @*state))
  )

(defn- read-calibration-data [& {:keys [config-folder
                                        width height ids]}]
  (sc/calibration-from-file
    (first (su/list-candidates config-folder width height
                               su/CALIB_POSTFIX ids))))

(defn configure
  "Creates new instance of ConfigLogic"
  {:static true
   :tag    ConfigLogic}
  [& {:as args}]
  (log/info (pr-str args))
  (->ConfigLogic
    (atom (map->LogicState {:block-matcher (bm/create-cpu-stereo-bm)
                            :calibration   (read-calibration-data args)
                            :camera        (camera/create args)
                            }))))