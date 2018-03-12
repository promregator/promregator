FROM openjdk:9-jre

ARG user=promregator
ARG group=promregator
ARG uid=1000
ARG gid=1000

USER root

RUN groupadd --gid $gid $group && useradd --gid $gid --uid $uid $user && \
	mkdir -p /home/promregator && \
	chown promregator.promregator /home/promregator && \
	chmod 0700 /home/promregator 

COPY data/* /opt/promregator/

RUN rm -f mkdir -p /etc/promregator && \
	chown -R promregator.promregator /opt/promregator && \
	chmod 0750 /opt/promregator /etc/promregator && \
	chmod 0640 /opt/promregator/* && \
	chmod 0770 /opt/promregator/promregator.sh 

USER $user

CMD ["/bin/bash", "-c /opt/promregator/promregator.sh"]

EXPOSE 8080
