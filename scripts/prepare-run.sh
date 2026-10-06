#!/usr/bin/env bash
# Copies the addon jar plus ArcadeCore into run/plugins for runServer/smokeTest.
set -uo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
CORE_JAR="/home/mallusrgreat/Projects/ArcadeCore/ArcadePlugin/build/libs/ArcadePlugin-1.0.0-all.jar"

if [ ! -f "$CORE_JAR" ]; then
  echo "[prepare-run] FAIL: missing $CORE_JAR (build ArcadeCore first)" >&2
  exit 1
fi

for jar in "$CORE_JAR" "$ADDON_JAR"; do
  if [ ! -f "$jar" ]; then
    echo "[prepare-run] FAIL: missing $jar (build it first)" >&2
    exit 1
  fi
done

mkdir -p "$ROOT/run/plugins"
# Only ArcadeCore: run-paper installs this project's own jar into
# run/plugins itself, so copying it here would load the plugin twice.
cp -f "$CORE_JAR" "$ROOT/run/plugins/"
echo "[prepare-run] plugins:"
ls -la "$ROOT/run/plugins/"
