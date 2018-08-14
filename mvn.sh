#!/usr/bin/env bash

source /mnt/crawl_j/setcreds.sh

export M2_HOME=/opt/mvn
export M2=/opt/mvn/bin
export MAVEN_OPTS=-Djava.net.preferIPv4Stack=true
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

/opt/mvn/bin/mvn -f $1 $2