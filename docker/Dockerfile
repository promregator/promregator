
# Note: jre does not include the support tools like jstack etc. That's why
# Warning! This script assumes that the files in data/* already have the right *permissions* set.
# Ownership will be adjusted by this script, though

# we use the JDK as foundation.
FROM openjdk:8-jdk

# Warning! We are currently unable to use openjdk9 due to https://github.com/docker-library/openjdk/issues/145
# Warning! If changing to 9-jre, we also have to change the options "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap" in data/promregator.sh

ARG uid=1000
ARG gid=1000

USER root

# Install some very basic tools
# Note: Don't install ca-certificates-java - or we will run into the issue 
# as reported in https://github.com/promregator/promregator/issues/169
# For the same reason we are also "holding back" the usual jre/jdk suspects, 
# as the parent image already provisions the appropriate Java version.
# Holding back might end up in a error message like "Depends: default-jre-headless
# but it is not going to be installed" which then would indicate that 
# something tried to install the debian's JDK version -- which we must not install.

RUN apt-get update && \
	apt-mark hold openjdk-11-jre-headless default-jre-headless java-common && \
	apt-get -y install -y \
	apt-transport-https \
	less \
	ca-certificates \
	procps \ 
	&& apt-get -q autoremove && apt-get -q clean -y && rm -rf /var/lib/apt/lists/*


# Setup of promregator user
RUN groupadd --gid $gid promregator && useradd --gid $gid --uid $uid promregator && \
	mkdir -p /home/promregator && \
	chown promregator.promregator /home/promregator && \
	chmod 0700 /home/promregator && \
	mkdir -p /etc/promregator && \
	chmod 0750 /etc/promregator && \
	chown -R promregator.promregator /etc/promregator 

# Setup folder et al.
RUN mkdir -p /opt/promregator && \
	chown -R promregator.promregator /opt/promregator && \
	chmod 0750 /opt/promregator 

# for the idea, see also https://stackoverflow.com/a/53398981
COPY --chown=promregator:promregator data/* /opt/promregator/

USER promregator

CMD /opt/promregator/promregator.sh

EXPOSE 8080
