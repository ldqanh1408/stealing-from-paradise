# Search Service Operations

**Service:** search-service | **Port:** 8091 | **Database:** Elasticsearch (index: skus)

## Overview

Full-text product search with Vietnamese language analysis. Consumes product Kafka events to keep the `skus` index in sync. Supports aliased index swapping for zero-downtime reindexing.

## Elasticsearch Resources

| Resource | Type | Purpose |
|---|---|---|
| `skus` | Index alias | Active search index (points to `skus_v1`, `skus_v2`, ...) |
| `skus_v{N}` | Index | Versioned backing index for alias swap |
| `vn_analyzer` | Custom analyzer | Vietnamese tokenization and stopword removal |

## Running Locally

```bash
docker-compose up -d search-service
# Standalone: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `ELASTICSEARCH_URI` | Yes | `http://localhost:9200` | Elasticsearch connection URI |
| `ELASTICSEARCH_INDEX` | Yes | `skus` | Index alias name |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |

## Health Check

```
GET /actuator/health
```

## Common Operational Tasks

### Reindex All Products
```bash
curl -X POST "http://localhost:8091/api/v1/search/reindex"
curl "http://localhost:8091/api/v1/search/reindex/status"
```

### Check Index Health
```bash
curl -s "http://localhost:9200/_cluster/health?pretty"
curl -s "http://localhost:9200/_cat/indices/skus*?v"
```

### Verify Consumer Lag
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 --group search-service --describe
```

### Swap Index Alias
```bash
# Create new index from template (if needed)
curl -X PUT "http://localhost:9200/skus_v2" -H 'Content-Type: application/json' -d '{
  "mappings": { "properties": { "name": { "type": "text", "analyzer": "vn_analyzer" } } }
}'
# Atomically swap alias
curl -X POST "http://localhost:9200/_aliases" -H 'Content-Type: application/json' -d '{
  "actions": [
    { "remove": { "index": "skus_v1", "alias": "skus" } },
    { "add":    { "index": "skus_v2", "alias": "skus" } }
  ]
}'
# Verify
curl "http://localhost:9200/_alias/skus"
```

### Check Vietnamese Text Analysis
```bash
curl -X POST "http://localhost:9200/skus/_analyze" -H 'Content-Type: application/json' \
  -d '{"analyzer": "vn_analyzer", "text": "ao thun nam chat lieu cotton"}'
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Search returns no results | Index empty or alias missing | `curl localhost:9200/_cat/indices/skus*?v` |
| Vietnamese search poor | Analyzer not applied | Test with `/_analyze` endpoint |
| Index out of sync | Kafka consumer lag | `kafka-consumer-groups --describe` for search-service group |
| Cluster yellow/red | Unassigned shards or node down | `curl localhost:9200/_cluster/health?pretty` |
| Reindex stuck | ES under load or timeout | `curl localhost:9200/_tasks?actions=*reindex*` |
| Slow queries | Missing mapping optimization | `curl localhost:9200/skus/_mapping?pretty` |
