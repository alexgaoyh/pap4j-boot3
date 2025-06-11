# [Openresty] 配合ImageMagick完成图像水印

## 介绍

&ensp;&ensp;在window环境下，支持中文路径的图像水印添加操作。

## 代码实现

<div class="filename">nginx.conf</div>

```shell
server {
    listen 8898;
    server_name  127.0.0.1;

    location ~* \.(jpg|jpeg|png|gif|bmp|webp)$ {
        content_by_lua_file conf/lua/image_handler2.lua;
    }

    location / {
        root "D:/knowledge2/";
    }
}
```


<div class="filename">image_handler2.lua</div>

```shell
local ffi = require("ffi")
local bit = require("bit")

local _M = {}

local function urldecode(s)
    s = s:gsub('+', ' ');
    s = s:gsub('%%(%x%x)', function(hex)
        return string.char(tonumber(hex, 16))
    end)
    return s
end

ffi.cdef[[
typedef unsigned short WCHAR;
typedef unsigned long DWORD;
typedef int BOOL;
typedef const WCHAR* LPCWSTR;
typedef char* LPSTR;
typedef const wchar_t* LPCWSTR;
int MultiByteToWideChar(
    unsigned int CodePage,
    unsigned long dwFlags,
    const char* lpMultiByteStr,
    int cbMultiByte,
    WCHAR* lpWideCharStr,
    int cchWideChar
);

int WideCharToMultiByte(
    unsigned int CodePage,
    unsigned long dwFlags,
    const WCHAR* lpWideCharStr,
    int cchWideChar,
    LPSTR lpMultiByteStr,
    int cbMultiByte,
    const char* lpDefaultChar,
    int* lpUsedDefaultChar
);
int GetLastError(void);
int CreateDirectoryW(LPCWSTR lpPathName, void* lpSecurityAttributes);
DWORD GetFileAttributesW(LPCWSTR lpFileName);
]];
local INVALID_FILE_ATTRIBUTES = 0xFFFFFFFF
local C = ffi.C
local CP_UTF8 = 65001
local CP_ACP = 0  -- Windows ANSI code page, 通常是 GBK

-- 先把原 io.open 存起来
local real_io_open = io.open

local function utf8_to_gbk(s)
  if not s or #s == 0 then return "" end

  -- 第一步：UTF-8 → UTF-16，先获取长度
  local wlen = ffi.C.MultiByteToWideChar(CP_UTF8, 0, s, #s, nil, 0)
  if wlen == 0 then return s end
  local wbuf = ffi.new("wchar_t[?]", wlen)
  ffi.C.MultiByteToWideChar(CP_UTF8, 0, s, #s, wbuf, wlen)

  -- 第二步：UTF-16 → GBK，获取目标长度
  local glen = ffi.C.WideCharToMultiByte(CP_ACP, 0, wbuf, wlen, nil, 0, nil, nil)
  if glen == 0 then return s end
  local gbkbuf = ffi.new("char[?]", glen)
  ffi.C.WideCharToMultiByte(CP_ACP, 0, wbuf, wlen, gbkbuf, glen, nil, nil)

  return ffi.string(gbkbuf, glen)
end

-- 重写 io.open，使它能接受 UTF-8 路径
function open_utf8(path, mode)
    if package.config:sub(1,1) == '\\' then
        -- Windows 下，把 path 从 UTF-8 转到 GBK
        path = utf8_to_gbk(path)
    end
	ngx.log(ngx.ERR, "path: ", path)
    return real_io_open(path, mode)
end

local function utf8_iter(str)
    local i = 1
    local len = #str
    return function()
        if i > len then return nil end
        local c = string.byte(str, i)

        local bytes = 1
        if c >= 0xF0 then
            bytes = 4
        elseif c >= 0xE0 then
            bytes = 3
        elseif c >= 0xC0 then
            bytes = 2
        end

        local codepoint = nil
        if bytes == 1 then
            codepoint = c
        elseif bytes == 2 then
            local b1 = string.byte(str, i+1)
            codepoint = bit.lshift(c - 0xC0, 6) + (b1 - 0x80)
        elseif bytes == 3 then
            local b1, b2 = string.byte(str, i+1, i+2)
            codepoint = bit.lshift(c - 0xE0, 12) + bit.lshift(b1 - 0x80, 6) + (b2 - 0x80)
        elseif bytes == 4 then
            local b1, b2, b3 = string.byte(str, i+1, i+3)
            codepoint = bit.lshift(c - 0xF0, 18) + bit.lshift(b1 - 0x80, 12) + bit.lshift(b2 - 0x80, 6) + (b3 - 0x80)
        end

        local current_i = i
        i = i + bytes
        return current_i, codepoint
    end
end

local function utf8_to_utf16le(str)
    local wstr = {}
    for _, codepoint in utf8_iter(str) do
        if codepoint <= 0xFFFF then
            table.insert(wstr, string.char(codepoint % 256, math.floor(codepoint / 256)))
        else
            codepoint = codepoint - 0x10000
            local high = 0xD800 + math.floor(codepoint / 0x400)
            local low = 0xDC00 + (codepoint % 0x400)
            table.insert(wstr, string.char(high % 256, math.floor(high / 256)))
            table.insert(wstr, string.char(low % 256, math.floor(low / 256)))
        end
    end
    table.insert(wstr, "\0\0")
    return table.concat(wstr)
end

local function file_exists(path)
    if package.config:sub(1, 1) == '\\' then  -- Windows 平台
		local wpath = utf8_to_utf16le(path) -- wpath 是 Lua 字符串，UTF-16LE 编码
		local wchar_len = #wpath / 2
		local wpath_c = ffi.new("wchar_t[?]", wchar_len)
		ffi.copy(wpath_c, wpath, #wpath)
		local attr = C.GetFileAttributesW(wpath_c)
		return attr ~= INVALID_FILE_ATTRIBUTES
    else
        local file = open_utf8(path, "r")
        if file then
            file:close()
            return true
        end
        return false
    end
end

local function mkdir_utf8_recursive(path)
    local sep = package.config:sub(1, 1)  -- Windows 是 "\"
    local normalized_path = path:gsub("[/\\]", sep)  -- 统一为 Windows 分隔符

    -- 拆分路径段
    local parts = {}
    for part in normalized_path:gmatch("[^" .. sep .. "]+") do
        table.insert(parts, part)
    end

    -- 提取盘符或网络路径前缀
    local root = ""
    if normalized_path:match("^[A-Za-z]:") then
        root = normalized_path:sub(1, 2)  -- D:
    elseif normalized_path:sub(1, 2) == sep .. sep then
        root = sep .. sep  -- 网络路径 \\server\share
    end

    local current = root
    for i = (root ~= "" and 2 or 1), #parts do
        current = current ~= "" and (current .. sep .. parts[i]) or parts[i]

        local utf16 = utf8_to_utf16le(current)
        local wchar_len = #utf16 / 2
        local wpath_c = ffi.new("wchar_t[?]", wchar_len)
        ffi.copy(wpath_c, utf16, #utf16)

        local result = C.CreateDirectoryW(wpath_c, nil)
        if result == 0 then
            local err = ffi.errno()
        else
        end
    end
end


function _M.handle_request()
    local raw_uri = ngx.var.uri
	local decoded_uri = ngx.unescape_uri(raw_uri)
    local uri = urldecode(raw_uri)
    local args = ngx.req.get_uri_args()
    local watermark_path = "D:/knowledge/watermark.png"
    local source_path = "D:/knowledge" .. uri
    local output_base = "D:/knowledge2" .. uri

    local width = tonumber(args["w"])
    local resize_suffix = width and ("_w" .. width) or ""
    local output_path = output_base:gsub("(.+)(%.%w+)$", "%1" .. resize_suffix .. "%2")
	
	local output_dir = output_path:match("^(.*)[/\\][^/\\]+$")
	if output_dir then
		if package.config:sub(1, 1) == '\\' then
			mkdir_utf8_recursive(output_dir)
		else
			local mkdir_cmd = string.format('mkdir -p "%s"', output_dir)
			os.execute(mkdir_cmd)
		end
	end

    if file_exists(output_path) then
        local file = open_utf8(output_path, "rb")
        local content = file:read("*all")
        file:close()
        ngx.header.content_type = "image/jpeg"
        ngx.say(content)
		return
    end

    if not file_exists(source_path) then
        ngx.status = ngx.HTTP_NOT_FOUND
        ngx.say("Source file not found: " .. source_path)
        return
    end

    local convert_cmd
    if width then
        convert_cmd = string.format(
            'magick "%s" "%s" -gravity southeast -geometry +5+10 -composite -resize %d "%s"',
            source_path, watermark_path, width, output_path
        )
    else
        convert_cmd = string.format(
            'magick "%s" "%s" -gravity southeast -geometry +5+10 -composite "%s"',
            source_path, watermark_path, output_path
        )
    end

    local result, reason, code = os.execute(utf8_to_gbk(convert_cmd))

    if file_exists(output_path) then
        local file = open_utf8(output_path, "rb")
        local content = file:read("*all")
        file:close()
        ngx.header.content_type = "image/jpeg"
        ngx.say(content)
    else
        ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
        ngx.say("Failed to generate image")
    end
end

-- 主动调用 handle_request 函数
_M.handle_request()

return _M
```


