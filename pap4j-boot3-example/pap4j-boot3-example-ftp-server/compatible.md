# [杂谈]兼容性调整

## OPTS 支持

&ensp;&ensp; 在不同的版本下，ftpClient.sendCommand("OPTS UTF8", "ON") 命令的返回值是不一样的，有些要求先登录，某些不要求登录；

&ensp;&ensp; 对于 org.apache.ftpserver-ftpserver-core-1.2.1 这个版本来说，它要求先登录，否则会返回 530；

```html
2025-11-06T16:00:10.202+08:00  INFO 29268 --- [pool-4-thread-1] o.a.f.listener.nio.FtpLoggingFilter      : RECEIVED: MODE S
2025-11-06T16:00:10.204+08:00  INFO 29268 --- [pool-4-thread-2] o.a.f.listener.nio.FtpLoggingFilter      : SENT: 530 Access denied.

2025-11-06T16:00:10.206+08:00  INFO 29268 --- [pool-4-thread-2] o.a.f.listener.nio.FtpLoggingFilter      : RECEIVED: OPTS UTF8 ON
2025-11-06T16:00:10.206+08:00  INFO 29268 --- [pool-4-thread-1] o.a.f.listener.nio.FtpLoggingFilter      : SENT: 530 Access denied.
```

&ensp;&ensp; 对于 FileZilla Server version 0.9.60 beta 这个版本来说，它不要求先登录，会返回 202；

```html
(000002)2025/11/6 15:58:48 - (not logged in) (127.0.0.1)> Connected on port 21, sending welcome message...
(000002)2025/11/6 15:58:48 - (not logged in) (127.0.0.1)> 220-FileZilla Server 0.9.60 beta
(000002)2025/11/6 15:58:48 - (not logged in) (127.0.0.1)> 220-written by Tim Kosse (tim.kosse@filezilla-project.org)
(000002)2025/11/6 15:58:48 - (not logged in) (127.0.0.1)> 220 Please visit https://filezilla-project.org/
(000002)2025/11/6 15:58:48 - (not logged in) (127.0.0.1)> MODE S
(000002)2025/11/6 15:58:48 - (not logged in) (127.0.0.1)> 530 Please log in with USER and PASS first.
(000002)2025/11/6 15:58:57 - (not logged in) (127.0.0.1)> OPTS UTF8 ON
(000002)2025/11/6 15:58:57 - (not logged in) (127.0.0.1)> 202 UTF8 mode is always enabled. No need to send this command.
(000002)2025/11/6 15:59:00 - (not logged in) (127.0.0.1)> OPTS UTF8 ON
(000002)2025/11/6 15:59:00 - (not logged in) (127.0.0.1)> 202 UTF8 mode is always enabled. No need to send this command.
(000002)2025/11/6 15:59:04 - (not logged in) (127.0.0.1)> QUIT
(000002)2025/11/6 15:59:04 - (not logged in) (127.0.0.1)> 221 Goodbye
```
&ensp;&ensp; 所以为了兼容已有的支持，可以做如下的调整；

```html
// git clone https://github.com/apache/mina-ftpserver.git
// 然后切换到 ftpserver-parent-1.2.1 这个tag，对应的就是当前项目的 maven 的依赖
// 主要下载下来的代码，存在编码问题，有可能需要调整文件的换行符，可以在根目录下执行如下命令。
Get-ChildItem -Recurse -Include "*.java" | ForEach-Object {
    $file = $_.FullName
    $content = Get-Content -Raw $file
            $newContent = $content -replace '\r\n', "`n"
    if ($content -ne $newContent) {
        Set-Content -Path $file -Value $newContent -NoNewline
        Write-Host "已转换: $file"
    }
}

// 修改代码
// org.apache.ftpserver.impl.DefaultFtpHandler.java 的 NON_AUTHENTICATED_COMMANDS 数组，增加 OPTS 的支持；
// private static final String[] NON_AUTHENTICATED_COMMANDS = new String[] { "USER", "PASS", "AUTH", "QUIT", "PROT", "PBSZ", "OPTS" };

// 打包： clean install -Dmaven.checkstyle.skip=true -Dcheckstyle.skip=true -DskipTests -Dmaven.test.skip=true
```

