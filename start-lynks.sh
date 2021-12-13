#!/bin/bash

docker-compose up --env-file ./config/.env "$@"
