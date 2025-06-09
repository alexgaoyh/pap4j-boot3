local _M = {}

local b64_chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

local stand_b64_chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_='
local custo_b64_chars = '9876543210=_-ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba'

function replaceCharsSafe(input, from, to)
    if #from ~= #to then
        error("from 和 to 的长度必须相同")
    end

    local out = {}
    for i = 1, #input do
        local c = input:sub(i, i)
        local replaced = false
        for j = 1, #from do
            if c == from:sub(j, j) then
                table.insert(out, to:sub(j, j))
                replaced = true
                break
            end
        end
        if not replaced then
            error("非法字符: " .. c)
        end
    end
    return table.concat(out)
end

function trim(s)
	s = s:gsub("^%s*(.-)%s*$", "%1")   -- trim whitespace
    s = s:gsub("%z+$", "")            -- strip trailing NULs
    return s
end

-- Base64 编码
function _M.encodeBase64(input)
    local output = ""
    local padding = 0
    local i = 1
    while i <= #input do
        local a = input:byte(i)
        local b = input:byte(i+1)
        local c = input:byte(i+2)

        if not b then b = 0; padding = padding + 1 end
        if not c then c = 0; padding = padding + 1 end

        local index1 = math.floor(a / 4)
        local index2 = math.floor(((a % 4) * 16) + math.floor(b / 16))
        local index3 = math.floor(((b % 16) * 4) + math.floor(c / 64))
        local index4 = c % 64

        output = output .. b64_chars:sub(index1+1, index1+1)
        output = output .. b64_chars:sub(index2+1, index2+1)
        output = output .. b64_chars:sub(index3+1, index3+1)
        output = output .. b64_chars:sub(index4+1, index4+1)

        i = i + 3
    end

    -- Add padding
    if padding > 0 then
        output = output:sub(1, #output - padding) .. string.rep("=", padding)
    end

    return output
end

-- Base64 解码
function _M.decodeBase64(input)
    input = input:gsub("=", ""):gsub("-", "+"):gsub("_", "/")
    local output = ""
    local padding = 0
    if input:sub(-1) == "=" then padding = 1 end
    if input:sub(-2) == "==" then padding = 2 end

    input = input:gsub("=", "")  -- Remove padding
    local i = 1
    while i <= #input do
        local index1 = b64_chars:find(input:sub(i,i))
        local index2 = b64_chars:find(input:sub(i+1,i+1))
        local index3 = b64_chars:find(input:sub(i+2,i+2))
        local index4 = b64_chars:find(input:sub(i+3,i+3))

        output = output .. string.char(((index1 - 1) * 4) + math.floor((index2 - 1) / 16))
        output = output .. string.char(((index2 - 1) % 16) * 16 + ((index3 - 1) / 4))
        output = output .. string.char(((index3 - 1) % 4) * 64 + (index4 - 1))

        i = i + 4
    end

    return output:sub(1, #output - padding)  -- Remove padding bytes
end

-- 编码：原始URL -> Base64 编码
function _M.encode(url)
    local compressed = _M.encodeBase64(url)
    return compressed
end

-- 解码：Base64 解码 -> 恢复原始URL
function _M.decode(shortUrl)
    local decoded = _M.decodeBase64(shortUrl)
    return decoded
end

-- 实际的真实的解码
function _M.realDecode()
    local url = ngx.var.arg_url  -- 获取 URL 参数

	ngx.header["Content-Type"] = "text/html; charset=utf-8"

	local short_url = _M.decode(url)
    -- local short_url = _M.decode(replaceCharsSafe(url, custo_b64_chars, stand_b64_chars))

    -- 输出解码后的结果
    ngx.status = ngx.HTTP_OK
    ngx.say(short_url)
    ngx.exit(ngx.HTTP_OK)

	-- 这里是返回真实的图像信息
	-- ngx.header.content_type = "image/jpeg"  -- 设置 MIME 类型
	-- ngx.exec(trim(short_url))
end


-- 主动调用 realDecode 函数
_M.realDecode()

return _M
