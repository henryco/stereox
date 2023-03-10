(ns stereox.calibration.calibration
  (:require [cljfx.api :as fx]
            [stereox.cv.stereo-camera :as camera]
            [stereox.serialization.calibration :as sc]
            [stereox.serialization.calibration :as serial]
            [stereox.serialization.utils :as su]
            [stereox.utils.commons :as commons]
            [stereox.utils.timer :as timer]
            [taoensso.timbre :as log])
  (:import (clojure.lang PersistentVector)
           (java.io File)
           (java.util ArrayList Collection List)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)
           (javafx.scene.image Image)
           (org.bytedeco.javacv JavaFXFrameConverter OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.opencv.calib3d Calib3d)
           (org.opencv.core CvType Mat MatOfFloat4 MatOfPoint2f Rect Size TermCriteria)
           (org.opencv.imgproc Imgproc)
           (stereox.serialization.calibration CalibrationData CameraData SingleCalibrationData))
  (:gen-class))

(defrecord Props
  [^PersistentVector ids
   ^File directory
   ^Float square_size
   ^Integer rows
   ^Integer columns
   ^Integer quality
   ^Integer delay
   ^Integer total
   ^Boolean full])

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

(defrecord Calibr8Data
  [^String id
   ^ArrayList object_points
   ^ArrayList image_points
   ^Size image_size
   ^Double rmse
   ^Mat camera_matrix
   ^Mat distortion_coeffs
   ^List rvecs
   ^List tvecs])

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
                  :number   1 :image []}
         }))

(defn- matrix-to-image
  {:tag    Image
   :static true}
  [^Mat matrix]
  (let [conv_1 (new JavaFXFrameConverter)
        conv_2 (new OpenCVFrameConverter$ToOrgOpenCvCoreMat)]
    (->> matrix (.convert conv_2) (.convert conv_1))))

(defn image-adapt [matrices]
  (if (some? matrices)
    (map #(deref %)
         (map #(future
                 (matrix-to-image %))
              matrices))
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
        n (-> @*state :camera :number)
        ow (-> @*state :camera :viewport :width)
        oh (-> @*state :camera :viewport :height)
        ww (-> @*state :width)
        hh (-> @*state :height)]
    (if (and (some? ww) (some? hh))
      (let [[dw dh] (map - [ww hh] [(* ow s n) (* oh s)])]
        (if (< dw dh)
          (swap! *state assoc :scale (/ ww ow n))
          (swap! *state assoc :scale (/ hh oh)))
        )
      )))

(defn on-win-height-change [v]
  (swap! *state assoc :height v)
  (on-win-change))

(defn on-win-width-change [v]
  (swap! *state assoc :width v)
  (on-win-change))

(defn start-ui-loop [func]
  (.start
    (proxy [AnimationTimer] []
      (handle [_]
        (if (:alive @*state)
          (func))))))

(defn render-images [{:keys [camera scale]}]
  {:fx/type    :h-box
   :style      {:-fx-background-color :black
                :-fx-alignment        :center}
   :min-width  (-> camera :viewport :width (* (:number camera) scale))
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
        pattern_size (Size. (- (:columns @*params) 1)
                            (- (:rows @*params) 1))
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
  (log/info "preparing calibration parameters")
  (let [obp (commons/obp-matrix (- (:columns @*params) 1)
                                (- (:rows @*params) 1)
                                (:square_size @*params))
        obj_p (ArrayList.)
        img_p (ArrayList.)]
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
                                                   0.001))
              (.add img_p corners)
              (.add obj_p obp))
            ) data)
    (OneOfPairData. id obj_p
                    img_p
                    (-> data (first) (:image) (.size)))))

(defn save-solo-calibrated
  "Save solo calibration data"
  {:static false}
  [Calibr8Data_vec]
  (run!
    (fn [^Calibr8Data data]
      (log/info "Saving single calibration results...")
      (let [id_list [(:id data)]
            dir_name (su/prepare-dir-name (.width (:image_size data))
                                          (.height (:image_size data))
                                          id_list)
            file_name (su/prepare-calib-name id_list su/CALIB_SOLO_POSTFIX)
            output_dir (File. ^File (:directory @*params)
                              ^String dir_name)
            output_file (File. ^File output_dir
                               ^String file_name)]
        (su/prep-dirs output_dir)
        (serial/single-calibration-to-file
          (SingleCalibrationData. (:id data)
                                  (:image_size data)
                                  (:rmse data)
                                  (:camera_matrix data)
                                  (:distortion_coeffs data)
                                  (into [] (:rvecs data))
                                  (into [] (:tvecs data)))
          output_file)))
    Calibr8Data_vec)
  Calibr8Data_vec)

