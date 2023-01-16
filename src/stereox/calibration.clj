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

(def ^:private capture
  (VideoCapture. 0))

(def ^:private imageView
  (ImageView.))

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


;; Define application state
(def *state
  (atom {:title "App title"}))

;; Define render functions
(defn title-input [{:keys [title]}]
  {:fx/type :text-field
   :on-text-changed #(swap! *state assoc :title %)
   :text title})

(defn root [{:keys [title]}]
  {:fx/type :stage
   :showing true
   :title title
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [{:fx/type :label
                              :text "Window title input"}
                             {:fx/type title-input
                              :title title}]}}})

;; Create renderer with middleware that maps incoming data - description -
;; to component description that can be used to render JavaFX state.
;; Here description is just passed as an argument to function component.
(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type root)))


(def ^:private timer
  (proxy [AnimationTimer] []
    (handle [_]
      (.setImage imageView (get-capture))
      ; FIXME: REPLACE WITH ATOM
      )))

(defn calibrate [& {:keys [rows columns square-size output-folder] :as all}]
  (println (pr-str all))
  (prep-dirs output-folder)

  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)

  (.start timer))