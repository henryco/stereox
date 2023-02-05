(ns stereox.cv.stereo-camera
  (:require [clojure.walk :as cw]
            [stereox.utils.commons :as commons])
  (:import (clojure.lang Atom PersistentVector)
           (nu.pattern OpenCV)
           (org.opencv.core Mat)
           (org.opencv.videoio VideoCapture VideoWriter Videoio))
  (:gen-class))

(OpenCV/loadShared)

(defrecord CameraProperties
  [^PersistentVector ids
   ^Integer width
   ^Integer height
   ^chars codec
   ^Integer gain
   ^Integer gamma
   ^Integer brightness
   ^Integer fps
   ^Integer exposure
   ^Integer buffer])

(defrecord CameraIO
  [^PersistentVector capture
   ^Integer width
   ^Integer height])

(defprotocol IStereoCamera
  "Interface for Stereo pair camera"

  (re-init [this ^CameraProperties properties]
    "Reinitialize stereo camera with properties")

  (params [_]
    "Returns:
      stereox.cv.stereo-camera.CameraProperties")

  (capture [_]
    "Returns org.opencv.core.Mat[] or nil.
    Asynchronously grab and retrieve data from both cameras.
    BLOCKING IO OPERATION")

  (release [_]
    "Cleanup allocated resources"))

(defn create-codec
  "Returns int video codec code.
  Expects array of chars, i.e. [\\M \\J \\P \\G]"
  {:tag    Integer
   :static true}
  [^chars [^char a ^char b ^char c ^char d]]
  (VideoWriter/fourcc a b c d))

(defn init-executor-pools
  "Warmup system thread pool for futures"
  [captures]
  (let [status (-> #(future (str (System/currentTimeMillis) " [" % "]: OK")) (map captures))]
    (run! #(deref %) status)))

(defn init-camera
  "Initialize stereo camera.
  Returns CameraIO."
  {:static true
   :tag    CameraIO}
  [^CameraProperties properties]
  (if (or (nil? (:ids properties))
          (= 0 (count (:ids properties))))
    (throw (Exception. "Capture identifiers not provided!")))
  (init-executor-pools (:ids properties))
  (map->CameraIO
    {:height  (:height properties)
     :width   (:width properties)
     :capture (map #(let [capture (VideoCapture.
                                    (Integer/parseInt %)
                                    Videoio/CAP_ANY
                                    (-> [Videoio/CAP_PROP_FOURCC (create-codec (:codec properties))
                                         Videoio/CAP_PROP_FRAME_WIDTH (:width properties)
                                         Videoio/CAP_PROP_FRAME_HEIGHT (:height properties)

                                         (if (some? (:buffer properties))
                                           [Videoio/CAP_PROP_BUFFERSIZE (:buffer properties)] [])

                                         (if (some? (:exposure properties))
                                           [Videoio/CAP_PROP_AUTO_EXPOSURE 1
                                            Videoio/CAP_PROP_EXPOSURE (:exposure properties)]
                                           [Videoio/CAP_PROP_AUTO_EXPOSURE 3])

                                         (if (some? (:brightness properties))
                                           [Videoio/CAP_PROP_BRIGHTNESS (:brightness properties)] [])

                                         (if (some? (:gamma properties))
                                           [Videoio/CAP_PROP_GAMMA (:gamma properties)] [])

                                         (if (some? (:gain properties))
                                           [Videoio/CAP_PROP_GAIN (:gain properties)] [])

                                         Videoio/CAP_PROP_FPS (:fps properties)]
                                        (commons/vecs-to-mat))
                                    )]
                      capture)                              ; return initialized video capture
                   (:ids properties))                       ; return captures for every id
     })
  )

(defn grab-capture
  "Asynchronously grab and retrieve data from both cameras.
  Expects vector of org.opencv.videoio.VideoCapture.
  Returns vector of org.opencv.core.Mat or nil.
  BLOCKING IO OPERATION"
  {:static true}
  [capture]
  (let [grabbed (-> #(future
                       (.grab ^VideoCapture %)
                       ) (map capture))
        ]
    (if (every? #(true? @%) grabbed)
      (let [results (-> #(future
                           (let [mat (Mat.)]
                             (if (.retrieve ^VideoCapture % mat)
                               mat
                               nil)
                             )) (map capture))]
        (if (every? #(some? @%) results)
          (map #(deref %) results)                          ; GRABBED & RETRIEVE: TRUE -> RESULTS
          nil))                                             ; RETRIEVE: FALSE -> NIL
      nil)                                                  ; GRABBED:  FALSE -> NIL
    ))

(defrecord StereoCamera [^Atom *io ^Atom *props]
  IStereoCamera

  (re-init [this properties]
    (dosync
      (release this)
      (reset! *io (cw/postwalk
                    #(if (record? %) (into {} %) %)
                    (init-camera properties)))
      (reset! *props properties)))

  (params [_]
    (map->CameraProperties @*props))

  (capture [_]
    (grab-capture (:capture @*io)))

  (release [_]
    (run! #(.release %) (:capture @*io))))

(defn create
  "Creates new instance of StereoCamera record"
  {:static true
   :tag    StereoCamera}
  [^CameraProperties properties]
  ; basically just (StereoCamera. (atom (into {} io)), but deep copy
  (->StereoCamera
    (atom
      (cw/postwalk
        #(if (record? %) (into {} %) %)
        (init-camera properties)))
    (atom properties)))