(defn save-calibrated-stereo
  "Save calibrated data"
  {:static false}
  [^CalibrationData data]
  (log/info "Saving calibration results...")
  (let [id_list (map #(:id %)
                     (:camera_data data))
        dir_name (su/prepare-dir-name (.width (:size data))
                                      (.height (:size data))
                                      id_list)
        file_name (su/prepare-calib-name id_list su/CALIB_POSTFIX)
        output_dir (File. ^File (:directory @*params)
                          ^String dir_name)
        output_file (File. ^File output_dir
                           ^String file_name)]
    (su/prep-dirs output_dir)
    (serial/calibration-to-file data output_file)))

(defn calibr8-each
  "Calibrates separately each camera in stereo pair.
  Returns:
    Calibr8Data[]"
  {:static true}
  [& vec_of_data]
  (log/info "calibrate each...")
  (map (fn [p] (deref p))
       (map
         (fn [^OneOfPairData data]
           (future
             (let [c_mat (Mat.)
                   d_cef (Mat.)
                   rvecs (ArrayList.)
                   tvecs (ArrayList.)
                   rmse (Calib3d/calibrateCamera (:object_points data)
                                                 (:image_points data)
                                                 (:image_size data)
                                                 c_mat
                                                 d_cef
                                                 rvecs
                                                 tvecs)]
               (log/info "Camera [" (:id data) "] RMSE: " rmse)
               (map->Calibr8Data
                 (merge data {:rmse              rmse
                              :camera_matrix     c_mat
                              :distortion_coeffs d_cef
                              :rvecs             rvecs
                              :tvecs             tvecs})))))
         (flatten vec_of_data))))

(defn calibr8-stereo
  "Calibrates stereo pair using data from previous solo calibration
  Expects:
    Calibr8Data[]"
  {:static false}
  [[^Calibr8Data left
    ^Calibr8Data right]]
  (log/info "calibrate stereo...")
  (let [img_size (:image_size left)
        cam_mtx1 (Mat.)
        cam_mtx2 (Mat.)
        dist_cf1 (Mat.)
        dist_cf2 (Mat.)
        rotation_mtx (Mat.)
        translation_mtx (Mat.)
        essential_mtx (Mat.)
        fundamental_mtx (Mat.)]
    (.copyTo (:camera_matrix left) cam_mtx1)
    (.copyTo (:camera_matrix right) cam_mtx2)
    (.copyTo (:distortion_coeffs left) dist_cf1)
    (.copyTo (:distortion_coeffs right) dist_cf2)
    (let [re-projection_error
          (Calib3d/stereoCalibrate (:object_points left)
                                   (:image_points left)
                                   (:image_points right)
                                   cam_mtx1
                                   dist_cf1
                                   cam_mtx2
                                   dist_cf2
                                   img_size
                                   rotation_mtx
                                   translation_mtx
                                   essential_mtx
                                   fundamental_mtx
                                   (+ Calib3d/CALIB_FIX_INTRINSIC
                                      ;Calib3d/CALIB_FIX_ASPECT_RATIO
                                      ;Calib3d/CALIB_ZERO_TANGENT_DIST
                                      ;Calib3d/CALIB_SAME_FOCAL_LENGTH
                                      )
                                   (TermCriteria. (+ TermCriteria/MAX_ITER
                                                     TermCriteria/EPS)
                                                  100
                                                  0.00001))]
      (log/info "Stereo re-projection error: " re-projection_error)

      (let [rect_transform1 (Mat.)
            rect_transform2 (Mat.)
            proj_mtx1 (Mat.)
            proj_mtx2 (Mat.)
            roi_mtx1 (Rect.)
            roi_mtx2 (Rect.)
            undistortion_map1 (Mat.)
            undistortion_map2 (Mat.)
            rectification_map1 (Mat.)
            rectification_map2 (Mat.)
            disp_to_depth_mtx (Mat.)

            fixed_dsp_dpt_mtx (doto (Mat. 4 4 CvType/CV_64F)
                                (.put 0 0 (double-array
                                            (flatten [[1 0 0 (* -0.5 (.width img_size))]
                                                      [0 -1 0 (* 0.5 (.height img_size))]
                                                      [0 0 0 (* -0.8 (.width img_size))]
                                                      [0 0 1 0]]))))

            pair [{:id                (:id left)
                   :cam_mtx           cam_mtx1
                   :dist_cf           dist_cf1
                   :rect_tr           rect_transform1
                   :prj_mtx           proj_mtx1
                   :roi_mtx           roi_mtx1
                   :undistortion_map  undistortion_map1
                   :rectification_map rectification_map1}
                  {:id                (:id right)
                   :cam_mtx           cam_mtx2
                   :dist_cf           dist_cf2
                   :rect_tr           rect_transform2
                   :prj_mtx           proj_mtx2
                   :roi_mtx           roi_mtx2
                   :undistortion_map  undistortion_map2
                   :rectification_map rectification_map2}]
            ]
        (Calib3d/stereoRectify cam_mtx1
                               dist_cf1
                               cam_mtx2
                               dist_cf2
                               img_size
                               rotation_mtx
                               translation_mtx
                               rect_transform1
                               rect_transform2
                               proj_mtx1
                               proj_mtx2
                               disp_to_depth_mtx
                               0
                               0
                               img_size
                               roi_mtx1
                               roi_mtx2)
        (run! #(Calib3d/initUndistortRectifyMap (:cam_mtx %)
                                                (:dist_cf %)
                                                (:rect_tr %)
                                                (:prj_mtx %)
                                                img_size
                                                CvType/CV_32FC1
                                                (:undistortion_map %)
                                                (:rectification_map %))
              pair)
        (save-calibrated-stereo
          (CalibrationData. img_size
                            rotation_mtx
                            translation_mtx
                            essential_mtx
                            fundamental_mtx
                            disp_to_depth_mtx
                            fixed_dsp_dpt_mtx
                            (map #(CameraData. (:id %)
                                               (:cam_mtx %)
                                               (:dist_cf %)
                                               (:rect_tr %)
                                               (:prj_mtx %)
                                               (:roi_mtx %)
                                               (:undistortion_map %)
                                               (:rectification_map %))
                                 pair)))
        ))))

