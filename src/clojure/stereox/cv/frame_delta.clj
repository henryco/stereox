(ns stereox.cv.frame-delta
  (:require [stereox.cuda.cuda-kernel :as kernel]
            [stereox.cuda.cuda :as cuda])
  (:import (org.bytedeco.cuda.cudart CUctx_st CUfunc_st CUmod_st CUstream_st)
           (org.bytedeco.javacv OpenCVFrameConverter$ToMat OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacpp BytePointer IntPointer LongPointer Pointer PointerPointer)
           (org.bytedeco.javacv GLCanvasFrame)
           (org.bytedeco.opencv.opencv_core GpuMat Mat Scalar)
           (org.bytedeco.opencv.global opencv_cudaarithm opencv_imgproc opencv_cudaimgproc)
           (org.opencv.core CvType))
  (:gen-class))

(def ^:private *function (atom nil))

(defprotocol FrameDelta
  "Delta calculator"
  (delta [_ input] "Calculate delta"))

(defn render [^GpuMat curr ^GpuMat prev]

  (let [out (GpuMat. (.size curr) (.type curr))]
    ;(opencv_cudaarithm/absdiff
    ;  ^GpuMat prev
    ;  ^GpuMat curr
    ;  ^GpuMat out)

    (cuda/with-context
      (fn [_ _]

        (let [function (deref *function)]

          (function (.ptr ^GpuMat out)
                    (.step out)
                    (.cols out)
                    (.rows out))

          )

        ;(println "-")
        ;(println (.type curr)                                     ;16
        ;         (.channels curr)                                 ;3
        ;         (.depth curr)                                    ;0
        ;         (.elemSize curr)                                 ;3
        ;         (.step curr)                                     ;4096
        ;         (.cols curr)                                     ;1280
        ;         (.rows curr)                                     ;720
        ;         (.isContinuous curr)                             ;false
        ;         (-> curr .type CvType/ELEM_SIZE)                 ;3
        ;         (-> curr .type CvType/typeToString)              ;CV_8UC3
        ;         (-> curr .depth CvType/typeToString)             ;CV_8UC1
        ;         )

        ))
    out))

(defn init [width height]
  (if (nil? @*function)
    (do
      ;(reset! *function
      ;        (kernel/kernel-function
      ;          "cuda/conv_bgr_8u.cu"
      ;          "conv"
      ;          width
      ;          height
      ;          :pointer
      ;          :pointer
      ;          ;shorts
      ;          ;shorts
      ;          int
      ;          int
      ;          int
      ;          int))
      (reset! *function
              (kernel/kernel-function
                "cuda/test_bgr_8u.cu"
                "test"
                width
                height
                Pointer
                int
                int
                int))
      )))

(deftype CudaDelta [*prev]
  FrameDelta
  (delta [_ input]
    (init (.cols input) (.rows input))
    (if (nil? @*prev)
      (do (reset! *prev input) input)
      (let [out (render input @*prev)]
        (reset! *prev input)
        out))))

(defn create-cuda-delta
  {:static true
   :tag    FrameDelta}
  []
  (->CudaDelta (atom nil)))