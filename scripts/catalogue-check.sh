#!/usr/bin/env bash
# scripts/catalogue-check.sh - the data half of the gate, in one command.
#
# Four checks, ordered so a failure reads as a cause rather than a stack trace:
#   1. node tests            packages/*/…test.mjs - the generators themselves (schema, parity, eval set).
#                            They are addressed by glob, not by directory, because a bare `node --test`
#                            walks the whole tree and picks up the JavaScript inside Gradle's HTML test
#                            reports as test files - two guaranteed failures on every run, discovered by
#                            running this script rather than by reading the docs.
#   2. validate:catalogue    the no-fabrication rule: every citizen-facing fact carries a source URL and
#                            a verification date. It fails while the YAML holds VERIFY_OFFICIAL, and that
#                            is the intended behaviour, not a broken script.
#   3. catalogue:build       YAML -> android/core/src/main/assets/catalogue.json, the bytes the APK ships.
#   4. parity + eval set     HI/EN key equality and the compiled eval set, both written into the repo.
#   5. asset freshness       in a git checkout with history, the committed asset must equal the one just
#                            generated. A reviewer has to be able to read exactly what the shipped APK
#                            contains (docs/PROJECT_STRUCTURE.md, "Generated assets: committed"), so a
#                            dirty asset is a build failure and not a "remember to commit" hint.
#
# Usage:
#   scripts/catalogue-check.sh                    strict - fails on any VERIFY_OFFICIAL field
#   scripts/catalogue-check.sh --allow-unverified  advisory - builds the dev asset and keeps going
#
# The second form is what CI and the Android build use today: the app is only able to refuse until the
# income-certificate fields are verified (Phase 1.4), and that is a data duty, not a code failure.
set -euo pipefail
cd "$(dirname "$0")/.."

ALLOW_UNVERIFIED=0
for arg in "$@"; do
    case "$arg" in
        --allow-unverified) ALLOW_UNVERIFIED=1 ;;
        -h | --help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "usage: $0 [--allow-unverified]" >&2; exit 64 ;;
    esac
done

echo "==> node tests"
npm test

if [ "$ALLOW_UNVERIFIED" = "1" ]; then
    echo "==> validate:catalogue (advisory)"
    # "Advisory" means "a placeholder is a data duty, not a build failure" - it does not mean anything
    # that happens is fine. A validator that crashes and a validator that fails on placeholders both
    # exit 1, and a corrupt schema.json once hid behind this line for a whole run. Its report banner
    # is what tells the two apart.
    if ! report=$(npm run validate:catalogue 2>&1); then
        printf '%s\n' "$report"
        if printf '%s' "$report" | grep -q "Seva-Marg catalogue validator"; then
            echo "    WARNING: fields still hold VERIFY_OFFICIAL - the app refuses instead of answering."
        else
            echo "    the validator did not produce a report - that is a code failure, not a data gap." >&2
            exit 1
        fi
    fi
    echo "==> catalogue:build:dev (provisional asset)"
    npm run catalogue:build:dev
else
    echo "==> validate:catalogue (strict)"
    npm run validate:catalogue
    echo "==> catalogue:build"
    npm run catalogue:build
fi

echo "==> i18n parity, then the eval set"
npm run i18n:parity
npm run evals:build

echo "==> generated assets"
if git rev-parse --verify HEAD >/dev/null 2>&1; then
    git diff --exit-code -- android/core/src/main/assets evals/dist && echo "    committed bytes match the generators"
else
    # No repo, or no commit yet: the check needs history, and pretending it passed would be worse than
    # saying it was skipped. It becomes a real gate the moment the repository exists.
    echo "    skipped the committed-asset diff - this checkout has no git history yet"
fi

echo "==> catalogue-check: DONE"
