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

source_dir=$(dirname "$source_file")
source_name=$(basename "$source_file")
source_base=${source_name%.*}
output_file="$source_dir/$source_base.csv"
escaped_name=$(printf '%s' "$source_name" | sed 's/"/""/g')

{
    printf 'file_name\n'
    printf '"%s"\n' "$escaped_name"
} > "$output_file"

printf '%s\n' "$output_file"
