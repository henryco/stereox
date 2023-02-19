(ns stereox.cv.block-matching
  (:import (clojure.lang Atom Ref)
           (org.bytedeco.javacv OpenCVFrameConverter$ToMat OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.bytedeco.opencv.opencv_core GpuMat Mat Size Stream)
           (org.bytedeco.opencv.opencv_cudafilters Filter)
           (org.bytedeco.opencv.opencv_cudastereo DisparityBilateralFilter StereoSGM)
           (org.bytedeco.opencv.opencv_calib3d StereoSGBM StereoBM StereoMatcher)
           (org.bytedeco.opencv.global opencv_imgproc opencv_calib3d opencv_cudastereo opencv_cudafilters opencv_core opencv_cudaimgproc opencv_ximgproc)
           (org.opencv.core CvType)
           (org.opencv.imgproc Imgproc))
  (:require [stereox.utils.iterators :as iter]
            [stereox.utils.commons :as commons])
  (:gen-class))

(defn- clamp
  "Constrain a value to lie between two further values"
  {:static true}
  [value $min$ $max$]
  (min $max$ (max $min$ value)))

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

(defn- core-to-gpu
  {:static true
   :tag    Mat}
  [mat]
  (let [g_mat (GpuMat.)]
    (.upload g_mat (core-to-cv mat))
    g_mat))

(defn- gpu-to-cv
  {:static true
   :tag    Mat}
  [^GpuMat mat]
  (let [cv_mat (Mat.)]
    (.download mat cv_mat)
    cv_mat))

(defn- cv-to-gpu
  {:static true
   :tag    GpuMat}
  [^Mat mat]
  (-> mat
      (cv-to-core)
      (core-to-gpu)))

(defn- gpu-to-core
  {:static true}
  [^GpuMat mat]
  (cv-to-core (gpu-to-cv mat)))

(defn- gpu-img-copy
  "Copy image matrix, optionally apply color change.
  e.g. code:  Imgproc/COLOR_BGR2GRAY
       rtype: CvType/CV_8U"
  {:tag    GpuMat
   :static true}
  ([^GpuMat image ^Integer code]
   (let [output (GpuMat.)]
     (opencv_cudaimgproc/cvtColor image output code)
     output))
  ([^GpuMat image ^Integer code ^Integer rtype]
   (let [output (GpuMat.)
         cpy (GpuMat.)]
     (.convertTo image cpy rtype)
     (opencv_cudaimgproc/cvtColor cpy output code)
     output)))

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

(defn- calc-disparity-cpu
  {:static true
   :tag    Ref}
  [left right ^StereoMatcher matcher]
  (let [disparity (Mat.)]
    (.compute matcher
              (core-to-cv left)
              (core-to-cv right)
              disparity)
    disparity))

(defn- calc-projection-cpu
  {:static true
   :tag    Ref}
  [^Mat disparity ^Mat disparity-to-depth-map handle ddepth]
  (let [_3dImage (Mat.)]
    (opencv_calib3d/reprojectImageTo3D
      ^Mat disparity
      ^Mat _3dImage
      ^Mat disparity-to-depth-map
      (boolean handle)
      (int ddepth))
    _3dImage))

(defn- calc-disparity-cuda
  {:static true
   :tag    Ref}
  [^GpuMat left ^GpuMat right ^StereoMatcher matcher]
  (let [disp_cuda_mat (GpuMat.)]
    (.compute ^StereoMatcher matcher
              ^GpuMat left
              ^GpuMat right
              ^GpuMat disp_cuda_mat)
    disp_cuda_mat))

(defn- calc-depth-cuda
  {:static true
   :tag    Ref}
  [^GpuMat disparity ^Integer ndisp]
  (let [color_cuda_mat (GpuMat.)]
    (opencv_cudastereo/drawColorDisp
      disparity
      color_cuda_mat
      ndisp)
    color_cuda_mat))

(defn- calc-projection-cuda
  {:static true
   :tag    Ref}
  [^GpuMat disparity
   ^Mat disparity-to-depth-map
   handle
   ddepth]
  (calc-projection-cpu
    (gpu-to-cv disparity)
    disparity-to-depth-map
    (boolean handle)
    (int ddepth))
  ;(delay
  ;  (let [image_3d_cuda (GpuMat.)]
  ;    (opencv_cudastereo/reprojectImageTo3D
  ;      disparity
  ;      image_3d_cuda
  ;      disparity-to-depth-map
  ;      ;(gpu-to-cv disparity-to-depth-map)
  ;      ;disparity-to-depth-map
  ;      )
  ;    image_3d_cuda))
  )

(defrecord MatchResults
  [^Ref left
   ^Ref right
   ^Ref disparity
   ^Ref disparity_bgr
   ^Ref depth
   ^Ref depth_bgr
   ^Ref projection])

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

  (compute [_ images]
    "Calculate disparity map
    Returns:
      MatchResults")
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
  [key disparity_to_depth_matrix]
  (case key
    :cpu-bm (create-cpu-stereo-bm disparity_to_depth_matrix)
    :cuda-bm (create-cuda-stereo-bm disparity_to_depth_matrix)
    :cpu-sgbm (create-cpu-stereo-sgbm disparity_to_depth_matrix)
    :cuda-sgm (create-cuda-stereo-sgm disparity_to_depth_matrix)
    (throw (Exception. (str "Unknown matcher name: " key)))
    ))