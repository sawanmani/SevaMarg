#!/usr/bin/env bash
# scripts/core-test.sh - the JVM half of the gate: :core's unit tests and :bench's report tests.
#
# Both modules are plain Kotlin/JVM (no Android, no emulator), which is the whole reason `:core` exists
# as its own module: the answer path - normaliser, router, composer, refusal ladder - is testable in
# seconds on a laptop, and CI runs it without ever booting a phone.
#
# Windows: run android\gradlew.bat :core:test :bench:test directly, or this script from Git Bash.
set -euo pipefail
cd "$(dirname "$0")/../android"

# The three forms, in the order they actually work: an executable wrapper, a wrapper without the exec bit
# (normal for a Windows checkout, where Git Bash cannot see a Unix mode), and a system gradle.
run_gradle() {
    if [ -x ./gradlew ]; then
        ./gradlew "$@"
    elif [ -f ./gradlew ]; then
        sh ./gradlew "$@"
    elif command -v gradle >/dev/null 2>&1; then
        echo "    note: using the system gradle - the wrapper pins 9.3.1 (gradle/wrapper)"
        gradle "$@"
    else
        echo "no gradlew and no gradle on PATH" >&2
        return 127
    fi
}

run_gradle --console=plain :core:test :bench:test "$@"
