#!/usr/bin/env bash
# Build script — compiles sources and packages the jar
set -e
BASE="$(cd "$(dirname "$0")" && pwd)"
BASE_WIN="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
LIBS="$BASE_WIN/libs"
OUT="$BASE_WIN/build/classes"
JAR="$BASE_WIN/build/TheBridge-1.3.5.jar"

rm -rf "$BASE/build/classes" && mkdir -p "$BASE/build/classes"
find "$BASE/src/main/java" -name "*.java" \
    | sed 's|^/\([a-zA-Z]\)/|\1:/|' \
    > "$BASE/build/sources.txt"

# Use ; on Windows (Git Bash/MSYS), : on macOS/Linux.
case "$(uname -s 2>/dev/null)" in
    MINGW*|CYGWIN*|MSYS*) SEP=";" ;;
    *) SEP=":" ;;
esac

CP="${LIBS}/paper-api.jar${SEP}${LIBS}/fawe-bukkit.jar${SEP}${LIBS}/adventure-api.jar${SEP}${LIBS}/adventure-key.jar${SEP}${LIBS}/jetbrains-annotations.jar${SEP}${LIBS}/guava.jar${SEP}${LIBS}/examination-api.jar${SEP}${LIBS}/bungeecord-chat.jar"

javac --release 21 -cp "$CP" -d "$OUT" @"$BASE_WIN/build/sources.txt"
cp "$BASE/src/main/resources/plugin.yml" "$BASE/build/classes/"
cp "$BASE/src/main/resources/config.yml" "$BASE/build/classes/"
cd "$BASE/build/classes" && jar cf "$JAR" .
echo "Built: $JAR ($(du -sh "$JAR" | cut -f1))"
