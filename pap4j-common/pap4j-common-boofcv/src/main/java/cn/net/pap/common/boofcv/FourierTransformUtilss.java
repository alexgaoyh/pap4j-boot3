package cn.net.pap.common.boofcv;

import boofcv.abst.transform.fft.DiscreteFourierTransform;
import boofcv.alg.misc.PixelMath;
import boofcv.alg.transform.fft.DiscreteFourierTransformOps;
import boofcv.gui.image.VisualizeImageData;
import boofcv.io.UtilIO;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.InterleavedF32;

import java.awt.image.BufferedImage;

/**
 * 傅里叶变换
 */
public class FourierTransformUtilss {

    /**
     * 仿照 https://github.com/lessthanoptimal/BoofCV/blob/v1.1.4/examples/src/main/java/boofcv/examples/imageprocessing/ExampleFourierTransform.java
     * @param filePath
     * @return
     */
    public static BufferedImage fourierTransformConvert(String filePath) {
        GrayF32 input = UtilImageIO.loadImage(UtilIO.pathExample(filePath), GrayF32.class);
        InterleavedF32 transform = new InterleavedF32(input.width, input.height, 2);
        DiscreteFourierTransform<GrayF32, InterleavedF32> dft = DiscreteFourierTransformOps.createTransformF32();
        PixelMath.divide(input, 255.0f, input);
        dft.forward(input, transform);
        GrayF32 magnitude = new GrayF32(transform.width, transform.height);
        GrayF32 phase = new GrayF32(transform.width, transform.height);

        transform = transform.clone();

        DiscreteFourierTransformOps.shiftZeroFrequency(transform, true);

        DiscreteFourierTransformOps.magnitude(transform, magnitude);
        DiscreteFourierTransformOps.phase(transform, phase);

        PixelMath.log(magnitude, 1.0f, magnitude);

        BufferedImage visualMag = VisualizeImageData.grayMagnitude(magnitude, null, -1);

        return visualMag;
    }
}
