(ns stereox.cv.delta
  (:require [stereox.cuda.cuda-kernel :as kernel]
            [stereox.cuda.cuda :as cuda])
  (:import (org.bytedeco.cuda.cudart CUctx_st)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacv GLCanvasFrame)
           (org.bytedeco.opencv.opencv_core GpuMat)
           (org.bytedeco.opencv.global opencv_cudaarithm opencv_imgproc opencv_cudaimgproc)
           (org.opencv.core CvType))
  (:gen-class))

(def ^:private kernel (kernel/file* "cuda/conv_bgr_8u.cu"))

(defprotocol DeltaCalculator
  "Delta calculator"
  (delta [_ input] "Calculate delta"))

(defn render [^GpuMat curr ^GpuMat last]

  (let [out (GpuMat.)]
    ;(opencv_cudaarithm/absdiff
    ;  ^GpuMat last
    ;  ^GpuMat curr
    ;  ^GpuMat out)

    (cuda/with-context
      (fn [c d]

        ;(println "-")
        ;TODO
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
      #(println %))
    out)
  curr                                                      ;TODO REMOVE
  )

(deftype CudaDelta [*prev]
  DeltaCalculator
  (delta [_ input]
    (if (nil? @*prev)
      (do (reset! *prev input) input)
      (let [out (render input @*prev)]
        (reset! *prev input)
        out))))

(defn create-cuda-delta
  {:static true
   :tag    DeltaCalculator}
  []
  (cuda/devices*)
  (->CudaDelta (atom nil)))