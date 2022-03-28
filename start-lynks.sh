#!/bin/bash

export CURRENT_UID=$(id -u)
export CURRENT_GUID=$(id -g)

docker-compose --env-file ./config/.env up "$@"
