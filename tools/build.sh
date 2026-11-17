#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.tools/gradle-cache}"
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home "$PWD/.tools/jdk25/Contents/Home"; do
        if [[ -x "$candidate/bin/javac" ]]; then export JAVA_HOME="$candidate"; break; fi
    done
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo 'Set JAVA_HOME to a Java 25 JDK (the FTB runtime is a JRE without javac).' >&2
    exit 1
fi
if ! "$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -Eq 'version "25[.\"]'; then
    echo 'This project requires a Java 25 JDK.' >&2
    exit 1
fi
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi
version=9.2.1
mkdir -p .tools
archive=".tools/gradle-$version-bin.zip"
if [[ ! -x ".tools/gradle-$version/bin/gradle" ]]; then
    curl --fail --location --retry 2 --output "$archive" "https://services.gradle.org/distributions/gradle-$version-bin.zip"
    curl --fail --location --retry 2 --output "$archive.sha256" "https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256"
    expected=$(cat "$archive.sha256")
    actual=$(shasum -a 256 "$archive" | cut -d ' ' -f 1)
    [[ "$actual" == "$expected" ]] || { echo 'Gradle checksum mismatch' >&2; exit 1; }
    unzip -q "$archive" -d .tools
fi
exec ".tools/gradle-$version/bin/gradle" "$@"
