#!/bin/bash

docker exec -t lynks_postgres_1 pg_dumpall -c -U lynksuser -d lynksdb > /tmp/db.dump
docker cp lynks_postgres_1:/tmp/db.dump dump_$(date +"%Y-%m-%d_%H_%M_%S").sql

# Restore
# docker cp db.dump lynks_postgres_1:/tmp/db_restore.dump
# docker exec -t lynks_postgres_1 pg_restore -c -U lynksuser -d lynksdb /tmp/db_restore.dump
