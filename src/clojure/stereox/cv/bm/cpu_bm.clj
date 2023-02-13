(in-ns 'stereox.cv.block-matching)

(deftype CpuStereoBM [^Atom *params
                      ^Atom *matcher]
  BlockMatcher
  (options [_]
    [["num-disparities" 0 100]
     ["block-size" 5 500]
     ["missing" 0 1]
     ["ddepth" -1 -1]
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

  (disparity-map [_ [left right]]
    (let [disparity (Mat.)]
      (.compute ^StereoMatcher @*matcher
                (core-to-cv left)
                (core-to-cv right)
                disparity)
      (cv-to-core disparity)))

  (project3d [_ disparity disparity-to-depth-map]
    (let [_3dImage (Mat.)
          handle (-> @*params :missing (> 0))
          ddepth (-> @*params :ddepth (ord -1))]
      (opencv_calib3d/reprojectImageTo3D (core-to-cv disparity)
                                         _3dImage
                                         (core-to-cv disparity-to-depth-map)
                                         (boolean handle)
                                         (int ddepth))
      (cv-to-core _3dImage))))

(defn create-cpu-stereo-bm
  {:static true
   :tag    BlockMatcher}
  ([^StereoBMProp props]
   (let [matcher (->CpuStereoBM (atom (map->StereoBMProp props))
                                (atom nil))]
     (setup matcher)
     matcher))
  ([] (create-cpu-stereo-bm
        (map->StereoBMProp
          {:num-disparities 1
           :block-size      21
           :missing         0
           :ddepth          -1
           }))))