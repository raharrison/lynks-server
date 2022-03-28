#!/bin/bash

set -e

if [ $# -eq 0 ]; then
    echo "Expected path to config directory"
    exit 1
fi

CONFIG_PATH=$1

source "$CONFIG_PATH"

DUMP_FILENAME=dump_$(date +"%Y-%m-%d_%H_%M_%S").sql

docker exec -t lynks_postgres_1 pg_dumpall -c -U "$POSTGRES_USER" -d "$POSTGRES_DB" > /tmp/db.dump
docker cp lynks_postgres_1:/tmp/db.dump "$DUMP_FILENAME"

echo "Database dump saved to ${DUMP_FILENAME}"


# Restore
# docker cp db.dump lynks_postgres_1:/tmp/db_restore.dump
# docker exec -t lynks_postgres_1 pg_restore -c -U lynksuser -d lynksdb /tmp/db_restore.dump
