package jstereox;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacv.OpenCVFrameConverter.*;


public class CvConverters {

    public static org.bytedeco.opencv.opencv_core.Mat
    coreMatToCvMat(org.opencv.core.Mat coreMat) {
        try (ToOrgOpenCvCoreMat converter2 = new ToOrgOpenCvCoreMat();
             ToMat converter1 = new ToMat()) {
            return converter1.convert(converter2.convert(coreMat));
        }
    }

    public static org.opencv.core.Mat
    cvMatToCoreMat(org.bytedeco.opencv.opencv_core.Mat cvMat) {
        try (ToOrgOpenCvCoreMat converter2 = new ToOrgOpenCvCoreMat();
             ToMat converter1 = new ToMat()) {
            return converter2.convert(converter1.convert(cvMat));
        }
    }
}
