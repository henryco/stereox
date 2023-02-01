(ns stereox.utils.commons
  (:import (java.io DataInput DataOutput File)
           (java.nio ByteBuffer)
           (org.opencv.core CvType Mat MatOfByte MatOfInt MatOfPoint3f Point3 Rect Size)
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

(defn write-8US
  "Write matrix of BYTE to byte array"
  [^Mat mat]
  (let [size (int (* (.total mat)
                     (.channels mat)))
        buffer (ByteBuffer/allocate size)
        array (byte-array size)]
    (.get mat 0 0 array)
    (.put buffer array)
    (.array buffer)))

(defn write-16US
  "Write matrix of SHORT to byte array"
  [^Mat mat]
  (let [size (int (* (.total mat)
                     (.channels mat)))
        buffer (ByteBuffer/allocate (* (.elemSize mat) size))
        array (short-array size)]
    (.get mat 0 0 array)
    (doall (for [i (range size)]
             (.putShort buffer (aget array i))))
    (.array buffer)))

(defn write-32S
  "Write matrix of INT to byte array"
  [^Mat mat]
  (let [size (int (* (.total mat)
                     (.channels mat)))
        buffer (ByteBuffer/allocate (* (.elemSize mat) size))
        array (int-array size)]
    (.get mat 0 0 array)
    (doall (for [i (range size)]
             (.putInt buffer (aget array i))))
    (.array buffer)))

(defn write-32F
  "Write matrix of FLOAT to byte array"
  [^Mat mat]
  (let [size (int (* (.total mat)
                     (.channels mat)))
        buffer (ByteBuffer/allocate (* (.elemSize mat) size))
        array (float-array size)]
    (.get mat 0 0 array)
    (doall (for [i (range size)]
             (.putFloat buffer (aget array i))))
    (.array buffer)))

(defn write-64F
  "Write matrix of DOUBLE to byte array"
  [^Mat mat]
  (let [size (int (* (.total mat)
                     (.channels mat)))
        buffer (ByteBuffer/allocate (* (.elemSize mat) size))
        array (double-array size)]
    (.get mat 0 0 array)
    (doall (for [i (range size)]
             (.putDouble buffer (aget array i))))
    (.array buffer)))

(defn mat-by-depth-type
  "Resolve required array type and fill matrix"
  {:tag    Mat
   :static true}
  [type array ^Mat mat]
  (let [size (- (alength array) 16)
        copy (let [c (byte-array size)]
               (System/arraycopy array 16 c 0 size) c)
        buff (ByteBuffer/wrap copy)]
    (if (or (= type CvType/CV_8U)
            (= type CvType/CV_8S))
      (.put mat 0 0 (.array buff)))
    (if (or (= type CvType/CV_16U)
            (= type CvType/CV_16S))
      (let [dest (short-array (int (/ size 2)))]
        (loop [i 0]
          (when (.hasRemaining buff)
            (aset dest i (.getShort buff))
            (recur (+ i 1))))
        (.put mat 0 0 dest)))
    (if (= type CvType/CV_32S)
      (let [dest (int-array (int (/ size 4)))]
        (loop [i 0]
          (when (.hasRemaining buff)
            (aset dest i (.getInt buff))
            (recur (+ i 1))))
        (.put mat 0 0 dest)))
    (if (= type CvType/CV_32F)
      (let [dest (float-array (int (/ size 4)))]
        (loop [i 0]
          (when (.hasRemaining buff)
            (aset dest i (.getFloat buff))
            (recur (+ i 1))))
        (.put mat 0 0 dest)))
    (if (= type CvType/CV_64F)
      (let [dest (double-array (int (/ size 8)))]
        (loop [i 0]
          (when (.hasRemaining buff)
            (aset dest i (.getDouble buff))
            (recur (+ i 1))))
        (.put mat 0 0 dest)))
    mat))

(defn array-by-depth-type
  "Resolve matrix type and writes to byte array"
  {:static true}
  [^Integer type ^Mat mat]
  (if (or (= type CvType/CV_8U)
          (= type CvType/CV_8S))
    (write-8US mat)
    (if (or (= type CvType/CV_16U)
            (= type CvType/CV_16S))
      (write-16US mat)
      (if (= type CvType/CV_32S)
        (write-32S mat)
        (if (= type CvType/CV_32F)
          (write-32F mat)
          (if (= type CvType/CV_64F)
            (write-64F mat)
            (throw (Exception. "Unsupported matrix type!!!"))))))))

(defn mat-to-bytes
  "Converts cv:matrix to byte array"
  {:tag    bytes
   :static true}
  [^Mat mat]
  (let [type (.type mat)
        dept (.depth mat)
        rows (.rows mat)
        cols (.cols mat)
        array (array-by-depth-type dept mat)]
    (.array
      (doto (ByteBuffer/allocate (+ (alength array) 16))
        (.putInt type)
        (.putInt dept)
        (.putInt rows)
        (.putInt cols)
        (.put ^bytes array)))))

(defn bytes-to-mat
  "Converts byte array to cv:matrix"
  {:tag    Mat
   :static true}
  [^bytes bytes]
  (let [buffer (ByteBuffer/wrap bytes)
        type (.getInt buffer)
        dept (.getInt buffer)
        rows (.getInt buffer)
        cols (.getInt buffer)]
    (mat-by-depth-type dept
                       (.array buffer)
                       (Mat. rows
                             cols
                             type))))

(defn write-bytes
  "Write byte array to DataOutput"
  {:static true}
  [^DataOutput os ^bytes bytes]
  (.writeInt os (alength bytes))
  (.write os ^bytes bytes))

(defn read-bytes
  "Read byte array from DataInput"
  {:tag    bytes
   :static true}
  [^DataInput is]
  (let [size (.readInt is)
        bytes (byte-array size)]
    (.readFully is bytes)
    bytes))