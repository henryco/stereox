(ns stereox.utils.commons
  (:import (java.io File)
           (java.util Arrays)
           (org.opencv.core Mat MatOfByte MatOfInt MatOfPoint3f Point3)
           (org.opencv.imgcodecs Imgcodecs)
           (org.opencv.imgproc Imgproc))
  (:gen-class))

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

(defn obp-matrix
  "Creates MatOfPoint3f"
  {:tag    MatOfPoint3f
   :static true}
  [^Integer cornersHorizontal
   ^Integer cornersVertical]
  (let [size (* cornersHorizontal cornersVertical)
        mat (MatOfPoint3f.)]
    (doall
      (for [j (range size)]
        (let [x (int (/ j cornersHorizontal))
              y (int (mod j cornersVertical))
              p (into-array Point3 [(Point3. x y 0.0)])]
          (.push_back mat (MatOfPoint3f.
                            ^"[Lorg.opencv.core.Point3;" p)))))
    mat))