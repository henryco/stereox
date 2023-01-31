(ns stereox.calibration.serialization
  (:require [taoensso.nippy :as nippy]
            [stereox.utils.commons :as cc])
  (:import (java.io DataInput DataOutput)
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

(defn write-bytes [^DataOutput os ^bytes bytes]
  (.writeInt os (alength bytes))
  (.write os ^bytes bytes))

(defn read-bytes [^DataInput is]
  (let [size (.readInt is)
        bytes (byte-array size)]
    (.readFully is bytes)
    bytes))

(defn write-camera-data
  "Writes camera data object to output stream"
  {:static true}
  [^CameraData data ^DataOutput os]
  (.writeUTF os (:id data))
  (write-bytes os (cc/mat-to-bytes (:camera_matrix data)))
  (write-bytes os (cc/mat-to-bytes (:distortion_coefficients data)))
  (write-bytes os (cc/mat-to-bytes (:rectification_transformation data)))
  (write-bytes os (cc/mat-to-bytes (:projection_matrix data)))
  (write-bytes os (cc/rect-to-bytes (:valid_pixels_roi data)))
  (write-bytes os (cc/mat-to-bytes (:undistortion_map data)))
  (write-bytes os (cc/mat-to-bytes (:rectification_map data))))

(defn read-camera-data
  "Reads camera data object from input stream"
  {:tag    CameraData
   :static true}
  [^DataInput is]
  (->CameraData (.readUTF is)
                (cc/bytes-to-mat (read-bytes is))
                (cc/bytes-to-mat (read-bytes is))
                (cc/bytes-to-mat (read-bytes is))
                (cc/bytes-to-mat (read-bytes is))
                (cc/bytes-to-rect (read-bytes is))
                (cc/bytes-to-mat (read-bytes is))
                (cc/bytes-to-mat (read-bytes is))))

(defn write-calibration-data
  "Writes calibration data object to output stream"
  {:static true}
  [^CalibrationData data ^DataOutput os]
  (write-bytes os (cc/size-to-bytes (:size data)))
  (write-bytes os (cc/mat-to-bytes (:rotation_matrix data)))
  (write-bytes os (cc/mat-to-bytes (:translation_matrix data)))
  (write-bytes os (cc/mat-to-bytes (:essential_matrix data)))
  (write-bytes os (cc/mat-to-bytes (:fundamental_matrix data)))
  (write-bytes os (cc/mat-to-bytes (:disparity_to_depth_matrix data)))
  (write-bytes os (cc/mat-to-bytes (:disparity_to_depth_matrix_v2 data)))
  (let [sequence (:camera_data data)]
    (.writeInt os (count sequence))
    (run! #(write-camera-data % os) sequence)))

(defn read-calibration-data
  "Reads calibration data object from input stream"
  {:tag    CalibrationData
   :static true}
  [^DataInput is]
  (->CalibrationData (cc/bytes-to-size (read-bytes is))
                     (cc/bytes-to-mat (read-bytes is))
                     (cc/bytes-to-mat (read-bytes is))
                     (cc/bytes-to-mat (read-bytes is))
                     (cc/bytes-to-mat (read-bytes is))
                     (cc/bytes-to-mat (read-bytes is))
                     (cc/bytes-to-mat (read-bytes is))
                     (let [sequence (range (.readInt is))]
                       (doall (map (fn [& _]
                                     (read-camera-data is))
                                   sequence)))))

(nippy/extend-freeze CameraData :serialization/camera-data [wsso s] (write-camera-data o s))
(nippy/extend-thaw :serialization/camera-data [s] (read-camera-data s))

(nippy/extend-freeze CalibrationData :serialization/calibration-data [wsso s] (write-calibration-data o s))
(nippy/extend-thaw :serialization/calibration-data [s] (read-calibration-data s))