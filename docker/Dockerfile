# Note: This build process uses a multi-stage approach to prevent
# unnecessary layers in the image
# For further information see https://docs.docker.com/develop/develop-images/multistage-build/
# --------------------------- Builder Image -------------------------

FROM openjdk:8-jdk AS builder

ARG user=promregator
ARG group=promregator
ARG uid=1000
ARG gid=1000

USER root

# Setup of promregator user
RUN groupadd --gid $gid $group && useradd --gid $gid --uid $uid $user && \
	mkdir -p /home/$user && \
	chown $user.$group /home/$user && \
	chmod 0700 /home/$user && \
	mkdir -p /opt/promregator && \
	chmod 0750 /opt/promregator

COPY data/* /opt/promregator/

RUN chown -R $user.$group /opt/promregator && \
	chmod 0640 /opt/promregator/* && \
	chmod 0770 /opt/promregator/promregator.sh


# --------------------------- Main Image -------------------------

# Note: jre does not include the support tools like jstack etc. That's why
# we use the JDK as foundation.
FROM openjdk:8-jdk

# Warning! We are currently unable to use openjdk9 due to https://github.com/docker-library/openjdk/issues/145
# Warning! If changing to 9-jre, we also have to change the options "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap" in data/promregator.sh


USER root

# Install some very basic tools
RUN apt-get update && apt-get -y install -y \
	apt-transport-https \
	less \
	ca-certificates \
	ca-certificates-java \
	procps \ 
	&& apt-get -q autoremove && apt-get -q clean -y && rm -rf /var/lib/apt/lists/*

# Setup of promregator user (needs to be done again, as it can't be copied)
RUN groupadd --gid $gid $group && useradd --gid $gid --uid $uid $user && \
	mkdir -p /home/$user && \
	chown $user.$group /home/$user && \
	chmod 0700 /home/$user && \
	mkdir -p /etc/promregator && \
	chmod 0750 /etc/promregator

COPY --from=builder /opt/promregator/ /opt/promregator/

USER $user

CMD /opt/promregator/promregator.sh

EXPOSE 8080
