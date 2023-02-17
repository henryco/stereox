(in-ns 'stereox.cv.block-matching)

(deftype CudaStereoBM [^Atom *params
                       ^Atom *matcher
                       ^Atom *filter
                       ^Mat disparity-to-depth-map]
  BlockMatcher
  (options [_]
    [["num-disparities" 1 16]
     ["block-size" 1 51]
     ["missing" 0 1]
     ["ddepth" -1 -1]
     ["kernel" 0 8]
     ["sigma-1" 0 5]
     ["sigma-2" 0 5]
     ])

  (values [_]
    [(* 16 (int (:num-disparities @*params)))
     (max 5 (to-odd (int (:block-size @*params))))
     (max 0 (- (* 2 (:kernel @*params)) 1))
     (max 0 (:sigma-1 @*params))
     (max 0 (:sigma-2 @*params))
     ])

  (setup [this]
    (let [[n b k sigma_1 sigma_2] (values this)
          kernel_size (Size. k k)]
      (reset! *matcher (opencv_cudastereo/createStereoBM n b))
      (reset! *filter (opencv_cudafilters/createGaussianFilter opencv_core/CV_8U
                                                               opencv_core/CV_8U
                                                               kernel_size
                                                               sigma_1))
      )
    )

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
    (let [cuda_l (delay (gpu-img-copy (core-to-gpu left)
                                      Imgproc/COLOR_BGR2GRAY))
          cuda_r (delay (gpu-img-copy (core-to-gpu right)
                                      Imgproc/COLOR_BGR2GRAY))
          ref_disparity (delay (calc-disparity-cuda
                                 @cuda_l
                                 @cuda_r
                                 @*matcher))
          ref_depth (delay (calc-depth-cuda
                             @ref_disparity
                             (first (values this))))
          ref_proj (delay (calc-projection-cuda
                            @ref_disparity
                            disparity-to-depth-map
                            (-> @*params :missing (> 0))
                            (-> @*params :ddepth (ord -1))))
          ]

      (map->MatchResults
        {:depth         ref_depth
         :disparity     (delay (gpu-to-core @ref_disparity))
         :left          (delay (gpu-to-core
                                 (gpu-img-copy @cuda_l
                                               Imgproc/COLOR_GRAY2BGR)))
         :right         (delay (gpu-to-core
                                 (gpu-img-copy @cuda_r
                                               Imgproc/COLOR_GRAY2BGR)))
         :disparity_bgr (delay (gpu-to-core
                                 (gpu-img-copy @ref_disparity
                                               Imgproc/COLOR_GRAY2BGR)))
         :depth_bgr     (delay (gpu-to-core
                                 (gpu-img-copy @ref_depth
                                               Imgproc/COLOR_BGRA2BGR)))
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
        :missing         0
        :ddepth          -1
        :kernel          5
        :sigma-1         0
        :sigma-2         0
        }))))
