#!/usr/bin/env bash

source /mnt/crawl_j/setcreds.sh

/opt/mvn/bin/mvn -f /mnt/crawl_j/versioneye_maven_crawler/pom.xml crawl:maven_index_worker >> /dev/stdout 2>&1
