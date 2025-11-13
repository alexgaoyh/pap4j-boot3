# [ImageMagick]静态编译

## 背景

&ensp;&ensp;最近遇到一些图像处理的需求，计划使用ImageMagick库，但是在业务场景中可能存在国产化CPU的要求，同时还有内网环境，所以期望对ImageMagick进行静态编译，从而达到便携性的效果。

&ensp;&ensp;经过验证，可以通过文件夹拷贝的方式，便携性的在 Intel 、海光C86 这两种不同的 CPU 上进行使用，不需要有额外依赖和额外的安装。

## 实现效果示例

<div style="display: flex; justify-content: space-between;">
    <img src="https://s2.loli.net/2025/11/13/qkpoXiuI7WlOnmB.jpg" alt="Hygon-C86-ImageMagick-static" style="width: 90%;">
</div>

&ensp;&ensp;图像备份: [访问](https://gitee.com/alexgaoyh/pap-docs/raw/master/md/other/imagemagick/Hygon-C86-ImageMagick-static.jpg)

## 参考
1. http://pap-docs.pap.net.cn/
2. https://gitee.com/alexgaoyh
