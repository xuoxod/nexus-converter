#!/bin/bash
set -euo pipefail

# ✨ Welcome to the Nexus Converter Launcher! ✨
echo ""

# --- Configuration ---
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
RUN_SCRIPT_NAME="run-nexus-converter.sh"
RUN_SCRIPT_PATH="$SCRIPT_DIR/$RUN_SCRIPT_NAME"
# Look for the runnable JAR, not the distribution zip
JAR_NAME_PATTERN="nexus-converter-*.jar"

# --- Helper Functions ---
print_error() {
    # ANSI escape codes for red color
    echo -e "\033[0;31m❌ Error: $1\033[0m" >&2
}

print_info() {
    # ANSI escape codes for blue color
    echo -e "\033[0;34mℹ️  $1\033[0m"
}

print_success() {
    # ANSI escape codes for green color
    echo -e "\033[0;32m✅ $1\033[0m"
}

# --- 1. Check for Java ---
print_info "Checking for Java installation (JDK 17+)..."
if ! command -v java &> /dev/null; then
    print_error "'java' command not found in your PATH."
    echo "   Please install Java (JRE or JDK version 17 or later)"
    echo "   and ensure the 'java' command is accessible."
    echo "   Visit: https://www.java.com/ or https://adoptium.net/"
    exit 1
else
    # Try to get version, but don't fail if it's weird format
    JAVA_VERSION_INFO=$(java -version 2>&1 | head -n 1)
        print_success "Java found: ($JAVA_VERSION_INFO)"
        # Attempt to enforce version >= 17
        JAVA_SPEC=$(java -XshowSettings:properties -version 2>&1 | awk -F= '/java.specification.version/ {gsub(/ /,""); print $2}') || true
        if [ -n "${JAVA_SPEC:-}" ]; then
            # JAVA_SPEC is like 17 or 21
            if [ "${JAVA_SPEC%.*}" -lt 17 ]; then
                print_error "Java 17+ required. Detected specification version: ${JAVA_SPEC}."
                echo "   Please install a newer JDK from https://adoptium.net/ or vendor of your choice."
                exit 1
            fi
        fi
fi

# --- 2. Check for the main application JAR ---
print_info "Looking for the application JAR ($JAR_NAME_PATTERN)..."
# Find the JAR relative to this script's location
JAR_FILE=$(find "$SCRIPT_DIR" -maxdepth 1 -name "$JAR_NAME_PATTERN" -not -name '*-sources.jar' -not -name '*-javadoc.jar' -print -quit)

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    print_error "Application JAR ($JAR_NAME_PATTERN) not found in '$SCRIPT_DIR'."
    echo "   Please ensure the application JAR file is in the same directory as this script."
    echo "   If you built from source, run the packaging script first ('package_for_distribution.sh')."
    exit 1
else
    print_success "Application JAR found: $(basename "$JAR_FILE")"
fi

# --- 3. Check for the run script ---
print_info "Looking for the execution script ($RUN_SCRIPT_NAME)..."
if [ ! -f "$RUN_SCRIPT_PATH" ]; then
    print_error "Execution script '$RUN_SCRIPT_NAME' not found in '$SCRIPT_DIR'."
    echo "   This script is required to run the application. Please ensure it's present."
    exit 1
elif [ ! -x "$RUN_SCRIPT_PATH" ]; then
     print_info "'$RUN_SCRIPT_NAME' is not executable. Attempting to fix..."
     chmod +x "$RUN_SCRIPT_PATH"
     if [ ! -x "$RUN_SCRIPT_PATH" ]; then
        print_error "Failed to make '$RUN_SCRIPT_NAME' executable. Please do it manually ('chmod +x $RUN_SCRIPT_PATH')."
        exit 1
     else
        print_success "'$RUN_SCRIPT_NAME' made executable."
     fi
else
    print_success "Execution script found: $RUN_SCRIPT_NAME"
fi

# --- 4. Ready to Run ---
echo ""
print_info "All checks passed! Preparing to launch Nexus Converter..."
echo "   Using JAR: $(basename "$JAR_FILE")"
echo "   Arguments passed: $@"
echo -e "\a" # Bell sound!

# Execute the actual run script, passing all arguments
"$RUN_SCRIPT_PATH" "$@"

EXIT_CODE=$? # Capture exit code from the run script
echo ""
if [ $EXIT_CODE -eq 0 ]; then print_success "Nexus Converter finished."; else print_error "Nexus Converter exited with code $EXIT_CODE."; fi
exit $EXIT_CODE