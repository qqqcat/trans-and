#!/usr/bin/env sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$SCRIPT_DIR/.gradle}" exec gradle "$@"
