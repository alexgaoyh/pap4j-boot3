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
# ==========================================
# Windows 极致精简打包步骤 (PowerShell/CMD 适用)
# ==========================================

# 步骤 1：清理并编译出干净的主 Jar 包
mvn clean package -DskipTests=true

# 步骤 2：创建独立发布目录，并导出所有运行时依赖 JAR 包 (避免 NoClassDefFoundError 或 Missing JavaFX application class 错误)
mkdir target/dist
copy target/pap4j-boot3-example-javafx-*.jar target/dist/
# 使用 Maven 将所有运行时依赖包拷贝到 target/dist 目录下，这样 jpackage 才能将它们一同打包并自动配置 Classpath
mvn dependency:copy-dependencies -DoutputDirectory=target/dist -DincludeScope=runtime "-Dfile.encoding=UTF-8"

# 步骤 3：使用 jdeps 静态分析最小 JDK 依赖模块 (必须使用带 JavaFX 的 zulu JDK 中的 jdeps 运行)
# 请根据实际安装的 JDK 路径替换 jmods 路径与 bin/jdeps 路径
D:\.jdks\zulu17.62.17-ca-fx-jdk17.0.17-win_x64\bin\jdeps.exe --module-path "D:\.jdks\zulu17.62.17-ca-fx-jdk17.0.17-win_x64\jmods" --add-modules javafx.controls,javafx.fxml,javafx.swing --print-module-deps --ignore-missing-deps --multi-release 17 target/dist/pap4j-boot3-example-javafx-*.jar
# 【使用说明】：上述命令会检测并输出当前 Jar 包依赖的 JDK 平台模块列表（例如：java.base,java.scripting,java.sql,javafx.graphics,jdk.unsupported.desktop）。
# 我们需要将这个输出结果，同我们显式要求的 JavaFX 核心模块（javafx.controls,javafx.fxml,javafx.swing）合并（以逗号分隔，并去重），作为步骤 4 中 --add-modules 的参数。

# 步骤 4：使用 jlink 组装极致压缩的自定义 JRE (必须使用 zulu JDK 中的 jlink)
# 下方命令已填入合并后的最优模块列表：java.base,java.scripting,java.sql,javafx.graphics,jdk.unsupported.desktop,javafx.controls,javafx.fxml,javafx.swing
# (注：jlink 会自动解析模块之间的间接/传递依赖，例如 java.desktop 和 java.xml 等，无需在列表中手动冗余列出)
D:\.jdks\zulu17.62.17-ca-fx-jdk17.0.17-win_x64\bin\jlink.exe --module-path "D:\.jdks\zulu17.62.17-ca-fx-jdk17.0.17-win_x64\jmods" --add-modules java.base,java.scripting,java.sql,javafx.graphics,jdk.unsupported.desktop,javafx.controls,javafx.fxml,javafx.swing --strip-debug --no-header-files --no-man-pages --compress=2 --output target/custom-runtime

# 步骤 5：使用 jpackage 包装为独立的 EXE (必须使用 zulu JDK 中的 jpackage)
# --input 指向刚才建好的 target/dist (里面只有单个 jar)，--runtime-image 指向极致精简的 custom-runtime，同时显式指定主类入口 --main-class
D:\.jdks\zulu17.62.17-ca-fx-jdk17.0.17-win_x64\bin\jpackage.exe --type exe --input target/dist --name example-javafx-0.0.1 --main-jar pap4j-boot3-example-javafx-0.0.3.jar --main-class cn.net.pap.example.javafx.MainApp --runtime-image target/custom-runtime --java-options "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED" --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.prism=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.util=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.geom=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/com.sun.javafx.text=ALL-UNNAMED" --java-options "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED" --java-options "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED" --java-options "--add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED" --java-options "--add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED" --java-options "-Dfile.encoding=UTF-8" --win-dir-chooser --win-menu --win-shortcut --win-console

# -----------------
# 附加说明：
# 1. 在 Windows 下构建完整的 EXE 安装包需要配置 WiX 3 环境：
#    从 GitHub 下载：https://github.com/wixtoolset/wix3/releases 并解压，将其文件夹配置进系统环境变量 PATH 中。
# 2. 如果打包测试时需要看到报错，可加上 --win-console 参数。如需隐藏命令行黑框，移除 --win-console 即可。
# -----------------

```