(ns stereox.cv.frame-delta
  (:require [stereox.cuda.cuda-kernel :as kernel]
            [stereox.cuda.cuda :as cuda])
  (:import (org.bytedeco.cuda.cudart CUctx_st CUfunc_st CUmod_st CUstream_st)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacpp IntPointer LongPointer Pointer PointerPointer)
           (org.bytedeco.javacv GLCanvasFrame)
           (org.bytedeco.opencv.opencv_core GpuMat)
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

        (let [function (deref *function)
              ;d_src (.address (.ptr ^GpuMat curr))
              ;d_dst (.address (.ptr ^GpuMat curr))
              ]

          (function (.address (.ptr curr))
                    (.address (.ptr out))
                    (.step curr)
                    (.step out)
                    (.cols curr)
                    (.rows curr))

          ; TODO MEM COPY D TO D

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

        )
      ;#(println %)
      )
    out))

(defn init [width height]
  (if (nil? @*function)
    (reset! *function
            (kernel/kernel-function
              "cuda/conv_bgr_8u.cu"
              "conv"
              width
              height
              shorts
              shorts
              int
              int
              int
              int))))

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