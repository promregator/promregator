
# Note: jre does not include the support tools like jstack etc. That's why
# Warning! This script assumes that the files in data/* already have the right *permissions* set.
# Ownership will be adjusted by this script, though

# we use the JDK as foundation.
FROM openjdk:8-jdk

# Warning! We are currently unable to use openjdk9 due to https://github.com/docker-library/openjdk/issues/145
# Warning! If changing to 9-jre, we also have to change the options "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap" in data/promregator.sh

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
	procps \ 
	&& apt-get -q autoremove && apt-get -q clean -y && rm -rf /var/lib/apt/lists/*

# Setup of promregator user
RUN groupadd --gid $gid $group && useradd --gid $gid --uid $uid $user && \
	mkdir -p /home/$user && \
	chown $user.$group /home/$user && \
	chmod 0700 /home/$user && \
	mkdir -p /etc/promregator && \
	chmod 0750 /etc/promregator && \
	chown -R $user.$group /etc/promregator 

# Setup folder et al.
RUN mkdir -o /opt/promregator && \
	chown -R $user.$group /opt/promregator && \
	chmod 0750 /opt/promregator && \
	chmod 0640 /opt/promregator/* && \

# for the idea, see also https://stackoverflow.com/a/53398981
COPY --chown $user:$group data/* /opt/promregator/

USER $user

CMD /opt/promregator/promregator.sh

EXPOSE 8080
