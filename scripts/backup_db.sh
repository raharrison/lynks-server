#!/bin/bash

set -e

if [ $# -lt 2 ]; then
    echo "Expected path to config directory containing .env file and target dir"
    echo "E.g. ./backup_db.sh ./lynks/config ./backups"
    exit 1
fi

CONFIG_PATH=$1
TARGET_DIR=$2

# retrieve Postgres credentials from .env file in main config dir
source "$CONFIG_PATH/.env"

POSTGRES_CONTAINER=lynks-postgres-1
DUMP_FILEPATH=$TARGET_DIR/dump_$(date +"%Y-%m-%d_%H_%M").gz

docker exec -t $POSTGRES_CONTAINER bash -c 'pg_dumpall -c -U $POSTGRES_USER -l $POSTGRES_DB | gzip > /tmp/lynksdb.dump'
docker cp $POSTGRES_CONTAINER:/tmp/lynksdb.dump "$DUMP_FILEPATH"

echo "Database dump saved to ${DUMP_FILEPATH}"


# Restore

#docker cp db.dump $POSTGRES_CONTAINER:/tmp/db_restore.dump
#docker exec -t $POSTGRES_CONTAINER pg_restore -c -U $POSTGRES_USER -l $POSTGRES_DB /tmp/db_restore.dump
