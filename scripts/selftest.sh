#!/bin/sh
# Runs the self-test harness on one test server.
#   scripts/selftest.sh <server dir> <server jar> <java> <expect: fresh|flat|legacy-folder>
# The server dir comes from your own setup (a vanilla-compatible Paper/Spigot folder with the EULA accepted).
# Needs build/libs/Edict-*.jar and build/libs/EdictSelfTest-*.jar (./gradlew build).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="$1"; JARNAME="$2"; JAVA="$3"; EXPECT="${4:-fresh}"
cd "$SERVER"
rm -rf plugins/Edict plugins/PluginRules plugins/Edict-*.jar plugins/EdictSelfTest-*.jar
rm -rf arena arena_the_end lonely_the_end world/dimensions 2>/dev/null || true
mkdir -p plugins
cp "$ROOT"/build/libs/Edict-*.jar "$ROOT"/build/libs/EdictSelfTest-*.jar plugins/
case "$EXPECT" in
  flat) mkdir -p plugins/Edict; cp "$ROOT/scripts/seed-0.1-config.yml" plugins/Edict/config.yml ;;
  legacy-folder) mkdir -p plugins/PluginRules; cp "$ROOT/scripts/seed-0.1-config.yml" plugins/PluginRules/config.yml ;;
esac
rm -f selftest-report.txt
"$JAVA" -Xmx3G -Dedict.selftest="$PWD/selftest-report.txt" -Dedict.selftest.expect="$EXPECT" -jar "$JARNAME" nogui > selftest-console.log 2>&1 < /dev/null || true
cat selftest-report.txt 2>/dev/null | grep -E "^(FAIL|SELFTEST|platform)" || echo "NO REPORT"
grep -E "ERROR|Exception|WARN.*Edict|\[Edict" selftest-console.log | grep -v "SelfTest" | head -30 || true
