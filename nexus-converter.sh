#!/usr/bin/env bash
set -euo pipefail

# Unified, hardened launcher for Nexus Converter
# - Works for both distribution (JAR next to script) and dev builds (JAR in target/)
# - Performs Java (17+) checks
# - Helpful diagnostics via --doctor / --dry-run
# - Optional auto-build via --build when pom.xml and mvn are available

RED="\033[0;31m"; GREEN="\033[0;32m"; BLUE="\033[0;34m"; YELLOW="\033[0;33m"; NC="\033[0m"
info()    { echo -e "${BLUE}ℹ️  $*${NC}"; }
warn()    { echo -e "${YELLOW}⚠️  $*${NC}"; }
success() { echo -e "${GREEN}✅ $*${NC}"; }
error()   { echo -e "${RED}❌ $*${NC}" >&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
JAR_PATTERN='nexus-converter-*.jar'

usage() {
  cat <<EOF
Nexus Converter Launcher

Usage: $(basename "$0") [--build|--no-build] [--dry-run] [--doctor] [--help] [--] [app-args...]

Options:
  --build       If no runnable JAR is found, attempt 'mvn -q -DskipTests=true package' automatically.
  --no-build    Never attempt to build automatically (default behavior).
  --dry-run     Show what would run (Java/JAR/args) without executing the app.
  --doctor      Print environment diagnostics (Java/Maven/JAR detection) and exit.
  -h, --help    Show this help and exit.
  --            Treat the rest of the arguments as application arguments.

Behavior:
  - Prefers a JAR next to this script (distribution). If not found, looks in ./target/ (dev build).
  - Requires Java 17+.
EOF
}

DOCTOR=0
DRY_RUN=0
AUTO_BUILD=0     # opt-in for safety; pass --build to enable
APP_ARGS=()

# Argument parsing
while [[ $# -gt 0 ]]; do
  case "$1" in
    --doctor) DOCTOR=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --build) AUTO_BUILD=1; shift ;;
    --no-build) AUTO_BUILD=0; shift ;;
    -h|--help) usage; exit 0 ;;
    --) shift; while [[ $# -gt 0 ]]; do APP_ARGS+=("$1"); shift; done ;;
    *) APP_ARGS+=("$1"); shift ;;
  esac
done

java_installed() { command -v java >/dev/null 2>&1; }
mvn_installed()  { command -v mvn  >/dev/null 2>&1; }

get_java_spec_version() {
  local spec
  spec=$(java -XshowSettings:properties -version 2>&1 | awk -F= '/java.specification.version/ {gsub(/ /,""); print $2}') || true
  if [[ -n "${spec:-}" ]]; then echo "$spec"; return 0; fi
  # Fallback from "openjdk version \"17.0.10\"" line
  local line major
  line=$(java -version 2>&1 | head -n1)
  major=$(sed -n 's/.*"\([0-9][0-9]*\)\(\.[0-9].*\)\?".*/\1/p' <<<"$line")
  echo "${major:-}"
}

require_java_17_plus() {
  if ! java_installed; then
    error "'java' command not found in your PATH. Please install Java (JDK 17+)."
    return 1
  fi
  local spec
  spec=$(get_java_spec_version)
  if [[ -z "${spec:-}" ]]; then
    warn "Could not determine Java specification version; proceeding but expecting 17+."
    return 0
  fi
  local major=${spec%%.*}
  if [[ "$major" -lt 17 ]]; then
    error "Java 17+ required. Detected specification version: ${spec}."
    return 1
  fi
}

find_jar() {
  local found
  # 1) Prefer distribution JAR next to script
  found=$(find "$SCRIPT_DIR" -maxdepth 1 -name "$JAR_PATTERN" -not -name '*-sources.jar' -not -name '*-javadoc.jar' -print -quit)
  if [[ -n "${found:-}" && -f "$found" ]]; then echo "$found"; return 0; fi
  # 2) Fall back to dev build JAR in target/
  found=$(find "$SCRIPT_DIR/target" -maxdepth 1 -name "$JAR_PATTERN" -not -name '*-sources.jar' -not -name '*-javadoc.jar' -print -quit 2>/dev/null || true)
  if [[ -n "${found:-}" && -f "$found" ]]; then echo "$found"; return 0; fi
  return 1
}

attempt_build_if_enabled() {
  if [[ $AUTO_BUILD -ne 1 ]]; then return 0; fi
  if [[ -f "$SCRIPT_DIR/pom.xml" ]] && mvn_installed; then
    info "No runnable JAR found. Attempting Maven build (skip tests)..."
    ( cd "$SCRIPT_DIR" && mvn -B -q -DskipTests=true package )
  else
    warn "Auto-build requested but Maven and/or pom.xml not available; skipping build."
  fi
}

print_doctor() {
  echo ""
  info "Environment diagnostics"
  if java_installed; then
    local jv; jv=$(java -version 2>&1 | head -n1)
    success "Java found: $jv"
    local spec; spec=$(get_java_spec_version || true)
    [[ -n "${spec:-}" ]] && info "Java specification version: $spec"
  else
    error "Java NOT found in PATH"
  fi
  if mvn_installed; then
    local mv; mv=$(mvn -v 2>&1 | head -n1)
    success "Maven found: $mv"
  else
    info "Maven not found (that's fine if running distribution JAR)"
  fi
  if [[ -f "$SCRIPT_DIR/pom.xml" ]]; then
    info "Detected project: pom.xml present"
  else
    info "No pom.xml in script directory (likely a distribution folder)"
  fi
  local jar; jar=$(find_jar || true)
  if [[ -n "${jar:-}" ]]; then
    success "Runnable JAR detected: $(basename "$jar")"
  else
    warn "No runnable JAR detected in '$SCRIPT_DIR' or '$SCRIPT_DIR/target'"
  fi
}

# 1) Java checks
require_java_17_plus

# 2) Locate JAR (and optionally build)
JAR_FILE=""
if ! JAR_FILE=$(find_jar); then
  attempt_build_if_enabled
  JAR_FILE=$(find_jar || true)
fi

if [[ $DOCTOR -eq 1 ]]; then
  print_doctor
  # If not dry-run, exit after doctor
  if [[ $DRY_RUN -eq 0 ]]; then exit 0; fi
fi

if [[ -z "${JAR_FILE:-}" ]]; then
  error "Could not find a runnable JAR ($JAR_PATTERN) in '$SCRIPT_DIR' or '$SCRIPT_DIR/target'."
  echo "   Hints:" >&2
  echo "   - If you're running a distribution: place the JAR next to this script." >&2
  echo "   - If you're in the source repo: run 'mvn -q -DskipTests=true package' (or re-run with --build)." >&2
  exit 1
fi

info "Launching Nexus Converter"
info "Using JAR: $(basename "$JAR_FILE")"
info "Arguments: ${APP_ARGS[*]:-}" 

if [[ $DRY_RUN -eq 1 ]]; then
  success "Dry run complete — no execution performed."
  exit 0
fi

set +e
java -jar "$JAR_FILE" "${APP_ARGS[@]:-}"
EXIT_CODE=$?
set -e

echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
  success "Nexus Converter finished."
else
  error "Nexus Converter exited with code $EXIT_CODE."
fi
exit $EXIT_CODE
