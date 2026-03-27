FROM confluentinc/cp-kafka:7.4.0

COPY infra/kafka-init/clean-startup.sh /usr/local/bin/clean-startup.sh
RUN chmod +x /usr/local/bin/clean-startup.sh

ENTRYPOINT ["/usr/local/bin/clean-startup.sh"]

