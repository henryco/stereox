(ns stereox.config.view
  (:require
    [stereox.utils.debounce :as deb]
    [stereox.config.logic :as logic]
    [stereox.utils.commons :as commons]
    [stereox.utils.guifx :as gfx])
  (:gen-class :main true)
  (:import (clojure.lang Keyword)
           (java.io ByteArrayInputStream)
           (javafx.scene.image Image)
           (jstereox BetterFxFrameConverter)
           (org.bytedeco.javacv JavaFXFrameConverter OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.opencv.core CvType Mat)
           (org.opencv.imgproc Imgproc)
           (stereox.cv.block_matching BlockMatcher)
           (stereox.cv.stereo_camera IStereoCamera)))

(def ^:private *logic
  "Logic"
  (atom nil))

(def ^:private *gui
  "GuiFx"
  (atom nil))

(def ^:private *state
  "JavaFX UI state"
  (atom {:title    "StereoX - Stereo Matcher configuration"
         :controls {:matcher [{:id "" :val 0 :min 0 :max 0}]
                    :camera  [{:id "" :val 0 :min 0 :max 0}]}
         :camera   {:viewport {:width 1 :height 1 :min-x 0 :min-y 0}
                    :image    nil}
         :panel    {:width 300}
         :init     {:w false
                    :h false}
         :saved    false
         :mode     "disparity_bgr"
         :scale    1.
         :alive    true
         :width    1
         :height   1
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
  (if-not (-> @*state :init :h)
    (do (swap! *state assoc :height v)
        (on-win-change))))

(defn- on-win-width-change [v]
  (if-not (-> @*state :init :w)
    (do (swap! *state assoc :width v)
        (on-win-change))))

(defn- on-scene-height-change [v]
  (swap! *state assoc-in [:init :h] true)
  (swap! *state assoc :height v)
  (on-win-change))

(defn- on-scene-width-change [v]
  (swap! *state assoc-in [:init :w] true)
  (swap! *state assoc :width v)
  (on-win-change))

(defn- matrix-to-image
  {:tag    Image
   :static true}
  [^Mat matrix]
  (let [conv_1 (new BetterFxFrameConverter)
        ;conv_1 (new JavaFXFrameConverter)
        conv_2 (new OpenCVFrameConverter$ToOrgOpenCvCoreMat)
        r_type (.type matrix)]
    (if (some #(= % r_type)
              [CvType/CV_8U CvType/CV_8UC1 CvType/CV_8UC2
               CvType/CV_8UC3 CvType/CV_8UC4])
      (->> matrix
           (.convert conv_2)
           (.convert conv_1))
      (let [mat (new Mat)]
        (.convertTo matrix mat CvType/CV_8U)
        (->> mat
             (.convert conv_2)
             (.convert conv_1))))))

(defn- main-cb-loop []
  (let [frame (logic/render-frame @*logic (-> @*state :mode (keyword)))
        image (matrix-to-image frame)]
    (if (some? image)
      (swap! *state assoc-in [:camera :image] image))))

(defn- update-matcher-params [k v]
  (swap! *state assoc :saved false)
  (.setup ^BlockMatcher (:block-matcher (logic/state @*logic)) k v))

(defn- update-camera-params [k v]
  (swap! *state assoc :saved false)
  (.setup ^IStereoCamera (:camera (logic/state @*logic)) k v))

(defn- on-matcher-update [k v]
  (swap! *state assoc :saved false)
  (swap! *state assoc-in [:controls :matcher]
         (-> (map (fn [{:keys [id max min] :as args}]
                    (if (.equalsIgnoreCase id k)
                      (do (if (not= v val)
                            (update-matcher-params k v))
                          {:id id :min min :max max :val v})
                      args))
                  (-> @*state :controls :matcher))
             (doall)
             (vec))))

(defn- matcher-state-update []
  (swap! *state assoc :saved false)
  (swap! *state assoc-in [:controls :matcher]
         (-> (map (fn [[k min max]]
                    {:val (get (logic/matcher-params @*logic)
                               (keyword k))
                     :min min
                     :max max
                     :id  k})
                  (logic/matcher-options @*logic))
             (doall)
             (vec))))

(defn- camera-state-update []
  (swap! *state assoc :saved false)
  (swap! *state assoc-in [:controls :camera]
         (-> (map (fn [[k min max]]
                    {:val (get (logic/camera-params @*logic)
                               (keyword k))
                     :min min
                     :max max
                     :id  k})
                  (logic/camera-options @*logic))
             (doall)
             (vec))))

(defn- on-camera-update [k v]
  (swap! *state assoc :saved false)
  (swap! *state assoc-in [:controls :camera]
         (-> (map (fn [{:keys [id max min] :as args}]
                    (if (.equalsIgnoreCase id k)
                      (do (if (not= v val)
                            (update-camera-params k v))
                          {:id id :min min :max max :val v})
                      args))
                  (-> @*state :controls :camera))
             (doall)
             (vec)))
  (.unify ^IStereoCamera (:camera (logic/state @*logic)))
  (if (not= v -1)
    (camera-state-update)))

(def ^:private debounce-matcher-update
  (deb/debounce on-matcher-update 2000))

(def ^:private debounce-camera-update
  (deb/debounce on-camera-update 500))

(defn- initialize-logic [{:as args}]
  (reset! *logic (logic/configure args)))

(defn- initialize-state [{:as args}]
  (swap! *state assoc :title
         (str (:title @*state)
              " [ " (-> (:matcher args)
                        (str)
                        (.replaceFirst ":"
                                       ""))
              " ] " (:width args) "x" (:height args)))
  (matcher-state-update)
  (camera-state-update)
  (swap! *state assoc-in [:camera :viewport]
         {:width  (:width args)
          :height (:height args)
          :min-x  0
          :min-y  0}))

(defn- save-settings [_]
  (if (false? (:saved @*state))
    (do (logic/save-settings @*logic)
        (swap! *state assoc :saved true))))

(defn- change-mode-selected [^Keyword mode]
  (swap! *state assoc :mode
         (get {:disparity "disparity_bgr"
               :depth     "depth_bgr"
               :3D        "projection"
               :L         "left"
               :R         "right"}
              mode
              "disparity_bgr")))

(def ^:private on-mode-selected
  (deb/debounce change-mode-selected 1000))

(load "dom")

(defn- initialize-gui []
  (reset! *gui (gfx/create-guifx *state root main-cb-loop)))

(defn start-gui [& {:as args}]
  (initialize-logic args)
  (initialize-state args)
  (initialize-gui))