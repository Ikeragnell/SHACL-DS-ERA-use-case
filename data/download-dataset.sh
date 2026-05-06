#!/bin/bash
set -e

ZENODO_URL="https://zenodo.org/api/records/18671823/files/RINF-dump-20260217.zip/content"
ZIP_FILE="data/RINF-dump-20260217.zip"
NQ_FILE="data/RINF-dump-20260217.nq"

if [ -f "$NQ_FILE" ]; then
    echo "Dataset already present: $NQ_FILE"
    exit 0
fi

echo "Downloading RINF dataset (~547 MB) from Zenodo..."
curl -L --progress-bar "$ZENODO_URL" -o "$ZIP_FILE"

echo "Unzipping..."
unzip -q "$ZIP_FILE" -d data/

rm "$ZIP_FILE"
echo "Done. Dataset available at: $NQ_FILE"
