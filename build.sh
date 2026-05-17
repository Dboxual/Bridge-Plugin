#!/usr/bin/env bash
# Build script — compiles sources and packages the jar
set -e
BASE="$(cd "$(dirname "$0")" && pwd)"
BASE_WIN="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
LIBS="$BASE_WIN/libs"
OUT="$BASE_WIN/build/classes"
JAR="$BASE_WIN/build/TheBridge-1.1.1.jar"

rm -rf "$BASE/build/classes" && mkdir -p "$BASE/build/classes"
find "$BASE/src/main/java" -name "*.java" \
    | sed 's|^/\([a-zA-Z]\)/|\1:/|' \
    > "$BASE/build/sources.txt"

CP="$LIBS/paper-api.jar;$LIBS/fawe-bukkit.jar;$LIBS/adventure-api.jar;$LIBS/adventure-key.jar;$LIBS/jetbrains-annotations.jar;$LIBS/guava.jar;$LIBS/examination-api.jar;$LIBS/bungeecord-chat.jar"

javac --release 21 -cp "$CP" -d "$OUT" @"$BASE_WIN/build/sources.txt"
cp "$BASE/src/main/resources/plugin.yml" "$BASE/build/classes/"
cp "$BASE/src/main/resources/config.yml" "$BASE/build/classes/"
cd "$BASE/build/classes" && jar cf "$JAR" .
echo "Built: $JAR ($(du -sh "$JAR" | cut -f1))"
