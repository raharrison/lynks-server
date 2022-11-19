#!/bin/bash

set -e

# assuming Lynks installation is located in /home/user/lynks
BASE_DIR=/home/$USER

# Create new database dump
$BASE_DIR/lynks/scripts/backup_db.sh $BASE_DIR/lynks/config/ $BASE_DIR/bak/db_backup

export $(cat $BASE_DIR/bak/restic.properties | xargs)

# Push media and db backups to remote store
$BASE_DIR/bak/restic backup --exclude=$BASE_DIR/lynks/media/temp --tag=media,db $BASE_DIR/lynks/media $BASE_DIR/bak/db_backup
