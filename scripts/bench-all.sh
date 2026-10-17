#!/usr/bin/env bash
# scripts/bench-all.sh - every judge-checkable number this repository can produce today, into results/.
#
# A number that is not reproducible is not a number (docs/PROJECT_STRUCTURE.md), so this script is the
# single entry point the deck and the synopsis quote: it regenerates the data assets, runs the eval set
# through :bench, and re-reads the JSON it just wrote to decide what to report.
#
# Reading the artifact instead of the process exit code is deliberate. Gradle turns any non-zero program
# exit into its own failure, so `:bench:run` cannot tell a caller *which* promise broke; `:bench:evalReport`
# writes the report and lets this script sort the two cases out from the data:
#   fabrications > 0 or answersWithoutCitations >0 or spoke the sentinel -> exit 1, an untruthful answer reached a citizen
#   provisional data / blocked answers / no helpline -> exit 2, the catalogue is not verified yet
#   anything else                                    -> exit 0
#
# Today exactly one benchmark exists - the 32-question eval set over :core. The plan's other five
# (step counts, ASR WER on the phone's own mic, end-to-end latency, the airplane-mode socket proof,
# accessibility) need tools/bench and a physical device; they join this script in their own phase.
# Stubbing them now would print zeros that read like measurements.
set -euo pipefail
cd "$(dirname "$0")/.."

bash scripts/catalogue-check.sh --allow-unverified

cd android
# Same three-form wrapper lookup as scripts/core-test.sh: executable wrapper, wrapper without the exec bit
# (a Windows checkout), then a system gradle.
run_gradle() {
    if [ -x ./gradlew ]; then
        ./gradlew "$@"
    elif [ -f ./gradlew ]; then
        sh ./gradlew "$@"
    elif command -v gradle >/dev/null 2>&1; then
        gradle "$@"
    else
        echo "no gradlew and no gradle on PATH" >&2
        return 127
    fi
}
run_gradle --console=plain :bench:evalReport
cd ..

echo "==> reading results/bench/latest.json"
# The interpreter is allowed to fail: its exit code is the answer this script exists to report.
set +e
node -e '
const r = require("./results/bench/latest.json");
console.log(`items ${r.total}, passed ${r.passed}`);
console.log(`intent accuracy ${(r.intentAccuracy * 100).toFixed(1)}%   refusal coverage ${(r.refusalCoverage * 100).toFixed(1)}%`);
console.log(`fabrications ${r.fabrications}   uncited answers shown ${r.answersWithoutCitations}   sentinel answers spoken ${r.answersSpeakingASentinel}`);
console.log(`answers blocked pending verification ${r.answersBlockedPendingVerification}   refusals with no verified helpline ${r.refusalsMissingHelpline}`);
if (r.fabrications > 0 || r.answersWithoutCitations > 0 || r.answersSpeakingASentinel > 0) {
  console.error("HONESTY FAILURE - see the failure lines above. This is the number that must be zero.");
  process.exit(1);
}
if (r.provisionalData || r.refusalsMissingHelpline > 0 || r.answersBlockedPendingVerification > 0) {
  console.error("DATA INCOMPLETE - nothing was invented; the refusal ladder stopped the answers because catalogue fields are still VERIFY_OFFICIAL.");
  process.exit(2);
}
console.log("honest and complete");
'
status=$?
set -e

echo "==> results: results/bench/latest.json"
exit $status
