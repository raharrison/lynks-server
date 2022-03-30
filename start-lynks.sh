#!/bin/bash

export CURRENT_UID=$(id -u)
export CURRENT_GUID=$(id -g)

if [ ! -d "media" ]; then
    echo "Media directory not found for volume mount, creating.."
    mkdir media
fi

docker-compose --env-file ./config/.env up "$@"
