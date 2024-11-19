# pap4j-boot3

#### 介绍
&ensp;&ensp; 基于spring-boot3的架子。

#### 详细介绍
https://pap-docs.pap.net.cn/

#### 项目描述

&ensp;&ensp; 当前项目是使用spring-boot3 jdk17 为基础，定义的脚手架。

&ensp;&ensp; 包括状态机statemachine、规则引擎drools/liteflow、国产图数据库gStore、向量数据库milvus、图数据库neo4j 等自定义starter。

&ensp;&ensp; 包括 位图bitmap、doc/excel/pdf 等操作工具类。

&ensp;&ensp; 包括 assembly proguard 插件的使用实例。

&ensp;&ensp; 下一步期望在基础组件的基础上，完成一系列业务系统的开发。

#### 项目结构

```
├─pap4j-boot3                                       根项目节点
   ├─pap4j-boot3-starters                               Starter机制,包括一些自定义start和使用示例
     ├─pap4j-boot3-starter-statemachine                     statemachine 状态机使用示例
     ├─pap4j-boot3-starter-cache                            Redis Cache 自定义注解
     ├─pap4j-boot3-starter-drools                           Drools 业务规则引擎
     ├─pap4j-boot3-starter-gStore                           gStore 图数据库
     ├─pap4j-boot3-starter-liteflow                         liteflow 组件式规则引擎
     ├─pap4j-boot3-starter-logback                          logback 扩展组件
     ├─pap4j-boot3-starter-milvus                           milvus 向量数据库
     ├─pap4j-boot3-starter-neo4j                            neo4j 图数据库
     ├─pap4j-boot3-starter-quartz                           quartz 作业调度框架扩展
     ├─pap4j-boot3-starter-task                             task 自定义异步任务组件
   ├─pap4j-common                                       工具类
     ├─pap4j-common-bitmap                                  位图
     ├─pap4j-common-boofcv                                  boofcv 计算机视觉库
     ├─pap4j-common-datastructure                           数据结构: 树形、矩形、设计模式、聚类算法……
     ├─pap4j-common-deeplearning4j                          DL4J
     ├─pap4j-common-docx                                    Docx 组件
     ├─pap4j-common-excel                                   Excel 组件
     ├─pap4j-common-file                                    File 文件操作组件
     ├─pap4j-common-jena                                    Jena RDF三元组操作组件
     ├─pap4j-common-jsonorm                                 JSON ORM 自定义基础业务工具集合
     ├─pap4j-common-jsqlparser                              Java SQL 解析操作组件 
     ├─pap4j-common-kingbase                                人大金仓数据库
     ├─pap4j-common-opencv                                  opencv 计算机视觉库
     ├─pap4j-common-pdf                                     PDF 组件
     ├─pap4j-common-spider                                  爬虫 组件
     ├─pap4j-common-webdav                                  WebDAV 协议
```

#### 安装指南

&ensp;&ensp;  mvn clean install 打包后引入依赖即可。

#### 使用指南

&ensp;&ensp;  项目已上传至中央仓库，[请访问](https://central.sonatype.com/namespace/cn.net.pap)

#### Special thanks to JetBrains for supporting open-source projects

[![](https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg)](https://gitee.com/alexgaoyh/pap4j-boot3)