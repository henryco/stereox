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

(def ^:private *window
  (atom {:width  nil
         :height nil}))

(def ^:private *state
  (atom {:title  "StereoX calibration"
         :alive  true
         :scale  1.
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

(defn init-camera [ids width height codec gain gamma brightness fps exposure buffer]
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
                                    Videoio/CAP_PROP_BUFFERSIZE buffer

                                    (if (some? exposure)
                                      [Videoio/CAP_PROP_AUTO_EXPOSURE 1
                                       Videoio/CAP_PROP_EXPOSURE exposure]
                                      [Videoio/CAP_PROP_AUTO_EXPOSURE 3])

                                    (if (some? brightness)
                                      [Videoio/CAP_PROP_BRIGHTNESS brightness] [])

                                    (if (some? gamma)
                                      [Videoio/CAP_PROP_GAMMA gamma] [])

                                    (if (some? gain)
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

(defn on-win-change []
  (let [s (-> @*state :scale)
        ow (-> @*state :camera :viewport :width)
        oh (-> @*state :camera :viewport :height)
        ww (-> @*window :width)
        hh (-> @*window :height)]
    (if (and (some? ww) (some? hh))
      (let [[dw dh] (map - [ww hh] [(* ow s 2) (* oh s)])]
        (if (< dw dh)
          (swap! *state assoc :scale (/ ww ow 2))
          (swap! *state assoc :scale (/ hh oh)))
        )
      )))

(defn on-win-height-change [v]
  (swap! *window assoc :height v)
  (on-win-change))

(defn on-win-width-change [v]
  (swap! *window assoc :width v)
  (on-win-change))

(defn render-images [{:keys [camera scale]}]
  {:fx/type    :h-box
   :style      {:-fx-background-color :black
                :-fx-alignment        :center}
   :min-width  (-> camera :viewport :width (* 2 scale))
   :min-height (-> camera :viewport :height (* scale))
   :children   (-> #(merge {:fx/type        :image-view
                            :viewport       (:viewport camera)
                            :preserve-ratio true
                            :fit-height     (-> camera :viewport :height (* scale))
                            :image          %})
                   (map (filter #(some? %)
                                (:image camera))))
   })

(defn root [state]
  {:fx/type           :stage
   :showing           true
   :resizable         true
   :title             (:title state)
   :on-close-request  shutdown
   :on-height-changed on-win-height-change
   :on-width-changed  on-win-width-change
   :scene             {:fx/type :scene
                       :root    {:fx/type  :v-box
                                 :style    {:-fx-background-color :black
                                            :-fx-alignment        :center}
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
                           buffer-size
                           camera-id] :as all}]
  (println (pr-str all))
  (prep-dirs output-folder)
  (init-camera camera-id width height codec gain gamma brightness fps exposure buffer-size)

  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)

  (.start timer))