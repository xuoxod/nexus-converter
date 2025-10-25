#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration: Source Project Paths ---
# !! Please double-check these paths match your existing projects !!
JAVA_PROJECT_SRC="/home/emhcet/private/projects/desktop/java/nexusbridge-app"
RUST_PROJECT_SRC="/home/emhcet/private/projects/desktop/rust/media_converter_lib"
# Assuming your Java package is com.nexusbridge.app
JAVA_PACKAGE_PATH="com/nexusbridge/app"

# --- Get the directory where this script is located (the new project root) ---
NEW_PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

echo "Setting up project structure in: $NEW_PROJECT_ROOT"
echo "Copying files from Java source: $JAVA_PROJECT_SRC"
echo "Copying files from Rust source: $RUST_PROJECT_SRC"

# --- Create Java Directories ---
echo "Creating Java source directories..."
mkdir -p "$NEW_PROJECT_ROOT/src/main/java/$JAVA_PACKAGE_PATH"
mkdir -p "$NEW_PROJECT_ROOT/src/main/resources"
mkdir -p "$NEW_PROJECT_ROOT/src/test/java/$JAVA_PACKAGE_PATH" # Optional: If you have tests
mkdir -p "$NEW_PROJECT_ROOT/src/test/resources"             # Optional: If you have test resources

# --- Create Native (Rust) Directory ---
echo "Creating native Rust source directory..."
mkdir -p "$NEW_PROJECT_ROOT/native/src"

# --- Copy Java Files ---
echo "Copying Java source files..."
cp -v "$JAVA_PROJECT_SRC/src/main/java/$JAVA_PACKAGE_PATH/"*.java "$NEW_PROJECT_ROOT/src/main/java/$JAVA_PACKAGE_PATH/"

echo "Copying Java resources..."
# Use find to copy contents if the resources directory exists and is not empty
if [ -d "$JAVA_PROJECT_SRC/src/main/resources" ] && [ "$(ls -A $JAVA_PROJECT_SRC/src/main/resources)" ]; then
    cp -rv "$JAVA_PROJECT_SRC/src/main/resources/." "$NEW_PROJECT_ROOT/src/main/resources/"
else
    echo "No Java resources found to copy."
fi

echo "Copying Maven pom.xml..."
cp -v "$JAVA_PROJECT_SRC/pom.xml" "$NEW_PROJECT_ROOT/pom.xml"

# --- Copy Rust Files ---
echo "Copying Rust source files (lib.rs)..."
cp -v "$RUST_PROJECT_SRC/src/lib.rs" "$NEW_PROJECT_ROOT/native/src/lib.rs"
# Add lines here if you have other .rs files in src/

echo "Copying Cargo.toml..."
cp -v "$RUST_PROJECT_SRC/Cargo.toml" "$NEW_PROJECT_ROOT/native/Cargo.toml"

# Optional: Copy build.rs if it exists
if [ -f "$RUST_PROJECT_SRC/build.rs" ]; then
    echo "Copying build.rs..."
    cp -v "$RUST_PROJECT_SRC/build.rs" "$NEW_PROJECT_ROOT/native/build.rs"
fi

# Optional: Copy Rust config if it exists
if [ -d "$RUST_PROJECT_SRC/.cargo" ]; then
    echo "Copying .cargo directory..."
    cp -rv "$RUST_PROJECT_SRC/.cargo" "$NEW_PROJECT_ROOT/native/.cargo"
fi

# --- Copy Other Root Files (Optional) ---
# Example: Copy README, LICENSE etc. if they exist in the Java project root
# if [ -f "$JAVA_PROJECT_SRC/README.md" ]; then
#     cp -v "$JAVA_PROJECT_SRC/README.md" "$NEW_PROJECT_ROOT/README.md"
# fi

echo "-------------------------------------"
echo "Project setup script finished!"
echo "New project structure created at: $NEW_PROJECT_ROOT"
echo "Next steps:"
echo "1. Review the copied files."
echo "2. Modify '$NEW_PROJECT_ROOT/pom.xml' to build the Rust code (e.g., using maven-cargo-plugin or exec-maven-plugin)."
echo "3. Adjust Java code (if needed) to load the library from the expected location relative to the JAR."
echo "4. Try building the combined project (e.g., 'mvn clean package')."
echo "-------------------------------------"

