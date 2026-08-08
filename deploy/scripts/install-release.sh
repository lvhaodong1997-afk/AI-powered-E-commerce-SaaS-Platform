#!/usr/bin/env bash
set -euo pipefail

RELEASE_DIR="${1:-$(pwd)}"
APP_DIR="${APP_DIR:-/opt/tk-auto-mix}"

sudo mkdir -p "$APP_DIR"/{backend,frontend,worker,sql,logs,nginx,systemd}
sudo rsync -a --delete "$RELEASE_DIR/backend/" "$APP_DIR/backend/"
sudo rsync -a --delete "$RELEASE_DIR/frontend/" "$APP_DIR/frontend/"
sudo rsync -a --delete "$RELEASE_DIR/worker/" "$APP_DIR/worker/"
sudo rsync -a "$RELEASE_DIR/sql/" "$APP_DIR/sql/"
sudo rsync -a "$RELEASE_DIR/nginx/" "$APP_DIR/nginx/"
sudo rsync -a "$RELEASE_DIR/systemd/" "$APP_DIR/systemd/"

cd "$APP_DIR/worker"
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

echo "Release files installed to $APP_DIR."
echo "Next: edit systemd/tk-yudao.service credentials, copy nginx/systemd templates, import SQL, then start services."
