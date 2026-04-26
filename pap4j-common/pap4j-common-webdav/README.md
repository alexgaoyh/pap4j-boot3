# WebDAV 本地测试环境搭建指南

## 1. 下载 WebDAV 服务器程序

测试代码推荐使用 `hacdias/webdav` 作为轻量级的 WebDAV 服务端。

* 访问 GitHub 的 Release 页面：[hacdias/webdav releases](https://github.com/hacdias/webdav/releases)
* 在下载列表中找到适用于 Windows 的版本（例如 `windows-amd64` 对应的 zip 压缩包）。
* 将压缩包下载并解压到一个你方便管理的目录（例如 `D:\webdav`）。解压后会得到一个类似 `webdav.exe` 的可执行文件。

## 2. 创建配置文件

在你解压 `webdav.exe` 的同级目录下，新建一个名为 `config.yaml` 的文件，根据 `WebDavTest.java` 中设定的端口和账号密码进行如下配置：

```yaml
# 监听本机的 6065 端口
address: 127.0.0.1:6065

# 设置 WebDAV 的根目录（这里用点 . 表示使用当前目录，也可以改成绝对路径如 C:\webdav_data）
scope: .

# 配置测试代码中使用的账户密码
users:
  - username: basic
    password: basic
```

## 3. 启动服务

* 打开 Windows 的命令提示符 (CMD) 或 PowerShell。
* 使用 `cd` 命令切换到你解压 `webdav.exe` 的目录：
  ```cmd
  cd D:\webdav
  ```
* 运行服务并指定你刚写好的配置文件：
  ```cmd
  webdav.exe -c config.yaml
  ```

如果启动成功，控制台通常会输出类似 `Listening on 127.0.0.1:6065` 的提示。此时你的本地 WebDAV 服务就已经搭建完毕了。
