#!/usr/bin/env bash
# Purge large sample media from Git history and push rewritten history safely.
# WARNING: This rewrites commit history. Coordinate with collaborators.
#
# Default behavior:
# - Creates a backup branch
# - Removes known large sample paths and common media globs from ALL history
# - Runs git gc, then pushes with --force-with-lease
#
# Usage:
#   scripts/purge-git-history-large-media.sh [--dry-run] [--no-push] [--force-run] [--yes|-y]
#       [--remote origin]
#       [--only-paths | --only-globs]
#       [--extra-path <path>]... [--extra-glob <glob>]...
#
# Examples:
#   scripts/purge-git-history-large-media.sh
#   scripts/purge-git-history-large-media.sh --no-push --extra-path some/other/big/folder/
#   scripts/purge-git-history-large-media.sh --extra-glob 'samples/**/*.mp4'

set -euo pipefail

REMOTE="origin"
PUSH_AFTER=true
DRY_RUN=false
FORCE_RUN=false
ONLY_PATHS=false
ONLY_GLOBS=false
ASSUME_YES=false
EXTRA_PATHS=()
EXTRA_GLOBS=()

print_info() { echo -e "\033[0;34mℹ️  $*\033[0m"; }
print_warn() { echo -e "\033[0;33m⚠️  $*\033[0m"; }
print_err()  { echo -e "\033[0;31m❌ $*\033[0m" >&2; }
print_ok()   { echo -e "\033[0;32m✅ $*\033[0m"; }

confirm() {
  local prompt=${1:-"Proceed?"}
  if $ASSUME_YES; then
    print_info "$prompt -> auto-confirmed by --yes"
    return 0
  fi
  read -r -p "$prompt [y/N]: " reply
  case "$reply" in
    [yY][eE][sS]|[yY]) return 0 ;;
    *) return 1 ;;
  esac
}

require_clean_repo() {
  if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    print_err "This is not a Git repository."
    exit 1
  fi
  if [ -n "$(git status --porcelain)" ]; then
    print_warn "Working tree not clean. Commit or stash changes first."
    if ! confirm "Continue anyway"; then exit 1; fi
  fi
}

require_filter_repo() {
  if ! command -v git-filter-repo >/dev/null 2>&1; then
    print_warn "git-filter-repo not found."
    echo "Install from https://github.com/newren/git-filter-repo" >&2
    echo "For example: pip install git-filter-repo" >&2
    exit 1
  fi
}

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --remote)
      REMOTE=${2:-origin}; shift 2 ;;
    --no-push)
      PUSH_AFTER=false; shift ;;
    --dry-run)
      DRY_RUN=true; shift ;;
    --force-run)
      FORCE_RUN=true; shift ;;
    --only-paths)
      ONLY_PATHS=true; shift ;;
    --only-globs)
      ONLY_GLOBS=true; shift ;;
    --yes|-y)
      ASSUME_YES=true; shift ;;
    --extra-path)
      EXTRA_PATHS+=("$2"); shift 2 ;;
    --extra-glob)
      EXTRA_GLOBS+=("$2"); shift 2 ;;
    -h|--help)
      sed -n '1,80p' "$0"; exit 0 ;;
    *)
      print_err "Unknown argument: $1"; exit 1 ;;
  esac
done

require_clean_repo
require_filter_repo

# Default removal sets — tuned for this project
DEFAULT_PATHS=(
  "native/assets/samples/resource/"
  "native/assets/samples/target/"
)
DEFAULT_GLOBS=(
  'native/assets/samples/**/*.mp4'
  'native/assets/samples/**/*.mkv'
  'native/assets/samples/**/*.mov'
  'native/assets/samples/**/*.webm'
  'native/assets/samples/**/*.mp3'
  'native/assets/samples/**/*.wav'
  'native/assets/samples/**/*.flac'
)

if $ONLY_PATHS; then
  ALL_PATHS=("${EXTRA_PATHS[@]}")
  ALL_GLOBS=()
elif $ONLY_GLOBS; then
  ALL_PATHS=()
  ALL_GLOBS=("${EXTRA_GLOBS[@]}")
else
  ALL_PATHS=("${DEFAULT_PATHS[@]}" "${EXTRA_PATHS[@]}")
  ALL_GLOBS=("${DEFAULT_GLOBS[@]}" "${EXTRA_GLOBS[@]}")
fi

print_info "Remote: $REMOTE"
print_info "Current branch: $(git rev-parse --abbrev-ref HEAD)"
print_info "Will remove paths:"; printf '  - %s\n' "${ALL_PATHS[@]}"
print_info "Will remove globs:"; printf '  - %s\n' "${ALL_GLOBS[@]}"

if ! confirm "This will rewrite history. Continue"; then
  print_warn "Aborted by user."; exit 1
fi

# Create backup branch
TS=$(date +%Y%m%d-%H%M%S)
BACKUP_BRANCH="backup-before-filter-$TS"
print_info "Creating backup branch: $BACKUP_BRANCH"
if ! $DRY_RUN; then git branch "$BACKUP_BRANCH"; fi

# Build git filter-repo command
CMD=(git filter-repo)
for p in "${ALL_PATHS[@]}"; do CMD+=(--path "$p"); done
for g in "${ALL_GLOBS[@]}"; do CMD+=(--path-glob "$g"); done
CMD+=(--invert-paths)
if $FORCE_RUN; then
  CMD+=(--force)
fi

print_info "Running git filter-repo ..."
if $DRY_RUN; then
  printf 'DRY-RUN: '; printf '%q ' "${CMD[@]}"; echo
else
  "${CMD[@]}"
fi

print_info "Running aggressive GC to drop unreachable blobs..."
if ! $DRY_RUN; then git gc --prune=now --aggressive; fi

if $PUSH_AFTER; then
  print_warn "About to push rewritten history to '$REMOTE' with --force-with-lease."
  if confirm "Push now"; then
    if $DRY_RUN; then
      echo "DRY-RUN: git push --force-with-lease $REMOTE"
    else
      git push --force-with-lease "$REMOTE"
      print_ok "Push completed."
    fi
  else
    print_warn "Skipped push. Remember to push with --force-with-lease later."
  fi
else
  print_warn "--no-push set; not pushing. Remember to push with --force-with-lease."
fi

print_ok "Done. Backup branch: $BACKUP_BRANCH"
print_info "If collaborators exist, coordinate a re-clone or a reset onto the new history."
