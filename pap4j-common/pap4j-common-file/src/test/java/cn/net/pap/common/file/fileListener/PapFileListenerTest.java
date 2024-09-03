package cn.net.pap.common.file.fileListener;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.junit.jupiter.api.Test;

import java.io.File;

public class PapFileListenerTest {

    // @Test
    public void test() throws Exception {
        // 目标文件路径
        String filePathToWatch = "C:\\Users\\86181\\Desktop\\file.txt";
        File file = new File(filePathToWatch);

        FileAlterationObserver observer = new FileAlterationObserver(file.getParentFile());

        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileChange(File changedFile) {
                if (changedFile.equals(file)) {
                    System.out.println("File " + changedFile.getName() + " has been modified. lastModified in : " + changedFile.lastModified());
                }
            }
        });

        FileAlterationMonitor monitor = new FileAlterationMonitor(500, observer);

        monitor.start();

        System.in.read();
    }

}
