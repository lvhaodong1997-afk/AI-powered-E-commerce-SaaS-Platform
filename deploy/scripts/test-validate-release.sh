#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
VALIDATOR="$SCRIPT_DIR/validate-release.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

# Font tools are isolated protocol fixtures; validation must reject exact-family substitution.
mkdir -p "$TEST_ROOT/font-bin" "$TEST_ROOT/fonts"
for stem in NotoSansCJK NotoSerifCJK; do
  touch "$TEST_ROOT/fonts/$stem-Regular.ttc" "$TEST_ROOT/fonts/$stem-Bold.ttc"
done
cat >"$TEST_ROOT/font-bin/fc-scan" <<'FONT_SCAN'
#!/usr/bin/env bash
file="${@: -1}"
family="Noto Sans CJK SC"
[[ "$file" == *NotoSerif* ]] && family="Noto Serif CJK SC"
weight="Regular"
[[ "$file" == *-Bold.ttc ]] && weight="Bold"
printf '%s|%s\n' "$family" "$weight"
FONT_SCAN
cat >"$TEST_ROOT/font-bin/fc-match" <<'FONT_MATCH'
#!/usr/bin/env bash
[[ "${TEST_FONT_SUBSTITUTE:-}" == yes ]] && { printf 'Arial'; exit 0; }
printf '%s' "${@: -1}" | sed 's/:style=.*//'
FONT_MATCH
chmod +x "$TEST_ROOT/font-bin/fc-scan" "$TEST_ROOT/font-bin/fc-match"
export PATH="$TEST_ROOT/font-bin:$PATH"
export TK_SUBTITLE_FONTS_DIR="$TEST_ROOT/fonts"

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

assert_rejects_font_substitution() {
  local release_dir="$TEST_ROOT/complete"
  if TEST_FONT_SUBSTITUTE=yes \
    TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="$release_dir/download-python" \
    TK_ASR_PYTHON="$release_dir/asr-python" \
    TK_ASR_MODEL_CACHE_DIR="$release_dir/models/faster-whisper" \
    "$VALIDATOR" "$release_dir" >"$TEST_ROOT/substitute.log" 2>&1; then
    echo 'validator accepted a substituted subtitle font' >&2
    return 1
  fi
  grep -q 'Silent font substitution' "$TEST_ROOT/substitute.log"
}

assert_rejects_missing_bold_font() {
  local release_dir="$TEST_ROOT/complete"
  mv "$TEST_ROOT/fonts/NotoSerifCJK-Bold.ttc" "$TEST_ROOT/missing-bold.ttc"
  if TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="$release_dir/download-python" \
    TK_ASR_PYTHON="$release_dir/asr-python" \
    TK_ASR_MODEL_CACHE_DIR="$release_dir/models/faster-whisper" \
    "$VALIDATOR" "$release_dir" >"$TEST_ROOT/missing-font.log" 2>&1; then
    echo 'validator accepted missing bold subtitle font' >&2
    return 1
  fi
  grep -q 'Missing required file or directory' "$TEST_ROOT/missing-font.log"
  mv "$TEST_ROOT/missing-bold.ttc" "$TEST_ROOT/fonts/NotoSerifCJK-Bold.ttc"
}

assert_fails_for_missing_tools
assert_accepts_complete_release
assert_rejects_font_substitution
assert_rejects_missing_bold_font
assert_fails_for_tampered_manifest
echo 'validate-release tests passed'
