#!/usr/bin/env bash
set -euo pipefail

# cleanup-mp3.sh — interactively or automatically delete MP3 files in a directory.
# Defaults: non-recursive, safe (confirmation), current directory.
# Options:
#   --dir DIR        Source directory to scan (default: current directory)
#   --all            Delete all .mp3 in the directory (non-recursive)
#   --recursive      Include subdirectories
#   --dry-run        Show what would be deleted without removing
#   --hard           Do not send to trash; use rm without prompt
#   --yes            Do not ask for confirmation

DIR=".$PWD"
DIR="."
ALL=false
RECURSIVE=false
DRY_RUN=false
HARD=false
YES=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)
      DIR="$2"; shift 2;;
    --all)
      ALL=true; shift;;
    --recursive)
      RECURSIVE=true; shift;;
    --dry-run)
      DRY_RUN=true; shift;;
    --hard)
      HARD=true; shift;;
    --yes)
      YES=true; shift;;
    -h|--help)
      cat <<EOF
Usage: $(basename "$0") [--dir DIR] [--all] [--recursive] [--dry-run] [--hard] [--yes]

Interactively or automatically delete MP3 files in a directory.
- Without --all, you'll be prompted to choose which files to delete.
- By default, deletion is safe and asks for confirmation; use --yes to skip prompts.
- Use --hard to permanently delete (rm); otherwise trash is attempted when available.
EOF
      exit 0;;
    *) echo "Unknown option: $1" >&2; exit 2;;
  esac
done

# Validate directory
if [[ ! -d "$DIR" ]]; then
  echo "❌ Error: Directory not found: $DIR" >&2
  exit 1
fi

# Build find expression
if $RECURSIVE; then
  FIND_OPTS=( -type f -name '*.mp3' )
else
  FIND_OPTS=( -maxdepth 1 -type f -name '*.mp3' )
fi

mapfile -t FILES < <(find "$DIR" "${FIND_OPTS[@]}" | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "No MP3 files found in: $DIR"
  exit 0
fi

choose_files() {
  echo "Found ${#FILES[@]} MP3 file(s):"
  local i=1
  for f in "${FILES[@]}"; do
    printf "  [%d] %s\n" "$i" "$f"
    ((i++))
  done
  echo "Enter numbers to delete (comma-separated), 'a' for all, or 'q' to quit:"
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

TO_DELETE=()
if $ALL; then
  TO_DELETE=("${FILES[@]}")
else
  mapfile -t TO_DELETE < <(choose_files)
fi

if [[ ${#TO_DELETE[@]} -eq 0 ]]; then
  echo "Nothing selected. Exiting."
  exit 0
fi

echo "Files to delete (${#TO_DELETE[@]}):"
printf '  %s\n' "${TO_DELETE[@]}"

if ! $YES; then
  echo -n "Proceed with deletion? [y/N]: "
  read -r ANS
  [[ "$ANS" == "y" || "$ANS" == "Y" ]] || { echo "Cancelled."; exit 0; }
fi

if $DRY_RUN; then
  echo "Dry-run: no files removed."
  exit 0
fi

trash_cmd() {
  if command -v gio >/dev/null 2>&1; then
    gio trash "$@"
  elif command -v trash-put >/dev/null 2>&1; then
    trash-put "$@"
  else
    return 1
  fi
}

if $HARD; then
  rm -f -- "${TO_DELETE[@]}"
else
  if ! trash_cmd "${TO_DELETE[@]}"; then
    echo "No trash command found; falling back to rm -i" >&2
    rm -i -- "${TO_DELETE[@]}"
  fi
fi

echo "Deleted ${#TO_DELETE[@]} file(s)."
