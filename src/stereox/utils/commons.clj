(ns stereox.utils.commons
  (:import (java.io File)
           (org.opencv.core Mat MatOfByte MatOfInt)
           (org.opencv.imgcodecs Imgcodecs)
           (org.opencv.imgproc Imgproc)))

(defn prep-dirs
  "Creates directory if not exists"
  {:static true}
  [^File dir]
  (if (not (.exists dir))
    (.mkdir dir)))

(defn img-copy
  "Copy image matrix, optionally apply color change."
  {:tag    Mat
   :static true}
  ([^Mat image ^Integer code]
   (let [output (Mat.)]
     (Imgproc/cvtColor image output code)
     output))
  ([^Mat image]
   (let [output (Mat.)]
     (.copyTo image
              output)
     output)))

(defn vecs-to-mat
  "Transforms and flatten vector to MatOfInt"
  {:tag    MatOfInt
   :static true}
  [v]
  (-> v (flatten) (vec) (int-array) (MatOfInt.)))

(defn mat-to-bytes
  "Transforms Mat to java array of bytes."
  {:tag    bytes
   :static true}
  [^Mat mat]
  (let [bytes (MatOfByte.)]
    (Imgcodecs/imencode ".png" mat bytes
                        (-> [Imgcodecs/IMWRITE_PNG_COMPRESSION 0
                             Imgcodecs/IMWRITE_PNG_STRATEGY Imgcodecs/IMWRITE_PNG_STRATEGY_FIXED
                             ] (vecs-to-mat)))
    (.toArray bytes)))