(in-ns 'stereox.cv.block-matching)

(deftype CpuStereoBM [^Atom *params
                      ^Atom *matcher
                      disparity-to-depth-maps]
  BlockMatcher
  (options [_]
    [; STEREO MATCHER
     ["num-disparities" 1 16 #(-> % int (* 16))]
     ["block-size" 5 51 #(-> % int to-odd)]
     ["min-disparity" 0 100 #(-> % int)]
     ["speckle-window-size" 0 100 #(-> % int)]
     ["speckle-range" 0 100 #(-> % int)]
     ["disparity-max-diff" 0 100 #(-> % int)]
     ; STEREO BM 6
     ["pre-filter-type" 0 1 #(-> % int)]
     ["pre-filter-size" 0 100 #(-> % int)]
     ["pre-filter-cap" 0 100 #(-> % int)]
     ["texture-threshold" 0 100 #(-> % int)]
     ["uniqueness" 0 100 #(-> % int)]
     ["smaller-block" 0 100 #(-> % int)]
     ; FILTER 12 TODO

     ; PROJECTION 17
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
          matcher (StereoBM/create)]
      (doto ^StereoMatcher matcher
        (.setNumDisparities (iter/>>> iterator))
        (.setBlockSize (iter/>>> iterator))
        (.setMinDisparity (iter/>>> iterator))
        (.setSpeckleWindowSize (iter/>>> iterator))
        (.setSpeckleRange (iter/>>> iterator))
        (.setDisp12MaxDiff (iter/>>> iterator)))
      (doto ^StereoBM matcher
        (.setPreFilterType (iter/>>> iterator))
        (.setPreFilterSize (iter/>>> iterator))
        (.setPreFilterCap (iter/>>> iterator))
        (.setTextureThreshold (iter/>>> iterator))
        (.setUniquenessRatio (iter/>>> iterator))
        (.setSmallerBlockSize (iter/>>> iterator)))
      ; TODO: FILTER
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
    (map->StereoBMProp @*params))

  (compute [this [left right]]
    (let [ref_disparity (delay (calc-disparity-cpu
                                 (commons/img-copy left Imgproc/COLOR_BGR2GRAY)
                                 (commons/img-copy right Imgproc/COLOR_BGR2GRAY)
                                 @*matcher))
          core_disp (delay (cv-to-core @ref_disparity))
          core_disp_bgr (delay (commons/img-copy
                                 @core_disp
                                 Imgproc/COLOR_GRAY2BGR
                                 CvType/CV_16U))]
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
                                   (nth disparity-to-depth-maps (last (values this)))
                                   (-> @*params :missing (> 0))
                                   (-> @*params :ddepth (ord -1)))))})))
  )

(defn create-cpu-stereo-bm
  {:static true
   :tag    BlockMatcher}
  ([disparity-to-depth-maps
    ^StereoBMProp props]
   (let [matcher (->CpuStereoBM (atom (map->StereoBMProp props))
                                (atom nil)
                                disparity-to-depth-maps)]
     (setup matcher)
     matcher))
  ([disparity-to-depth-maps]
   (create-cpu-stereo-bm
     disparity-to-depth-maps
     (map->StereoBMProp
       {; STEREO MATCHER
        :num-disparities     1
        :block-size          21
        :min-disparity       0
        :speckle-window-size 0
        :speckle-range       0
        :disparity-max-diff  0
        ; STEREO BM
        :pre-filter-type     0
        :pre-filter-size     9
        :pre-filter-cap      31
        :texture-threshold   3
        :uniqueness          0
        :smaller-block       0
        ; FILTER TODO

        ; PROJECTION
        :missing             0
        :ddepth              -1
        :disp-to-depth-type  0
        }))))