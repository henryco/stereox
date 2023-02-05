(ns stereox.config.logic
  (:require [taoensso.timbre :as log]
            [stereox.utils.commons :as commons]
            [stereox.cv.stereo-normalizer :as nrm]
            [stereox.cv.stereo-camera :as camera]
            [stereox.cv.block-matching :as bm]
            [stereox.serialization.utils :as su]
            [stereox.serialization.calibration :as sc])
  (:import (clojure.lang Atom IPersistentCollection)
           (java.io File)
           (org.opencv.core Mat)
           (org.opencv.imgproc Imgproc)
           (stereox.cv.block_matching BlockMatcher)
           (stereox.cv.stereo_camera StereoCamera)
           (stereox.cv.stereo_normalizer StereoNormalizer)
           (stereox.serialization.calibration CalibrationData))
  (:gen-class))

(defn- to-gray [images]
  (map #(commons/img-copy % Imgproc/COLOR_BGR2GRAY)
       images))

(defrecord ConfigParameters
  [^IPersistentCollection ids
   ^IPersistentCollection codec
   ^File output-folder
   ^File config-folder
   ^Integer width
   ^Integer height
   ^Integer buffer
   ^Integer fps])

(defrecord LogicState
  [^StereoNormalizer normalizer
   ^CalibrationData calibration
   ^BlockMatcher block-matcher
   ^StereoCamera camera])

(defrecord Frame
  [^IPersistentCollection captured
   ^IPersistentCollection rectified
   ^Mat disparity])

(defprotocol ConfigurationLogic
  "Configuration logic interface"

  (matcher-params [_]
    "Returns block matcher parameters as a map")

  (camera-params [_]
    "Returns stereo camera parameters as a map")

  (state [_]
    "Returns:
      stereox.config.logic.LogicState")

  (render-frame [_]
    "Grabs frame from stereo camera and
    calculates disparity map.
    Returns:
      Frame")

  (save-settings [_]
    "Save camera and block matcher settings")
  )

(deftype ConfigLogic
  [^Atom *state ^Atom *params]
  ConfigurationLogic

  (matcher-params [_]
    (bm/params (:block-matcher @*state)))

  (state [_]
    (map->LogicState @*state))

  (render-frame [_]
    (let [captured (camera/capture (:camera @*state))
          rectified (nrm/rectify (:normalizer @*state)
                                 captured)
          disparity (bm/disparity-map (:block-matcher @*state)
                                      (to-gray rectified))]
      (->Frame captured
               rectified
               disparity)))

  (save-settings [_]
    ; TODO
    )
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
  (let [c_data (read-calibration-data args)
        params (map->ConfigParameters args)]
    (->ConfigLogic
      (atom
        (map->LogicState {:normalizer    (nrm/create-normalizer c_data (:ids params))
                          :block-matcher (bm/create-cpu-stereo-bm)
                          :camera        (camera/create params)
                          :calibration   c_data
                          }))
      (atom params))))