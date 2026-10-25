#!/usr/bin/env bash
# Populates libs/ (gitignored) from a local Minecraft 26.3 installation.
#
#   tools/fetch-deps.sh "/path/to/.minecraft/versions/<26.3 version dir>"
#
# What it does:
#   * extracts only the game classes (net/**, com/**) of the 26.3 client jar into libs/client-26.3
#   * copies the compile time dependencies out of <instance>/../../libraries and <instance>/mods
#
# Nothing here is committed; libs/ is in .gitignore. Re-run it whenever the instance is updated.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INSTANCE="${1:-${MC_INSTANCE:-}}"
if [ -z "$INSTANCE" ] || [ ! -d "$INSTANCE" ]; then
    echo "usage: tools/fetch-deps.sh <path to a 26.3 version folder>" >&2
    echo "       e.g. \"/path/to/.minecraft/versions/<26.3 version dir>\"" >&2
    exit 1
fi
LIBRARIES="${MC_LIBRARIES:-$(cd "$INSTANCE/../.." && pwd)/libraries}"
MODS="$INSTANCE/mods"
LIBS="$ROOT/libs"

mkdir -p "$LIBS/client-26.3"
# start from a clean slate so re-runs are deterministic
rm -f "$LIBS"/*.jar

echo "== extracting game classes"
CLIENT_JAR=$(find "$INSTANCE" -maxdepth 1 -name '*.jar' | head -1)
if [ -z "$CLIENT_JAR" ]; then
    echo "error: no client jar in $INSTANCE" >&2
    exit 1
fi
unzip -q -o "$CLIENT_JAR" 'net/*' 'com/*' -d "$LIBS/client-26.3"

# <instance>/<instance>.json is the version manifest; debug-profile.json etc. are not
VERSION_JSON="$INSTANCE/$(basename "$INSTANCE").json"
if [ ! -f "$VERSION_JSON" ]; then
    VERSION_JSON=$(grep -l 'clientVersion' "$INSTANCE"/*.json 2>/dev/null | head -1)
fi
# Pin the loader/mixin versions that this instance actually runs, so compiling matches runtime.
pinned() {
    [ -n "$VERSION_JSON" ] || return 1
    python3 - "$VERSION_JSON" "$1" <<'EOF'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
group = sys.argv[2]
for library in data.get("libraries", []):
    name = library.get("name", "")
    if name.startswith(group + ":"):
        print(name.split(":")[2])
        break
EOF
}

copy_pinned() { # $1 = maven group path, $2 = group:artifact, $3 = fallback pattern
    local base="$1" coordinate="$2" pattern="$3" version jar
    version=$(pinned "$coordinate" || true)
    if [ -n "${version:-}" ]; then
        jar=$(find "$base/$version" -maxdepth 1 -name "$pattern" ! -name '*natives*' 2>/dev/null | head -1)
        if [ -n "$jar" ]; then
            cp "$jar" "$LIBS/"
            echo "   + $(basename "$jar") (pinned by version json)"
            return
        fi
    fi
    copy_newest "$base" "$pattern"
}

echo "== copying libraries from $LIBRARIES"
# newest matching jar under a maven-style directory
copy_newest() {
    local base="$1" pattern="$2" jar
    jar=$(find "$base" -maxdepth 3 -name "$pattern" ! -name '*natives*' 2>/dev/null | sort -V | tail -1)
    if [ -n "$jar" ]; then
        cp "$jar" "$LIBS/"
        echo "   + $(basename "$jar")"
    else
        echo "   ! missing $pattern under $base" >&2
    fi
}

copy_pinned "$LIBRARIES/net/fabricmc/fabric-loader" "net.fabricmc:fabric-loader" 'fabric-loader-*.jar'
copy_pinned "$LIBRARIES/net/fabricmc/sponge-mixin" "net.fabricmc:sponge-mixin" 'sponge-mixin-*.jar'
copy_pinned "$LIBRARIES/net/java/dev/jna/jna" "net.java.dev.jna:jna" 'jna-*.jar'
copy_pinned "$LIBRARIES/org/lwjgl/lwjgl" "org.lwjgl:lwjgl" 'lwjgl-[0-9]*.jar'
copy_pinned "$LIBRARIES/org/lwjgl/lwjgl-opengl" "org.lwjgl:lwjgl-opengl" 'lwjgl-opengl-*.jar'
copy_pinned "$LIBRARIES/org/joml/joml" "org.joml:joml" 'joml-*.jar'
copy_pinned "$LIBRARIES/org/slf4j/slf4j-api" "org.slf4j:slf4j-api" 'slf4j-api-*.jar'
copy_pinned "$LIBRARIES/com/google/code/gson/gson" "com.google.code.gson:gson" 'gson-*.jar'
copy_pinned "$LIBRARIES/com/mojang/datafixerupper" "com.mojang:datafixerupper" 'datafixerupper-*.jar'
copy_pinned "$LIBRARIES/com/mojang/brigadier" "com.mojang:brigadier" 'brigadier-*.jar'

echo "== MixinExtras (bundled inside the loader jar)"
LOADER=$(find "$LIBS" -maxdepth 1 -name 'fabric-loader-*.jar' | head -1)
if [ -n "$LOADER" ]; then
    ( cd "$LIBS" && unzip -o -q "$LOADER" 'META-INF/jars/mixinextras-fabric-*.jar' \
        && mv META-INF/jars/mixinextras-fabric-*.jar ./mixinextras.jar && rm -rf META-INF )
    echo "   + mixinextras.jar"
fi

echo "== copying mod dependencies from $MODS"
for pattern in 'cloth-config-fabric-*.jar' '*modmenu-*.jar'; do
    jar=$(find "$MODS" -maxdepth 1 -name "$pattern" 2>/dev/null | sort -V | tail -1)
    if [ -n "$jar" ]; then
        cp "$jar" "$LIBS/"
        echo "   + $(basename "$jar")"
    else
        echo "   ! missing $pattern in $MODS" >&2
    fi
done

echo
echo "done. libs/ contains:"
ls -1 "$LIBS" | sed 's/^/   /'
echo
echo "next: ./build-local.sh && python3 verify-mixins.py"
