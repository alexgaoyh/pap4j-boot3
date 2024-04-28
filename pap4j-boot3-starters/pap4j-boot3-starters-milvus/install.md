```shell
## milvus
wget https://github.com/milvus-io/milvus/releases/download/v2.3.10/milvus_2.3.10-1_amd64.deb
sudo apt-get update
sudo dpkg -i milvus_2.3.10-1_amd64.deb
sudo apt-get -f install
```

```shell
## The GUI for Milvus https://github.com/zilliztech/attu?tab=readme-ov-file
sudo docker run -p 8000:3000 -e MILVUS_URL=192.168.1.115:19530 zilliz/attu:v2.3.10
```

```shell
sudo systemctl restart milvus
sudo systemctl status milvus
```
