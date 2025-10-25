#!/usr/bin/env bash
set -euo pipefail

# move-mp3.sh — interactively or automatically move MP3 files from a source directory to a destination directory.
# Validates destination; creates it in HOME when appropriate.
# Options:
#   --src DIR        Source directory to scan (default: current directory)
#   --dest DIR       Destination directory (default: $HOME/Music if writable; else $HOME/Pictures/NexusConverterAudio)
#   --recursive      Include subdirectories when scanning source
#   --force          Overwrite existing files at destination
#   --yes            Do not ask for confirmation

SRC="."
DEST=""
RECURSIVE=false
FORCE=false
YES=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --src)
      SRC="$2"; shift 2;;
    --dest)
      DEST="$2"; shift 2;;
    --recursive)
      RECURSIVE=true; shift;;
    --force)
      FORCE=true; shift;;
    --yes)
      YES=true; shift;;
    -h|--help)
      cat <<EOF
Usage: $(basename "$0") [--src DIR] [--dest DIR] [--recursive] [--force] [--yes]

Move MP3 files from a source directory to a destination directory (validated).
- Without selection flags, you'll be prompted to choose which files to move.
- Destination defaults to HOME/Music (if writable), else HOME/Pictures/NexusConverterAudio (auto-created).
EOF
      exit 0;;
    *) echo "Unknown option: $1" >&2; exit 2;;
  esac
done

# Resolve default destination
if [[ -z "$DEST" ]]; then
  if [[ -d "$HOME/Music" && -w "$HOME/Music" ]]; then
    DEST="$HOME/Music"
  else
    DEST="$HOME/Pictures/NexusConverterAudio"
  fi
fi

# Validate/create destination
if [[ -e "$DEST" && ! -d "$DEST" ]]; then
  echo "❌ Error: Destination exists and is not a directory: $DEST" >&2
  exit 1
fi
if [[ ! -d "$DEST" ]]; then
  # Only auto-create under HOME for safety
  case "$DEST" in
    "$HOME"/*)
      echo "Creating destination: $DEST"
      mkdir -p "$DEST"
      ;;
    *)
      echo "❌ Error: Destination does not exist: $DEST (will not auto-create outside HOME)" >&2
      exit 1
      ;;
  esac
fi
if [[ ! -w "$DEST" ]]; then
  echo "❌ Error: Destination is not writable: $DEST" >&2
  exit 1
fi

# Validate source
if [[ ! -d "$SRC" ]]; then
  echo "❌ Error: Source directory not found: $SRC" >&2
  exit 1
fi

# Build file list
if $RECURSIVE; then
  FIND_OPTS=( -type f -name '*.mp3' )
else
  FIND_OPTS=( -maxdepth 1 -type f -name '*.mp3' )
fi

mapfile -t FILES < <(find "$SRC" "${FIND_OPTS[@]}" | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "No MP3 files found in: $SRC"
  exit 0
fi

choose_files() {
  echo "Found ${#FILES[@]} MP3 file(s):"
  local i=1
  for f in "${FILES[@]}"; do
    printf "  [%d] %s\n" "$i" "$f"
    ((i++))
  done
  echo "Enter numbers to move (comma-separated), 'a' for all, or 'q' to quit:"
  read -r SEL
  if [[ "$SEL" == "q" ]]; then
    echo "Aborted."
    exit 0
  fi
  local SELECTED=()
  if [[ "$SEL" == "a" ]]; then
    SELECTED=("${FILES[@]}")
  else
    IFS=',' read -ra IDX <<< "$SEL"
    for n in "${IDX[@]}"; do
      if [[ "$n" =~ ^[0-9]+$ ]] && (( n>=1 && n<=${#FILES[@]} )); then
        SELECTED+=("${FILES[$((n-1))]}")
      fi
    done
  fi
  printf '%s\n' "${SELECTED[@]}"
}

mapfile -t TO_MOVE < <(choose_files)

if [[ ${#TO_MOVE[@]} -eq 0 ]]; then
  echo "Nothing selected. Exiting."
  exit 0
fi

echo "Destination: $DEST"
echo "Files to move (${#TO_MOVE[@]}):"
printf '  %s\n' "${TO_MOVE[@]}"

if ! $YES; then
  echo -n "Proceed with move? [y/N]: "
  read -r ANS
  [[ "$ANS" == "y" || "$ANS" == "Y" ]] || { echo "Cancelled."; exit 0; }
fi

MV_OPTS=( -n )
$FORCE && MV_OPTS=( -f )

for src in "${TO_MOVE[@]}"; do
  base=$(basename "$src")
  mv "${MV_OPTS[@]}" -- "$src" "$DEST/$base"
  echo "Moved: $base"
done

echo "Moved ${#TO_MOVE[@]} file(s) to: $DEST"
