
# Note: jre does not include the support tools like jstack etc. That's why we are using JDK here instead.

# Warning! This script assumes that the files in data/* already have the right *permissions* set.
# Ownership will be adjusted by this script, though

# we use the JDK as foundation.
FROM openjdk:17-jdk

ARG uid=1000
ARG gid=1000

USER root

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
	chmod 0750 /opt/promregator && \
	cd /etc/pki/ca-trust/extracted/java && \
	keytool -list -cacerts -storepass changeit

# for the idea, see also https://stackoverflow.com/a/53398981
COPY --chown=promregator:promregator data/* /opt/promregator/

USER promregator

CMD /opt/promregator/promregator.sh

EXPOSE 8080