(defn- read-solo-calibration-data [directory pair_data]
  (doall
    (map (fn [^OneOfPairData data]
           (log/info "Reading prepared calibration data...")
           (let [c_data (sc/single-calibration-from-file
                          (first (su/list-candidates
                                   directory
                                   (int (.width (:image_size data)))
                                   (int (.height (:image_size data)))
                                   su/CALIB_SOLO_POSTFIX
                                   (:id data))))]
             (->Calibr8Data
               (:id data)
               (:object_points data)
               (:image_points data)
               (:image_size data)
               (:rmse c_data)
               (:camera_matrix c_data)
               (:distortion_coefficients c_data)
               (ArrayList. ^Collection (:rvecs c_data))
               (ArrayList. ^Collection (:tvecs c_data)))))
         pair_data)))

(defn calibrate-pair-full
  {:static false}
  [^OneOfPairData left
   ^OneOfPairData right]
  (log/info "calibrate pair...")
  (if (:full @*params)
    (let [single_data (calibr8-each left right)]
      (save-solo-calibrated single_data)
      (calibr8-stereo single_data))
    (let [single_data (try (read-solo-calibration-data
                             (:directory @*params)
                             [left right])
                           (catch Exception _
                             (log/warn "Single calibration data not found")
                             (calibr8-each left right)))]
      (calibr8-stereo single_data))))

(defn calibrate-single
  {:static false}
  [^OneOfPairData data]
  (log/info "calibrate single...")
  (let [data (calibr8-each data)]
    (save-solo-calibrated data)))

(defn stereo-calibration
  "Camera stereo calibration.
  Expects vector of OneOfPairData (each element corresponds to some camera)"
  {:static false}
  [configuration]
  (let [size (count configuration)]
    (cond
      (= 1 size) (calibrate-single (first configuration))
      (= 2 size) (calibrate-pair-full (first configuration)
                                      (last configuration))
      :else (throw (NoSuchMethodException.                  ; IMPLEMENT LATER MAYBE?
                     "More then 2 cameras actually not supported")))))

(defn calibrate-cameras
  {:static false}
  []
  (try
    (let [calibration_map (reduce (fn [o n]
                                    (let [key (str (:id n))]
                                      (assoc o key (conj (get o key []) n))))
                                  {} (flatten @*images))
          configuration (map #(deref %)
                             (map (fn [[k v]]
                                    (future (prepare-parameters k v)))
                                  calibration_map))]
      (stereo-calibration configuration)
      (shutdown))
    (catch Exception e (do (.printStackTrace e)
                           (shutdown :code 1))))
  )

(defn store-cb-data
  "Store data from vector of CBData.
  IF stored enough data, shutdowns ui and calibrate camera.
  Expects vector or sequence of CBData"
  {:static false}
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

(defn calibrate [& {:keys [config-folder
                           images-number
                           square-size
                           columns
                           quality
                           full
                           rows
                           width
                           height
                           delay
                           ids
                           ] :as all}]
  (log/info (pr-str all))
  ; setup calibration properties
  (reset! *params (->Props ids config-folder square-size
                           rows columns quality delay
                           images-number full))
  (log/info (pr-str @*params))
  ; setup camera
  (reset! *camera (camera/create all))
  ; setup timer
  (reset! *timer (timer/create-start-timer delay))
  ; setup javafx
  (swap! *state assoc-in [:camera :viewport]
         {:width width :height height :min-x 0 :min-y 0})
  (swap! *state assoc-in [:camera :number]
         (count ids))
  ;; Convenient way to add watch to an atom + immediately render app
  (fx/mount-renderer *state renderer)
  (start-ui-loop main-cb))