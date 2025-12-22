package cn.net.pap.example.javafx.dto;

import javafx.scene.control.TreeItem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 目录树
 */
public class FileTreeItem extends TreeItem<Path> {

    private boolean childrenLoaded = false;

    public FileTreeItem(Path path) {
        super(path);

        // 目录：放一个占位节点，用于显示展开箭头
        if (Files.isDirectory(path)) {
            getChildren().add(new TreeItem<>());
        }

        // 监听展开事件（关键）
        expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded) {
                loadChildrenIfNecessary();
            }
        });
    }

    private void loadChildrenIfNecessary() {
        // 后面这个 false，每次都拉一下新的
        if (childrenLoaded && false) {
            return;
        }
        childrenLoaded = true;

        getChildren().clear(); // 清除占位节点

        Path parentPath = getValue();
        if (parentPath == null || !Files.isDirectory(parentPath)) {
            return;
        }

        File[] files = parentPath.toFile().listFiles();
        if (files == null) return;

        // 文件夹排前，文件排后，名称忽略大小写排序
        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        for (File file : files) {
            if (file.isDirectory() || isImageFile(file.getName())) {
                getChildren().add(new FileTreeItem(file.toPath()));
            }
        }
    }


    @Override
    public boolean isLeaf() {
        Path path = getValue();
        return path == null || !Files.isDirectory(path);
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".bmp")
                || lower.endsWith(".tif") || lower.endsWith(".tiff")
                || lower.endsWith(".webp");
    }
}
