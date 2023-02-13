(in-ns 'stereox.cv.block-matching)

(defrecord StereoSGMProp
  [^Integer min-disparity
   ^Integer num-disparities
   ^Integer p1
   ^Integer p2
   ^Integer uniqueness
   ^Integer mode])

(deftype CudaStereoSGM [^Atom *params
                        ^Atom *matcher]
  BlockMatcher
  (options [_]
    [["min-disparity" 0 256]
     ["num-disparities" 0 2]
     ["p1" 0 5000]
     ["p2" 1 5000]
     ["uniqueness" 0 100]
     ["mode" 0 1]
     ; MODE_HH = 1 MODE_HH4 = 3 : {0 1 1 3}
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

(defn create-cuda-stereo-sgm
  {:static true
   :tag    BlockMatcher}
  ([^StereoSGMProp props]
   (let [matcher (->CudaStereoSGM (atom (map->StereoSGMProp props))
                                  (atom nil))]
     (setup matcher)
     matcher))
  ([] (create-cuda-stereo-sgm
        (map->StereoSGMProp
          {:min-disparity   0
           :num-disparities 1
           :p1              10
           :p2              120
           :uniqueness      5
           :mode            0
           }))))