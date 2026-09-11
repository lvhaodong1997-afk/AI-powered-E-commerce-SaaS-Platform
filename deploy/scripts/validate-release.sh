#!/usr/bin/env bash
set -euo pipefail

WRITE_MANIFEST=false
if [[ "${1:-}" == "--write-manifest" ]]; then
  WRITE_MANIFEST=true
  shift
fi

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 [--write-manifest] <release-dir>" >&2
  exit 64
fi

RELEASE_DIR="$(cd -- "$1" && pwd -P)"
DOWNLOAD_SCRIPT="$RELEASE_DIR/tools/reference-video-download/douyin_tiktok_bilibili_tool.py"
DOWNLOAD_REPO="$RELEASE_DIR/tools/reference-video-download/Douyin_TikTok_Download_API"
ASR_SCRIPT="$RELEASE_DIR/tools/subtitle/asr_faster_whisper.py"
DOWNLOAD_PYTHON="${TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON:-$RELEASE_DIR/tools/reference-video-download/.venv/bin/python}"
ASR_PYTHON="${TK_ASR_PYTHON:-$RELEASE_DIR/.venv-asr/bin/python}"
MODEL_CACHE_DIR="${TK_ASR_MODEL_CACHE_DIR:-$RELEASE_DIR/models/faster-whisper}"
MANIFEST="$RELEASE_DIR/manifest.sha256"

fail() {
  echo "Release validation failed: $*" >&2
  exit 1
}

require_file_or_dir() {
  local path="$1"
  [[ -e "$path" ]] || fail "Missing required file or directory: $path"
}

require_executable() {
  local path="$1"
  [[ -x "$path" ]] || fail "Missing executable: $path"
}

require_file_or_dir "$DOWNLOAD_SCRIPT"
require_file_or_dir "$DOWNLOAD_REPO"
require_file_or_dir "$ASR_SCRIPT"
require_executable "$DOWNLOAD_PYTHON"
require_executable "$ASR_PYTHON"
require_file_or_dir "$MODEL_CACHE_DIR"

find -L "$MODEL_CACHE_DIR" -mindepth 1 -print -quit | grep -q . \
  || fail "Model cache is empty: $MODEL_CACHE_DIR"

if ! "$DOWNLOAD_PYTHON" -m pip check >/dev/null 2>&1; then
  fail "Download Python dependency check failed: $DOWNLOAD_PYTHON"
fi

if ! "$ASR_PYTHON" -m pip check >/dev/null 2>&1; then
  fail "ASR Python dependency check failed: $ASR_PYTHON"
fi

if ! TK_RELEASE_REPO="$DOWNLOAD_REPO" "$DOWNLOAD_PYTHON" -c \
  'import os, sys; sys.path.insert(0, os.environ["TK_RELEASE_REPO"]); import httpx, fastapi, uvicorn; import crawlers.hybrid.hybrid_crawler' \
  >/dev/null 2>&1; then
  fail "Download runtime import check failed"
fi

if ! "$DOWNLOAD_PYTHON" "$DOWNLOAD_SCRIPT" --repo "$DOWNLOAD_REPO" --help \
  >/dev/null 2>&1; then
  fail "Download script help check failed"
fi

if ! "$ASR_PYTHON" "$ASR_SCRIPT" --help >/dev/null 2>&1; then
  fail "ASR script help check failed"
fi

mapfile -t MANIFEST_FILES < <(
  cd "$RELEASE_DIR"
  find tools/reference-video-download tools/subtitle \
    -type f \
    ! -path '*/.venv/*' \
    ! -path '*/__pycache__/*' \
    -print | sort
)
[[ ${#MANIFEST_FILES[@]} -gt 0 ]] || fail "No tool files found for manifest"

if [[ -f "$MANIFEST" && "$WRITE_MANIFEST" != true ]]; then
  if ! (cd "$RELEASE_DIR" && sha256sum -c manifest.sha256 >/dev/null 2>&1); then
    fail "manifest verification failed: $MANIFEST"
  fi
fi

if [[ "$WRITE_MANIFEST" == true ]]; then
  manifest_tmp="$MANIFEST.tmp.$$"
  (
    cd "$RELEASE_DIR"
    sha256sum "${MANIFEST_FILES[@]}"
  ) >"$manifest_tmp"
  mv -f -- "$manifest_tmp" "$MANIFEST"
fi

echo "Release validation passed: $RELEASE_DIR"
