#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[ -d bin/com ] || ./build.sh
exec java -cp "bin:libs/gson-2.11.0.jar" com.eightfold.veritas.Tests
