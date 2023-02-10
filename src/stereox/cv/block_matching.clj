(ns stereox.cv.block-matching
  (:import (clojure.lang Atom)
           (org.opencv.calib3d Calib3d StereoBM StereoMatcher StereoSGBM)
           (org.opencv.core Mat))
  (:gen-class))

(defn- ord
  "Returns original value if not nil,
  otherwise return default value"
  [v default]
  (if (nil? v) default v))

(defn- to-odd
  "Returns 'v' if odd, otherwise 'v+1'"
  [v]
  (if (= (mod v 2)
         0)
    (+ 1 v)
    v))

(defprotocol BlockMatcher
  "Block matcher algorithm interface"

  (options [_]
    "Returns tweakable options map {:key :max-val}")

  (setup [_] [_ map] [_ k v]
    "Update algorithm parameter (key value)")

  (param [_ key]
    "Get algorithm parameter")

  (params [_]
    "Get algorithm parameters")

  (disparity-map [_ images]
    "Calculate disparity map
    Returns:
      org.opencv.core.Mat")

  (project3d [_ disparity disparity-to-depth-map]
    "Project image to 3D
    Returns:
      org.opencv.core.Mat")
  )

(defrecord StereoBMProp
  [^Integer num-disparities
   ^Integer block-size
   ^Integer missing
   ^Integer ddepth])

(deftype CpuStereoBM [^Atom *params
                      ^Atom *matcher]
  BlockMatcher

  (options [_]
    [["num-disparities" 0 100]
     ["block-size" 5 100]
     ["missing" 0 1]
     ["ddepth" -1 -1]
     ])

  (setup [_]
    (reset! *matcher (StereoBM/create (* 16 (int (:num-disparities @*params)))
                                      (max 5 (to-odd (int (:block-size @*params)))))))

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
      (.compute ^StereoBM @*matcher left right disparity)
      disparity))

  (project3d [_ disparity disparity-to-depth-map]
    (let [_3dImage (Mat.)
          handle (-> @*params :missing (> 0))
          ddepth (-> @*params :ddepth (ord -1))]
      (Calib3d/reprojectImageTo3D disparity _3dImage disparity-to-depth-map handle ddepth)
      _3dImage)))

(defn create-cpu-stereo-bm
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
                        ^Atom *matcher]
  BlockMatcher

  (options [_]
    [["min-disparity" 0 100]
     ["num-disparities" 1 100]
     ["block-size" 1 100]
     ["p1" 0 5000]
     ["p2" 1 5000]
     ["max-disparity" 0 1000]
     ["pre-filter-cap" 0 100]
     ["uniqueness" 4 16]
     ["speckle-window-size" 0 201]
     ["speckle-range" 0 100]
     ["mode" 0 3]
     ; MODE_SGBM = 0 MODE_HH = 1 MODE_SGBM_3WAY = 2 MODE_HH4 = 3
     ["missing" 0 1]
     ["ddepth" -1 -1]
     ])

  (setup [_]
    (reset! *matcher (StereoSGBM/create
                       (int (:min-disparity @*params))
                       (* 16 (max 1 (int (:num-disparities @*params))))
                       (max 0 (min 100 (to-odd (int (:block-size @*params)))))
                       (min (- (int (:p2 @*params)) 1)
                            (int (:p1 @*params)))
                       (max (+ (int (:p1 @*params)) 1)
                            (int (:p2 @*params)))
                       (int (:max-disparity @*params))
                       (int (:pre-filter-cap @*params))
                       (min 16 (max 4 (int (:uniqueness @*params))))
                       (min 201 (max 0 (int (:speckle-window-size @*params))))
                       (max 0 (int (:speckle-range @*params)))
                       (int (:mode @*params)))))

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
    (let [disparity (Mat.)]
      (.compute ^StereoSGBM @*matcher left right disparity)
      disparity))

  (project3d [_ disparity disparity-to-depth-map]
    (let [_3dImage (Mat.)
          handle (-> @*params :missing (> 0))
          ddepth (-> @*params :ddepth (ord -1))]
      (Calib3d/reprojectImageTo3D disparity _3dImage disparity-to-depth-map handle ddepth)
      _3dImage)))

(defn create-cpu-stereo-sgbm
  ([^StereoSGBMProp props]
   (let [matcher (->CpuStereoSGBM (atom (map->StereoSGBMProp props))
                                  (atom nil))]
     (setup matcher)
     matcher))
  ([] (create-cpu-stereo-sgbm
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

(defn create-stereo-matcher
  "Create stereo matcher instance.
  key: [:cpu-bm|:cpu-sgbm|:gpu-sgbm]"
  {:tag    StereoMatcher
   :static true}
  [key]
  (case key
    :cpu-bm (create-cpu-stereo-bm)
    :cpu-sgbm (create-cpu-stereo-sgbm)
    :cuda-sgbm (throw (Exception. "Not implemented yet"))
    (throw (Exception. (str "Unknown matcher name: " key)))
    ))