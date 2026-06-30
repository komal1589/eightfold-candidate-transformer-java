#!/usr/bin/env bash
# Run the transformer. Builds first if ./bin is missing.
set -euo pipefail
cd "$(dirname "$0")"
[ -d bin/com ] || ./build.sh
exec java -cp "bin:libs/gson-2.11.0.jar" com.eightfold.veritas.Cli "$@"
