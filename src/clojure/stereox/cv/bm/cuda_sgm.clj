(in-ns 'stereox.cv.block-matching)

(defrecord StereoSGMProp
  [^Integer num-disparities
   ^Integer block-size
   ^Integer min-disparity
   ^Integer speckle-window-size
   ^Integer speckle-range
   ^Integer disparity-max-diff
   ^Integer pre-filter-cap
   ^Integer uniqueness
   ^Integer p1
   ^Integer p2
   ^Integer mode
   ^Integer iterations
   ^Integer radius
   ^Integer missing
   ^Integer ddepth
   ^Float edge-threshold
   ^Float disp-threshold
   ^Float sigma-range])

(deftype CudaStereoSGM [^Atom *params
                        ^Atom *matcher
                        ^Atom *dsp-filter
                        ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [; STEREO MATCHER 0
     ["num-disparities" 0 2 #(->> % int (Math/pow 2) (* 64))]
     ["block-size" 5 51 #(-> % int to-odd)]
     ["min-disparity" 0 256 #(-> % int)]
     ["speckle-window-size" 0 100 #(-> % int)]
     ["speckle-range" 0 100 #(-> % int)]
     ["disparity-max-diff" 0 100 #(-> % int)]
     ;STEREO SGM 6
     ["pre-filter-cap" 0 100 #(-> % int)]
     ["uniqueness" 0 100 #(-> % int)]
     ["p1" 0 5000 #(min (- (int (:p2 @*params)) 1) (int %))]
     ["p2" 1 5000 #(max (+ (int (:p1 @*params)) 1) (int %))]
     ["mode" 0 1 #(->> % int (nth [1 3]))]
     ; FILTER 11
     ["iterations" 0 5 #(-> % int)]
     ["radius" 3 64 #(-> % int)]
     ["edge-threshold" 0 1000 #(-> % float (* 0.01))]
     ["disp-threshold" 0 1000 #(-> % float (* 0.01))]
     ["sigma-range" 0 1000 #(-> % float (* 0.1))]
     ; PROJECTION 16
     ["missing" 0 1 #(-> % int)]
     ["ddepth" -1 -1 #(-> % int)]
     ["disp-to-depth-type" 0 1 #(-> % int)]
     ])

  (values [this]
    (let [p @*params]
      (vec (map (fn [[name min max validator]]
                  (validator (clamp (ord (get p (keyword name)) min) min max)))
                (options this)))))

  (setup [this]
    (let [val_arr (values this)
          iterator (iter/->Iterator (values this))
          d_filter (if (< 0 (nth val_arr 11))
                     (opencv_cudastereo/createDisparityBilateralFilter) nil)
          matcher (opencv_cudastereo/createStereoSGM)]
      (doto ^StereoMatcher matcher
        (.setNumDisparities (iter/>>> iterator))
        (.setBlockSize (iter/>>> iterator))
        (.setMinDisparity (iter/>>> iterator))
        (.setSpeckleWindowSize (iter/>>> iterator))
        (.setSpeckleRange (iter/>>> iterator))
        (.setDisp12MaxDiff (iter/>>> iterator)))
      (doto ^StereoSGM matcher
        (.setPreFilterCap (iter/>>> iterator))
        (.setUniquenessRatio (iter/>>> iterator))
        (.setP1 (iter/>>> iterator))
        (.setP2 (iter/>>> iterator))
        (.setMode (iter/>>> iterator)))
      (if (some? d_filter)
        (doto ^DisparityBilateralFilter d_filter
          (.setNumDisparities (nth val_arr 0))
          (.setNumIters (iter/>>> iterator))
          (.setRadius (iter/>>> iterator))
          (.setEdgeThreshold (iter/>>> iterator))
          (.setMaxDiscThreshold (iter/>>> iterator))
          (.setSigmaRange (iter/>>> iterator))))
      (reset! *dsp-filter d_filter)
      (reset! *matcher matcher)))

  (setup [this m]
    (dosync
      (swap! *params
             #(map (fn [[k v]]
                     (assoc % k v))
                   (seq m)))
      (setup this)))

  (setup [this k v]
    (let [kk (if (keyword? k) k (keyword k))]
      (dosync
        (swap! *params assoc kk v)
        (setup this))))

  (param [_ key]
    (if (keyword? key)
      (get @*params key)
      (get @*params (keyword key))))

  (params [_]
    @*params)

  (compute [this [left right]]
    (let [cuda_l (delay (gpu-img-copy (core-to-gpu left) Imgproc/COLOR_BGR2GRAY))
          cuda_r (delay (gpu-img-copy (core-to-gpu right) Imgproc/COLOR_BGR2GRAY))
          ref_disparity (delay (let [disp (calc-disparity-cuda @cuda_l @cuda_r @*matcher)
                                     filter @*dsp-filter]
                                 (if (some? filter)
                                   (do (.apply filter disp @cuda_l disp)
                                       (.apply filter disp @cuda_r disp)))
                                 disp))
          ref_depth (delay (calc-depth-cuda
                             @ref_disparity
                             (first (values this))))
          ref_proj (delay (calc-projection-cuda
                            @ref_disparity
                            disparity-to-depth-map
                            (-> @*params :missing (> 0))
                            (-> @*params :ddepth (ord -1))))]
      (map->MatchResults
        {:depth         ref_depth
         :disparity     (delay (gpu-to-core @ref_disparity))
         :left          (delay (gpu-to-core (gpu-img-copy @cuda_l Imgproc/COLOR_GRAY2BGR)))
         :right         (delay (gpu-to-core (gpu-img-copy @cuda_r Imgproc/COLOR_GRAY2BGR)))
         :disparity_bgr (delay (gpu-to-core (gpu-img-copy @ref_disparity Imgproc/COLOR_GRAY2BGR CvType/CV_16U)))
         :depth_bgr     (delay (gpu-to-core (gpu-img-copy @ref_depth Imgproc/COLOR_BGRA2BGR)))
         :projection    (delay (cv-to-core @ref_proj))
         })))
  )

(defn create-cuda-stereo-sgm
  {:static true
   :tag    BlockMatcher}
  ([^Mat disparity-to-depth-map
    ^StereoSGMProp props]
   (let [matcher (->CudaStereoSGM (atom (map->StereoSGMProp props))
                                  (atom nil)
                                  (atom nil)
                                  (core-to-cv disparity-to-depth-map))]
     (setup matcher)
     matcher))
  ([^Mat disparity-to-depth-map]
   (create-cuda-stereo-sgm
     disparity-to-depth-map
     (map->StereoSGMProp
       {; STEREO MATCHER
        :num-disparities     0
        :block-size          5
        :min-disparity       0
        :speckle-window-size 0
        :speckle-range       0
        :disparity-max-diff  0
        ; STEREO SGM
        :pre-filter-cap      0
        :uniqueness          5
        :p1                  20
        :p2                  200
        :mode                0
        ; FILTER
        :iterations          0
        :radius              3
        :edge-threshold      10
        :disp-threshold      20
        :sigma-range         100
        ; PROJECTION
        :missing             0
        :ddepth              -1
        }))))