#!/bin/bash

# Cause the script to exit on failure.
set -eo pipefail

cd /workspace

readonly DATA_DIR="/workspace/data"
readonly WEIGHTS_DIR="/workspace/weights"
readonly YOLO_WEIGHTS_DIR="$WEIGHTS_DIR/yolo"
readonly YOLO_CHECKPOINT="$YOLO_WEIGHTS_DIR/weights/best.pt"
readonly YOLO_WEIGHTS_REPO="https://huggingface.co/openfoodfacts/price-tag-detection"

ensure_yolo_weights() {
    if [ -f "$YOLO_CHECKPOINT" ]; then
        echo "YOLO weights cache hit: $YOLO_CHECKPOINT"
        return 0
    fi

    echo "YOLO weights cache miss; downloading to $YOLO_WEIGHTS_DIR"
    mkdir -p "$WEIGHTS_DIR"

    local tmp_dir
    tmp_dir=$(mktemp -d "$WEIGHTS_DIR/yolo.tmp.XXXXXX")
    trap 'rm -rf "$tmp_dir"' RETURN

    git lfs install --skip-repo
    git clone "$YOLO_WEIGHTS_REPO" "$tmp_dir"

    rm -rf "$YOLO_WEIGHTS_DIR"
    mv "$tmp_dir" "$YOLO_WEIGHTS_DIR"
    trap - RETURN

    test -f "$YOLO_CHECKPOINT"
    echo "YOLO weights cached: $YOLO_CHECKPOINT"
}

ensure_yolo_weights

## download data
#gdown --folder --fuzzy \
#    https://drive.google.com/drive/folders/1_UbQ7x4MK9fZjA-9DqJY_A8nedsg9KMy?usp=sharing \
#    -O "$DATA_DIR"


