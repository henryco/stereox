(in-ns 'stereox.cv.block-matching)

(deftype CudaStereoBM [^Atom *params
                       ^Atom *matcher
                       ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [["num-disparities" 1 16]
     ["block-size" 1 51]
     ])

  (values [_]
    [(* 16 (int (:num-disparities @*params)))
     (max 5 (to-odd (int (:block-size @*params))))])

  (setup [this]
    (let [[a b] (values this)]
      (reset! *matcher (opencv_cudastereo/createStereoBM a b))))

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
                     disparity-to-depth-map)
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


(defn create-cuda-stereo-bm
  {:static true
   :tag    BlockMatcher}
  ([^Mat disparity-to-depth-map
    ^StereoBMProp props]
   (let [matcher (->CudaStereoBM (atom (map->StereoBMProp props))
                                 (atom nil)
                                 (core-to-cv disparity-to-depth-map))]
     (setup matcher)
     matcher))
  ([^Mat disparity-to-depth-map]
   (create-cuda-stereo-bm
     disparity-to-depth-map
     (map->StereoBMProp
       {:num-disparities 1
        :block-size      21
        }))))
