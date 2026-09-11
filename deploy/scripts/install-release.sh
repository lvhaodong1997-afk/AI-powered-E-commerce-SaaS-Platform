#!/usr/bin/env bash
set -euo pipefail

RELEASE_DIR="${1:-$(pwd)}"
APP_DIR="${APP_DIR:-/opt/tk-auto-mix}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
VALIDATOR="${RELEASE_VALIDATOR:-$SCRIPT_DIR/validate-release.sh}"
RUNTIME_DIR="${TK_RELEASE_RUNTIME_ROOT:-$APP_DIR/shared}"

[[ -f "$VALIDATOR" ]] || {
  echo "Release validator is missing: $VALIDATOR" >&2
  exit 1
}

export TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="${TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON:-$RUNTIME_DIR/reference-video-download/.venv/bin/python}"
export TK_ASR_PYTHON="${TK_ASR_PYTHON:-$RUNTIME_DIR/asr/.venv/bin/python}"
export TK_ASR_MODEL_CACHE_DIR="${TK_ASR_MODEL_CACHE_DIR:-$RUNTIME_DIR/models/faster-whisper}"

bash "$VALIDATOR" --write-manifest "$RELEASE_DIR"

sudo mkdir -p "$APP_DIR"/{backend,frontend,worker,tools,sql,logs,nginx,systemd}
sudo rsync -a --delete "$RELEASE_DIR/backend/" "$APP_DIR/backend/"
sudo rsync -a --delete "$RELEASE_DIR/frontend/" "$APP_DIR/frontend/"
sudo rsync -a --delete "$RELEASE_DIR/worker/" "$APP_DIR/worker/"
sudo rsync -a --delete "$RELEASE_DIR/tools/" "$APP_DIR/tools/"
sudo rsync -a "$RELEASE_DIR/sql/" "$APP_DIR/sql/"
sudo rsync -a "$RELEASE_DIR/nginx/" "$APP_DIR/nginx/"
sudo rsync -a "$RELEASE_DIR/systemd/" "$APP_DIR/systemd/"
sudo install -m 0644 "$RELEASE_DIR/manifest.sha256" "$APP_DIR/manifest.sha256"
sudo mkdir -p "$APP_DIR/models" "$APP_DIR/tools/reference-video-download"
sudo ln -sfn "$RUNTIME_DIR/reference-video-download/.venv" "$APP_DIR/tools/reference-video-download/.venv"
sudo ln -sfn "$RUNTIME_DIR/asr/.venv" "$APP_DIR/.venv-asr"
sudo ln -sfn "$RUNTIME_DIR/models/faster-whisper" "$APP_DIR/models/faster-whisper"

cd "$APP_DIR/worker"
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

bash "$VALIDATOR" "$APP_DIR"

echo "Release files installed to $APP_DIR."
echo "Next: edit systemd/tk-yudao.service credentials, copy nginx/systemd templates, import SQL, then start services."
