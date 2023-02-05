(ns stereox.cv.stereo-normalizer
  (:import (clojure.lang PersistentVector)
           (org.opencv.core Mat)
           (org.opencv.imgproc Imgproc)
           (stereox.serialization.calibration CalibrationData CameraData)
           )
  (:gen-class))

(defrecord ImageWrapper
  [^String id
   ^Mat image])

(defn- find-camera-data
  "Find the right camera data for image wrapper"
  {:tag    CameraData
   :static true}
  [^ImageWrapper img ^CalibrationData cdt]
  (first
    (filter (fn [^CameraData data]
              (.equalsIgnoreCase (str (:id data))
                                 (:id img)))
            (:camera_data cdt))))

(defn rectify_image
  "Rectify stereo image using calibration data."
  {:tag    Mat
   :static true}
  [^CameraData data ^Mat image]
  (let [output (Mat.)]
    (Imgproc/remap image
                   output
                   (:undistortion_map data)
                   (:rectification_map data)
                   Imgproc/INTER_NEAREST)
    output))

(defprotocol StereoNormalizer
  "Rectification and other useful stuff"

  (rectify [_ images]
    "Rectify stereo images.
    Expects:
      ImageWrapper[] or Mat[]
    Returns:
      org.opencv.core.Mat[]"))

(deftype SimpleStereoNormalizer
  [^CalibrationData calibration_data]
  StereoNormalizer

  (rectify [_ images]
    (map (fn [^ImageWrapper image]
           (rectify_image
             (find-camera-data image calibration_data)
             (:image image)))
         images)))

(deftype OrderedStereoNormalizer
  [^CalibrationData calibration_data
   order]
  StereoNormalizer

  (rectify [_ images]
    (map (fn [^Mat image]
           (rectify_image
             (find-camera-data image calibration_data)
             (:image image)))
         ;a[]: [ {:id 1} {:id 2} ...]
         ;b[]: [ img1 img2 ...]
         ;o[]: [ {:id 1 :image img1} ...]
         (map (fn [a b]
                (assoc a :image b))
              order images)))
  )

(defn create-normalizer
  "Create new instance of StereoNormalizer"
  {:tag    StereoNormalizer
   :static true}

  ([^CalibrationData calibration_data]
   (->SimpleStereoNormalizer calibration_data))

  ([^CalibrationData calibration_data
    ^PersistentVector ids]
   (->OrderedStereoNormalizer calibration_data
                              (map (fn [v] {:id v})
                                   ids))))