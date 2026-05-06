@echo off
setlocal

set ZENODO_URL=https://zenodo.org/api/records/18671823/files/RINF-dump-20260217.zip/content
set ZIP_FILE=data\RINF-dump-20260217.zip
set NQ_FILE=data\RINF-dump-20260217.nq

if exist "%NQ_FILE%" (
    echo Dataset already present: %NQ_FILE%
    exit /b 0
)

echo Downloading RINF dataset (~547 MB) from Zenodo...
curl -L --progress-bar "%ZENODO_URL%" -o "%ZIP_FILE%"
if errorlevel 1 ( echo Download failed. & exit /b 1 )

echo Unzipping...
tar -xf "%ZIP_FILE%" -C data
if errorlevel 1 ( echo Unzip failed. & exit /b 1 )

del "%ZIP_FILE%"
echo Done. Dataset available at: %NQ_FILE%
