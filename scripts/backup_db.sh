#!/bin/bash

set -e

if [ $# -eq 0 ]; then
    echo "Expected path to config directory"
    exit 1
fi

CONFIG_PATH=$1

source "$CONFIG_PATH"

POSTGRES_CONTAINER=lynks-postgres-1
DUMP_FILENAME=dump_$(date +"%Y-%m-%d_%H_%M_%S").sql

docker exec -t $POSTGRES_CONTAINER bash -c 'pg_dumpall -c -U $POSTGRES_USER -l $POSTGRES_DB > /tmp/lynksdb.dump'
docker cp $POSTGRES_CONTAINER:/tmp/lynksdb.dump "$DUMP_FILENAME"

echo "Database dump saved to ${DUMP_FILENAME}"


# Restore

#docker cp db.dump $POSTGRES_CONTAINER:/tmp/db_restore.dump
#docker exec -t $POSTGRES_CONTAINER pg_restore -c -U $POSTGRES_USER -l $POSTGRES_DB /tmp/db_restore.dump
