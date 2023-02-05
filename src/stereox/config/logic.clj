(ns stereox.config.logic
  (:require [taoensso.timbre :as log]
            [stereox.cv.stereo-normalizer :as nrm]
            [stereox.cv.stereo-camera :as camera]
            [stereox.cv.block-matching :as bm]
            [stereox.serialization.utils :as su]
            [stereox.serialization.calibration :as sc])
  (:import (clojure.lang Atom)
           (stereox.cv.block_matching BlockMatcher)
           (stereox.cv.stereo_camera StereoCamera)
           (stereox.cv.stereo_normalizer StereoNormalizer)
           (stereox.serialization.calibration CalibrationData))
  (:gen-class))


(defrecord LogicState
  [^StereoNormalizer normalizer
   ^CalibrationData calibration
   ^BlockMatcher block-matcher
   ^StereoCamera camera])

(defn- wrap-captured [images ^CalibrationData data]

  )

(defprotocol ConfigurationLogic
  "Configuration logic interface"
  ;TODO
  (state [_]
    "Returns:
      stereox.config.logic.LogicState")

  (render-frame [_]
    "Grabs frame from stereo camera and
    calculates disparity map.
    Returns:
      org.opencv.core.Mat")
  )

(deftype ConfigLogic [^Atom *state]
  ConfigurationLogic
  ; TODO

  (state [_]
    (map->LogicState @*state))

  (render-frame [_]
    (let [captured (camera/capture (:camera @*state))

          ]

      ))
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
  (let [c_data (read-calibration-data args)]
    (->ConfigLogic
      (atom (map->LogicState {:normalizer    (nrm/create-normalizer c_data)
                              :block-matcher (bm/create-cpu-stereo-bm)
                              :camera        (camera/create args)
                              :calibration   c_data
                              })))))