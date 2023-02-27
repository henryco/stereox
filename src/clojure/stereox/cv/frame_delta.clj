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
        (let [width (int-array [(.cols curr)])
              height (int-array [(.rows curr)])
              step_src (int-array [(.step curr)])
              step_dst (int-array [(.step out)])
              dev_src (long-array [(.address (.cudaPtr curr))])
              dev_dst (long-array [(.address (.cudaPtr out))])
              params (into-array Pointer [(LongPointer. dev_src)
                                          (LongPointer. dev_dst)
                                          (IntPointer. step_src)
                                          (IntPointer. step_dst)
                                          (IntPointer. width)
                                          (IntPointer. height)])
              dimension (cuda/optimal-bt (.cols curr) (.rows curr))
              b_x (-> dimension first first int)
              b_y (-> dimension first last int)
              t_x (-> dimension last first int)
              t_y (-> dimension last last int)
              ]

          ; TODO MEM COPY D TO D

          (cuda/status (cudart/cuLaunchKernel ^CUfunc_st (deref *function)
                                              b_x b_y 1
                                              t_x t_y 1
                                              0 nil
                                              (PointerPointer. ^"[Lorg.bytedeco.javacpp.Pointer;" params)
                                              nil))
          (cuda/status (cudart/cuCtxSynchronize))

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
    out)
  ;curr                                                      ;TODO REMOVE
  )

(defn init []
  (if (nil? @*function)
    (reset! *function (cuda/load-func "cuda/conv_bgr_8u.cu" "conv"))
    ; MORE
    ))

(deftype CudaDelta [*prev]
  FrameDelta
  (delta [_ input]
    (init)
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