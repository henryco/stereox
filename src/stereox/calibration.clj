(ns stereox.calibration
  (:import (java.io ByteArrayInputStream File)
           (javafx.animation AnimationTimer)
           (javafx.scene.image Image ImageView)
           (org.opencv.core Mat MatOfByte)
           (org.opencv.imgcodecs Imgcodecs)
           (org.opencv.videoio VideoCapture)
           (nu.pattern OpenCV))
  (:require [cljfx.api :as fx])
  (:gen-class))

(OpenCV/loadShared)

(def ^:private *state
  (atom {:title "StereoX calibration"
         :camera {:viewport {:width 640 :height 480 :min-x 0 :min-y 0}
                  :image nil}
         ; TODO: MORE STATE
         }))

; TODO: CAMERA CHOICE
(def ^:private capture
  (VideoCapture. 0))

(defn mat2image ^Image [^Mat mat]
  (let [bytes (MatOfByte.)]
    (Imgcodecs/imencode ".png" mat bytes)
    (Image. (ByteArrayInputStream. (.toArray bytes)))))

(defn get-capture []
  (let [mat (Mat.)]
    (.read capture mat)
    (mat2image mat)))

(defn prep-dirs [^File dir]
  (if (not (.exists dir))
    (.mkdir dir)))

(defn render-image-view [{:keys [camera]}]
  (merge {:fx/type :image-view
          :viewport (:viewport camera)
          :preserve-ratio true}
         (let [{img :image} camera]
           (if (nil? img) {} {:image img}))))

(defn render-label [{:keys [title]}]
  {:fx/type :label :text title})

(defn root [state]
  {:fx/type :stage
   :showing true
   :title (:title state)
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [(merge state {:fx/type render-label})
                             (merge state {:fx/type render-image-view})]
                  }}})

(def ^:private timer
  (proxy [AnimationTimer] []
    (handle [_]
      (swap! *state assoc-in [:camera :image] (get-capture))
      ;TODO: MORE
      )))

(def ^:private renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type root)))

(defn calibrate [& {:keys [rows columns square-size output-folder] :as all}]
  (println (pr-str all))
  (prep-dirs output-folder)

  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)

  (.start timer))