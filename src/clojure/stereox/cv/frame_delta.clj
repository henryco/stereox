(ns stereox.cv.frame-delta
  (:require [stereox.cuda.cuda-kernel :as kernel]
            [stereox.cuda.cuda :as cuda])
  (:import (clojure.lang IFn)
           (org.bytedeco.cuda.cudart CUctx_st CUfunc_st CUmod_st CUstream_st)
           (org.bytedeco.javacv OpenCVFrameConverter$ToMat OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacpp BytePointer IntPointer LongPointer Pointer PointerPointer)
           (org.bytedeco.javacv GLCanvasFrame)
           (org.bytedeco.opencv.opencv_core GpuMat Mat Scalar)
           (org.bytedeco.opencv.global opencv_cudaarithm opencv_imgproc opencv_cudaimgproc)
           (org.opencv.core CvType))
  (:use [stereox.utils.cmacros])
  (:gen-class))

(defn init [input]
  (let [width (.cols input)
        height (.rows input)]
    {

     :function
     (kernel/kernel-function
       "cuda/test_bgr_8u.cu"
       "test"
       width
       height
       Pointer
       int
       int
       int)

     }))

(defn render [{:keys [^GpuMat curr ^GpuMat prev ^IFn function]}]

  (let [out (GpuMat. (.size curr) (.type curr))]
    ;(opencv_cudaarithm/absdiff
    ;  ^GpuMat prev
    ;  ^GpuMat curr
    ;  ^GpuMat out)

    (cuda/with-context
      (fn [_ _]

        (function (.ptr ^GpuMat out)
                  (.step out)
                  (.cols out)
                  (.rows out))

        ))
    out))


(defprotocol FrameDelta
  "Delta calculator"
  (delta [_ input] "Calculate delta"))

(deftype CudaDelta [*prev *state]
  FrameDelta
  (delta [_ input]
    (if (nil? @*state)
      (reset! *state (init input)))
    (if (nil? @*prev)
      (do (reset! *prev input) input)
      (let [out (render (merge @*state {:curr input :prev @*prev}))]
        (reset! *prev input)
        out))))

(defn create-cuda-delta
  {:static true
   :tag    FrameDelta}
  []
  (->CudaDelta
    (atom nil)
    (atom nil)))