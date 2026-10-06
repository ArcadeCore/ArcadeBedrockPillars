#!/usr/bin/env bash
# Real smoke test for ArcadeBedrockPillars: boots Paper in tmux, asserts
# ArcadeCore + the addon enable cleanly, exercises the /bp console command,
# stops. Usage: ./gradlew smokeTest (or bash scripts/smoke-test.sh)
#
# The server is launched with plain `java -jar` (no nested Gradle): a nested
# build deadlocks on the outer build's project locks, so the task graph never
# resolves. run-paper must have bootstrapped Paper once beforehand via
# `./gradlew runServer` (or scripts/prepare-run.sh + one interactive boot);
# the Paper jar and libraries/ then live under run/.
set -uo pipefail

SESSION="arcade-bp-smoke"
ROOT="$(dirname "$(readlink -f "$0")")/.."
RUN_DIR="$ROOT/run"
LOG_FILE="${SMOKE_LOG:-/tmp/arcade-bp-smoke.log}"
TIMEOUT="${SMOKE_TIMEOUT:-420}"
rm -f "$LOG_FILE"

echo "[smoke] root=$ROOT"
bash "$ROOT/scripts/prepare-run.sh"

PAPER_JAR="$(ls "$RUN_DIR"/versions/*/paper-*.jar 2>/dev/null | head -n 1 || true)"
if [ -z "$PAPER_JAR" ]; then
  echo "[smoke] FAIL: no Paper jar under $RUN_DIR/versions."
  echo "[smoke] Bootstrap once with: ./gradlew runServer (Ctrl-C after 'Done'), then re-run."
  exit 1
fi
echo "[smoke] paper=$PAPER_JAR"

# run-paper only writes eula.txt when missing; pre-seed acceptance so the
# first boot doesn't exit with "You need to agree to the EULA".
echo "eula=true" > "$RUN_DIR/eula.txt"

tmux kill-session -t "$SESSION" 2>/dev/null || true
# Fail fast instead of OOMing the box: a Paper server needs ~1G free.
AVAILABLE_MB="$(free -m | awk '/^Mem:/ {print $7}')"
if [ "${AVAILABLE_MB:-0}" -lt 1024 ]; then
  echo "[smoke] FAIL: only ${AVAILABLE_MB}MB RAM available, need >= 1024MB. Free memory and re-run." >&2
  exit 1
fi
# 1G cap, matching runServer in build.gradle.kts (see memory note there).
tmux new-session -d -s "$SESSION" -x 200 -y 50 -c "$RUN_DIR" \
  "java -Xms512M -Xmx1G -jar '$PAPER_JAR' nogui"

cleanup() {
  tmux kill-session -t "$SESSION" 2>/dev/null || true
}
trap cleanup EXIT

# capture <dest>: snapshot pane to dest only when non-empty, so a dead
# session never wipes a good log with an empty capture.
capture() {
  local tmp="$1.tmp"
  if tmux capture-pane -p -t "$SESSION" -S -5000 > "$tmp" 2>/dev/null && [ -s "$tmp" ]; then
    mv "$tmp" "$1"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

echo "[smoke] waiting up to ${TIMEOUT}s for server startup..."
elapsed=0
started=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  if capture "$LOG_FILE" && grep -qE "Done \([0-9.]+s\)" "$LOG_FILE"; then
    echo "[smoke] startup marker found after ${elapsed}s"
    started=1
    break
  fi
  if [ -f "$LOG_FILE" ] && grep -qE "You need to agree to the EULA|FAILED TO BIND TO PORT|OutOfMemoryError" "$LOG_FILE"; then
    echo "[smoke] fatal startup error:"
    tail -n 30 "$LOG_FILE"
    exit 1
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done

capture "$LOG_FILE" || true
if [ "$started" -ne 1 ]; then
  echo "[smoke] FAIL: server did not finish startup within ${TIMEOUT}s"
  tail -n 80 "$LOG_FILE" 2>/dev/null || echo "(no log captured)"
  exit 1
fi

fail=0
check_present() {
  if grep -qE "$1" "$LOG_FILE"; then
    echo "[smoke] ok: found '$1'"
  else
    echo "[smoke] FAIL: missing '$1'"
    fail=1
  fi
}
check_absent() {
  if grep -qE "$1" "$LOG_FILE"; then
    echo "[smoke] FAIL: found '$1'"
    fail=1
  else
    echo "[smoke] ok: no '$1'"
  fi
}

check_present "Enabling ArcadeCore"
check_present "Enabling ArcadeBedrockPillars"
check_absent "Failed to connect to database"
check_absent "ERROR.*ArcadeBedrockPillars|ArcadeBedrockPillars.*ERROR"
check_absent "Exception.*ArcadeBedrockPillars|ArcadeBedrockPillars.*Exception"

# Exercise the console-safe /bp info command through the running server.
tmux send-keys -t "$SESSION" "bp" Enter
sleep 5
capture "$LOG_FILE" || true
check_present "Bedrock Pillars"

echo "[smoke] stopping server..."
tmux send-keys -t "$SESSION" "stop" Enter

for _ in $(seq 1 18); do
  sleep 5
  capture "$LOG_FILE" || true
  if ! tmux has-session -t "$SESSION" 2>/dev/null; then
    break
  fi
done
trap - EXIT
tmux kill-session -t "$SESSION" 2>/dev/null || true

if [ "$fail" -ne 0 ]; then
  echo "[smoke] FAIL: see $LOG_FILE"
  tail -n 60 "$LOG_FILE"
  exit 1
fi

echo "[smoke] PASS: ArcadeCore + ArcadeBedrockPillars enabled, /bp console command + stop OK"
echo "[smoke] log: $LOG_FILE"
