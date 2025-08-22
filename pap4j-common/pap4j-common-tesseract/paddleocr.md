目前使用的是 paddle ocr 3.2.0 的版本

# 安装 PaddlePaddle CPU 版本 (GPU 根据实际安装)
pip install paddlepaddle -i https://mirror.baidu.com/pypi/simple

# 安装 PaddleOCR 
pip install paddleocr

# 安装 FastAPI 和 Uvicorn 作为服务
pip install fastapi uvicorn python-multipart


```shell
# paddleocr_init.py 下载模型
from paddleocr import PaddleOCR

ocr = PaddleOCR(use_angle_cls=True, 
                lang='ch')  # 'en' 英文

```

```shell
# paddleocr_server.py 一个web服务 发送的是 form-data 的参数为 file 的文件
from fastapi import FastAPI, UploadFile, File
from paddleocr import PaddleOCR
import uvicorn
from PIL import Image
import numpy as np
import io
from typing import List, Dict, Any

app = FastAPI(title="PP-OCRv5 API")

# 初始化 OCR
ocr = PaddleOCR(use_angle_cls=True, lang='ch')

def parse_ocr_result_to_json(ocr_result: List[Dict]) -> List[Dict]:
    """将OCR结果解析为JSON可序列化的格式"""
    if not ocr_result or not isinstance(ocr_result, list):
        return []
    
    result_data = []
    
    # 遍历每个结果项（通常只有一个）
    for result_item in ocr_result:
        if not isinstance(result_item, dict):
            continue
            
        # 提取文本识别结果
        rec_texts = result_item.get('rec_texts', [])
        rec_scores = result_item.get('rec_scores', [])
        rec_polys = result_item.get('rec_polys', [])
        
        # 确保所有列表长度一致
        min_length = min(len(rec_texts), len(rec_scores), len(rec_polys))
        
        # 构建结构化结果
        for i in range(min_length):
            text = rec_texts[i]
            confidence = float(rec_scores[i])
            polygon = rec_polys[i]
            
            # 处理多边形坐标（numpy数组转列表）
            if hasattr(polygon, 'tolist'):
                polygon_coords = polygon.tolist()
            elif isinstance(polygon, np.ndarray):
                polygon_coords = polygon.tolist()
            else:
                polygon_coords = polygon
            
            result_data.append({
                "text": text,
                "confidence": confidence,
                "polygon": polygon_coords,
                "index": i
            })
    
    return result_data

def parse_ocr_result_simple(ocr_result: List[Dict]) -> List[Dict]:
    """简化版解析，只返回文本和置信度"""
    if not ocr_result or not isinstance(ocr_result, list):
        return []
    
    result_data = []
    
    for result_item in ocr_result:
        if not isinstance(result_item, dict):
            continue
            
        rec_texts = result_item.get('rec_texts', [])
        rec_scores = result_item.get('rec_scores', [])
        
        min_length = min(len(rec_texts), len(rec_scores))
        
        for i in range(min_length):
            result_data.append({
                "text": rec_texts[i],
                "confidence": float(rec_scores[i])
            })
    
    return result_data

@app.post("/ocr")
async def ocr_image(file: UploadFile = File(...)):
    try:
        img_bytes = await file.read()
        img = Image.open(io.BytesIO(img_bytes)).convert('RGB')
        img_array = np.array(img)
        
        print("开始OCR识别...")
        # 执行 OCR
        result = ocr.ocr(img_array)
        
        print(f"OCR返回结果类型: {type(result)}")
        print(f"OCR返回结果长度: {len(result) if result else 0}")
        
        if result and isinstance(result, list):
            print(f"第一个元素的类型: {type(result[0])}")
            if isinstance(result[0], dict):
                print("检测到字典格式的结果")
                print(f"包含的键: {list(result[0].keys())}")
                print(f"识别到的文本: {result[0].get('rec_texts', [])}")
        
        # 解析结果为JSON格式
        parsed_result = parse_ocr_result_to_json(result)
        
        return {
            "success": True, 
            "results": parsed_result,
            "total_texts": len(parsed_result)
        }
    
    except Exception as e:
        print(f"发生错误: {str(e)}")
        import traceback
        traceback.print_exc()
        return {"success": False, "error": str(e)}

@app.post("/ocr/simple")
async def ocr_image_simple(file: UploadFile = File(...)):
    """简化版接口，只返回文本和置信度"""
    try:
        img_bytes = await file.read()
        img = Image.open(io.BytesIO(img_bytes)).convert('RGB')
        img_array = np.array(img)
        
        result = ocr.ocr(img_array)
        parsed_result = parse_ocr_result_simple(result)
        
        return {
            "success": True, 
            "texts": [item["text"] for item in parsed_result],
            "details": parsed_result
        }
    
    except Exception as e:
        return {"success": False, "error": str(e)}

@app.get("/health")
async def health_check():
    return {"status": "healthy"}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
```
