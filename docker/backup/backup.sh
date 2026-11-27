#!/bin/sh
# Periodic PostgreSQL backup for the BonFlow compose stack.
#
# Runs in a postgres client container (same major version as the database). Connection settings
# come from the standard PG* environment variables. Every BACKUP_INTERVAL_SECONDS a dump in
# PostgreSQL custom format (-Fc, compressed, restorable with pg_restore) is written to /backups;
# dumps older than BACKUP_KEEP_DAYS are deleted. A failed run keeps all older dumps.
set -eu

DIR=/backups
INTERVAL="${BACKUP_INTERVAL_SECONDS:-86400}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"

mkdir -p "$DIR"
echo "backup: every ${INTERVAL}s to ${DIR}, keeping ${KEEP_DAYS} days"

while true; do
  stamp="$(date +%Y%m%d-%H%M%S)"
  tmp="$DIR/.bonflow-$stamp.tmp"
  if pg_dump --format=custom --file="$tmp"; then
    mv "$tmp" "$DIR/bonflow-$stamp.dump"
    echo "backup: bonflow-$stamp.dump written ($(du -h "$DIR/bonflow-$stamp.dump" | cut -f1))"
    find "$DIR" -name 'bonflow-*.dump' -type f -mtime "+$KEEP_DAYS" -print -delete
  else
    rm -f "$tmp"
    echo "backup: FAILED at $stamp, older dumps are kept" >&2
  fi
  sleep "$INTERVAL"
done
