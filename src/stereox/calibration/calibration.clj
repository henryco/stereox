(ns stereox.calibration.calibration
  (:require [cljfx.api :as fx]
            [stereox.camera.stereo-camera :as camera]
            [stereox.utils.commons :as commons]
            [stereox.utils.timer :as timer]
            [taoensso.timbre :as log])
  (:import (clojure.lang PersistentVector)
           (java.io ByteArrayInputStream File)
           (java.util ArrayList)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)
           (javafx.scene.image Image)
           (org.opencv.calib3d Calib3d)
           (org.opencv.core Mat MatOfPoint2f Size TermCriteria)
           (org.opencv.imgproc Imgproc))
  (:gen-class))

(defrecord Props
  [^PersistentVector ids
   ^File directory
   ^Integer rows
   ^Integer columns
   ^Integer quality
   ^Integer delay
   ^Integer total])

(defrecord CBData
  [^Mat image_original
   ^Mat image_chessboard
   ^Boolean found
   ^MatOfPoint2f corners])

(defrecord CabD
  [^String id
   ^Mat image
   ^MatOfPoint2f corners])

(defrecord OneOfPairData
  [^String id
   ^ArrayList object_points
   ^ArrayList image_points
   ^Size image_size])

(def ^:private *params
  "Props"
  (atom nil))

(def ^:private *camera
  "StereoCamera"
  (atom nil))

(def ^:private *timer
  "FnTimer"
  (atom nil))

(def ^:private *images
  "CabD[]"
  (atom []))

(def ^:private *state
  "JavaFX UI State"
  (atom {:title  "StereoX calibration"
         :scale  1.
         :alive  true
         :width  nil
         :height nil
         :camera {:viewport {:width 0 :height 0 :min-x 0 :min-y 0}
                  :image    ^Image []
                  }
         }))

(defn image-adapt [matrices]
  (if (some? matrices)
    (map #(deref %)
         (map #(future
                 (-> (commons/mat-to-bytes %)
                     (ByteArrayInputStream.)
                     (Image.))
                 ) matrices))
    nil))

(defn terminate-graphics []
  (swap! *state assoc :alive false)
  (timer/stop @*timer)
  (camera/release @*camera))

(defn shutdown [& {:keys [code]}]
  (try
    (terminate-graphics)
    (catch Exception e (.printStackTrace e)))
  (try
    (Platform/exit)
    (catch Exception e (.printStackTrace e))
    (finally (System/exit (if (some? code) code 0)))))

(defn on-win-change []
  (let [s (-> @*state :scale)
        ow (-> @*state :camera :viewport :width)
        oh (-> @*state :camera :viewport :height)
        ww (-> @*state :width)
        hh (-> @*state :height)]
    (if (and (some? ww) (some? hh))
      (let [[dw dh] (map - [ww hh] [(* ow s 2) (* oh s)])]
        (if (< dw dh)
          (swap! *state assoc :scale (/ ww ow 2))
          (swap! *state assoc :scale (/ hh oh)))
        )
      )))

(defn on-win-height-change [v]
  (swap! *state assoc :height v)
  (on-win-change))

(defn on-win-width-change [v]
  (swap! *state assoc :width v)
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
   :resizable         true
   :showing           (:alive state)
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
        (if (:alive @*state)
          (func))))))

(defn calc-quality
  "Calculates quality flags for chessboard finder algorithm.
  Quality should be integer from 1 to 4 included.
  Returns integer quality flag."
  {:tag    Integer
   :static true}
  [^Integer quality]
  (reduce +
          (-> [Calib3d/CALIB_CB_FAST_CHECK
               Calib3d/CALIB_CB_NORMALIZE_IMAGE
               Calib3d/CALIB_CB_FILTER_QUADS
               Calib3d/CALIB_CB_ADAPTIVE_THRESH]
              (subvec 0 quality))))

(defn find-squares ^CBData [^Mat image]
  (let [buffer_result (commons/img-copy image)
        buffer_gray (commons/img-copy image
                                      Imgproc/COLOR_BGR2GRAY)
        corners (MatOfPoint2f.)
        pattern_size (Size. (- (:rows @*params) 1)
                            (- (:columns @*params) 1))
        flags (calc-quality (:quality @*params))
        found (Calib3d/findChessboardCorners buffer_gray
                                             pattern_size
                                             corners
                                             flags)]
    (Calib3d/drawChessboardCorners buffer_result
                                   pattern_size
                                   corners
                                   found)
    (CBData. image
             buffer_result
             found
             corners)))

