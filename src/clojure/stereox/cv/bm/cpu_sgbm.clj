(in-ns 'stereox.cv.block-matching)

(defrecord StereoSGBMProp
  [^Integer num-disparities
   ^Integer block-size
   ^Integer min-disparity
   ^Integer speckle-window-size
   ^Integer speckle-range
   ^Integer disparity-max-diff
   ^Integer pre-filter-cap
   ^Integer uniqueness
   ^Integer p1
   ^Integer p2
   ^Integer mode
   ^Integer missing
   ^Integer ddepth])

(deftype CpuStereoSGBM [^Atom *params
                        ^Atom *matcher
                        disparity-to-depth-maps]
  BlockMatcher
  (options [_]
    [; STEREO MATCHER 0
     ["num-disparities" 1 16 #(->> % int (* 16))]
     ["block-size" 1 51 #(-> % int to-odd)]
     ["min-disparity" 0 256 #(-> % int)]
     ["speckle-window-size" 0 100 #(-> % int)]
     ["speckle-range" 0 100 #(-> % int)]
     ["disparity-max-diff" 0 100 #(-> % int)]
     ;STEREO SGM 6
     ["pre-filter-cap" 0 100 #(-> % int)]
     ["uniqueness" 0 100 #(-> % int)]
     ["p1" 0 5000 #(min (- (int (:p2 @*params)) 1) (int %))]
     ["p2" 1 5000 #(max (+ (int (:p1 @*params)) 1) (int %))]
     ["mode" 0 3 #(-> % int)]
     ; FILTER 11 TODO

     ; PROJECTION 16
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
          matcher (StereoSGBM/create)]
      (doto ^StereoMatcher matcher
        (.setNumDisparities (iter/>>> iterator))
        (.setBlockSize (iter/>>> iterator))
        (.setMinDisparity (iter/>>> iterator))
        (.setSpeckleWindowSize (iter/>>> iterator))
        (.setSpeckleRange (iter/>>> iterator))
        (.setDisp12MaxDiff (iter/>>> iterator)))
      (doto ^StereoSGBM matcher
        (.setPreFilterCap (iter/>>> iterator))
        (.setUniquenessRatio (iter/>>> iterator))
        (.setP1 (iter/>>> iterator))
        (.setP2 (iter/>>> iterator))
        (.setMode (iter/>>> iterator)))
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
    @*params)

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

(defn create-cpu-stereo-sgbm
  {:static true
   :tag    BlockMatcher}
  ([disparity-to-depth-maps
    ^StereoSGBMProp props]
   (let [matcher (->CpuStereoSGBM (atom (map->StereoSGBMProp props))
                                  (atom nil)
                                  disparity-to-depth-maps)]
     (setup matcher)
     matcher))
  ([disparity-to-depth-maps]
   (create-cpu-stereo-sgbm
     disparity-to-depth-maps
     (map->StereoSGBMProp
       {; STEREO MATCHER
        :num-disparities     1
        :block-size          3
        :min-disparity       1
        :speckle-window-size 100
        :speckle-range       32
        :disparity-max-diff  0
        ; STEREO SGBM
        :pre-filter-cap      31
        :uniqueness          5
        :p1                  10
        :p2                  120
        :mode                0
        ; FILTER TODO

        ; PROJECTION
        :missing             0
        :ddepth              -1
        :disp-to-depth-type  0
        }))))