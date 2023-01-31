(ns stereox.utils.commons
  (:import (java.io File)
           (java.nio ByteBuffer)
           (org.opencv.core Mat MatOfByte MatOfInt MatOfPoint3f Point3 Rect Size)
           (org.opencv.imgcodecs Imgcodecs)
           (org.opencv.imgproc Imgproc))
  (:gen-class))

(defn prep-dirs
  "Creates directory if not exists"
  {:static true}
  [^File dir]
  (if (not (.exists dir))
    (.mkdirs dir)))

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

(defn image-mat-to-bytes
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

(defn size-to-bytes
  "Converts cv:size to byte array"
  {:tag    bytes
   :static true}
  [^Size size]
  (.array
    (doto (ByteBuffer/allocate 16)
      (.putDouble (.width size))
      (.putDouble (.height size)))))

(defn bytes-to-size
  "Converts byte array to cv:size"
  {:tag    Size
   :static true}
  [^bytes bytes]
  (let [buffer (ByteBuffer/wrap bytes)
        w (.getDouble buffer)
        h (.getDouble buffer)]
    (Size. w h)))

(defn rect-to-bytes
  "Converts cv:rect to byte array"
  {:tag    bytes
   :static true}
  [^Rect rect]
  (.array
    (doto (ByteBuffer/allocate 16)
      (.putInt (.x rect))
      (.putInt (.y rect))
      (.putInt (.width rect))
      (.putInt (.height rect)))))

(defn bytes-to-rect
  "Converts byte array to cv:rect"
  {:tag    Rect
   :static true}
  [^bytes bytes]
  (let [buffer (ByteBuffer/wrap bytes)
        x (.getInt buffer)
        y (.getInt buffer)
        w (.getInt buffer)
        h (.getInt buffer)]
    (Rect. x y w h)))

(defn mat-to-bytes
  "Converts cv:matrix to byte array"
  {:tag    bytes
   :static true}
  [^Mat mat]
  (let [rows (.rows mat)
        cols (.cols mat)
        type (.type mat)
        size (* (.total mat)
                (.elemSize mat))
        bytes (byte-array size)]
    (.get mat 0 0 bytes)
    (.array
      (doto (ByteBuffer/allocate (+ size 12))
        (.putInt rows)
        (.putInt cols)
        (.putInt type)
        (.put bytes)))))

(defn bytes-to-mat
  "Converts byte array to cv:matrix"
  {:tag    Mat
   :static true}
  [^bytes bytes]
  (let [buffer (ByteBuffer/wrap bytes)
        rows (.getInt buffer)
        cols (.getInt buffer)
        type (.getInt buffer)
        array (.array buffer)
        mat (Mat. rows cols type)
        length (- (alength array) 12)]
    (.put mat 0 0 array 12 length)
    mat))