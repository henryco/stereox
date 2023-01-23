(ns stereox.calibration
  (:require [cljfx.api :as fx]
            [stereox.calibration.stereo-camera :as camera])
  (:import (java.io ByteArrayInputStream File)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)
           (javafx.scene.image Image))
  (:gen-class))

(def ^:private *window
  (atom {:alive  true
         :width  nil
         :height nil
         }))

(def ^:private *camera
  (atom nil))

(def ^:private *state
  (atom {:title  "StereoX calibration"
         :scale  1.
         :camera {:viewport {:width 0 :height 0 :min-x 0 :min-y 0}
                  :image    ^Image []
                  }
         }))

(defn prep-dirs [^File dir]
  (if (not (.exists dir))
    (.mkdir dir)))

(defn image-adapt [matrices]
  (if (some? matrices)
    (map #(deref %)
         (map #(future
                 (-> (camera/mat-to-bytes %)
                     (ByteArrayInputStream.)
                     (Image.))
                 ) matrices))
    nil))

(defn shutdown [& {:keys [code]}]
  (try
    (swap! *window assoc :alive false)
    (camera/release @*camera)
    (catch Exception e (.printStackTrace e)))
  (try
    (Platform/exit)
    (catch Exception e (.printStackTrace e))
    (finally (System/exit (if (some? code) code 0)))))

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

(defn start-ui-loop [func]
  (.start
    (proxy [AnimationTimer] []
      (handle [_]
        (if (:alive @*window)
          (func))
        ))))

(defn main-cb []
  (let [captured (camera/capture @*camera)
        images (image-adapt captured)]
    (if (some? images)
      (swap! *state assoc-in [:camera :image] images))
    ; TODO: MORE
    )
  )

(defn calibrate [& {:keys [output-folder width height] :as all}]
  (prep-dirs output-folder)

  ; setup camera
  (reset! *camera (camera/create all))

  ; setup javafx
  (swap! *state assoc-in [:camera :viewport]
         {:width width :height height :min-x 0 :min-y 0})

  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)

  (start-ui-loop main-cb)
  )