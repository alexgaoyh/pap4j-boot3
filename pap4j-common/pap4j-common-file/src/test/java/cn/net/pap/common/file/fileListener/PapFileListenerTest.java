package cn.net.pap.common.file.fileListener;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.*;

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

    // @Test
    public void watchService1() throws Exception {
        WatchService watchService = FileSystems.getDefault().newWatchService();

        java.nio.file.Path path = java.nio.file.Paths.get("C:\\Users\\86181\\Desktop\\");

        path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        WatchKey key;
        while ((key = watchService.take()) != null) {
            // 某些编辑器（如Notepad++、VS Code）在保存文件时可能会先写入临时文件，再替换原文件，导致两次事件。
            for (WatchEvent<?> event : key.pollEvents()) {
                Path file = path.resolve((Path) event.context());
                if(Files.exists(file)) {
                    long currentModifiedTime = Files.getLastModifiedTime(file).toMillis();

                    System.out.println( "Event kind:" + event.kind() + ". File affected: " + event.context() + ". ModifiedTime : " + currentModifiedTime + ". FilePath : " + file.toString() + ".");
                } else {
                    System.out.println( "Event kind:" + event.kind() + ". File affected: " + event.context() + ". ModifiedTime : " + System.currentTimeMillis() + ". FilePath : " + file.toString() + ".");
                }
            }
            key.reset();
        }
    }

}
