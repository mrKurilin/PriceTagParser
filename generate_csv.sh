#!/bin/sh
set -eu

recognition_pid=

log() {
    printf '%s [price-tag-processing] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"
}

stop_recognition() {
    if [ -n "$recognition_pid" ]; then
        kill "$recognition_pid" 2>/dev/null || true
        wait "$recognition_pid" 2>/dev/null || true
        recognition_pid=
    fi
}

trap 'stop_recognition; exit 129' HUP
trap 'stop_recognition; exit 130' INT
trap 'stop_recognition; exit 143' TERM

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
hf_cache_dir=${HF_HOME:-$recognition_dir/.huggingface-cache}

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
log "Hugging Face cache: $hf_cache_dir"
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
    mkdir -p "$hf_cache_dir"
    PYTHONUNBUFFERED=1
    HF_HOME="$hf_cache_dir"
    HF_HUB_CACHE=${HF_HUB_CACHE:-$hf_cache_dir/hub}
    TRANSFORMERS_CACHE=${TRANSFORMERS_CACHE:-$hf_cache_dir/transformers}
    export PYTHONUNBUFFERED HF_HOME HF_HUB_CACHE TRANSFORMERS_CACHE
    exec "$python_bin" "$@"
) &
recognition_pid=$!

set +e
wait "$recognition_pid"
recognition_status=$?
set -e
recognition_pid=

if [ "$recognition_status" -ne 0 ]; then
    log "ML/CV recognition command failed with exit code $recognition_status"
    exit "$recognition_status"
fi

log "ML/CV recognition command finished"

if [ ! -f "$output_file" ]; then
    echo "Recognition completed without creating CSV: $output_file" >&2
    exit 1
fi

log "CSV created: $output_file"
printf '%s\n' "$output_file"
