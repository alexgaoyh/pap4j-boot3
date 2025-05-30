# [SSL] 不同操作系统下的单元测试验证

## 方法

&ensp;&ensp;假设在 Windows 进行开发之后，需要兼容 Linux ，想通过单元测试方法来进行验证，可以将源码迁移到 Linux 环境下，在项目根路径下执行 mvn test 来进行验证.


```shell
cmake -DBUILD_SHARED_LIBS=OFF -D CMAKE_BUILD_TYPE=Release       -D CMAKE_INSTALL_PREFIX=/usr/local       -D OPENCV_GENERATE_PKGCONFIG=ON       -D OPENCV_EXTRA_MODULES_PATH=~/opencv_build/opencv_contrib/modules       -D BUILD_EXAMPLES=OFF       -D BUILD_TESTS=OFF       -D BUILD_PERF_TESTS=OFF       -D BUILD_DOCS=OFF       -D BUILD_opencv_java=ON       -D JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 -DWITH_FFMPEG=OFF ..

sudo make -j1

sudo make install


sudo apt install libdc1394-25 libdc1394-utils

```