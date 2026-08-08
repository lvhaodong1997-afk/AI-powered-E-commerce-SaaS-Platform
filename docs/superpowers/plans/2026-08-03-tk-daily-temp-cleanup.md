# TK Daily Temp Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean safe TK server temporary files automatically every day without touching release or rollback assets.

**Architecture:** A single shell cleanup script runs from cron on the server. It only targets allowlisted temporary directories under `/data/Tk`, applies age-based retention, logs what it removes, and refuses to run outside the project root.

**Tech Stack:** Bash, Linux cron, standard GNU coreutils/find.

## Global Constraints

- Only clean allowlisted temporary paths under `/data/Tk`.
- Never touch `/data/Tk/current`, `/data/Tk/releases`, or rollback backups unless explicitly added later.
- Use age-based cleanup with conservative retention windows.

---

### Task 1: Add the cleanup script

**Files:**
- Create: `deploy/scripts/cleanup-tk-temp.sh`

**Interfaces:**
- Consumes: `/data/Tk/shared/tmp/tk-generation`, `/data/Tk/voice-preview-cache`, `/data/Tk/deploy-incoming`, `/data/Tk/deploy-work`, `/data/Tk/uploads`
- Produces: logged deletions of expired files and empty directories

- [ ] **Step 1: Write the script**

```bash
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
      | xargs -0r stat -c '%s' \
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
  shift 2
  local label="$1"
  shift
  local resolved
  resolved="$(ensure_safe_dir "$dir")"
  [ -d "$resolved" ] || return 0

  local patterns=("$@")
  local expr=()
  for pattern in "${patterns[@]}"; do
    expr+=( -o -name "$pattern" )
  done
  expr=( "${expr[@]:1}" )

  local expired_count expired_bytes
  read -r expired_count expired_bytes < <(
    find "$resolved" -xdev -mindepth 1 -type f -mtime +"$keep_days" \( "${expr[@]}" \) -print0 \
      | xargs -0r stat -c '%s' \
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
```

- [ ] **Step 2: Run a dry run**

Run: `DRY_RUN=1 bash deploy/scripts/cleanup-tk-temp.sh`
Expected: prints only expired files from allowlisted dirs; deletes nothing.

- [ ] **Step 3: Run the real cleanup**

Run: `bash deploy/scripts/cleanup-tk-temp.sh`
Expected: expired files removed, empty dirs pruned, log written to `/data/Tk/shared/logs/tk-cleanup.log`.

### Task 2: Install the daily cron job

**Files:**
- Modify: server crontab only

**Interfaces:**
- Consumes: `/data/Tk/shared/scripts/tk-cleanup.sh`
- Produces: a daily 03:00 cleanup run

- [ ] **Step 1: Copy script to server**
- [ ] **Step 2: Install cron entry**

```cron
0 3 * * * /data/Tk/shared/scripts/tk-cleanup.sh >> /data/Tk/shared/logs/tk-cleanup.cron.log 2>&1
```

- [ ] **Step 3: Verify cron and logs**

Run: `crontab -l | grep tk-cleanup` and `tail -n 20 /data/Tk/shared/logs/tk-cleanup.log`
Expected: one cron entry and a fresh log line from the dry run or first real run.

