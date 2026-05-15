```html
server {
    listen 9878;
    server_name 127.0.0.1;

    # 加密接口: POST 127.0.0.1:9878/encrypt
    # 请求体: {"text":"alexgaoyh"}
    location /encrypt {
        content_by_lua_block {
            local mycrypto = require "conf/lua/crypto"
            local cjson = require "cjson.safe"

            ngx.req.read_body()
            local body = ngx.req.get_body_data()
            local json = cjson.decode(body or "{}") or {}

            local text = json.text or "default text"
            local encrypted = mycrypto.encrypt(text)

            ngx.header.content_type = "application/json; charset=utf-8"
            ngx.say(cjson.encode({
                success = true,
                action  = "encrypt",
                input   = text,
                output  = encrypted
            }))
        }
    }

    # 解密接口: POST 127.0.0.1:9878/decrypt
    # 请求体: {"data":"LOteFgO22MXi7b0YhmlG9Q=="}
    location /decrypt {
        content_by_lua_block {
            local mycrypto = require "conf/lua/crypto"
            local cjson = require "cjson.safe"

            ngx.req.read_body()
            local body = ngx.req.get_body_data()
            local json = cjson.decode(body or "{}") or {}

            local data = json.data
            ngx.header.content_type = "application/json; charset=utf-8"

            if not data then
                ngx.say(cjson.encode({
                    success = false,
                    error   = "缺少参数: data(base64)"
                }))
                return
            end

            local decrypted, err = mycrypto.decrypt(data)
            if not decrypted then
                ngx.say(cjson.encode({
                    success = false,
                    action  = "decrypt",
                    input   = data,
                    error   = err
                }))
                return
            end

            ngx.say(cjson.encode({
                success = true,
                action  = "decrypt",
                input   = data,
                output  = decrypted
            }))
        }
    }
}
```


```html
-- 文件: conf/lua/crypto.lua
local aes = require "resty.aes"
local str = require "resty.string"

local _M = {}

-- AES 配置
local key = "1234567890abcdef1234567890abcdef"  -- 32字节
local iv  = "abcdef1357924680"                 -- 16字节

local aes_256_cbc = assert(aes:new(
    key, nil, aes.cipher(256, "cbc"), { iv = iv }
))

-- 加密方法
function _M.encrypt(data)
    local encrypted = aes_256_cbc:encrypt(data)

    -- 2. 标准 Base64 编码
    local b64 = ngx.encode_base64(encrypted)
    
    -- 3. 手动转换为 URL-safe:
    -- 将 "+" 替换为 "-", 将 "/" 替换为 "_"
    b64 = string.gsub(b64, "%+", "-")
    b64 = string.gsub(b64, "/", "_")
    
    -- 4. 移除末尾的等号 "=" (对应 Java 的 withoutPadding)
    b64 = string.gsub(b64, "=", "")
    
    return b64
end

-- 解密方法
function _M.decrypt(base64_str)
    -- 1. 还原 URL-safe: 将 "-" 替换回 "+", 将 "_" 替换回 "/"
    local b64 = string.gsub(base64_str, "-", "+")
    b64 = string.gsub(b64, "_", "/")
    
    -- 2. 补全缺失的等号 "="
    -- 标准 Base64 字符串的长度必须是 4 的倍数，如果去掉了 padding，这里需要补回来
    local remainder = #b64 % 4
    if remainder > 0 then
        b64 = b64 .. string.rep("=", 4 - remainder)
    end
    
    -- 3. 标准 Base64 解码
    local encrypted = ngx.decode_base64(b64)
    if not encrypted then
        return nil, "base64 解码失败"
    end
    local decrypted = aes_256_cbc:decrypt(encrypted)
    if not decrypted then
        return nil, "解密失败"
    end
    return decrypted
end

return _M

```