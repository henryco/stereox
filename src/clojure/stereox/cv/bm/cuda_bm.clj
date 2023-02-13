(in-ns 'stereox.cv.block-matching)

(deftype CudaStereoBM [^Atom *params
                       ^Atom *matcher]
  BlockMatcher
  (options [_]
    [["num-disparities" 0 16]
     ["block-size" 5 500]
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

  (disparity-map [_ [left right]]
    (let [disp_cv_mat (Mat.)
          left_cv_mat (core-to-cv left)
          right_cv_mat (core-to-cv right)
          disp_cuda_mat (GpuMat.)
          left_cuda_mat (GpuMat.)
          right_cuda_mat (GpuMat.)]
      (.upload left_cuda_mat left_cv_mat)
      (.upload right_cuda_mat right_cv_mat)
      (.compute ^StereoMatcher @*matcher
                left_cuda_mat
                right_cuda_mat
                disp_cuda_mat)
      (.download disp_cuda_mat disp_cv_mat)
      (cv-to-core disp_cv_mat)))

  (project3d [_ disparity disparity-to-depth-map]
    (let [disparity_cv (core-to-cv disparity)
          disparity_cuda (GpuMat.)
          image_3d_cv (Mat.)
          image_3d_cuda (GpuMat.)
          dtp_cv (core-to-cv disparity-to-depth-map)
          dtp_cuda (GpuMat.)]
      (.upload disparity_cuda disparity_cv)
      (.upload dtp_cuda dtp_cv)
      (opencv_cudastereo/reprojectImageTo3D disparity_cuda
                                            image_3d_cuda
                                            dtp_cuda)
      (.download image_3d_cuda image_3d_cv)
      (cv-to-core image_3d_cv)))

  )

(defn create-cuda-stereo-bm
  {:static true
   :tag    BlockMatcher}
  ([^StereoBMProp props]
   (let [matcher (->CudaStereoBM (atom (map->StereoBMProp props))
                                 (atom nil))]
     (setup matcher)
     matcher))
  ([] (create-cuda-stereo-bm
        (map->StereoBMProp
          {:num-disparities 1
           :block-size      21
           }))))