(defn prepare-images
  "Founds chessboard pattern on the image.
  Expects vector or sequence of org.opencv.core.Mat
  Returns vector or sequence of CBData"
  [matrices]
  (if (some? matrices)
    (map #(deref %)
         (map #(future (find-squares %))
              matrices))))

(defn unwrap-prepared
  "Unwrap prepared data sequence returning images.
  Expects vector or sequence of CBData
  Returns vector of org.opencv.core.Mat"
  [data]
  (if (some? data)
    (map #(:image_chessboard %) data)))

(defn prepare-parameters
  "Calibrate camera
  Expects:
    id   - camera id
    data - vector of CabD
  Returns: OneOfPairData"
  {:tag    OneOfPairData
   :static false}
  [id data]
  (println "prepare params")

  (try
    (let [obp (commons/obp-matrix (:rows @*params)
                                  (:columns @*params))
          obj_p (ArrayList.)
          img_p (ArrayList.)
          img_size (Size.)] ; FIXME
      (run! (fn [{:keys [^Mat image ^MatOfPoint2f corners]}]
              (let [buffer_gray (commons/img-copy image
                                                  Imgproc/COLOR_BGR2GRAY)]
                (Imgproc/cornerSubPix buffer_gray
                                      corners
                                      (Size. 11 11)
                                      (Size. -1 -1)
                                      (TermCriteria. (+ TermCriteria/MAX_ITER
                                                        TermCriteria/COUNT)
                                                     30
                                                     0.1))
                (.set img_size (.size buffer_gray)) ; FIXME
                (.add img_p corners)
                (.add obj_p obp))
              ) data)
      (OneOfPairData. id obj_p img_p img_size))

    (catch Exception e (do (.printStackTrace e)
                           (shutdown -1))))
  )

(defn stereo-calibration [config_map]
  (log/info "calibration..." config_map)
  ;TODO
  )

(defn calibrate-cameras []
  ; map -> {id1: [{}{}{}] id2: [{}{}{}] ...}
  (let [calibration_map (reduce (fn [o n]
                                  (let [key (str (:id n))]
                                    (assoc o key (conj (get o key []) n))))
                                {} (flatten @*images))
        config_map (map #(deref %)
                        (map (fn [[k v]]
                               (future (prepare-parameters k v)))
                             calibration_map))]
    (println "START")
    (stereo-calibration config_map)
    (shutdown)))

(defn store-cb-data
  "Store data from vector of CBData.
  IF stored enough data, shutdowns ui and calibrate camera.
  Expects vector or sequence of CBData"
  [data]
  (swap! *images
         conj
         (map-indexed
           (fn [idx item]
             (CabD. (nth (:ids @*params) idx)
                    (:image_original item)
                    (:corners item)))
           data))
  (let [size (count @*images)
        total (:total @*params)]
    (log/info "Captured: [" size "] of [" total "]")
    (if (>= size total)
      (do (terminate-graphics)
          (future (calibrate-cameras))))))

(defn main-cb []
  (let [captured (camera/capture @*camera)
        prepared (prepare-images captured)
        images (image-adapt (unwrap-prepared prepared))]
    (if (every? #(true? (:found %)) prepared)
      (timer/tick @*timer #(store-cb-data prepared))
      (timer/reset @*timer))
    (if (some? images)
      (swap! *state assoc-in [:camera :image] images))))

(defn calibrate [& {:keys [output-folder
                           images-number
                           columns
                           quality
                           rows
                           width
                           height
                           delay
                           ids
                           ] :as all}]
  ; setup calibration properties
  (reset! *params (Props. ids output-folder rows
                          columns quality delay
                          images-number))
  ; setup camera
  (reset! *camera (camera/create all))
  ; setup timer
  (reset! *timer (timer/create-start-timer delay))
  ; setup javafx
  (swap! *state assoc-in [:camera :viewport]
         {:width width :height height :min-x 0 :min-y 0})
  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)
  (start-ui-loop main-cb))