package cn.net.pap.example.javafx.dto;

import cn.net.pap.example.javafx.comparator.OSAlignedNaturalComparator;
import cn.net.pap.example.javafx.util.ImageUtil;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentPath)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                } else if (ImageUtil.isImageFile(p.getFileName().toString().toLowerCase())) {
                    files.add(p);
                }
            }
        } catch (IOException e) {
            // 可记录日志
            return;
        }
        Comparator<Path> nameComparator = Comparator.comparing( (Path p) -> p.getFileName().toString(), new OSAlignedNaturalComparator());

        dirs.sort(nameComparator);
        files.sort(nameComparator);

        // 文件夹在前，文件在后
        for (Path p : dirs) {
            getChildren().add(new FileTreeItem(p));
        }
        for (Path p : files) {
            getChildren().add(new FileTreeItem(p));
        }
    }


    @Override
    public boolean isLeaf() {
        Path path = getValue();
        return path == null || !Files.isDirectory(path);
    }

}
