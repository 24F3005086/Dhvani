#!/bin/bash
set -e

echo "=== Dhvani (ध्वनि) Model Downloader ==="
echo "Downloading on-device STT, TTS, and VAD models..."

ASSETS_DIR="$(pwd)/app/src/main/assets"
TEMP_DIR="$(pwd)/downloads"

mkdir -p "$ASSETS_DIR"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

# 1. Silero VAD
echo "--> [1/5] Downloading Silero VAD..."
mkdir -p "$ASSETS_DIR/vad"
curl -L --retry 5 -o "$ASSETS_DIR/vad/silero_vad.onnx" "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

# 2. Whisper Tiny Multilingual
echo "--> [2/5] Downloading Whisper Tiny INT8 Multilingual STT..."
curl -L --retry 5 -o "sherpa-onnx-whisper-tiny.tar.bz2" "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
tar -xvf "sherpa-onnx-whisper-tiny.tar.bz2"
mkdir -p "$ASSETS_DIR/whisper-tiny"
cp sherpa-onnx-whisper-tiny/tiny-encoder.int8.onnx "$ASSETS_DIR/whisper-tiny/"
cp sherpa-onnx-whisper-tiny/tiny-decoder.int8.onnx "$ASSETS_DIR/whisper-tiny/"
cp sherpa-onnx-whisper-tiny/tiny-tokens.txt "$ASSETS_DIR/whisper-tiny/"

# 3. espeak-ng-data
echo "--> [3/5] Downloading espeak-ng phonemizer data..."
curl -L --retry 5 -o "espeak-ng-data.tar.bz2" "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"
tar -xvf "espeak-ng-data.tar.bz2"
cp -r espeak-ng-data "$ASSETS_DIR/"

# 4. Piper Hindi TTS
echo "--> [4/5] Downloading Piper Hindi TTS (hi_IN-rohan-medium)..."
curl -L --retry 5 -o "vits-piper-hi_IN-rohan-medium.tar.bz2" "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium.tar.bz2"
tar -xvf "vits-piper-hi_IN-rohan-medium.tar.bz2"
mkdir -p "$ASSETS_DIR/tts-hindi"
cp vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx "$ASSETS_DIR/tts-hindi/"
cp vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx.json "$ASSETS_DIR/tts-hindi/"
cp vits-piper-hi_IN-rohan-medium/tokens.txt "$ASSETS_DIR/tts-hindi/"

# 5. Piper English TTS
echo "--> [5/5] Downloading Piper English TTS (en_US-amy-medium)..."
curl -L --retry 5 -o "vits-piper-en_US-amy-medium.tar.bz2" "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2"
tar -xvf "vits-piper-en_US-amy-medium.tar.bz2"
mkdir -p "$ASSETS_DIR/tts-english"
cp vits-piper-en_US-amy-medium/en_US-amy-medium.onnx "$ASSETS_DIR/tts-english/"
cp vits-piper-en_US-amy-medium/en_US-amy-medium.onnx.json "$ASSETS_DIR/tts-english/"
cp vits-piper-en_US-amy-medium/tokens.txt "$ASSETS_DIR/tts-english/"

# 6. sherpa-onnx AAR
echo "--> [6/7] Downloading sherpa-onnx AAR..."
mkdir -p "../app/libs"
curl -L --retry 5 -o "../app/libs/sherpa-onnx-1.13.8.aar" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"

# 7. Supertonic 3 Hindi Neural TTS
echo "--> [7/7] Downloading Supertonic 3 Hindi Flow-Matching TTS..."
curl -L --retry 5 -o "sherpa-onnx-supertonic-3-tts-int8.tar.bz2" "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
tar -xvf "sherpa-onnx-supertonic-3-tts-int8.tar.bz2"
mkdir -p "$ASSETS_DIR/supertonic-3-hi"
cp -r sherpa-onnx-supertonic-3-tts-int8-2026-05-11/* "$ASSETS_DIR/supertonic-3-hi/"

echo "=== All on-device models successfully downloaded! ==="
