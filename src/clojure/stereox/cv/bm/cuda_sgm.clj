(in-ns 'stereox.cv.block-matching)

(defrecord StereoSGMProp
  [^Integer min-disparity
   ^Integer num-disparities
   ^Integer p1
   ^Integer p2
   ^Integer uniqueness
   ^Integer mode])

(deftype CudaStereoSGM [^Atom *params
                        ^Atom *matcher
                        ^Atom *dsp-filter
                        ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [["min-disparity" 0 256]
     ["num-disparities" 0 2]
     ["iterations" 0 5]
     ["radius" 3 64]
     ["edge-threshold" 0 1000]
     ["disp-threshold" 0 1000]
     ["sigma-range" 0 1000]
     ["p1" 0 5000]
     ["p2" 1 5000]
     ["uniqueness" 0 100]
     ["mode" 0 1]
     ; MODE_HH = 1 MODE_HH4 = 3 : {0 1 1 3}
     ["missing" 0 1]
     ["ddepth" -1 -1]
     ])

  (values [_]
    [(max 0 (int (:min-disparity @*params)))
     (get {0 64 1 128 2 256} (int (:num-disparities @*params)) 64)
     (min (- (int (:p2 @*params)) 1)
          (int (:p1 @*params)))
     (max (+ (int (:p1 @*params)) 1)
          (int (:p2 @*params)))
     (max 0 (int (:uniqueness @*params)))
     (get {0 1 1 3} (int (:mode @*params)) 1)
     (max 3 (int (:radius @*params)))
     (max 0 (int (:iterations @*params)))
     (max 0 (float (* 0.01 (:edge-threshold @*params))))
     (max 0 (float (* 0.01 (:disp-threshold @*params))))
     (max 0 (float (* 0.1 (:sigma-range @*params))))
     ])

  (setup [this]
    (let [vals (iter/->Iterator (values this))
          algorithm (opencv_cudastereo/createStereoSGM
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals))
          algorithm (StereoSGM. algorithm)

          ;min_disp (.getMinDisparity algorithm)
          num_disp (.getNumDisparities algorithm)
          ;r-matcher (opencv_cudastereo/createStereoSGM)

          r (iter/>>> vals)
          i (iter/>>> vals)
          d_filter (if (> i 0) (opencv_cudastereo/createDisparityBilateralFilter num_disp r i) nil)
          ]
      (if (some? d_filter)
        (do (.setEdgeThreshold d_filter (iter/>>> vals))
            (.setMaxDiscThreshold d_filter (iter/>>> vals))
            (.setSigmaRange d_filter (iter/>>> vals))
            ))

      ;(.setMinDisparity r-matcher (- 1 (+ min_disp num_disp)))
      ;(.setNumDisparities r-matcher num_disp)
      ;(.setUniquenessRatio r-matcher 0)
      ;(.setBlockSize r-matcher (.getBlockSize algorithm))
      ;(.setP1 r-matcher (.getP1 algorithm))
      ;(.setP2 r-matcher (.getP2 algorithm))
      ;(.setMode r-matcher (.getMode algorithm))
      ;(.setPreFilterCap r-matcher (.getPreFilterCap algorithm))
      ;(.setDisp12MaxDiff r-matcher 1000000)
      ;(.setSpeckleWindowSize r-matcher 0)

      (reset! *dsp-filter d_filter)
      (reset! *matcher algorithm)))

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
    (let [cuda_l (future (gpu-img-copy (core-to-gpu left) Imgproc/COLOR_BGR2GRAY))
          cuda_r (future (gpu-img-copy (core-to-gpu right) Imgproc/COLOR_BGR2GRAY))
          ref_disparity (delay (let [disp (calc-disparity-cuda @cuda_l @cuda_r @*matcher)
                                     filter @*dsp-filter]
                                 (if (some? filter)
                                   (do (.apply filter disp @cuda_l disp)
                                       (.apply filter disp @cuda_r disp)))
                                 disp))
          ref_depth (delay (calc-depth-cuda
                             @ref_disparity
                             (second (values this))))
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
       {:min-disparity   0
        :num-disparities 0
        :p1              10
        :p2              120
        :uniqueness      5
        :mode            0
        :missing         0
        :ddepth          -1
        :radius          3
        :iterations      0
        :edge-threshold  1
        :disp-threshold  2
        :sigma-range     100
        }))))