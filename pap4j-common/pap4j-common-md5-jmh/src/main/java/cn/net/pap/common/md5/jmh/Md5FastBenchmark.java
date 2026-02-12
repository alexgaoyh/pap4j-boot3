package cn.net.pap.common.md5.jmh;

import cn.net.pap.common.md5.jmh.util.Md5Fast;
import cn.net.pap.common.md5.jmh.util.Md5Normal;
import net.coobird.thumbnailator.Thumbnails;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class Md5FastBenchmark {

    private String input;

    private File file;

    @Setup
    public void setup() {
        input = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        file = new File("d:\\knowledge\\input.jpg");
    }

    @Benchmark
    public String md5_fast() {
        return Md5Fast.md5(input);
    }

    @Benchmark
    public String md5_normal() throws Exception {
        return Md5Normal.md5(input);
    }

    @Benchmark
    public BufferedImage thumb_thumbnailator() throws Exception {
        BufferedImage image = Thumbnails.of(file).size(141, Integer.MAX_VALUE)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(0.7).asBufferedImage();
        return image;
    }

    @Benchmark
    public BufferedImage thumb_lowMemory() throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int actualWidth = reader.getWidth(0);
                int sampling = Math.max(actualWidth / 141, 1);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sampling, sampling, 0, 0);

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

}
