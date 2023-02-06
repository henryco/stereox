(ns stereox.config.view
  (:require
    [stereox.config.logic :as logic]
    [stereox.utils.commons :as commons]
    [stereox.utils.guifx :as gfx])
  (:gen-class :main true)
  (:import (java.io ByteArrayInputStream)
           (javafx.scene.image Image)
           (org.opencv.core Mat)))

(def ^:private *logic
  "Logic"
  (atom nil))

(def ^:private *gui
  "GuiFx"
  (atom nil))

(def ^:private *state
  "JavaFX UI state"
  (atom {:title  "StereoX Pattern Matching configuration"
         :camera {:viewport {:width 1 :height 1 :min-x 0 :min-y 0}
                  :image    nil}
         :panel  {:width 300}
         :scale  1.
         :alive  true
         :width  nil
         :height nil
         }))

(defn- shutdown [& {:keys [code]}]
  (try
    (gfx/shutdown @*gui)
    (catch Exception e (.printStackTrace e))
    (finally (System/exit (if (some? code) code 0)))))

(defn- on-win-change []
  (let [s (-> @*state :scale)
        ow (-> @*state :camera :viewport :width)
        oh (-> @*state :camera :viewport :height)
        ww (-> @*state :width)
        hh (-> @*state :height)
        rw (-> @*state :panel :width)]
    (if (and (some? ww) (some? hh))
      (let [[dw dh] (map - [(- ww rw) hh] [(* ow s) (* oh s)])]
        (if (< dw dh)
          (swap! *state assoc :scale (/ (- ww rw) ow))
          (swap! *state assoc :scale (/ hh oh)))))))

(defn- on-win-height-change [v]
  (swap! *state assoc :height v)
  (on-win-change))

(defn- on-win-width-change [v]
  (swap! *state assoc :width v)
  (on-win-change))

(defn matrix-to-image [^Mat matrix]
  (-> (commons/image-mat-to-bytes matrix)
      (ByteArrayInputStream.)
      (Image.)))

(defn- main-cb-loop []
  (let [frame (logic/render-frame @*logic)
        image (matrix-to-image (:disparity frame))
        ;image (matrix-to-image (first (:captured frame)))
        ;image (matrix-to-image (first (:rectified frame)))
        ]
    (if (some? image)
      (swap! *state assoc-in [:camera :image] image))))

(load "dom")
(defn start-gui [& {:as args}]
  (swap! *state assoc-in [:camera :viewport] {:width  (:width args)
                                              :height (:height args)
                                              :min-x  0
                                              :min-y  0})
  (reset! *logic (logic/configure args))
  (reset! *gui (gfx/create-guifx *state root main-cb-loop)))