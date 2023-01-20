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
         :alive  true
         :camera {:viewport {:width 0 :height 0 :min-x 0 :min-y 0}
                  :capture  ^VideoCapture []
                  :image    ^Image []
                  }
         }))

(defn create-codec [[a b c d]]
  (VideoWriter/fourcc a b c d))

(defn prep-dirs [^File dir]
  (if (not (.exists dir))
    (.mkdir dir)))

(defn init-camera [ids width height codec gain gamma brightness fps exposure]
  ; setup Image canvas settings
  (swap! *state assoc-in [:camera :viewport]
         {:width width :height height :min-x 0 :min-y 0})
  ; setup Video capture vector
  (swap! *state assoc-in [:camera :capture]
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
                 capture)                                   ; return initialized video capture
              ids)                                          ; return captures for every id
         )
  )

(defn mat2image ^Image [^Mat mat]
  (let [bytes (MatOfByte.)]
    (Imgcodecs/imencode ".png" mat bytes)
    (Image. (ByteArrayInputStream. (.toArray bytes)))))

(defn grab-capture []
  (let [captures (-> @*state :camera :capture)]
    (run! #(.grab ^VideoCapture %) captures)
    (-> #(let [mat (Mat.)]
           (.retrieve ^VideoCapture % mat)
           (mat2image mat))
        (map captures))))

(def ^:private timer
  (proxy [AnimationTimer] []
    (handle [_]
      (if (:alive @*state)
        (swap! *state assoc-in [:camera :image] (grab-capture))
        ;TODO: MORE
        ))))

(defn shutdown [& _]
  (try
    (swap! *state assoc :alive false)
    (run! #(.release %) (:capture (:camera @*state)))
    (catch Exception e (.printStackTrace e)))
  (try
    (Platform/exit)
    (catch Exception e (.printStackTrace e))
    (finally (System/exit 0))))

(defn render-images [{:keys [camera]}]
  {:fx/type    :h-box
   :min-width  (-> camera :viewport :width (* 2))
   :min-height (-> camera :viewport :height)
   :children   (-> #(merge {:fx/type        :image-view
                            :viewport       (:viewport camera)
                            :preserve-ratio true
                            :image          %})
                   (map (filter #(not (nil? %))
                                (:image camera))))
   })

(defn root [state]
  {:fx/type          :stage
   :showing          true
   :resizable        false
   :title            (:title state)
   :on-close-request shutdown
   :scene            {:fx/type :scene
                      :root    {:fx/type  :v-box
                                :children [(merge state {:fx/type render-images})]
                                }
                      }
   })

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