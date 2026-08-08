#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/data/Tk"
LOCK_FILE="$PROJECT_ROOT/shared/tmp/tk-cleanup.lock"
LOG_DIR="$PROJECT_ROOT/shared/logs"
LOG_FILE="$LOG_DIR/tk-cleanup.log"

KEEP_GENERATION_DAYS="${KEEP_GENERATION_DAYS:-1}"
KEEP_PREVIEW_DAYS="${KEEP_PREVIEW_DAYS:-7}"
KEEP_DEPLOY_INCOMING_DAYS="${KEEP_DEPLOY_INCOMING_DAYS:-3}"
KEEP_DEPLOY_WORK_DAYS="${KEEP_DEPLOY_WORK_DAYS:-7}"
KEEP_UPLOADS_DAYS="${KEEP_UPLOADS_DAYS:-7}"
DRY_RUN="${DRY_RUN:-0}"

mkdir -p "$LOG_DIR" "$(dirname "$LOCK_FILE")"

exec 9>"$LOCK_FILE"
flock -n 9 || {
  echo "[$(date '+%F %T')] cleanup already running" >> "$LOG_FILE"
  exit 0
}

log() {
  echo "[$(date '+%F %T')] $*" | tee -a "$LOG_FILE"
}

ensure_safe_dir() {
  local dir="$1"
  local resolved
  resolved="$(readlink -f "$dir")"
  case "$resolved" in
    "$PROJECT_ROOT"/*) printf '%s\n' "$resolved" ;;
    *)
      echo "refusing to clean outside project root: $resolved" >&2
      exit 3
      ;;
  esac
}

count_candidates() {
  local dir="$1"
  shift
  find "$dir" -xdev -mindepth 1 -type f "$@" -print0 \
    | xargs -0r stat -c '%s' 2>/dev/null \
    | awk '{count+=1; bytes+=$1} END {printf "%s %s\n", count+0, bytes+0}'
}

clean_all_files() {
  local dir="$1"
  local keep_days="$2"
  local label="$3"
  local resolved
  resolved="$(ensure_safe_dir "$dir")"
  [ -d "$resolved" ] || return 0

  local expired_count expired_bytes
  read -r expired_count expired_bytes < <(
    find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" -print0 \
      | xargs -0r stat -c '%s' 2>/dev/null \
      | awk '{count+=1; bytes+=$1} END {printf "%s %s\n", count+0, bytes+0}'
  )

  log "$label expired_files=$expired_count expired_bytes=$expired_bytes path=$resolved dry_run=$DRY_RUN"
  if [ "$DRY_RUN" = "1" ]; then
    find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" -print
    return 0
  fi

  find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" -print -delete
  find "$resolved" -xdev -depth -mindepth 1 -type d -empty -delete
}

clean_selected_files() {
  local dir="$1"
  local keep_days="$2"
  local label="$3"
  shift 3
  local resolved
  resolved="$(ensure_safe_dir "$dir")"
  [ -d "$resolved" ] || return 0

  local expr=( )
  local pattern
  for pattern in "$@"; do
    expr+=( -o -name "$pattern" )
  done
  expr=( "${expr[@]:1}" )

  local expired_count expired_bytes
  read -r expired_count expired_bytes < <(
    find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" \( "${expr[@]}" \) -print0 \
      | xargs -0r stat -c '%s' 2>/dev/null \
      | awk '{count+=1; bytes+=$1} END {printf "%s %s\n", count+0, bytes+0}'
  )

  log "$label expired_files=$expired_count expired_bytes=$expired_bytes path=$resolved dry_run=$DRY_RUN"
  if [ "$DRY_RUN" = "1" ]; then
    find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" \( "${expr[@]}" \) -print
    return 0
  fi

  find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" \( "${expr[@]}" \) -print -delete
  find "$resolved" -xdev -depth -mindepth 1 -type d -empty -delete
}

clean_all_files "$PROJECT_ROOT/shared/tmp/tk-generation" "$KEEP_GENERATION_DAYS" "tk-generation"
clean_selected_files "$PROJECT_ROOT/voice-preview-cache" "$KEEP_PREVIEW_DAYS" "voice-preview-cache" '*.mp3'
clean_all_files "$PROJECT_ROOT/deploy-incoming" "$KEEP_DEPLOY_INCOMING_DAYS" "deploy-incoming"
clean_all_files "$PROJECT_ROOT/deploy-work" "$KEEP_DEPLOY_WORK_DAYS" "deploy-work"
clean_selected_files "$PROJECT_ROOT/uploads" "$KEEP_UPLOADS_DAYS" "uploads" '*.tar.gz' '*.tgz' '*.zip' '*.jar'
