## 多环境

方案一： 在不同环境不同操作系统下，添加 CI/CD ，做不同的打包和进一步处理，比如当前模块就需要在 win 和 linux 分别做打包。

```shell
# ubuntu 区分发行版本.
/usr/lib/jvm/java-17-openjdk-amd64/bin/jpackage --input . --name example-javafx-0.0.1 --main-jar pap4j-boot3-example-javafx-0.0.1.jar --module-path "/usr/lib/jvm/java-17-openjdk-amd64/jmods" --add-modules java.base,java.desktop,java.rmi,java.scripting,java.sql,java.naming,java.xml,jdk.unsupported --java-options "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED" --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.prism=ALL-UNNAMED" --java-options "-Dprism.fontdir=/usr/share/fonts/truetype:/usr/share/fonts/opentype" --java-options "-Dfile.encoding=UTF-8"

# --input 的路径，可以调整一下，里面尽可能干净，不要导入其他的内容， 最后可以尝试打出来一个 AppImage 包.
/usr/lib/jvm/java-17-openjdk-amd64/bin/jpackage --input build --name example-javafx-0.0.1 --main-jar pap4j-boot3-example-javafx-0.0.1.jar --module-path "/usr/lib/jvm/java-17-openjdk-amd64/jmods" --add-modules java.base,java.desktop,java.rmi,java.scripting,java.sql,java.naming,java.xml,jdk.unsupported --java-options "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED" --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.prism=ALL-UNNAMED" --java-options "-Dprism.fontdir=/usr/share/fonts/truetype:/usr/share/fonts/opentype" --java-options "-Dfile.encoding=UTF-8" --type app-image

# 本地测试的时候，没有桌面环境，所以这里做了一系列处理，安装中文环境
sudo apt install ubuntu-desktop language-pack-zh-hans xrdp -y

sudo apt install fonts-noto-cjk fonts-wqy-microhei fonts-wqy-zenhei

sudo vim /etc/default/locale
    LANG="zh_CN.UTF-8"
    LANGUAGE="zh_CN:zh:en_US:en"
    LC_NUMERIC="zh_CN.UTF-8"
    LC_TIME="zh_CN.UTF-8"
    LC_MONETARY="zh_CN.UTF-8"
    LC_PAPER="zh_CN.UTF-8"
    LC_IDENTIFICATION="zh_CN.UTF-8"
    LC_NAME="zh_CN.UTF-8"
    LC_ADDRESS="zh_CN.UTF-8"
    LC_TELEPHONE="zh_CN.UTF-8"
    LC_MEASUREMENT="zh_CN.UTF-8"

```

```shell
# windows
jpackage --input . --name example-javafx-0.0.1 --main-jar pap4j-boot3-example-javafx-0.0.1.jar --module-path "D:\.jdks\jdk-17.0.16+8\bin\jmods" --add-modules java.base,java.desktop,java.rmi,java.scripting,java.sql,java.naming,java.xml,jdk.unsupported --java-options "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED" --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.prism=ALL-UNNAMED" --java-options "-Dfile.encoding=UTF-8" --win-dir-chooser --win-menu --win-shortcut

# windows 依赖调整
jpackage --input . --name example-javafx-0.0.1 --main-jar pap4j-boot3-example-javafx-0.0.1.jar --runtime-image "D:\.jdks\jdk-17.0.16+8" --java-options "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED" --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.prism=ALL-UNNAMED" --java-options "-Dfile.encoding=UTF-8" --win-dir-chooser --win-menu --win-shortcut

# 在 Windows 下配置 WiX 3 环境变量 从 GitHub 下载：https://github.com/wixtoolset/wix3/releases 下载 wix3xx-binaries.zip 并解压，之后配置环境变量 PATH 部分，增加此文件夹
```