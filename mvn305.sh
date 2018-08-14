#!/usr/bin/env bash

source /mnt/crawl_j/setcreds.sh

export M2=/opt/apache-maven-3.0.5/bin
export M2_HOME=/opt/apache-maven-3.0.5
export MAVEN_OPTS=-Djava.net.preferIPv4Stack=true
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
/opt/apache-maven-3.0.5/bin/mvn -f $1 $2