#!/bin/bash
set -euo pipefail

# Script to run the nexus-converter application

RED="\033[0;31m"; GREEN="\033[0;32m"; BLUE="\033[0;34m"; NC="\033[0m"

info() { echo -e "${BLUE}ℹ️  $*${NC}"; }
success() { echo -e "${GREEN}✅ $*${NC}"; }
error() { echo -e "${RED}❌ $*${NC}" >&2; }

# Check if java command exists
if ! command -v java &> /dev/null; then
  error "'java' command not found in your PATH. Please install Java (JDK 17+)."
  exit 1
fi

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Prefer a JAR next to the script; if not found, fall back to target/
JAR_DIR="$SCRIPT_DIR"
JAR_PATTERN='nexus-converter-*.jar'
JAR_FILE=$(find "$JAR_DIR" -maxdepth 1 -name "$JAR_PATTERN" -not -name '*-sources.jar' -not -name '*-javadoc.jar' -print -quit)
if [ -z "$JAR_FILE" ]; then
  JAR_DIR="$SCRIPT_DIR/target"
  JAR_FILE=$(find "$JAR_DIR" -maxdepth 1 -name "$JAR_PATTERN" -not -name '*-sources.jar' -not -name '*-javadoc.jar' -print -quit)
fi

# Check if the JAR file was found (in either location)
if [ -z "$JAR_FILE" ]; then
  error "Could not find the nexus-converter-*.jar in '$SCRIPT_DIR' or '$SCRIPT_DIR/target'"
  echo "   Make sure the JAR file is in the same directory as this script, or run 'mvn package' from the project root." >&2
  exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
  error "Found path is not a file: '$JAR_FILE'"
  exit 1
fi

# Run the Java application, passing all script arguments ("$@") to the JAR
info "Running Nexus Converter"
info "Using JAR: $(basename "$JAR_FILE")"
info "Arguments: $*"
java -jar "$JAR_FILE" "$@"

EXIT_CODE=$?
if [ $EXIT_CODE -eq 0 ]; then success "Nexus Converter finished."; else error "Nexus Converter exited with code $EXIT_CODE."; fi
exit $EXIT_CODE

