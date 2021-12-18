#!/bin/bash

docker exec -t lynks-server_postgres pg_dumpall -c -U lynksuser -d lynksdb > /tmp/db.dump
docker cp lynks-server_postgres:/tmp/db.dump dump_$(date +"%Y-%m-%d_%H_%M_%S").sql

# Restore
# docker cp db.dump lynks-server_postgres:/tmp/db_restore.dump
# docker exec -t lynks-server_postgres pg_restore -c -U lynksuser -d lynksdb /tmp/db_restore.dump
