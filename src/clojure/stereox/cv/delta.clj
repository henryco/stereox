(ns stereox.cv.delta
  (:import (org.bytedeco.javacv GLCanvasFrame)
           (org.bytedeco.opencv.opencv_core GpuMat)
           (org.bytedeco.opencv.global opencv_cudaarithm opencv_imgproc opencv_cudaimgproc)
           (org.opencv.core CvType))
  (:gen-class))
(defprotocol DeltaCalculator
  "Delta calculator"
  (delta [_ input] "Calculate delta"))

(deftype CudaDelta [*prev]
  DeltaCalculator
  (delta [_ input]
    (if (nil? @*prev)
      (do (reset! *prev input) input)
      (let [out (GpuMat.)]
        ; TODO
        ;(opencv_cudaarithm/absdiff
        ;  ^GpuMat (deref *prev)
        ;  ^GpuMat input
        ;  ^GpuMat out)

        (reset! *prev input)
        out
        input
        ))))

(defn create-cuda-delta
  {:static true
   :tag    DeltaCalculator}
  []
  (->CudaDelta (atom nil)))