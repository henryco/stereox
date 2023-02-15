(in-ns 'stereox.cv.block-matching)

(defrecord StereoSGBMProp
  [^Integer min-disparity
   ^Integer num-disparities
   ^Integer block-size
   ^Integer p1
   ^Integer p2
   ^Integer max-disparity
   ^Integer pre-filter-cap
   ^Integer uniqueness
   ^Integer speckle-window-size
   ^Integer speckle-range
   ^Integer mode
   ^Integer missing
   ^Integer ddepth])

(deftype CpuStereoSGBM [^Atom *params
                        ^Atom *matcher
                        ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [["min-disparity" 0 100]
     ["num-disparities" 1 100]
     ["block-size" 1 500]
     ["p1" 0 5000]
     ["p2" 1 5000]
     ["max-disparity" 0 1000]
     ["pre-filter-cap" 0 1000]
     ["uniqueness" 0 100]
     ["speckle-window-size" 0 500]
     ["speckle-range" 0 500]
     ["mode" 0 3]
     ; MODE_SGBM = 0 MODE_HH = 1 MODE_SGBM_3WAY = 2 MODE_HH4 = 3
     ["missing" 0 1]
     ["ddepth" -1 -1]
     ])

  (values [_]
    [(max 0 (int (:min-disparity @*params)))
     (* 16 (max 1 (int (:num-disparities @*params))))
     (max 0 (to-odd (int (:block-size @*params))))
     (min (- (int (:p2 @*params)) 1)
          (int (:p1 @*params)))
     (max (+ (int (:p1 @*params)) 1)
          (int (:p2 @*params)))
     (max 0 (int (:max-disparity @*params)))
     (max 0 (int (:pre-filter-cap @*params)))
     (max 0 (int (:uniqueness @*params)))
     (max 0 (int (:speckle-window-size @*params)))
     (max 0 (int (:speckle-range @*params)))
     (max 0 (min 3 (int (:mode @*params))))])

  (setup [this]
    (let [vals (iter/->Iterator (values this))]
      (reset! *matcher (StereoSGBM/create
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)
                         (iter/>>> vals)))))

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

  (compute [_ [left right]]
    (let [ref_disparity (calc-disparity-cpu
                          (commons/img-copy left Imgproc/COLOR_BGR2GRAY)
                          (commons/img-copy right Imgproc/COLOR_BGR2GRAY)
                          @*matcher)
          core_disp (delay (cv-to-core @ref_disparity))
          core_disp_bgr (delay (commons/img-copy
                                 @core_disp
                                 Imgproc/COLOR_GRAY2BGR))]
      (map->MatchResults
        {:left          (ref left)
         :right         (ref right)
         :disparity     core_disp
         :depth         core_disp
         :disparity_bgr core_disp_bgr
         :depth_bgr     core_disp_bgr
         :projection    (delay (cv-to-core
                                 (calc-projection-cpu
                                   @ref_disparity
                                   disparity-to-depth-map
                                   (-> @*params :missing (> 0))
                                   (-> @*params :ddepth (ord -1)))))})))
  )

(defn create-cpu-stereo-sgbm
  {:static true
   :tag    BlockMatcher}
  ([^Mat disparity-to-depth-map
    ^StereoSGBMProp props]
   (let [matcher (->CpuStereoSGBM (atom (map->StereoSGBMProp props))
                                  (atom nil)
                                  (core-to-cv disparity-to-depth-map))]
     (setup matcher)
     matcher))
  ([^Mat disparity-to-depth-map]
   (create-cpu-stereo-sgbm
     disparity-to-depth-map
     (map->StereoSGBMProp
       {:min-disparity       1
        :num-disparities     1
        :block-size          3
        :p1                  216
        :p2                  284
        :max-disparity       1
        :pre-filter-cap      1
        :uniqueness          10
        :speckle-window-size 100
        :speckle-range       32
        :mode                0
        :missing             0
        :ddepth              -1
        }))))