# Nexus Converter 🌌✨ v1.0.0

[![Build Status](https://img.shields.io/badge/Build-Maven%20%26%20Cargo-blueviolet)](pom.xml)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Rust Edition](https://img.shields.io/badge/Rust-2021-blue)](https://www.rust-lang.org/)
[![FFmpeg Backend](https://img.shields.io/badge/Backend-FFmpeg%20via%20ffmpeg--next-red)](https://github.com/zmwangx/rust-ffmpeg)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Warp Speed Media Conversion: Bridging Java Elegance with Rust Power!** 🚀

```text
  _______           _________ _______ _________ _______  _______
 (  ____ \|\     /|( \        (  ____ \\__   __/(  ____ \(  ____ \
 | (    \/| )   ( || (        | (    \/   ) (   | (    \/| (    \/
 | (_____ | |   | || |        | (__       | |   | (__    | (_____
 (_____  )| |   | || |        |  __)      | |   |  __)   (_____  )
       ) || |   | || |        | (         | |   | (            ) |
 /\____) || (___) || (________| )      ___) (___| (____/\/\____) |
 \_______)(_______)(________/|/       \_______/(_______/\_______)
```

Nexus Converter is a high-performance command-line tool designed to seamlessly convert various media files into the universally compatible MP3 format. It leverages the robustness and extensive ecosystem of Java for application structure and command-line parsing, while harnessing the raw speed, memory safety, and concurrency potential of Rust for the computationally intensive task of media processing via the legendary FFmpeg libraries.

---

## For End Users: Quick Start

Note: On Linux/macOS use `nexus-converter.sh`; on Windows use `nexus-converter.bat`. If you mix them up, the launcher will print a friendly hint and exit. See [Launcher troubleshooting](#launcher-troubleshooting).

- Minimal usage (Linux/macOS):

  ```bash
  ./nexus-converter.sh -- /path/to/my_video.mp4
  ```

- Windows:

  ```bat
  nexus-converter.bat -- C:\\path\\to\\my_video.mp4
  ```

- Useful options:
  - `-o, --output <file>` set output filename or full path
  - `-d, --directory <dir>` put output in this directory (keeps default name)
  - `-f, --force` overwrite if the file exists
  - `--skip-probe` skip input decodability check (not recommended)
  - `-q, --quiet` minimal console output (spinner + final result)
  - `--debug` verbose logs for troubleshooting (cannot be combined with `--quiet`)

Launcher help:

```bash
./nexus-converter.sh --help
```

Application help (forwarded to the JAR):

```bash
./nexus-converter.sh -- --help
```

Diagnostics and dry-run:

```bash
# Print environment checks (Java/Maven/JAR detection)
./nexus-converter.sh --doctor

# Show what would run without executing the app
./nexus-converter.sh --dry-run -- --input file.mp4
```

### Launcher troubleshooting

- If you accidentally run the Windows launcher on Unix:

  ```text
  This is a Windows .bat script. On Unix, run ./nexus-converter.sh
  ```

- If you invoke the Unix launcher with sh/dash instead of bash:

  ```text
  This script requires bash. Try: bash ./nexus-converter.sh [options] -- [app-args]
  ```

---

### What to expect (runtime)

- Quiet mode (final line only on error/success):

```text
❌ Error: Input file not found or is not a regular file: /no/such/file.mp4
```

- Debug mode (excerpt):

```text
INFO: Attempting to load native library from resource path: /native/linux-x86_64/libmedia_converter_lib.so
INFO: ✅ Native library {0} loaded successfully from temporary file.
[.... rust backend initialization logs ....]
NexusBridge Media Converter (backend 0.1.0-alpha)
❌ Error: Input file not found or is not a regular file: /no/such/file.mp4
```

---
Notes:

- No native install steps: the bundled library auto-loads.
- If you don’t set an output path, the app saves to Music → Downloads → input directory.

### 🧹 Housekeeping helpers (optional)

These convenience scripts are included in the distribution folder (`dist/`) to tidy up MP3s after converting:

- `cleanup-mp3.sh` — interactively delete selected MP3s in a folder
- `move-mp3.sh` — interactively move selected MP3s to a destination folder (validated and created in your HOME if needed)

Examples:

```bash
# Delete MP3s from the current directory (choose which ones)
./cleanup-mp3.sh

# Delete all MP3s in a directory
./cleanup-mp3.sh --dir /some/folder --all

# Move MP3s from current directory to your Music folder (default)
./move-mp3.sh

# Move from a specific source to a specific destination
./move-mp3.sh --src /tmp/exports --dest "$HOME/Pictures/NexusConverterAudio"
```

Behavior:

- Destination defaults to `$HOME/Music` if writable; otherwise `$HOME/Pictures/NexusConverterAudio` (auto-created).
- Both scripts validate directories and writability and ask for confirmation before destructive actions.

---

## 🤔 Project Genesis: Why Nexus Converter?

This project represents the convergence and refinement of two earlier efforts:

1. [media-converter-cli](https://github.com/xuoxod/media-converter-cli.git): A Java application providing the user interface and initial JNI bridging logic.
2. [media-converter-jni](https://github.com/xuoxod/media-converter-jni.git): A separate Rust project containing the core media conversion logic using FFmpeg.

While functional, managing two separate projects introduced complexities in the build process and dependency management. **Nexus Converter** was created to consolidate these into a single, streamlined Maven project. This unified structure simplifies building, testing, and distribution by:

- Integrating the Rust build (`cargo`) directly into the Maven lifecycle (`pom.xml`).
- Automatically packaging the compiled native Rust library (`.so`, `.dylib`, or `.dll`) inside the final Java JAR.
- Eliminating the need for manual library placement or configuring `java.library.path`.

The result is a more robust, maintainable, and user-friendly application.

---

## 💡 Core Features & Technical Deep Dive

- **🦀 Rust-Powered Backend (`ffmpeg-next`):** Utilizes the `ffmpeg-next` crate, providing safe Rust bindings to the underlying FFmpeg C libraries.
- **🎬 FFmpeg Integration:** Dynamically links against your system's installed FFmpeg runtime libraries (`libavformat`, `libavcodec`, `libavutil`, `libswresample`, etc.).
- **☕ Java Frontend (Picocli):** User-friendly CLI built with Java and Picocli for parsing arguments and providing help.
- **🌉 JNI Bridge:** Java Native Interface (JNI) layer connects the Java frontend to the Rust backend.
  - **Java Side:** `NativeConverter.java` declares `native boolean convertToMp3(String inputPath, String outputPath)`.
  - **Rust Side:** `lib.rs` implements the JNI function, marshals arguments, invokes the core conversion logic, and returns success/failure.
- **📦 Self-Contained Native Library Loading:** The Maven build copies the compiled Rust library into the JAR resources. `NativeConverter.java` extracts and loads it at runtime, so no manual library path configuration is needed.
- **🎵 MP3 Output Focus:** Extracts audio streams and encodes them into MP3 using the `libmp3lame` encoder (if available via FFmpeg).
- **🧠 Smart Defaults:** Automatically determines a sensible output location (e.g., user's Music directory) if none is specified.
- **📜 Logging:** Uses `log` and `env_logger` in Rust (set `RUST_LOG=debug` for diagnostics). Java logs to standard output/error.

---

## 🔧 Prerequisites

Before you embark on your conversion journey, ensure your system meets these requirements:

1. **Java Development Kit (JDK):** Version 17 or later. Verify with `java -version`.
2. **FFmpeg Runtime Libraries:** Must be installed and accessible on your system's library path.
    - **Debian/Ubuntu:**

      ```sh
      sudo apt update && sudo apt install ffmpeg
      ```

    - **Fedora:**

      ```sh
      sudo dnf install ffmpeg-libs
      ```

    - **macOS (Homebrew):**  

      ```sh
      brew install ffmpeg
      ```

    - **Windows:**  
      Download pre-built binaries from [ffmpeg.org](https://ffmpeg.org/download.html) and add the `bin` directory (with `.dll` files) to your `PATH`.
3. **Rust Toolchain (for building only):**  
   If you plan to build from source, install Rust via [rustup](https://rustup.rs/).

---

## 🛠️ Building from Source

1. **Clone the Repository:**

    ```sh
    git clone <your-repo-url>
    cd nexus-converter
    ```

2. **Build with Maven:**  
   This compiles Java, builds the Rust backend, and packages everything into a single JAR.

    ```sh
    mvn clean package
    ```

3. **Locate the Artifact:**  
   The final executable JAR will be at:

    ```text
    target/nexus-converter-1.0.0-SNAPSHOT.jar
    ```

---

## ▶️ Running Nexus Converter

### Output MP3 to default directory (e.g., `~/Music`)

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar /path/to/your/video.mp4
```

### Specify output directory

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar -d /path/to/output/dir /path/to/another/video.avi
```

### Filename with spaces

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar "/home/user/Downloads/momma said.mp4"
```

### Enable debug logging from Rust backend

```sh
RUST_LOG=debug java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar /path/to/your/video.mp4
```

### Show help

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar --help
```

---

### Command-Line Arguments

```text
Usage: nexus-convert [-fhV] [--debug] [-d=<outputDirectory>] [-o=<outputFile>] <inputFile>
🎬 Converts various media files (e.g., FLV, AVI, MP4) to MP3 audio format.
Utilizes a high-performance Rust backend for the heavy lifting. 🚀
      <inputFile>   Path to the input media file (e.g., video.mp4, audio.flv).
  -d, --directory=<outputDirectory>
                    Specify an output directory. If used with -o, the directory
                      part of -o takes precedence. If used alone, the output
                      filename defaults as described for -o, but placed in this
                      directory.
  -f, --force       Overwrite the output file if it already exists. Default:
                      false
  -h, --help        Show this help message and exit.
  -o, --output=<outputFile>
                    Path to the output MP3 file. If omitted, defaults to the
                      input file's name with an .mp3 extension in the same
                      directory.
  -V, --version     Print version information and exit.
  --skip-probe      Skip preflight probe that validates decodability and audio
                    stream presence (default: probe enabled)
  -q, --quiet       Reduce native backend logs to warnings only and suppress
                    non-essential Java output (spinner + final line only).
      --debug       Enable verbose native logs and extra Java diagnostics.

Notes:
- --quiet and --debug are mutually exclusive; the application will exit with an
  error if both are provided.
— Output is colorized when the terminal supports ANSI; messages are consistent
  and user-friendly by default.
```

---

## 🧑‍💻 Examples

**Basic Conversion (Output to Default Music/Downloads Directory):**

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar /home/user/Videos/holiday_footage.mov
```

**Windows:**

```cmd
java -jar C:\path\to\nexus-converter-1.0.0-SNAPSHOT.jar "C:\Users\User\Videos\Holiday Footage.mov"
```

**Specify Output Directory:**

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar -d /mnt/storage/extracted_audio /path/to/source/input_video.mkv
```

**Windows:**

```cmd
java -jar C:\path\to\nexus-converter-1.0.0-SNAPSHOT.jar -d D:\ExtractedAudio C:\VideoSources\input_video.mkv
```

**Output Directory with Spaces:**

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar -o "/home/user/My Music/Extracted Audio" "/home/user/My Videos/Important Presentation.mp4"
```

**Windows:**

```cmd
java -jar C:\path\to\nexus-converter-1.0.0-SNAPSHOT.jar -o "C:\Users\User\My Music\Extracted Audio" "C:\Users\User\My Videos\Important Presentation.mp4"
```

**Show Help Message:**

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar --help
```

**Show Version Information:**

```sh
java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar --version
```

**Enable Debug Logging (for Troubleshooting):**

*Linux/macOS:*

```sh
RUST_LOG=debug java -jar target/nexus-converter-1.0.0-SNAPSHOT.jar /path/to/problematic/video.avi
```

*Windows Command Prompt:*

```cmd
set RUST_LOG=debug
java -jar C:\path\to\nexus-converter-1.0.0-SNAPSHOT.jar C:\Videos\problematic_video.avi
```

*Windows PowerShell:*

```powershell
$env:RUST_LOG="debug"
java -jar C:\path\to\nexus-converter-1.0.0-SNAPSHOT.jar C:\Videos\problematic_video.avi
```

---

## 🚀 Happy Converting

---

## 📝 License

MIT License. See [LICENSE](LICENSE) for details.

---

### **Built with ❤️ by emhcet & contributors**

---

## Developer Guide 🧑‍💻

This section documents the architecture, build pipeline, JNI boundary, and operational practices so any contributor can work productively without guesswork.

### Architecture overview

- Java CLI (Picocli) in `src/main/java/com/gmail/xuoxod/nexusbridge/`.
  - Entrypoint: `Main.java` (parsing, UX, spinner, quiet/debug handling)
  - JNI wrapper: `NativeConverter.java` (native methods + resource-based loader)
- Rust native backend in `native/` using `ffmpeg-next`.
  - Core JNI implementation: `native/src/lib.rs`
  - Shared utilities: `native/nexus-kit`
  - Local debugging CLI: `native/nexus-lab`
- Packaging: Maven builds Java and invokes Cargo; the resulting `.so/.dll/.dylib` is copied under `target/classes/native/<platform>/` and then shaded into the JAR resources.

### Project layout (key paths)

```text
src/main/java/com/gmail/xuoxod/nexusbridge/  # Java app + JNI wrapper
native/src/lib.rs                             # JNI + media conversion
native/nexus-kit/src/lib.rs                   # Rust utility crate
native/nexus-lab/src/main.rs                  # Rust lab CLI for local tests
scripts/                                      # Packaging + helpers + git tools
```

### Build lifecycle

1. `mvn compile` compiles Java sources.
2. `exec-maven-plugin` runs `cargo build` in `native/` (debug by default).
3. `maven-resources-plugin` copies the built native library to `target/classes/native/<os-arch>/`.
4. `maven-shade-plugin` produces a runnable fat jar.

Notes:

- We default to Rust debug build for faster iteration; tune Cargo profile for release if needed.
- Packaging job (`package_for_distribution.sh`) uses `-DskipTests` to avoid sample-dependent test failures; CI jobs can still run tests.

### Native library loading strategy

`NativeConverter.loadNativeLibrary()` extracts the embedded native library to a temporary file and loads it with `System.load`. This avoids requiring `java.library.path` or prior installation. Quiet/debug flags propagate to the backend by selecting the native log level early.

### CLI UX contract

- `--quiet` and `--debug` are mutually exclusive. The app exits with an error if both are supplied.
- Pretty, colorized output when ANSI is supported.
- A spinner shows progress during conversion.
- Defaults-first: destination selection falls back from Music → Downloads → input directory.

### Testing strategy

- JUnit 5 tests live under `src/test/java`.
- Sample-dependent tests auto-skip if media assets aren’t present locally (see `NativeConverterTest.probe_validSample_returnsTrue` which uses `Assumptions.assumeTrue`).
- Run tests:

```sh
mvn -q clean test
```

### Packaging & distribution

- Script: `package_for_distribution.sh`
  - Builds shaded JAR
  - Assembles `dist/` with launchers and helper scripts
  - Produces `nexus-converter-<version>-dist.zip`

What to expect (packaging run, highlights):

```text
🚀 Starting packaging process for Nexus Converter...
📦 Building the final self-contained JAR with 'mvn clean package -DskipTests'...
[INFO] Building nexus-converter 1.0.0-SNAPSHOT
[INFO] Tests are skipped.
[INFO] Building jar: .../target/nexus-converter-1.0.0-SNAPSHOT.jar
[INFO] Replacing ... with .../target/nexus-converter-1.0.0-SNAPSHOT-shaded.jar
[INFO] BUILD SUCCESS
🛠️ Preparing distribution directory: .../dist
➡️ Copying JAR: .../target/nexus-converter-1.0.0-SNAPSHOT.jar
➡️ Copying run scripts...
➡️ Copying distribution README (README.dist.md) ...
➡️ Copying helper scripts from .../scripts ...
📦 Creating distribution archive: nexus-converter-1.0.0-SNAPSHOT-dist.zip ...
  Archive created at: .../nexus-converter-1.0.0-SNAPSHOT-dist.zip
✅ Packaging complete!
Distribution files prepared in: .../dist
Archive ready: nexus-converter-1.0.0-SNAPSHOT-dist.zip
```

Contents of `dist/`:

- `nexus-converter-<version>.jar` — runnable fat JAR
- `nexus-converter.sh` / `.bat` — unified launcher (doctor/dry-run/build)
- `cleanup-mp3.sh`, `move-mp3.sh` — optional helpers
- `README.md` — end-user quick start

### Git hygiene & large media

- `.gitignore` excludes build outputs, dist artifacts, and sample media.
- History cleanup helper: `scripts/purge-git-history-large-media.sh`
  - Safe flow with backup branch and prompts
  - Supports `--only-paths` and `--extra-path <dir/>` to surgically remove directories
  - `--force-run` passes `--force` to `git filter-repo` when a fresh-clone check blocks the run
  - `--yes` to auto-confirm prompts

Examples:

```sh
# Dry-run, path-only, force allowed, no push
bash scripts/purge-git-history-large-media.sh \
  --only-paths \
  --extra-path native/assets/samples/resource/ \
  --extra-path native/assets/samples/target/ \
  --dry-run \
  --force-run \
  --no-push \
  --yes

# Real run with push
bash scripts/purge-git-history-large-media.sh \
  --only-paths \
  --extra-path native/assets/samples/resource/ \
  --extra-path native/assets/samples/target/ \
  --force-run --yes
```

Verify removals after the rewrite (no output means nothing left):

```bash
git rev-list --objects --all | grep -E 'native/assets/samples/(resource|target)/' || echo "No matches (good)"
```

What to expect (dry run):

```text
⚠️  Working tree not clean. Commit or stash changes first.
ℹ️  Continue anyway -> auto-confirmed by --yes
ℹ️  Remote: origin
ℹ️  Current branch: main
ℹ️  Will remove paths:
  - native/assets/samples/resource/
  - native/assets/samples/target/
ℹ️  Will remove globs:
  - 
ℹ️  This will rewrite history. Continue -> auto-confirmed by --yes
ℹ️  Creating backup branch: backup-before-filter-20251025-080957
ℹ️  Running git filter-repo ...
DRY-RUN: git filter-repo --path native/assets/samples/resource/ --path native/assets/samples/target/ --invert-paths --force 
ℹ️  Running aggressive GC to drop unreachable blobs...
⚠️  --no-push set; not pushing. Remember to push with --force-with-lease.
✅ Done. Backup branch: backup-before-filter-20251025-080957
ℹ️  If collaborators exist, coordinate a re-clone or a reset onto the new history.
```

Typical output (real run; timestamps/branch names will vary):

```text
ℹ️  Remote: origin
ℹ️  Current branch: main
ℹ️  Will remove paths:
  - native/assets/samples/resource/
  - native/assets/samples/target/
ℹ️  Will remove globs:
  - 
ℹ️  This will rewrite history. Continue -> auto-confirmed by --yes
ℹ️  Creating backup branch: backup-before-filter-YYYYMMDD-HHMMSS
ℹ️  Running git filter-repo ...
ℹ️  Running aggressive GC to drop unreachable blobs...
⚠️  About to push rewritten history to 'origin' with --force-with-lease.
✅ Push completed.
✅ Done. Backup branch: backup-before-filter-YYYYMMDD-HHMMSS
```

### Troubleshooting

- Native library fails to load
  - Ensure the shaded JAR includes `native/<platform>/libmedia_converter_lib.*`.
  - On Linux, `ldd` the extracted file to verify FFmpeg libs are resolvable.
- FFmpeg errors at runtime
  - Install FFmpeg runtime: `apt install ffmpeg` (Debian/Ubuntu), Homebrew on macOS, or vendor binaries on Windows.
- Java version issues
  - JDK 17+ required. The start script checks and provides guidance.
- Tests fail due to missing samples
  - Sample-dependent tests now auto-skip; verify you’re on the latest commit.
- Rust toolchain
  - Install via `rustup`. Build errors often indicate missing system FFmpeg dev packages for static link scenarios; we use dynamic linking by default.

### CI & releases (suggested)

- CI pipeline:
  - `mvn -B -q clean verify` (tests) on PRs
  - `./package_for_distribution.sh` on main branch to produce a zip artifact
  - Optional: GitHub Release job that uploads the `*-dist.zip`

### Contributing

Issues and PRs are welcome. Please keep the CLI UX consistent (colorized, friendly), maintain the JNI boundary stable, and avoid reintroducing large media assets to the repo history.
