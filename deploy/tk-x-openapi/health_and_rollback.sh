#!/bin/bash
# Shared health + conditional rollback contract for TK X OpenAPI releases.
# Success requires ALL of the following over a stability window, not merely systemd "active":
#   1. /data/Tk/current still points at this release (DST)
#   2. JAR sha256 matches
#   3. tcp 127.0.0.1:48080 is listening
#   4. systemctl is-active tk-yudao-prod == active
#   5. NRestarts is unchanged across the window (no crash loop)
#   6. local unauthenticated X OpenAPI returns OPEN_API_AUTH_HEADER_MISSING (typically HTTP 401)
#   7. local homepage http://127.0.0.1:18080/ returns HTTP 200
#   8. public homepage 200 and public unauthenticated X API returns the same auth-missing body
# Curl output files are truncated before each request so a stale body cannot satisfy the check.
# On failure, if current is still this DST, roll back to SRC and restart immediately while the
# same deploy lock is held. Do not release the lock, declare success, and wait unlocked.
#
# Required env: SRC DST JAR_SHA LOCK (optional PUBLIC_BASE default https://clipforgestudio.fnn.net.cn)
# This file is sourced by the release driver; it does not take the lock itself.

health_log() { echo "$(date '+%F %T %z') $*"; }

current_resolved() { readlink -f /data/Tk/current; }

_truncate_curl() {
  local f="$1" url="$2"
  : > "$f"
  curl -sS -m 10 -o "$f" -w '%{http_code}' "$url" || echo FAIL
}

_listening_48080() {
  ss -lnt | awk '$4 ~ /(127.0.0.1|0.0.0.0|\[::\]|\[::1\]):48080$/ {found=1} END{exit !found}'
}

_auth_missing() {
  local f="$1"
  [ -s "$f" ] && grep -q 'OPEN_API_AUTH_HEADER_MISSING' "$f"
}

