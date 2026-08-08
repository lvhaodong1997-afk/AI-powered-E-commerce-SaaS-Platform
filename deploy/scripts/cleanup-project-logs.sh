#!/usr/bin/env bash
set -euo pipefail

KEEP_DAYS="${1:-3}"
PROJECT_ROOT="/data/Tk"
LOG_DIRS=(
  "/data/Tk/shared/logs"
  "/data/Tk/current/logs"
)

case "$KEEP_DAYS" in
  ''|*[!0-9]*)
    echo "KEEP_DAYS must be a positive integer" >&2
    exit 2
    ;;
esac

if [ "$KEEP_DAYS" -lt 1 ]; then
  echo "KEEP_DAYS must be >= 1" >&2
  exit 2
fi

for dir in "${LOG_DIRS[@]}"; do
  [ -d "$dir" ] || continue
  resolved="$(readlink -f "$dir")"
  case "$resolved" in
    "$PROJECT_ROOT"/*/logs|"$PROJECT_ROOT"/*/logs/*) ;;
    *)
      echo "Refusing to clean outside project logs: $resolved" >&2
      exit 3
      ;;
  esac

  find "$resolved" -xdev -type f \
    \( -name '*.log' -o -name '*.log.*' -o -name '*.out' -o -name '*.out.*' -o -name '*.err' -o -name '*.err.*' -o -name '*.gz' \) \
    -mtime +"$((KEEP_DAYS - 1))" -print -delete
done
