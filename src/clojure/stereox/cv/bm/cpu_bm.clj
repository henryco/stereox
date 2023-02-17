(in-ns 'stereox.cv.block-matching)

(deftype CpuStereoBM [^Atom *params
                      ^Atom *matcher
                      ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [["num-disparities" 0 100]
     ["block-size" 5 500]
     ["missing" 0 1]
     ["ddepth" -1 -1]
     ["kernel" 0 8]
     ["sigma-1" 0 5]
     ["sigma-2" 0 5]
     ])

  (values [_]
    [(* 16 (int (:num-disparities @*params)))
     (max 5 (to-odd (int (:block-size @*params))))])

  (setup [this]
    (let [[a b] (values this)]
      (reset! *matcher (StereoBM/create a b))))

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
    (map->StereoBMProp @*params))

  (compute [_ [left right]]
    (let [ref_disparity (delay (calc-disparity-cpu
                                 (commons/img-copy left Imgproc/COLOR_BGR2GRAY)
                                 (commons/img-copy right Imgproc/COLOR_BGR2GRAY)
                                 @*matcher))
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

(defn create-cpu-stereo-bm
  {:static true
   :tag    BlockMatcher}
  ([^Mat disparity-to-depth-map
    ^StereoBMProp props]
   (let [matcher (->CpuStereoBM (atom (map->StereoBMProp props))
                                (atom nil)
                                (core-to-cv disparity-to-depth-map))]
     (setup matcher)
     matcher))
  ([^Mat disparity-to-depth-map]
   (create-cpu-stereo-bm
     disparity-to-depth-map
     (map->StereoBMProp
       {:num-disparities 1
        :block-size      21
        :missing         0
        :ddepth          -1
        }))))