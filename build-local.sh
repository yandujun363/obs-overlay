#!/usr/bin/env bash
# Local build for the 26.3 port.
#
# Minecraft 26.3 ships an unobfuscated client jar and Fabric has no intermediary/refmap layer for
# it any more, so the mod can be compiled straight against the extracted client classes. That makes
# Gradle/Loom optional and keeps the toolchain tiny; see AGENTS.md for why this is the verified
# path in this branch.
#
# Usage:
#   tools/fetch-deps.sh <path-to-26.3-instance>   # once, populates libs/ (gitignored)
#   ./build-local.sh                             # produces build/obs_overlay-<version>.jar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
CLIENT="${CLIENT_JAR_DIR:-$ROOT/libs/client-26.3}"
# single source of truth for the version
VERSION=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['version'])" \
    "$ROOT/src/main/resources/fabric.mod.json")
OUT="$ROOT/build"

if [ ! -d "$CLIENT/net/minecraft" ]; then
    echo "error: 26.3 client classes not found at $CLIENT" >&2
    echo "       run tools/fetch-deps.sh <instance dir> or set CLIENT_JAR_DIR" >&2
    exit 1
fi

CP="$CLIENT"
for jar in "$ROOT"/libs/*.jar; do
    CP="$CP:$jar"
done

rm -rf "$OUT"
mkdir -p "$OUT/classes"
find "$ROOT/src/main/java" -name '*.java' > "$OUT/sources.txt"

echo "== compiling $(wc -l < "$OUT/sources.txt") sources"
javac --release 25 -encoding UTF-8 -proc:none -Xlint:-options \
    -d "$OUT/classes" -cp "$CP" "@$OUT/sources.txt"

echo "== copying resources"
cp -r "$ROOT/src/main/resources/." "$OUT/classes/"

echo "== packaging"
( cd "$OUT/classes" && jar --create --file "$OUT/obs_overlay-$VERSION.jar" . )
ls -la "$OUT/obs_overlay-$VERSION.jar"
