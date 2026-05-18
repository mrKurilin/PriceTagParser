#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <source-file>" >&2
    exit 2
fi

source_file=$1

if [ ! -f "$source_file" ]; then
    echo "Source file does not exist: $source_file" >&2
    exit 1
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
recognition_dir=${PRICE_TAG_RECOGNITION_DIR:-"$script_dir/priceTagRecognition"}
recognition_script="$recognition_dir/price_tag_recognition/demo_track.py"
python_bin=${PRICE_TAG_RECOGNITION_PYTHON:-python3}
ckpt_file=${PRICE_TAG_RECOGNITION_CKPT:-}

if [ -z "$ckpt_file" ]; then
    if [ -f "$recognition_dir/weights/yolo/weights/best.pt" ]; then
        ckpt_file="$recognition_dir/weights/yolo/weights/best.pt"
    else
        ckpt_file="$recognition_dir/models/best_finger.pt"
    fi
fi

if [ ! -f "$recognition_script" ]; then
    echo "Recognition script does not exist: $recognition_script" >&2
    exit 1
fi

if [ ! -f "$ckpt_file" ]; then
    echo "Recognition checkpoint does not exist: $ckpt_file" >&2
    exit 1
fi

source_dir=$(dirname -- "$source_file")
source_name=$(basename -- "$source_file")
source_base=${source_name%.*}
output_file="$source_dir/$source_base.csv"
visualization_file=${PRICE_TAG_RECOGNITION_OUT_VIDEO:-}

set -- \
    "$recognition_script" \
    --video_path "$source_file" \
    --ckpt "$ckpt_file" \
    --csv_path "$output_file" \
    --use_byte

if [ "${PRICE_TAG_RECOGNITION_ROTATE:-}" = "1" ]; then
    set -- "$@" --rotate
fi

if [ -n "$visualization_file" ]; then
    set -- "$@" --out_path "$visualization_file"
fi

(
    cd "$recognition_dir"
    "$python_bin" "$@"
)

if [ ! -f "$output_file" ]; then
    echo "Recognition completed without creating CSV: $output_file" >&2
    exit 1
fi

printf '%s\n' "$output_file"
