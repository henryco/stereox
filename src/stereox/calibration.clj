(ns stereox.calibration
  (:require [cljfx.api :as fx])
  (:import (java.io ByteArrayInputStream File)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)
           (javafx.scene.image Image)
           (nu.pattern OpenCV)
           (org.opencv.core Mat MatOfByte MatOfInt)
           (org.opencv.imgcodecs Imgcodecs)
           (org.opencv.videoio VideoCapture VideoWriter Videoio))
  (:gen-class))

(OpenCV/loadShared)

(def ^:private *state
  (atom {:title  "StereoX calibration"
         :camera {:viewport {:width 0 :height 0 :min-x 0 :min-y 0}
                  :image    nil}
         ; TODO: MORE STATE
         }))

(def ^:private *internal
  (atom {:alive   true
         :capture []                                        ; vector of VideoCaptures
         }))

(defn create-codec [[a b c d]]
  (VideoWriter/fourcc a b c d))

(defn init-camera [ids width height codec gain gamma brightness fps exposure]
  (swap! *state assoc-in [:camera :viewport]
         {:width width :height height :min-x 0 :min-y 0})

  (swap! *internal assoc :capture
         (map #(let [capture (VideoCapture.
                               (Integer/parseInt %)
                               Videoio/CAP_ANY
                               (-> [Videoio/CAP_PROP_FOURCC (create-codec codec)
                                    Videoio/CAP_PROP_FRAME_WIDTH width
                                    Videoio/CAP_PROP_FRAME_HEIGHT height

                                    (if (not (nil? exposure))
                                      [Videoio/CAP_PROP_AUTO_EXPOSURE 1
                                       Videoio/CAP_PROP_EXPOSURE exposure]
                                      [Videoio/CAP_PROP_AUTO_EXPOSURE 3])

                                    (if (not (nil? brightness))
                                      [Videoio/CAP_PROP_BRIGHTNESS brightness] [])

                                    (if (not (nil? gamma))
                                      [Videoio/CAP_PROP_GAMMA gamma] [])

                                    (if (not (nil? gain))
                                      [Videoio/CAP_PROP_GAIN gain] [])

                                    Videoio/CAP_PROP_FPS fps]
                                   (flatten) (vec) (int-array) (MatOfInt.)))]
                 (println "FPS:" (.get capture Videoio/CAP_PROP_FPS))
                 capture) ids))
  )

(defn mat2image ^Image [^Mat mat]
  (let [bytes (MatOfByte.)]
    (Imgcodecs/imencode ".png" mat bytes)
    (Image. (ByteArrayInputStream. (.toArray bytes)))))

(defn get-capture []
  (let [mat (Mat.)
        cap (nth (:capture @*internal) 0)]
    (.read ^VideoCapture cap mat)
    (mat2image mat)))

(defn prep-dirs [^File dir]
  (if (not (.exists dir))
    (.mkdir dir)))

(defn render-image-view [{:keys [camera]}]
  (merge {:fx/type        :image-view
          :viewport       (:viewport camera)
          :preserve-ratio true}
         (let [{img :image} camera]
           (if (nil? img) {} {:image img}))))

(defn render-label [{:keys [title]}]
  {:fx/type :label :text title})

(defn shutdown [& _]
  (try
    (swap! *internal assoc :alive false)
    (run! #(.release %) (:capture @*internal))
    (catch Exception e (.printStackTrace e)))
  (try
    (Platform/exit)
    (catch Exception e (.printStackTrace e))
    (finally (System/exit 0))))

(defn root [state]
  {:fx/type          :stage
   :showing          true
   :resizable        false
   :title            (:title state)
   :on-close-request shutdown
   :scene            {:fx/type :scene
                      :root    {:fx/type  :v-box
                                :children [(merge state {:fx/type render-label})
                                           (merge state {:fx/type render-image-view})]
                                }}})

(def ^:private timer
  (proxy [AnimationTimer] []
    (handle [_]
      (if (:alive @*internal)
        (swap! *state assoc-in [:camera :image] (get-capture))
        ;TODO: MORE
        ))))

(def ^:private renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type root)))

(defn calibrate [& {:keys [rows
                           columns
                           width
                           height
                           codec
                           gamma
                           exposure
                           fps
                           gain
                           brightness
                           square-size
                           output-folder
                           camera-id] :as all}]
  (println (pr-str all))
  (prep-dirs output-folder)
  (init-camera camera-id width height codec gain gamma brightness fps exposure)

  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)

  (.start timer))