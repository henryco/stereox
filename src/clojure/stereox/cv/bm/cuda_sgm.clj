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
                        ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [["min-disparity" 0 256]
     ["num-disparities" 0 2]
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
     (get {0 1 1 3} (int (:mode @*params)) 1)])

  (setup [this]
    (let [vals (iter/->Iterator (values this))
          algorithm (opencv_cudastereo/createStereoSGM
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals)
                      (iter/>>> vals))
          algorithm (StereoSGM. algorithm)]
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
    (let [ref_disparity (calc-disparity-cuda
                          (commons/img-copy left Imgproc/COLOR_BGR2GRAY)
                          (commons/img-copy right Imgproc/COLOR_BGR2GRAY)
                          @*matcher)
          ref_depth (calc-depth-cuda
                      @ref_disparity
                      (first (values this)))
          ref_proj (calc-projection-cuda
                     @ref_disparity
                     disparity-to-depth-map
                     (-> @*params :missing (> 0))
                     (-> @*params :ddepth (ord -1)))
          disp_core (delay (gpu-to-core @ref_disparity))]
      (map->MatchResults
        {:left          (ref left)
         :right         (ref right)
         :disparity     disp_core
         :depth         ref_depth
         :disparity_bgr (delay (commons/img-copy
                                 @disp_core
                                 Imgproc/COLOR_GRAY2BGR))
         :depth_bgr     (delay (commons/img-copy
                                 (gpu-to-core @ref_depth)
                                 Imgproc/COLOR_BGRA2BGR))
         ;:projection    (delay (gpu-to-core @ref_proj))
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
        }))))