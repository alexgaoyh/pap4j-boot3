## 🚀 极简打包步骤 (两步完成)

### 步骤 1：Maven 编译打包
在项目根目录下执行编译，生成自包含全部依赖的 Executable Jar 包：
```powershell
mvn clean package -pl pap4j-common/pap4j-common-exe -DskipTests=true "-Dfile.encoding=UTF-8"
```

---

### 步骤 2：直接使用 jpackage 生成 EXE
进入模块目录，将打包好的 Fat Jar 转换为独立的 exe 程序：

```powershell
cd pap4j-common/pap4j-common-exe

# 1. 创建打包临时目录并拷贝可执行 jar
New-Item -ItemType Directory -Force -Path target/dist
Copy-Item target/pap4j-common-exe-*-exec.jar target/dist/app.jar

# 2. 一键打包为独立 exe（自带运行环境，无需安装 JRE）
jpackage --type app-image `
         --dest target/exe `
         --input target/dist `
         --name pap4j-common-exe `
         --main-jar app.jar
```

---