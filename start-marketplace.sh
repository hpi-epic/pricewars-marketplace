#!/bin/bash

POSTGRES_URL=jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB
PGPASSWORD=$POSTGRES_PASSWORD

if [[ $PRICEWARS_PRODUCER_URL != http://* ]];then
    PRICEWARS_PRODUCER_URL=http://$PRICEWARS_PRODUCER_URL
    export PRICEWARS_PRODUCER_URL
fi

export POSTGRES_URL
export PGPASSWORD

/marketplace/wait-for-it.sh postgres:5432 -t 0
sbt ~tomcat:start &

# Getting the process and waiting for it to ensure the container will stay up
PID_MARKETPLACE=$!

/marketplace/wait-for-it.sh marketplace:8080 -t 0
sleep 20

for file in /db-seeds/*; do
    if [ "${file}" != "${file%.sql}" ];then
        echo "Processing $file ..."
        psql -a -d $POSTGRES_DB -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -f $file
    fi
done

wait $PID_MARKETPLACE
