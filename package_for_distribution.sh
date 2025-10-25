#!/bin/bash

# Script to build and package the Nexus Converter for distribution

# Ensure the script stops if any command fails
set -euo pipefail

# Get the directory where this script is located (the project root)
PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

POM_FILE="$PROJECT_ROOT/pom.xml"
README_FILE="$PROJECT_ROOT/README.md"
DIST_README_TEMPLATE="$PROJECT_ROOT/README.dist.md"
LAUNCHER_SH="$PROJECT_ROOT/nexus-converter.sh"
LAUNCHER_BAT="$PROJECT_ROOT/nexus-converter.bat"

echo "🚀 Starting packaging process for Nexus Converter..."

# --- 1. Check Prerequisites ---
if [ ! -f "$POM_FILE" ]; then
  echo "❌ Error: pom.xml not found in $PROJECT_ROOT" >&2
  exit 1
fi
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: 'mvn' command not found. Please install Maven." >&2
    exit 1
fi
if [ ! -f "$LAUNCHER_SH" ]; then
  echo "⚠️ Warning: nexus-converter.sh not found. Skipping." >&2
fi
if [ ! -f "$LAUNCHER_BAT" ]; then
  echo "⚠️ Warning: nexus-converter.bat not found. Skipping." >&2
fi

# --- 2. Run Maven Build ---
echo "📦 Building the final self-contained JAR with 'mvn clean package -DskipTests'..."
mvn -B -DskipTests clean package # Skip tests to avoid sample-dependent failures during packaging

# --- 3. Prepare Distribution Directory ---
DIST_DIR="$PROJECT_ROOT/dist"
TARGET_DIR="$PROJECT_ROOT/target"

echo "🛠️ Preparing distribution directory: $DIST_DIR"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# Find the built JAR (excluding sources/javadoc)
BUILT_JAR=$(find "$TARGET_DIR" -maxdepth 1 -name 'nexus-converter-*.jar' -not -name '*-sources.jar' -not -name '*-javadoc.jar' -print -quit)

if [ -z "$BUILT_JAR" ] || [ ! -f "$BUILT_JAR" ]; then
  echo "❌ Error: Could not find the built JAR in $TARGET_DIR after build." >&2
  exit 1
fi

echo "➡️ Copying JAR: $BUILT_JAR"
cp "$BUILT_JAR" "$DIST_DIR/"

echo "➡️ Copying launcher scripts..."
if [ -f "$LAUNCHER_SH" ]; then
  cp "$LAUNCHER_SH" "$DIST_DIR/"
  chmod +x "$DIST_DIR/nexus-converter.sh" # Ensure executable permission
else
  echo "   Skipped: nexus-converter.sh"
fi
if [ -f "$LAUNCHER_BAT" ]; then
  cp "$LAUNCHER_BAT" "$DIST_DIR/"
else
  echo "   Skipped: nexus-converter.bat"
fi

# Copy distribution README (prefer template, fallback to root README)
if [ -f "$DIST_README_TEMPLATE" ]; then
  echo "➡️ Copying distribution README (README.dist.md) ..."
  cp "$DIST_README_TEMPLATE" "$DIST_DIR/README.md"
elif [ -f "$README_FILE" ]; then
  echo "➡️ Copying root README as fallback ..."
  cp "$README_FILE" "$DIST_DIR/README.md"
fi

# Copy housekeeping helper scripts if available
SCRIPTS_DIR="$PROJECT_ROOT/scripts"
if [ -d "$SCRIPTS_DIR" ]; then
  echo "➡️ Copying helper scripts from $SCRIPTS_DIR ..."
  cp -v "$SCRIPTS_DIR"/*.sh "$DIST_DIR/" 2>/dev/null || true
  chmod +x "$DIST_DIR"/*.sh || true
fi

# --- 4. Create Archive ---
JAR_BASENAME=$(basename "$BUILT_JAR")
VERSION=${JAR_BASENAME#nexus-converter-}
VERSION=${VERSION%.jar}
ARCHIVE_NAME="nexus-converter-${VERSION}-dist.zip"
echo "📦 Creating distribution archive: $ARCHIVE_NAME ..."
(
  cd "$DIST_DIR"
  zip -qr "../${ARCHIVE_NAME}" .
)
echo "   Archive created at: $PROJECT_ROOT/${ARCHIVE_NAME}"


echo "✅ Packaging complete!"
echo "Distribution files prepared in: $DIST_DIR"
echo "Archive ready: $ARCHIVE_NAME"
