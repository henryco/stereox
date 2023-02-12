(ns stereox.cv.block-matching
  (:import (clojure.lang Atom)
           (org.bytedeco.javacv OpenCVFrameConverter$ToMat OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.bytedeco.opencv.opencv_core Mat)
           (org.bytedeco.opencv.opencv_cudastereo StereoSGM)
           (org.bytedeco.opencv.opencv_calib3d StereoSGBM StereoBM StereoMatcher)
           (org.bytedeco.opencv.global opencv_calib3d))
  (:require [stereox.utils.iterators :as iter])
  (:gen-class))

(defn- core-to-cv
  {:static true
   :tag    Mat}
  [^org.opencv.core.Mat coreMat]
  (let [conv_1 (new OpenCVFrameConverter$ToMat)
        conv_2 (new OpenCVFrameConverter$ToOrgOpenCvCoreMat)]
    (->> coreMat (.convert conv_2) (.convert conv_1))))

(defn- cv-to-core
  {:static true
   :tag    org.opencv.core.Mat}
  [^Mat cvMat]
  (let [conv_1 (new OpenCVFrameConverter$ToMat)
        conv_2 (new OpenCVFrameConverter$ToOrgOpenCvCoreMat)]
    (->> cvMat (.convert conv_1) (.convert conv_2))))

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

(defprotocol MatcherState
  (-*matcher [_]))

(defprotocol BlockMatcher
  "Block matcher algorithm interface"

  (values [_]
    "Returns vector or sequence of fitted parameter values")

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
  MatcherState
  (-*matcher [_] *matcher)

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
  MatcherState
  (-*matcher [_] *matcher)

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

(deftype CudaStereoBM [^CpuStereoBM stereo]
  BlockMatcher

  (setup [this]
    (let [[a b] (values this)]
      (reset! (-*matcher stereo)
              (org.bytedeco.opencv.opencv_cudastereo.StereoBM/create a b))))

  (values [_]
    (values stereo))

  (options [_]
    (options stereo))

  (setup [_ map]
    (setup stereo map))

  (setup [_ k v]
    (setup stereo k v))

  (param [_ key]
    (param stereo key))

  (params [_]
    (params stereo))

  (disparity-map [_ images]
    (disparity-map stereo images))

  (project3d [_ disparity disparity-to-depth-map]
    (project3d stereo disparity disparity-to-depth-map)))

(deftype CudaStereoSGBM [^CpuStereoSGBM stereo]
  BlockMatcher

  (setup [this]
    (let [*matcher (-*matcher stereo)
          vals (iter/->Iterator (values this))]
      (reset! *matcher (StereoSGM/create
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

  (values [_]
    (values stereo))

  (options [_]
    (options stereo))

  (setup [_ map]
    (setup stereo map))

  (setup [_ k v]
    (setup stereo k v))

  (param [_ key]
    (param stereo key))

  (params [_]
    (params stereo))

  (disparity-map [_ images]
    (disparity-map stereo images))

  (project3d [_ disparity disparity-to-depth-map]
    (project3d stereo disparity disparity-to-depth-map)))

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

(defn create-cuda-stereo-bm
  {:static true
   :tag    BlockMatcher}
  ([^StereoBMProp props]
   (let [matcher (->CudaStereoBM
                   (->CpuStereoBM (atom (map->StereoBMProp props))
                                  (atom nil)))]
     (setup matcher)
     matcher))
  ([] (create-cuda-stereo-bm
        (map->StereoBMProp
          {:num-disparities 1
           :block-size      21
           :missing         0
           :ddepth          -1
           }))))

(defn create-cpu-stereo-sgbm
  {:static true
   :tag    BlockMatcher}
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

(defn create-cuda-stereo-sgbm
  {:static true
   :tag    BlockMatcher}
  ([^StereoSGBMProp props]
   (let [matcher (->CudaStereoSGBM
                   (->CpuStereoSGBM (atom (map->StereoSGBMProp props))
                                    (atom nil)))]
     (setup matcher)
     matcher))
  ([] (create-cuda-stereo-sgbm
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
  "Create BlockMatcher instance.
  key: [:cpu-bm|:cpu-sgbm|:cuda-bm|:cuda-sgbm]"
  {:tag    BlockMatcher
   :static true}
  [key]
  (case key
    :cpu-bm (create-cpu-stereo-bm)
    :cuda-bm (create-cuda-stereo-bm)
    :cpu-sgbm (create-cpu-stereo-sgbm)
    :cuda-sgbm (create-cuda-stereo-sgbm)
    (throw (Exception. (str "Unknown matcher name: " key)))
    ))