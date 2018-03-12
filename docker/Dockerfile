FROM openjdk:9-jre

ARG user=promregator
ARG group=promregator
ARG uid=1000
ARG gid=1000

USER root

# Install some very basic tools
RUN apt-get update && apt-get -y install -y \
	apt-transport-https \
	less \
	ca-certificates \
	ca-certificates-java \
	&& apt-get -q autoremove && apt-get -q clean -y && rm -rf /var/lib/apt/lists/*

# Workaround for keystore.type bug in openjdk9
# see also https://github.com/docker-library/openjdk/issues/145
RUN sed -i 's/keystore.type=pkcs12/keystore.type=jks/g' /docker-java-home/conf/security/java.security && \
	cat /docker-java-home/conf/security/java.security | grep keystore.type

# Setup of promregator user
RUN groupadd --gid $gid $group && useradd --gid $gid --uid $uid $user && \
	mkdir -p /home/$user && \
	chown $user.$group /home/$user && \
	chmod 0700 /home/$user && \
	mkdir -p /opt/promregator /etc/promregator && \
	chmod 0750 /opt/promregator /etc/promregator

COPY data/* /opt/promregator/

RUN chown -R $user.$group /opt/promregator /etc/promregator && \
	chmod 0640 /opt/promregator/* && \
	chmod 0770 /opt/promregator/promregator.sh 

USER $user

CMD /opt/promregator/promregator.sh

EXPOSE 8080
