(ns stereox.cv.block-matching
  (:import (clojure.lang Atom)
           (org.bytedeco.javacv OpenCVFrameConverter$ToMat OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.bytedeco.opencv.opencv_core GpuMat Mat)
           (org.bytedeco.opencv.opencv_cudastereo StereoSGM)
           (org.bytedeco.opencv.opencv_calib3d StereoSGBM StereoBM StereoMatcher)
           (org.bytedeco.opencv.global opencv_calib3d opencv_cudastereo))
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

(defprotocol BlockMatcher
  "Block matcher algorithm interface"

  (values [_]
    "Returns vector or sequence of fitted parameter values")

  (options [_]
    "Returns tweakable options vector [[id min max]...]")

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

(load "bm/cuda_sgm")
(load "bm/cpu_sgbm")
(load "bm/cuda_bm")
(load "bm/cpu_bm")

(defn create-stereo-matcher
  "Create BlockMatcher instance.
  key: [:cpu-bm|:cpu-sgbm|:cuda-bm|:cuda-sgm]"
  {:tag    BlockMatcher
   :static true}
  [key]
  (case key
    :cpu-bm (create-cpu-stereo-bm)
    :cuda-bm (create-cuda-stereo-bm)
    :cpu-sgbm (create-cpu-stereo-sgbm)
    :cuda-sgm (create-cuda-stereo-sgm)
    (throw (Exception. (str "Unknown matcher name: " key)))
    ))