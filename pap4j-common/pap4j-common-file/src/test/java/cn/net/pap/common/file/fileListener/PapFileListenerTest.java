package cn.net.pap.common.file.fileListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class PapFileListenerTest {

    private static final Logger log = LoggerFactory.getLogger(PapFileListenerTest.class);

    @Test
    public void test() throws Exception {
        // 使用系统的临时文件夹创建测试目录
        Path tempDir = Files.createTempDirectory("watch_test_commons_");
        File file = new File(tempDir.toFile(), "file.txt");
        file.createNewFile(); // 确保初始文件存在

        FileAlterationObserver observer = new FileAlterationObserver(file.getParentFile());

        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileChange(File changedFile) {
                // 保持原状的输出逻辑
                if (changedFile.equals(file)) {
                    log.info("{}", "File " + changedFile.getName() + " has been modified. lastModified in : " + changedFile.lastModified());
                }
            }
        });

        // 保持原本的 500ms 轮询间隔
        FileAlterationMonitor monitor = new FileAlterationMonitor(500, observer);

        monitor.start();

        // 自动化测试：主动修改文件以触发事件
        Files.writeString(file.toPath(), "test update", StandardOpenOption.APPEND);

        // 替代原有的 System.in.read()，让主线程等待一下，确保 monitor 能轮询到变化
        Thread.sleep(1500);

        // 清理环境
        monitor.stop();
        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void watchService1() throws Exception {
        // 使用系统的临时文件夹创建测试目录
        Path path = Files.createTempDirectory("watch_test_nio_");
        Path fileToModify = path.resolve("target.txt");
        Files.writeString(fileToModify, "init");

        WatchService watchService = FileSystems.getDefault().newWatchService();

        path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        // 自动化测试：主动修改文件以触发事件
        Files.writeString(fileToModify, "update", StandardOpenOption.APPEND);

        // 替代原有的无限 while 循环，改为带超时的 poll，获取一次事件后退出
        WatchKey key = watchService.poll(3, TimeUnit.SECONDS);
        if (key != null) {
            // 保持原状的内部遍历和输出逻辑
            for (WatchEvent<?> event : key.pollEvents()) {
                Path file = path.resolve((Path) event.context());
                if(Files.exists(file)) {
                    long currentModifiedTime = Files.getLastModifiedTime(file).toMillis();

                    log.info("{}",  "Event kind:" + event.kind() + ". File affected: " + event.context() + ". ModifiedTime : " + currentModifiedTime + ". FilePath : " + file.toString() + ".");
                } else {
                    log.info("{}",  "Event kind:" + event.kind() + ". File affected: " + event.context() + ". ModifiedTime : " + System.currentTimeMillis() + ". FilePath : " + file.toString() + ".");
                }
            }
            key.reset();
        }

        // 清理环境
        watchService.close();
        Files.deleteIfExists(fileToModify);
        Files.deleteIfExists(path);
    }

}
