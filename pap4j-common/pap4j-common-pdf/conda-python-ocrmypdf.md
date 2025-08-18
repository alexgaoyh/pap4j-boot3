使用 conda pack 打包环境
    安装 conda-pack       conda install -c conda-forge conda-pack
    打包环境：             conda pack -o pdf_processor.tar.gz
    迁移环境：             本质实际是拷贝到另外一台机器之后，然后使用 python 命令做执行，这个时候可以手动的设置环境变量，达到和 conda 一样的效果


```shell
conda config --add channels conda-forge
conda config --set channel_priority strict

conda create -n pdf_processor python=3.10 -y
			conda env remove -n pdf_processor

pip install flask 
pip install tesseract
pip install pcrmypdf

```

```shell
try:
    from flask import __version__
    print(f"✓ Flask 版本: {__version__}")
except ImportError:
    print("✗ Flask 未安装")
    print("安装命令: pip install flask")
```

```shell
try:
    from ocrmypdf import __version__
    print(f"✓ ocrmypdf 版本: {__version__}")
except ImportError:
    print("✗ ocrmypdf 未安装")
    print("安装命令: pip install ocrmypdf")
```

```shell
from flask import Flask, request, jsonify
import subprocess
import os
import logging
from pathlib import Path

app = Flask(__name__)

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def generate_output_path(pdf_path):
    """生成带_ocrmypdf后缀的输出路径"""
    pdf_path = Path(pdf_path)
    return str(pdf_path.with_stem(f"{pdf_path.stem}_ocrmypdf"))

@app.route('/process-pdf', methods=['POST'])
def process_pdf():
    # 获取上传的PDF路径
    pdf_path = request.json.get('pdf_path')
    
    if not pdf_path:
        logger.error("未提供PDF路径")
        return jsonify({"error": "PDF path is required"}), 400
    
    try:
        pdf_path = Path(pdf_path).resolve()  # 转换为绝对路径
        if not pdf_path.exists():
            logger.error(f"文件不存在: {pdf_path}")
            return jsonify({"error": "PDF file does not exist"}), 400
            
        # 生成输出路径
        output_path = generate_output_path(pdf_path)
        
        if Path(output_path).exists():
            logger.warning(f"输出文件已存在，将被覆盖: {output_path}")
        
        logger.info(f"开始处理: {pdf_path} -> {output_path}")
        
        # 执行OCR处理
        cmd = [
            "ocrmypdf",
            "--skip-text",
            "--optimize", "0",
            str(pdf_path),
            output_path
        ]
        
        logger.info(f"执行命令: {' '.join(cmd)}")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=86400)
        
        if result.returncode == 0:
            response = {
                "status": "success",
                "original_path": str(pdf_path),
                "original_size": os.path.getsize(pdf_path),
                "output_path": output_path,
                "processed_size": os.path.getsize(output_path),
                "compression_ratio": f"{os.path.getsize(output_path)/os.path.getsize(pdf_path):.1%}"
            }
            logger.info(f"处理成功: {response}")
            return jsonify(response)
        else:
            error_msg = f"OCR处理失败: {result.stderr}"
            logger.error(error_msg)
            return jsonify({
                "error": "OCR processing failed",
                "details": result.stderr
            }), 500
            
    except subprocess.TimeoutExpired:
        error_msg = "OCR处理超时(86400秒)"
        logger.error(error_msg)
        return jsonify({"error": error_msg}), 500
    except Exception as e:
        error_msg = f"处理异常: {str(e)}"
        logger.error(error_msg, exc_info=True)
        return jsonify({"error": error_msg}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
```

```shell
from flask import Flask, request, jsonify
import subprocess
import os
import logging
from pathlib import Path

env_path = r"D:\pdf_processor"
env = os.environ.copy()
env["PATH"] = os.path.join(env_path, "Scripts") + ";" + os.path.join(env_path, "Library/bin") + ";" + env.get("PATH", "")
env["TESSDATA_PREFIX"] = os.path.join(env_path, "share/tessdata")

app = Flask(__name__)

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def generate_output_path(pdf_path):
    """生成带_ocrmypdf后缀的输出路径"""
    pdf_path = Path(pdf_path)
    return str(pdf_path.with_stem(f"{pdf_path.stem}_ocrmypdf"))

@app.route('/process-pdf', methods=['POST'])
def process_pdf():
    # 获取上传的PDF路径
    pdf_path = request.json.get('pdf_path')
    
    if not pdf_path:
        logger.error("未提供PDF路径")
        return jsonify({"error": "PDF path is required"}), 400
    
    try:
        pdf_path = Path(pdf_path).resolve()  # 转换为绝对路径
        if not pdf_path.exists():
            logger.error(f"文件不存在: {pdf_path}")
            return jsonify({"error": "PDF file does not exist"}), 400
            
        # 生成输出路径
        output_path = generate_output_path(pdf_path)
        
        if Path(output_path).exists():
            logger.warning(f"输出文件已存在，将被覆盖: {output_path}")
        
        logger.info(f"开始处理: {pdf_path} -> {output_path}")
        
        # 执行OCR处理
        cmd = [
            "Scripts\ocrmypdf",
            "--skip-text",
            "--optimize", "0",
            str(pdf_path),
            output_path
        ]
        
        logger.info(f"执行命令: {' '.join(cmd)}")
        logger.info("subprocess 将使用的 PATH:", env.get("PATH"))
        result = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=86400)
        
        if result.returncode == 0:
            response = {
                "status": "success",
                "original_path": str(pdf_path),
                "original_size": os.path.getsize(pdf_path),
                "output_path": output_path,
                "processed_size": os.path.getsize(output_path),
                "compression_ratio": f"{os.path.getsize(output_path)/os.path.getsize(pdf_path):.1%}"
            }
            logger.info(f"处理成功: {response}")
            return jsonify(response)
        else:
            error_msg = f"OCR处理失败: {result.stderr}"
            logger.error(error_msg)
            return jsonify({
                "error": "OCR processing failed",
                "details": result.stderr
            }), 500
            
    except subprocess.TimeoutExpired:
        error_msg = "OCR处理超时(86400秒)"
        logger.error(error_msg)
        return jsonify({"error": error_msg}), 500
    except Exception as e:
        error_msg = f"处理异常: {str(e)}"
        logger.error(error_msg, exc_info=True)
        return jsonify({"error": error_msg}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
```