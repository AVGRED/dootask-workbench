#!/usr/bin/env bash

# Create a portable git bundle of the commits that are not on the upstream branch.
# This is a local fallback for "the work only exists on this machine": a bundle can
# be copied to another host and unbundled on top of the same base commit.
#
# Usage: scripts/backup-local-bundle.sh [base-ref]
#   base-ref defaults to the upstream of the current branch, then origin/pro.
#   DOOTASK_BACKUP_DIR overrides the output directory (default: <repo>/../backups).
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="${DOOTASK_BACKUP_DIR:-$project_root/../backups}"

base="${1:-}"
if [[ -z "${base:-}" ]]; then
    base="$(git -C "$project_root" rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)"
    base="${base:-origin/pro}"
fi
if ! git -C "$project_root" rev-parse --verify --quiet "$base" >/dev/null; then
    echo "Base ref '$base' does not exist in this repository." >&2
    exit 2
fi

count="$(git -C "$project_root" rev-list --count "$base..HEAD")"
if [[ "$count" == "0" ]]; then
    echo "Nothing to back up: HEAD is at or behind $base."
    exit 0
fi

mkdir -p "$out_dir"
file="$out_dir/dootask-$(date +%Y%m%d-%H%M%S).bundle"
git -C "$project_root" bundle create "$file" "$base..HEAD" >/dev/null
git -C "$project_root" bundle verify "$file" >/dev/null

echo "commits=$count base=$base"
echo "$file"
echo "restore on another clone at $base: git bundle unbundle \"$file\""

copy_dir="${DOOTASK_BACKUP_COPY_DIR:-}"
if [[ -n "$copy_dir" ]]; then
    mkdir -p "$copy_dir"
    cp "$file" "$copy_dir/"
    target="$copy_dir/$(basename "$file")"
    if [[ "$(sha256sum "$file" | awk '{print $1}')" != "$(sha256sum "$target" | awk '{print $1}')" ]]; then
        echo "Checksum mismatch after copying to $copy_dir" >&2
        exit 3
    fi
    echo "copied_to=$target"
fi
