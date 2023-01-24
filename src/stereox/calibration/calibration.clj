(ns stereox.calibration.calibration
  (:require [cljfx.api :as fx]
            [stereox.camera.stereo-camera :as camera])
  (:import (java.io ByteArrayInputStream File)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)
           (javafx.scene.image Image)
           (org.opencv.calib3d Calib3d)
           (org.opencv.core Mat MatOfPoint2f Size TermCriteria)
           (org.opencv.imgproc Imgproc))
  (:gen-class))

(defrecord Props
  [^File directory
   ^Integer rows
   ^Integer columns])

(def ^:private *params
  "Props"
  (atom nil))

(def ^:private *camera
  "StereoCamera"
  (atom nil))

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
    (swap! *state assoc :alive false)
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
        (if (:alive @*state)
          (func))
        ))))

(defrecord CBData
  [^Mat image_original
   ^Mat image_chessboard
   ^Boolean found
   ^MatOfPoint2f corners])

(defn cvt-color [^Mat image ^Integer code]
  (let [output (Mat.)]
    (Imgproc/cvtColor image output code)
    output))

(defn find-squares ^CBData [^Mat image]
  (let [output_image (cvt-color image Imgproc/COLOR_BGR2GRAY)
        corners (MatOfPoint2f.)
        flags (+ Calib3d/CALIB_CB_ADAPTIVE_THRESH
                 Calib3d/CALIB_CB_NORMALIZE_IMAGE
                 Calib3d/CALIB_CB_FAST_CHECK)
        pattern_size (Size. (- (:rows @*params) 1)
                            (- (:columns @*params) 1))
        found (Calib3d/findChessboardCorners image
                                             pattern_size
                                             corners
                                             flags)]
    (if found
      (Imgproc/cornerSubPix output_image
                            corners
                            (Size. 11 11)
                            (Size. -1 -1)
                            (TermCriteria. (+ TermCriteria/MAX_ITER
                                              TermCriteria/COUNT)
                                           30
                                           0.1)))
    (Calib3d/drawChessboardCorners output_image
                                   pattern_size
                                   corners
                                   found)
    (CBData. image
             output_image
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

(defn main-cb []
  (let [captured (camera/capture @*camera)
        prepared (prepare-images captured)
        images (image-adapt (unwrap-prepared prepared))]
    (if (some? images)
      (swap! *state assoc-in [:camera :image] images))
    ; TODO: MORE
    )
  )

(defn calibrate [& {:keys [output-folder
                           columns
                           rows
                           width
                           height
                           ] :as all}]
  ; setup calibration properties
  (reset! *params (Props. output-folder rows columns))

  ; setup camera
  (reset! *camera (camera/create all))

  ; setup javafx
  (swap! *state assoc-in [:camera :viewport]
         {:width width :height height :min-x 0 :min-y 0})

  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)

  (start-ui-loop main-cb))