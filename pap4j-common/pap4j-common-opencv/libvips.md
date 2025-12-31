## 去除区域内 

&ensp;&ensp;基于vips-dev-w64-all-8.18.0 编写如下bat命令，执行： imageRemoveIn.bat 1.tiff 100 100 5000 5000 。 效果： 对 i.tiff 文件的 x=100,y=100,w=5000,h=5000 的区域，置白。

#### windows

```shell
@echo off
setlocal enabledelayedexpansion

:: 1. 参数获取（%1:原图, %2:X, %3:Y, %4:W, %5:H）
set "IMG=%~1"
if "%IMG%"=="" exit /b

:: 设置默认坐标和大小（如果未传参）
set "X=%~2" & if "!X!"=="" set "X=100"
set "Y=%~3" & if "!Y!"=="" set "Y=100"
set "W=%~4" & if "!W!"=="" set "W=5000"
set "H=%~5" & if "!H!"=="" set "H=3000"

:: 2. 核心处理管道
:: 生成白色补丁并直接插入，输出到临时文件
vips black t.v !W! !H!
vips linear t.v w.v 1 255
vips cast w.v p.v uchar
vips insert "%IMG%" p.v "tmp_out.tif" !X! !Y!

:: 3. 强制覆盖并清理
move /y "tmp_out.tif" "%IMG%" >nul
del t.v w.v p.v

echo [OK] %IMG% 已更新。
```

#### linux

```shell
#!/bin/bash

# 1. 参数获取（$1:原图, $2:X, $3:Y, $4:W, $5:H）
IMG="$1"
if [ -z "$IMG" ]; then
    exit 0
fi

# 设置默认坐标和大小（如果未传参）
X="${2:-100}"
Y="${3:-100}"
W="${4:-5000}"
H="${5:-3000}"

# 2. 核心处理管道
# 生成白色补丁并直接插入，输出到临时文件
vips black t.v $W $H
vips linear t.v w.v 1 255
vips cast w.v p.v uchar
vips insert "$IMG" p.v tmp_out.tif $X $Y

# 3. 强制覆盖并清理
mv -f tmp_out.tif "$IMG"
rm -f t.v w.v p.v

echo "[OK] $IMG 已更新。"
```

## 去除区域外

&ensp;&ensp;基于vips-dev-w64-all-8.18.0 编写如下bat命令，执行： imageRemoveOut.bat 1.tiff 100 100 5000 5000 。 效果： 对 i.tiff 文件的 x=100,y=100,w=5000,h=5000 的区域外，置白。

#### windows

```shell
@echo off
setlocal enabledelayedexpansion

set "IMG=%~1"
if "%IMG%"=="" exit /b

for /f "usebackq" %%a in (`vipsheader -f width "%IMG%"`) do set "W_FULL=%%a"
for /f "usebackq" %%a in (`vipsheader -f height "%IMG%"`) do set "H_FULL=%%a"

if "!W_FULL!"=="" (echo Error: Cannot get image width & exit /b)

set "X=%~2" & if "!X!"=="" set "X=100"
set "Y=%~3" & if "!Y!"=="" set "Y=100"
set "W=%~4" & if "!W!"=="" set "W=500"
set "H=%~5" & if "!H!"=="" set "H=300"

vips crop "%IMG%" roi.tif !X! !Y! !W! !H!
vips black bg.tif !W_FULL! !H_FULL!
vips linear bg.tif bg_w.tif 1 255
vips cast bg_w.tif bg_final.tif uchar

vips insert bg_final.tif roi.tif "tmp_out.tif" !X! !Y!

move /y "tmp_out.tif" "%IMG%" >nul
del roi.tif bg.tif bg_w.tif bg_final.tif

echo [OK] %IMG% 已更新。
```

#### linux

```shell
#!/bin/bash

```

