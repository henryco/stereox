(ns stereox.cv.stereo-camera
  (:require [clojure.walk :as cw]
            [stereox.utils.commons :as commons])
  (:import (clojure.lang Atom PersistentVector)
           (org.opencv.core Mat)
           (org.opencv.videoio VideoCapture VideoWriter Videoio))
  (:gen-class))

(def ^:private *AUTO_EXPOSURE_ON
  (atom 3))

(def ^:private *AUTO_EXPOSURE_OFF
  (atom 1))

(defn set-auto-exposure-on-value
  {:static true} [v]
  (reset! *AUTO_EXPOSURE_ON v))

(defn set-auto-exposure-off-value
  {:static true} [v]
  (reset! *AUTO_EXPOSURE_OFF v))

(defn force-auto-exposure
  {:static true} []
  ; TODO
  )

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
   ^Integer buffer
   ^Integer sharpness
   ^Integer iso])

(defrecord CameraIO
  [^PersistentVector capture
   ^Integer width
   ^Integer height])

(defprotocol IStereoCamera
  "Interface for Stereo pair camera"

  (unify [_]
    "Unify stereo camera properties")

  (re-init [_ ^CameraProperties properties]
    "Reinitialize stereo camera with properties")

  (setup [_ map] [_ k v]
    "Update stereo camera parameter (key value) or map")

  (options [_]
    "Returns:
      Tweakable options vector [[id min max]...]")

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

                                         (if (some? (:sharpness properties))
                                           [Videoio/CAP_PROP_SHARPNESS (:sharpness properties)] [])

                                         (if (some? (:iso properties))
                                           [Videoio/CAP_PROP_ISO_SPEED (:iso properties)] [])

                                         (if (some? (:exposure properties))
                                           [Videoio/CAP_PROP_AUTO_EXPOSURE @*AUTO_EXPOSURE_ON
                                            Videoio/CAP_PROP_EXPOSURE (:exposure properties)]
                                           [Videoio/CAP_PROP_AUTO_EXPOSURE @*AUTO_EXPOSURE_OFF])

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

(def C_PROPS
  {:buffer     Videoio/CAP_PROP_BUFFERSIZE
   :sharpness  Videoio/CAP_PROP_SHARPNESS
   :iso        Videoio/CAP_PROP_ISO_SPEED
   :auto-exp   Videoio/CAP_PROP_AUTO_EXPOSURE
   :exposure   Videoio/CAP_PROP_EXPOSURE
   :brightness Videoio/CAP_PROP_BRIGHTNESS
   :gamma      Videoio/CAP_PROP_GAMMA
   :gain       Videoio/CAP_PROP_GAIN
   :fps        Videoio/CAP_PROP_FPS
   })

(def C_OPTS
  [["buffer" 1]
   ["exposure" -1]
   ["brightness" 0]
   ["sharpness" 0]
   ["gamma" 0]
   ["gain" 0]
   ["fps" 0]
   ["iso" 0]
   ])

(defn- find-max-values
  {:static true}
  [^VideoCapture capture]
  (let [auto_exposure (.get capture Videoio/CAP_PROP_AUTO_EXPOSURE)]
    (let [found (filter
                  #(true? (:ok %))
                  (map
                    (fn [[key val]]
                      (if (= key :exposure)
                        (.set capture Videoio/CAP_PROP_AUTO_EXPOSURE @*AUTO_EXPOSURE_OFF))
                      (let [initial (.get capture val)
                            supported (.set capture val Short/MAX_VALUE)
                            maximal (.get capture val)]
                        (.set capture val initial)
                        (if (= key :exposure)
                          (.set capture Videoio/CAP_PROP_AUTO_EXPOSURE auto_exposure))
                        {:name (.replaceFirst (str key) ":" "")
                         :ok   supported
                         :max  maximal}))
                    (filter
                      (fn [[k _]]
                        (and (not= k :auto-exp)
                             (not= k :buffer)))
                      C_PROPS)))]
      (apply merge
             (map (fn [{:keys [name max]}]
                    {(keyword name) max})
                  found))
      )))

(defn- create-allowed-opts
  {:static true}
  [^VideoCapture capture]
  (let [allowed (find-max-values capture)]
    (conj
      (vec (filter (fn [[_ _ max]]
                     (some? max))
                   (map (fn [[name min]]
                          [name min (get allowed (keyword name))])
                        C_OPTS)))
      ["buffer" 1 10])))

(defrecord StereoCamera [^Atom *io ^Atom *props ^Atom *allowed]
  IStereoCamera

  (options [_]
    @*allowed)

  (unify [this]
    (let [[l r] (:capture @*io)]
      (run! (fn [[k v]]
              (let [lv (.get ^VideoCapture l v)
                    rv (.get ^VideoCapture r v)]
                (swap! *props assoc k lv)
                (if (not= lv rv)
                  (setup this k lv))))
            C_PROPS)))

  (setup [this m]
    (re-init this (into {} (map (fn [[k v]]
                                  [k (if (>= v 0) v nil)])
                                (merge @*props m)))))

  (setup [_ k v]
    (let [vc (:capture @*io)
          kk (if (keyword? k) k (keyword k))
          vv (if (>= v 0) (int v) nil)
          pp (assoc @*props kk vv)]
      (case kk
        :sharpness (if (some? vv)
                     (run! #(.set % Videoio/CAP_PROP_SHARPNESS vv)
                           vc))
        :iso (if (some? vv)
               (run! #(.set % Videoio/CAP_PROP_ISO_SPEED vv)
                     vc))
        :auto-exp (if (some? vv)
                    (run! #(.set % Videoio/CAP_PROP_AUTO_EXPOSURE vv)
                          vc))
        :exposure (if (some? vv)
                    (run! (fn [c]
                            (.set c Videoio/CAP_PROP_AUTO_EXPOSURE @*AUTO_EXPOSURE_OFF)
                            (.set c Videoio/CAP_PROP_EXPOSURE vv))
                          vc)
                    (run! #(.set % Videoio/CAP_PROP_AUTO_EXPOSURE @*AUTO_EXPOSURE_ON)
                          vc))
        :buffer (if (some? vv)
                  (run! #(.set % Videoio/CAP_PROP_BUFFERSIZE vv)
                        vc))
        :brightness (if (some? vv)
                      (run! #(.set % Videoio/CAP_PROP_BRIGHTNESS vv)
                            vc))
        :gamma (if (some? vv)
                 (run! #(.set % Videoio/CAP_PROP_GAMMA vv)
                       vc))
        :gain (if (some? vv)
                (run! #(.set % Videoio/CAP_PROP_GAIN vv)
                      vc))
        :fps (if (some? vv)
               (run! #(.set % Videoio/CAP_PROP_FPS vv)
                     vc)))
      (reset! *props pp)))

  (re-init [this properties]
    (dosync
      (release this)
      (reset! *io (cw/postwalk
                    #(if (record? %) (into {} %) %)
                    (init-camera properties)))
      (reset! *props properties)
      (unify this)))

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
  (let [io (cw/postwalk
             #(if (record? %) (into {} %) %)
             (init-camera properties))
        allowed (create-allowed-opts
                  (nth (:capture io) 0))
        camera (->StereoCamera
                 (atom io)
                 (atom properties)
                 (atom allowed))]
    (unify camera)
    camera))