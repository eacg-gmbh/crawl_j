#!/usr/bin/env bash

export SECRETS_BUCKET_NAME=secrets.ve.eacg.de
source /mnt/crawl_j/setcreds.sh

export M2=/opt/apache-maven-3.0.5/bin
export M2_HOME=/opt/apache-maven-3.0.5
export MAVEN_OPTS=-Djava.net.preferIPv4Stack=true
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export MC_PORT_11211_TCP_PORT=11211
export MC_PORT_11211_TCP_ADDR=memcached-service.ve.eacg.intern
export RM_PORT_5672_TCP_PORT=5672
export RM_PORT_5672_TCP_ADDR=rabbitmq-service.ve.eacg.intern
export DB_PORT_27017_TCP_PORT=27017
export DB_PORT_27017_TCP_ADDR=db1.ve.eacg.intern
export RAILS_ENV=enterprise

rm -rf /tmp/*
/opt/apache-maven-3.0.5/bin/mvn -f $1 $2  >> /dev/stdout 2>&1
rm -rf /tmp/*
