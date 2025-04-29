package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;

public class ProcessBuilderTest {

    // @Test
    public void commandTest1() {
        ProcessBuilder processBuilder = new ProcessBuilder();

        String[] command1 = {"echo", "Hello"};
        String[] command2 = {"echo", "World"};
        String[] command3 = {"echo1", "World"};
        String[] command4 = {"magick", "jpg.jp2", "-density", "300", "-units", " PixelsPerInch", "jpg.jpg"};

        for (String[] command : new String[][]{command1, command2, command3, command4}) {
            processBuilder.command(command);
            long start = System.currentTimeMillis();
            try {
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                System.out.println((exitCode == 0) + " : " + (System.currentTimeMillis() - start));
            } catch (Exception e) {
                System.out.println(false + " : " + (System.currentTimeMillis() - start));
            }
        }

    }

}
