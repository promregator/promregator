FROM openjdk:9-jre

ARG user=promregator
ARG group=promregator
ARG uid=1000
ARG gid=1000

USER root

RUN groupadd --gid $gid $group && useradd --gid $gid --uid $uid $user

COPY data/* /opt/promregator/

RUN mkdir -p /etc/promregator && \
	chown -R promregator.promregator /opt/promregator && \
	chmod 0750 /opt/promregator /etc/promregator && \
	chmod 0640 /opt/promregator/*

USER $user

CMD ["/bin/bash", "-c /opt/promregator/promregator.sh"]

EXPOSE 8080
