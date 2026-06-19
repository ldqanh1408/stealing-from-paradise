# Kafka KRaft mode — single node, no Zookeeper needed.
# Kafka 3.3+ supports KRaft (Kafka Raft) which eliminates Zookeeper.
# Perfect for Render's single-container constraint.
ARG KAFKA_VERSION=3.6.1
FROM apache/kafka:${KAFKA_VERSION}

# Pre-format KRaft storage with a fixed cluster ID
RUN /opt/kafka/bin/kafka-storage.sh format \
    --ignore-formatted \
    --cluster-id KQA0ZmQwS6yABCDEFGHIJQ \
    --config /opt/kafka/config/kraft/server.properties

EXPOSE 9092

CMD ["/opt/kafka/bin/kafka-server-start.sh", "/opt/kafka/config/kraft/server.properties"]
