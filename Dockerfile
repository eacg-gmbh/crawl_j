FROM        java:8
MAINTAINER  Robert Reiz <reiz@versioneye.com>
#crawlj_worker image!!

ENV RAILS_ENV enterprise
ENV M2_HOME /opt/mvn
ENV M2 /opt/mvn/bin
ENV PATH $PATH:/opt/mvn/bin
ENV MAVEN_OPTS -Djava.net.preferIPv4Stack=true

RUN mkdir -p /opt; \
    wget -O /opt/apache-maven-3.3.9-bin.tar.gz http://apache.lauf-forum.at/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz; \
    cd /opt/; tar -xzf apache-maven-3.3.9-bin.tar.gz; \
    ln -f -s /opt/apache-maven-3.3.9 /opt/mvn; \
    mkdir -p /mnt/crawl_j;

ADD . /mnt/crawl_j

ADD mongo.properties /mnt/crawl_j/j_versioneye_persistence/src/test/resources/mongo.properties
ADD mongo.properties /mnt/crawl_j/j_versioneye_service/src/test/resources/mongo.properties
ADD mongo.properties /mnt/crawl_j/jcrawler/src/test/resources/mongo.properties
ADD mongo.properties /mnt/crawl_j/versioneye_maven_crawler/src/test/resources/mongo.properties
ADD mongo.properties /mnt/crawl_j/versioneye_maven_crawler/src/main/resources/mongo.properties
ADD settings.properties /mnt/crawl_j/versioneye_maven_crawler/src/main/resources/settings.properties

RUN cd /mnt/crawl_j; /opt/mvn/bin/mvn clean install -Dmaven.test.skip=true -Dgpg.skip; \
    apt-get update && apt-get install -y supervisor; \
    mkdir -p /var/log/supervisor; \
    cp supervisord.conf /etc/supervisor/conf.d/supervisord.conf;

WORKDIR /mnt/crawl_j

CMD ["/usr/bin/supervisord"]
