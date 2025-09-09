
```shell

# Ubuntu 锁定 21 端口有哪些链接，可以明确一个链接  本地IP:本地端口 <-> 对端IP:对端端口
netstat -antop | grep :21

# Ubuntu 根据上述命令的结果，维护一个 tcpdump 的命令，精确抓取流量
sudo tcpdump -i any src 192.168.1.115 and dst 192.168.1.66 and src port 21 and dst port 8230 -nn -vv

```