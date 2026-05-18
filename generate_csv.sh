#!/bin/sh
set -eu

log() {
    printf '%s [price-tag-processing] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"
}

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <source-file>" >&2
    exit 2
fi

source_file=$1

if [ ! -f "$source_file" ]; then
    echo "Source file does not exist: $source_file" >&2
    exit 1
fi

log "Accepted source file: $source_file"

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
recognition_dir="$script_dir/priceTagRecognition"
recognition_script="$recognition_dir/price_tag_recognition/demo_track.py"
python_bin=${PRICE_TAG_RECOGNITION_PYTHON:-python3}
ckpt_file=${PRICE_TAG_RECOGNITION_CKPT:-}

if [ -z "$ckpt_file" ]; then
    ckpt_file="$recognition_dir/weights/yolo/weights/best.pt"
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

log "Recognition directory: $recognition_dir"
log "Recognition checkpoint: $ckpt_file"
log "Output CSV: $output_file"

set -- \
    "$recognition_script" \
    --video_path "$source_file" \
    --ckpt "$ckpt_file" \
    --csv_path "$output_file" \
    --rotate \
    --use_byte

if [ -n "$visualization_file" ]; then
    set -- "$@" --out_path "$visualization_file"
fi

log "Starting ML/CV recognition"
(
    cd "$recognition_dir"
    PYTHONUNBUFFERED=1 "$python_bin" "$@"
)
log "ML/CV recognition command finished"

if [ ! -f "$output_file" ]; then
    echo "Recognition completed without creating CSV: $output_file" >&2
    exit 1
fi

log "CSV created: $output_file"
printf '%s\n' "$output_file"
