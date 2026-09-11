#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
VALIDATOR="$SCRIPT_DIR/validate-release.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

assert_fails_for_missing_tools() {
  local release_dir="$TEST_ROOT/missing-tools"
  mkdir -p "$release_dir"

  if "$VALIDATOR" "$release_dir" >"$TEST_ROOT/missing-tools.log" 2>&1; then
    echo "validator accepted a release without required tools" >&2
    return 1
  fi

  grep -q 'Missing required file or directory' "$TEST_ROOT/missing-tools.log"
}

assert_accepts_complete_release() {
  local release_dir="$TEST_ROOT/complete"
  local download_python="$release_dir/download-python"
  local asr_python="$release_dir/asr-python"

  mkdir -p \
    "$release_dir/tools/reference-video-download/Douyin_TikTok_Download_API" \
    "$release_dir/tools/subtitle" \
    "$release_dir/models/faster-whisper"
  touch \
    "$release_dir/tools/reference-video-download/douyin_tiktok_bilibili_tool.py" \
    "$release_dir/tools/subtitle/asr_faster_whisper.py" \
    "$release_dir/models/faster-whisper/model.bin"

  printf '#!/usr/bin/env bash\nexit 0\n' >"$download_python"
  printf '#!/usr/bin/env bash\nexit 0\n' >"$asr_python"
  chmod +x "$download_python" "$asr_python"

  TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="$download_python" \
  TK_ASR_PYTHON="$asr_python" \
  TK_ASR_MODEL_CACHE_DIR="$release_dir/models/faster-whisper" \
    "$VALIDATOR" --write-manifest "$release_dir" >/dev/null

  test -s "$release_dir/manifest.sha256"
}

assert_fails_for_tampered_manifest() {
  local release_dir="$TEST_ROOT/tampered"
  local download_python="$release_dir/download-python"
  local asr_python="$release_dir/asr-python"

  mkdir -p \
    "$release_dir/tools/reference-video-download/Douyin_TikTok_Download_API" \
    "$release_dir/tools/subtitle" \
    "$release_dir/models/faster-whisper"
  touch \
    "$release_dir/tools/reference-video-download/douyin_tiktok_bilibili_tool.py" \
    "$release_dir/tools/subtitle/asr_faster_whisper.py" \
    "$release_dir/models/faster-whisper/model.bin"
  printf '#!/usr/bin/env bash\nexit 0\n' >"$download_python"
  printf '#!/usr/bin/env bash\nexit 0\n' >"$asr_python"
  chmod +x "$download_python" "$asr_python"

  TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="$download_python" \
  TK_ASR_PYTHON="$asr_python" \
  TK_ASR_MODEL_CACHE_DIR="$release_dir/models/faster-whisper" \
    "$VALIDATOR" --write-manifest "$release_dir" >/dev/null
  printf 'tampered\n' >>"$release_dir/tools/subtitle/asr_faster_whisper.py"

  if TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="$download_python" \
    TK_ASR_PYTHON="$asr_python" \
    TK_ASR_MODEL_CACHE_DIR="$release_dir/models/faster-whisper" \
    "$VALIDATOR" "$release_dir" >"$TEST_ROOT/tampered.log" 2>&1; then
    echo "validator accepted a tampered release" >&2
    return 1
  fi

  grep -q 'manifest verification failed' "$TEST_ROOT/tampered.log"
}

assert_fails_for_missing_tools
assert_accepts_complete_release
assert_fails_for_tampered_manifest
echo 'validate-release tests passed'