sample_health() {
  local fe_file=/tmp/tk_health_fe x_file=/tmp/tk_health_x
  local pub_fe_file=/tmp/tk_health_pub_fe pub_x_file=/tmp/tk_health_pub_x
  local public_base="${PUBLIC_BASE:-https://clipforgestudio.fnn.net.cn}"

  local active listen jar_ok cur nrestarts fe_code x_code pub_fe pub_x
  active=$(systemctl is-active tk-yudao-prod 2>/dev/null || echo inactive)
  nrestarts=$(systemctl show tk-yudao-prod -p NRestarts --value 2>/dev/null || echo '?')
  cur=$(current_resolved || true)
  jar_ok=no
  if [ -n "${JAR_SHA:-}" ] && [ -f /data/Tk/current/app/yudao-server.jar ] \
     && echo "$JAR_SHA  /data/Tk/current/app/yudao-server.jar" | sha256sum -c --status; then
    jar_ok=yes
  fi
  listen=no
  if _listening_48080; then listen=yes; fi

  fe_code=$(_truncate_curl "$fe_file" http://127.0.0.1:18080/)
  x_code=FAIL
  x_ok=no
  if [ "$listen" = yes ]; then
    x_code=$(_truncate_curl "$x_file" http://127.0.0.1:48080/admin-api/tk/open/v1/x/connections)
    if _auth_missing "$x_file"; then x_ok=yes; fi
  fi

  pub_fe=$(_truncate_curl "$pub_fe_file" "${public_base}/")
  pub_x=$(_truncate_curl "$pub_x_file" "${public_base}/admin-api/tk/open/v1/x/connections")
  pub_x_ok=no
  if _auth_missing "$pub_x_file"; then pub_x_ok=yes; fi

  HEALTH_SAMPLE_ACTIVE=$active
  HEALTH_SAMPLE_NRESTARTS=$nrestarts
  HEALTH_SAMPLE_CUR=$cur
  HEALTH_SAMPLE_JAR_OK=$jar_ok
  HEALTH_SAMPLE_LISTEN=$listen
  HEALTH_SAMPLE_FE=$fe_code
  HEALTH_SAMPLE_X=$x_code
  HEALTH_SAMPLE_X_OK=$x_ok
  HEALTH_SAMPLE_PUB_FE=$pub_fe
  HEALTH_SAMPLE_PUB_X=$pub_x
  HEALTH_SAMPLE_PUB_X_OK=$pub_x_ok

  health_log "HEALTH_SAMPLE active=$active nrestarts=$nrestarts current_is_dst=$([ "$cur" = "$DST" ] && echo yes || echo no) jar_ok=$jar_ok listen48080=$listen fe=$fe_code x=$x_code x_ok=$x_ok pub_fe=$pub_fe pub_x=$pub_x pub_x_ok=$pub_x_ok"

  [ "$active" = active ] || return 1
  [ "$cur" = "$DST" ] || return 1
  [ "$jar_ok" = yes ] || return 1
  [ "$listen" = yes ] || return 1
  [ "$fe_code" = 200 ] || return 1
  [ "$x_ok" = yes ] || return 1
  [ "$pub_fe" = 200 ] || return 1
  [ "$pub_x_ok" = yes ] || return 1
  return 0
}

rollback_if_this_release() {
  local reason="${1:-unspecified}"
  health_log "ROLLBACK_BEGIN reason=$reason"
  local cur
  cur=$(current_resolved || true)
  if [ "$cur" != "$DST" ]; then
    health_log "ROLLBACK_REFUSED current=${cur:-none} is not this DST=$DST (will not overwrite a later or previous release)"
    return 1
  fi
  if ! sudo -n ln -sfn "$SRC" /data/Tk/current; then
    health_log "ROLLBACK_SYMLINK_FAIL"
    return 1
  fi
  health_log "ROLLBACK_SYMLINK=$(current_resolved)"
  if ! sudo -n systemctl restart tk-yudao-prod; then
    health_log "ROLLBACK_RESTART_FAIL"
    return 1
  fi
  local i fe_code x_code
  for i in $(seq 1 48); do
    if [ "$(systemctl is-active tk-yudao-prod 2>/dev/null || echo inactive)" = active ] \
       && _listening_48080; then
      : > /tmp/tk_rb_x
      x_code=$(curl -sS -m 10 -o /tmp/tk_rb_x -w '%{http_code}' http://127.0.0.1:48080/admin-api/tk/open/v1/x/connections || echo FAIL)
      : > /tmp/tk_rb_fe
      fe_code=$(curl -sS -m 10 -o /tmp/tk_rb_fe -w '%{http_code}' http://127.0.0.1:18080/ || echo FAIL)
      if _auth_missing /tmp/tk_rb_x && [ "$fe_code" = 200 ]; then
        health_log "ROLLBACK_HEALTH_OK x=$x_code fe=$fe_code"
        return 0
      fi
    fi
    sleep 5
  done
  health_log "ROLLBACK_HEALTH_UNCERTAIN"
  return 1
}

# Poll until one good sample, then require a stability window of STABLE_SECONDS
# with NRestarts unchanged and every sample still good.
wait_healthy_or_rollback() {
  local timeout="${HEALTH_TIMEOUT:-240}"
  local stable="${STABLE_SECONDS:-30}"
  local started now first_ok elapsed restarts0
  started=$(date +%s)
  first_ok=0
  restarts0=
  while true; do
    now=$(date +%s)
    elapsed=$(( now - started ))
    if [ "$elapsed" -ge "$timeout" ]; then
      health_log "HEALTH_FAIL timeout"
      rollback_if_this_release "health_fail_timeout"
      return 20
    fi
    if [ "$(systemctl is-active tk-yudao-prod 2>/dev/null || echo inactive)" = failed ]; then
      health_log "HEALTH_FAIL service_failed"
      rollback_if_this_release "health_fail_service_failed"
      return 20
    fi
    if sample_health; then
      if [ "$first_ok" -eq 0 ]; then
        first_ok=$now
        restarts0=$HEALTH_SAMPLE_NRESTARTS
        health_log "HEALTH_WINDOW_BEGIN nrestarts=$restarts0"
      else
        if [ "$HEALTH_SAMPLE_NRESTARTS" != "$restarts0" ]; then
          health_log "HEALTH_FAIL nrestarts changed $restarts0 -> $HEALTH_SAMPLE_NRESTARTS during stability window"
          first_ok=0
          # treat as crash loop; rollback
          rollback_if_this_release "health_fail_nrestarts_changed"
          return 20
        fi
        if [ $(( now - first_ok )) -ge "$stable" ]; then
          health_log "HEALTH_OK stable_for=$(( now - first_ok ))s nrestarts=$HEALTH_SAMPLE_NRESTARTS"
          return 0
        fi
      fi
    else
      first_ok=0
    fi
    sleep 5
  done
}