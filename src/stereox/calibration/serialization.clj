(ns stereox.calibration.serialization
  (:require [taoensso.nippy :as nippy]
            [stereox.utils.commons :as cc]
            [taoensso.timbre :as log])
  (:import (java.io DataInput DataOutput File FileInputStream FileOutputStream)
           (org.opencv.core Mat Rect Size))
  (:gen-class))

(defrecord CameraData
  [^String id
   ^Mat camera_matrix
   ^Mat distortion_coefficients
   ^Mat rectification_transformation
   ^Mat projection_matrix
   ^Rect valid_pixels_roi
   ^Mat undistortion_map
   ^Mat rectification_map])

(defrecord CalibrationData
  [^Size size
   ^Mat rotation_matrix
   ^Mat translation_matrix
   ^Mat essential_matrix
   ^Mat fundamental_matrix
   ^Mat disparity_to_depth_matrix
   ^Mat disparity_to_depth_matrix_v2
   camera_data])

(defn- write-camera-data
  "Writes camera data object to output stream"
  {:static true}
  [^CameraData data ^DataOutput os]
  (.writeUTF os (:id data))
  (cc/write-bytes os (cc/mat-to-bytes (:camera_matrix data)))
  (cc/write-bytes os (cc/mat-to-bytes (:distortion_coefficients data)))
  (cc/write-bytes os (cc/mat-to-bytes (:rectification_transformation data)))
  (cc/write-bytes os (cc/mat-to-bytes (:projection_matrix data)))
  (cc/write-bytes os (cc/rect-to-bytes (:valid_pixels_roi data)))
  (cc/write-bytes os (cc/mat-to-bytes (:undistortion_map data)))
  (cc/write-bytes os (cc/mat-to-bytes (:rectification_map data))))

(defn- read-camera-data
  "Reads camera data object from input stream"
  {:tag    CameraData
   :static true}
  [^DataInput is]
  (->CameraData (.readUTF is)
                (cc/bytes-to-mat (cc/read-bytes is))
                (cc/bytes-to-mat (cc/read-bytes is))
                (cc/bytes-to-mat (cc/read-bytes is))
                (cc/bytes-to-mat (cc/read-bytes is))
                (cc/bytes-to-rect (cc/read-bytes is))
                (cc/bytes-to-mat (cc/read-bytes is))
                (cc/bytes-to-mat (cc/read-bytes is))))

(defn- write-calibration-data
  "Writes calibration data object to output stream"
  {:static true}
  [^CalibrationData data ^DataOutput os]
  (cc/write-bytes os (cc/size-to-bytes (:size data)))
  (cc/write-bytes os (cc/mat-to-bytes (:rotation_matrix data)))
  (cc/write-bytes os (cc/mat-to-bytes (:translation_matrix data)))
  (cc/write-bytes os (cc/mat-to-bytes (:essential_matrix data)))
  (cc/write-bytes os (cc/mat-to-bytes (:fundamental_matrix data)))
  (cc/write-bytes os (cc/mat-to-bytes (:disparity_to_depth_matrix data)))
  (cc/write-bytes os (cc/mat-to-bytes (:disparity_to_depth_matrix_v2 data)))
  (let [sequence (:camera_data data)]
    (.writeInt os (count sequence))
    (run! #(write-camera-data % os) sequence)))

(defn- read-calibration-data
  "Reads calibration data object from input stream"
  {:tag    CalibrationData
   :static true}
  [^DataInput is]
  (->CalibrationData (cc/bytes-to-size (cc/read-bytes is))
                     (cc/bytes-to-mat (cc/read-bytes is))
                     (cc/bytes-to-mat (cc/read-bytes is))
                     (cc/bytes-to-mat (cc/read-bytes is))
                     (cc/bytes-to-mat (cc/read-bytes is))
                     (cc/bytes-to-mat (cc/read-bytes is))
                     (cc/bytes-to-mat (cc/read-bytes is))
                     (let [sequence (range (.readInt is))]
                       (doall (map (fn [& _]
                                     (read-camera-data is))
                                   sequence)))))

(nippy/extend-freeze CameraData :serialization/camera-data [o s] (write-camera-data o s))
(nippy/extend-thaw :serialization/camera-data [s] (read-camera-data s))

(nippy/extend-freeze CalibrationData :serialization/calibration-data [o s] (write-calibration-data o s))
(nippy/extend-thaw :serialization/calibration-data [s] (read-calibration-data s))

(defn calibration-to-file
  "Writes calibration data to file"
  {:static true}
  [^CalibrationData data ^File file]
  (with-open [o (FileOutputStream. file)]
    (.write o ^bytes (nippy/freeze data))))

(defn calibration-from-file
  "Reads calibration data from file"
  {:tag    CalibrationData
   :static true}
  [^File file]
  (with-open [i (FileInputStream. file)]
    (let [bts (.readAllBytes i)
          rsl (nippy/thaw bts)]
      rsl)))