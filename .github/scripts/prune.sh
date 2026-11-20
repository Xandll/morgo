#!/usr/bin/env bash
# Löscht Workflow-Läufe, Artefakte und Caches älter als 7 Tage.
# Behält die 5 neuesten Releases und löscht ältere Releases samt Tags.
set -euo pipefail

: "${REPO:?}"
cutoff=$(date -u -d "7 days ago" +%Y-%m-%dT%H:%M:%SZ)

gh api --paginate "/repos/${REPO}/actions/runs?per_page=100" \
  --jq ".workflow_runs[] | select(.created_at < \"${cutoff}\") | .id" |
  while read -r id; do
    [ -n "$id" ] || continue
    gh api --method DELETE "/repos/${REPO}/actions/runs/${id}" >/dev/null || true
  done

gh api --paginate "/repos/${REPO}/actions/artifacts?per_page=100" \
  --jq ".artifacts[] | select(.created_at < \"${cutoff}\") | .id" |
  while read -r id; do
    [ -n "$id" ] || continue
    gh api --method DELETE "/repos/${REPO}/actions/artifacts/${id}" >/dev/null || true
  done

gh api --paginate "/repos/${REPO}/actions/caches?per_page=100" \
  --jq ".actions_caches[] | select(.created_at < \"${cutoff}\") | .id" |
  while read -r id; do
    [ -n "$id" ] || continue
    gh api --method DELETE "/repos/${REPO}/actions/caches/${id}" >/dev/null || true
  done

gh api --paginate "/repos/${REPO}/releases?per_page=100" \
  --jq '.[] | select(.published_at != null) | [.published_at, (.id | tostring), .tag_name] | @tsv' |
  sort -r | tail -n +6 | while IFS=$'\t' read -r _published id tag; do
    [ -n "$id" ] || continue
    gh api --method DELETE "/repos/${REPO}/releases/${id}" >/dev/null || true
    if [ -n "$tag" ]; then
      gh api --method DELETE "/repos/${REPO}/git/refs/tags/${tag}" >/dev/null || true
    fi
  done
