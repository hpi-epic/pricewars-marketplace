#!/bin/bash

POSTGRES_URL=jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB

export POSTGRES_URL

/marketplace/wait-for-it.sh postgres:5432 -t 0
sbt ~tomcat:start
