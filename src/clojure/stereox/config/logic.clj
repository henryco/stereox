(ns stereox.config.logic
  (:require [taoensso.timbre :as log]
            [stereox.utils.commons :as commons]
            [stereox.cv.stereo-normalizer :as nrm]
            [stereox.cv.stereo-camera :as camera]
            [stereox.cv.block-matching :as bm]
            [stereox.serialization.utils :as su]
            [stereox.serialization.calibration :as sc])
  (:import (clojure.lang Atom IPersistentCollection Keyword)
           (java.io File)
           (org.opencv.core Mat)
           (org.opencv.imgproc Imgproc)
           (stereox.cv.block_matching BlockMatcher)
           (stereox.cv.stereo_camera StereoCamera)
           (stereox.cv.stereo_normalizer StereoNormalizer)
           (stereox.serialization.calibration CalibrationData))
  (:gen-class))

(defrecord ConfigParameters
  [^IPersistentCollection ids
   ^IPersistentCollection codec
   ^File config-folder
   ^Integer width
   ^Integer height
   ^Integer buffer
   ^Integer fps
   ^Keyword matcher])

(defrecord LogicState
  [^StereoNormalizer normalizer
   ^CalibrationData calibration
   ^BlockMatcher block-matcher
   ^StereoCamera camera])

(defprotocol ConfigurationLogic
  "Configuration logic interface"

  (matcher-options [_]
    "Returns block matcher options [[id min max]...]")

  (matcher-params [_]
    "Returns block matcher parameters as a map")

  (camera-options [_]
    "Returns stereo camera options [[id min max]...]")

  (camera-params [_]
    "Returns stereo camera parameters as a map")

  (state [_]
    "Returns:
      stereox.config.logic.LogicState")

  (render-frame [_ mode]
    "Grabs frame from stereo camera and
    calculates disparity map.
    Params:
      mode - [:disparity_bgr|:disparity|:depth_bgr|:depth|:projection|:left|:right]
    Returns:
      Mat")

  (save-settings [_]
    "Save camera and block matcher settings")
  )

(deftype ConfigLogic
  [^Atom *state ^Atom *params]
  ConfigurationLogic

  (matcher-options [_]
    (bm/options (:block-matcher @*state)))

  (matcher-params [_]
    (bm/params (:block-matcher @*state)))

  (camera-options [_]
    (camera/options (:camera @*state)))

  (camera-params [_]
    (camera/params (:camera @*state)))

  (state [_]
    (map->LogicState @*state))

  (render-frame [_ mode]
    (if-not
      (contains? #{:disparity_bgr :disparity :depth_bgr :depth :projection :left :right} mode)
      (throw (Exception. (str "No such computation type: " mode))))
    (let [captured (camera/capture (:camera @*state))
          rectified (nrm/rectify (:normalizer @*state) captured)
          computed (bm/compute (:block-matcher @*state) rectified)]
      (deref (get computed mode))))

  (save-settings [_]
    ; TODO
    (log/info "saving stereo configuration...")
    )
  )

(defn- read-calibration-data
  {:static true
   :tag    CalibrationData}
  [& {:keys [config-folder width height ids]}]
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
        params (map->ConfigParameters args)
        matrices [(:disparity_to_depth_matrix c_data)
                  (:disparity_to_depth_matrix_v2 c_data)]]
    (->ConfigLogic
      (atom
        (map->LogicState {:block-matcher (bm/create-stereo-matcher (:matcher params) matrices)
                          :normalizer    (nrm/create-normalizer c_data (:ids params))
                          :camera        (camera/create params)
                          :calibration   c_data
                          }))
      (atom params))))