### 第一步：安装系统基础依赖
首先更新系统软件包，并安装 C/C++ 编译基础环境以及 Node.js（用于后续前端测试）。
```bash
sudo apt update
sudo apt upgrade -y
sudo apt install build-essential curl nodejs npm -y
```

### 第二步：安装 Rust 环境（启用中科大镜像）
为了防止 `curl https://sh.rustup.rs` 卡死，在安装前必须先声明国内镜像源环境变量：
```bash
# 设置 Rust 安装脚本的下载镜像
export RUSTUP_DIST_SERVER=[https://mirrors.ustc.edu.cn/rust-static](https://mirrors.ustc.edu.cn/rust-static)
export RUSTUP_UPDATE_ROOT=[https://mirrors.ustc.edu.cn/rust-static/rustup](https://mirrors.ustc.edu.cn/rust-static/rustup)

# 执行安装脚本（提示时按回车默认安装即可）
curl --proto '=https' --tlsv1.2 -sSf [https://sh.rustup.rs](https://sh.rustup.rs) | sh

# 让 Rust 环境变量在当前终端立即生效
source $HOME/.cargo/env
```

### 第三步：配置 Cargo 字节跳动加速源（防卡死核心）
这是最关键的一步。为了防止后续下载第三方依赖库时卡死，使用字节跳动的 `rsproxy` 镜像替换默认的 crates.io 源：
```bash
# 创建配置目录
mkdir -p ~/.cargo

# 一键写入国内镜像配置
cat << 'EOF' > ~/.cargo/config.toml
[source.crates-io]
replace-with = 'rsproxy-sparse'

[source.rsproxy-sparse]
registry = "sparse+[https://rsproxy.cn/index/](https://rsproxy.cn/index/)"

[net]
git-fetch-with-cli = true
EOF
```

### 第四步：安装 Wasm 底层编译工具链
既然 `wasm-pack` 容易在后台偷偷请求 GitHub 导致卡死，我们直接手动安装它底层的核心工具（这一步因为配置了字节源，会非常快）：
```bash
# 1. 为 Rust 添加 WebAssembly 编译目标架构
rustup target add wasm32-unknown-unknown

# 2. 安装 wasm 和 js 之间的胶水代码生成器
cargo install wasm-bindgen-cli
```
*(注：这里我们舍弃了容易卡死的 `wasm-pack`，直接准备使用底层的 `wasm-bindgen-cli` 进行硬核编译)*

### 第五步：创建并编写 Rust 项目
```bash
# 1. 创建库项目
cargo new --lib xor-crypto-wasm
cd xor-crypto-wasm

# 2. 编辑项目配置文件 (这里使用 vim，你可以换成 nano)
vim Cargo.toml
```
将 `Cargo.toml` 的内容修改为：
```toml
[package]
name = "xor-crypto-wasm"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
wasm-bindgen = "0.2"
```

接着，编辑核心代码逻辑：
```bash
vim src/lib.rs
```
*(在此文件中填入你写好的异或加解密 Rust 代码，保存并退出)*

### 第六步：终极手工编译（秒出包）
不使用 `wasm-pack build --target web`，直接使用底层命令分两步完成编译，进度全透明，告别卡死：

```bash
# 1. 将 Rust 源码编译为原生 WebAssembly 二进制文件
cargo build --target wasm32-unknown-unknown --release

# 2. 使用 bindgen 生成供前端调用的 js 胶水代码和最终的 wasm 包
wasm-bindgen target/wasm32-unknown-unknown/release/xor_crypto_wasm.wasm --out-dir ./pkg --target web
```