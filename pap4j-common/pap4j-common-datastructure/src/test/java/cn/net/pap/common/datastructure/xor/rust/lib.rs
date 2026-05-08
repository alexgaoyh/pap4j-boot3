use wasm_bindgen::prelude::*;

// 完整复制的 32 字节高熵密钥
const HARDCODED_KEY: [u8; 32] = [
    0x5D, 0xE2, 0x7F, 0x1A, 0xC9, 0x04, 0x33, 0x8B, 
    0x61, 0xF5, 0x2C, 0x9D, 0x70, 0x4A, 0xEE, 0x1B, 
    0x88, 0x34, 0x09, 0x56, 0xBD, 0x9F, 0xC1, 0x72, 
    0x0B, 0x3D, 0x44, 0x27, 0x81, 0xAF, 0x59, 0x6E
];

/// 内部处理函数：核心异或逻辑
fn process_internal(data: &mut [u8]) {
    let key_len = HARDCODED_KEY.len();
    for i in 0..data.len() {
        data[i] ^= HARDCODED_KEY[i % key_len];
    }
}

/**
 * 对应 Java 的 process(byte[] data) 方法
 * 接收前端的 Uint8Array，返回一个新的经过异或处理的 Uint8Array
 */
#[wasm_bindgen]
pub fn process(data: &[u8]) -> Vec<u8> {
    let mut copy = data.to_vec(); // 克隆数组
    process_internal(&mut copy);  // 处理数据
    copy // 自动转换为前端的 Uint8Array
}

/**
 * 对应 Java 的 processInPlace(byte[] data) 方法
 * 直接修改前端传入的 Uint8Array，减少内存分配压力
 */
#[wasm_bindgen]
pub fn process_in_place(data: &mut [u8]) {
    process_internal(data);
}