package cn.net.pap.common.md5.jmh;

import cn.net.pap.common.md5.jmh.util.Md5Fast;
import cn.net.pap.common.md5.jmh.util.Md5Normal;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class Md5FastBenchmark {

    private String input;

    @Setup
    public void setup() {
        input = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    }

    @Benchmark
    public String md5_fast() {
        return Md5Fast.md5(input);
    }

    @Benchmark
    public String md5_normal() throws Exception {
        return Md5Normal.md5(input);
    }

}
