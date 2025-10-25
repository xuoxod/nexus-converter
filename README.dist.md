# Nexus Converter — Quick start (dist)

This `dist/` folder contains a ready-to-run distribution of Nexus Converter. It includes:

- A shaded JAR (`nexus-converter-<version>.jar`) with Java dependencies and the Linux x86_64 native library embedded.
- Unified launchers: `nexus-converter.sh` (POSIX) and `nexus-converter.bat` (Windows).
- Handy housekeeping scripts: `cleanup-mp3.sh` and `move-mp3.sh`.

## Usage

Quick usage from `dist/`:

```bash
./nexus-converter.sh -- /path/to/video.mp4
```

Windows:

```bat
nexus-converter.bat -- C:\\path\\to\\video.mp4
```

### Options

- `-o, --output <file>`: Set output filename or full path
- `-d, --directory <dir>`: Place output in this directory (keeps default name)
- `-f, --force`: Overwrite if output file exists
- `--skip-probe`: Skip input validation (not recommended)
- `-q, --quiet`: Minimal console output (spinner + final line)
- `--debug`: Verbose logs; cannot be combined with `--quiet`

### Examples

Launcher help and diagnostics:

```bash
./nexus-converter.sh --help       # launcher help
./nexus-converter.sh --doctor     # environment checks (Java/Maven/JAR)
./nexus-converter.sh --dry-run -- --help  # show app help without running
```

Convert with defaults to Music/Downloads/input directory fallback:

```bash
./nexus-converter.sh -- ~/Videos/clip.mp4
```

Specify an output directory:

```bash
./nexus-converter.sh -- -d "$HOME/Music" ~/Videos/clip.avi
```

Force overwrite and quiet mode:

```bash
./nexus-converter.sh -- -f -q ~/Videos/clip.mov
```

Enable debug output:

```bash
./nexus-converter.sh -- --debug ~/Videos/clip.mkv
```

## Housekeeping helpers

These scripts live in this `dist/` folder:

- `cleanup-mp3.sh`: interactive or batch delete of MP3 files (tries trash, falls back to rm).
- `move-mp3.sh`: interactive mover that validates destination and creates directories under $HOME when appropriate.

Both helpers validate paths, ensure writability, and prompt for confirmation unless `--yes` is passed.

## Notes

- For full developer documentation and build instructions, see the project root `README.md`.
- If needed, we can produce a zip of this folder (e.g., `nexus-converter-dist.zip`).

Color output is enabled when your terminal supports ANSI; messages are friendly and consistent. Defaults-first UX tries your Music folder, then Downloads, then the input file's directory.

### What to expect

Show the CLI help (first lines):

```text
Usage: nexus-convert [-fhqV] [--debug] [--skip-probe] [-d=<outputDirectory>]
           [-o=<outputFile>] <inputFile>
🎬 Converts various media files (e.g., FLV, AVI, MP4) to MP3 audio format.
Utilizes a high-performance Rust backend for the heavy lifting. 🚀
   <inputFile>    Path to the input media file (e.g., video.mp4, audio.flv).
 -d, --directory=<outputDirectory>
           Specify an output directory. If used with -o, the
            directory part of -o takes precedence. If used alone,
            the output filename defaults as described for -o, but
            placed in this directory.
   --debug        Enable verbose native logs and extra Java diagnostics.
 -f, --force        Overwrite the output file if it already exists. Default:
            false
 -h, --help         Show this help message and exit.
 -o, --output=<outputFile>
           Path to the output MP3 file. If omitted, defaults to the
            input file's name with an .mp3 extension in the same
            directory.
 -q, --quiet        Reduce native backend logs to warnings only.
   --skip-probe   Skip preflight probe that validates decodability and
            presence of an audio stream.
 -V, --version      Print version information and exit.
```

Conversion run:

- In quiet mode you’ll see a spinner and a single final line indicating success or failure.
- In debug mode you’ll see detailed native and Java diagnostics.

Quiet mode (example, missing input):

```text
❌ Error: Input file not found or is not a regular file: /no/such/file.mp4
```

Debug mode (excerpt, missing input):

```text
INFO: Attempting to load native library from resource path: /native/linux-x86_64/libmedia_converter_lib.so
INFO: ✅ Native library {0} loaded successfully from temporary file.
[.... rust backend initialization logs ....]
NexusBridge Media Converter (backend 0.1.0-alpha)
❌ Error: Input file not found or is not a regular file: /no/such/file.mp4
```

### Helper scripts: What to expect

cleanup-mp3.sh help:

```text
Usage: cleanup-mp3.sh [--dir DIR] [--all] [--recursive] [--dry-run] [--hard] [--yes]

Interactively or automatically delete MP3 files in a directory.
- Without --all, you'll be prompted to choose which files to delete.
- By default, deletion is safe and asks for confirmation; use --yes to skip prompts.
- Use --hard to permanently delete (rm); otherwise trash is attempted when available.
```

move-mp3.sh help:

```text
Usage: move-mp3.sh [--src DIR] [--dest DIR] [--recursive] [--force] [--yes]

Move MP3 files from a source directory to a destination directory (validated).
- Without selection flags, you'll be prompted to choose which files to move.
- Destination defaults to HOME/Music (if writable), else HOME/Pictures/NexusConverterAudio (auto-created).
```

If there are no MP3s in the current directory, both commands print:

```text
No MP3 files found in: .
```

— Built with ❤️ by emhcet & contributors —
