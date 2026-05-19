# Price Tag Recognition

- build and push CPU-capable docker image with:

```bash
docker buildx build --platform linux/amd64 -t zarus03/price-tag-recognition:latest --push .
```

- build CUDA 12.4 / NVIDIA GPU image with:

```bash
docker buildx build --platform linux/amd64 \
    --build-arg PYTORCH_INDEX_URL=https://download.pytorch.org/whl/cu124 \
    --build-arg PYTORCH_EXPECT_CUDA=true \
    -t zarus03/price-tag-recognition:cuda124 \
    --push .
```

- launch the demo with:

```bash
python price_tag_recognition/demo_track.py \
    --video_path data/Данные/25_12-20/25_12-20.mp4 \
    --ckpt weights/yolo/weights/best.pt \
    --out_path result.mp4 \
    --rotate \
    --use_byte
```

## Sources

- many thanks to CarlosMena01/QRCodeSeeker-ML and openfoodfacts/price-tag-extractor from huggingface
