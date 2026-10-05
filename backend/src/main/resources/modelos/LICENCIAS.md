# Modelos de reconocimiento facial y lectura de la INE

Los tres modelos ONNX provienen de [OpenCV Model Zoo](https://github.com/opencv/opencv_zoo) y los datos de
Tesseract de [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast). Se usan sin modificar.

| Archivo | Uso | Licencia | SHA-256 |
|---|---|---|---|
| `face_detection_yunet_2023mar.onnx` | YuNet: detecta rostros y 5 puntos de referencia (ojos, nariz, comisuras) | MIT, Copyright (c) 2020 Shiqi Yu | verificado contra el puntero Git LFS del repositorio oficial |
| `face_recognition_sface_2021dec.onnx` | SFace: convierte un rostro alineado en un vector de 128 dimensiones | Apache License 2.0 | verificado contra el puntero Git LFS del repositorio oficial |
| `text_detection_en_ppocrv3_2023may.onnx` | PP-OCRv3 (DB): localiza los renglones de texto de la credencial | Apache License 2.0 | verificado contra el puntero Git LFS del repositorio oficial |
| `tessdata/eng.traineddata` | Tesseract: lee cada renglón (limitado a A-Z y 0-9) | Apache License 2.0 | descargado de tessdata_fast |

Textos completos de las licencias:

- https://github.com/opencv/opencv_zoo/blob/main/models/face_detection_yunet/LICENSE
- https://github.com/opencv/opencv_zoo/blob/main/models/face_recognition_sface/LICENSE
- https://github.com/opencv/opencv_zoo/blob/main/models/text_detection_ppocr/LICENSE
- https://github.com/tesseract-ocr/tessdata_fast/blob/main/LICENSE
