# ZooKeeper & Kafka Error Summary (Tổng Kết Lỗi)

## 🔴 Lỗi Ban Đầu

### Error 1: ZooKeeper Crash
```
java.io.IOException: Len error 1650553704
    at org.apache.zookeeper.server.NIOServerCnxn.readLength(NIOServerCnxn.java:549)
    at org.apache.zookeeper.server.NIOServerCnxn.doIO(NIOServerCnxn.java:340)
    at org.apache.zookeeper.server.NIOServerCnxnFactory$IOWorkRequest.doWork(NIOServerCnxnFactory.java:522)
```

**Nguyên nhân chính:**
- Health check quá phức tạp + không tương thích với container
- Cấu hình ZooKeeper có các tham số không cần thiết gây xung đột
- Dữ liệu bị corrupted từ lần chạy trước (volume reuse)

### Error 2: Kafka Connection Refused
```
java.net.ConnectException: Connection refused
    at java.base/sun.nio.ch.SocketChannelImpl.checkConnect(Native Method)
    at org.apache.zookeeper.ClientCnxnSocketNIO.doTransport(ClientCnxnSocketNIO.java:344)

[Kafka logs]
EndOfStreamException: Unable to read additional data from server sessionid 0x100000cfe750000, 
likely server has closed socket
```

**Nguyên nhân chính:**
- Kafka phụ thuộc vào `service_started` thay vì `service_healthy`
- ZooKeeper không khởi động xong khi Kafka bắt đầu kết nối
- Không có delay giữa startup của ZooKeeper và Kafka

---

## ✅ Giải Pháp Cuối Cùng

### 1. **Đơn Giản Hóa Cấu Hình ZooKeeper**

❌ **Cũ (gây lỗi):**
```yaml
zookeeper:
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_SERVER_ID: 1                    # ← Gây xung đột
    ZOOKEEPER_SERVERS: zookeeper:2888:3888    # ← Gây xung đ突
    ZOOKEEPER_SYNC_LIMIT: 10
    ZOOKEEPER_INIT_LIMIT: 60
    ZOO_CFG_EXTRA: "..."                      # ← Gây xung đột
  healthcheck:
    test: ["CMD", "bash", "-c", "echo ruok | timeout 1 bash -i >& /dev/tcp/127.0.0.1/2181 && echo imok || exit 1"]
    # ↑ Health check không hoạt động trong container
```

✅ **Mới (hoạt động):**
```yaml
zookeeper:
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181    # Đủ!
    ZOOKEEPER_TICK_TIME: 2000      # Đủ!
  # Không cần health check phức tạp
```

### 2. **Sửa Dependencies - Service Health vs Service Started**

❌ **Cũ:**
```yaml
kafka:
  depends_on:
    zookeeper: { condition: service_healthy }  # Chờ health check → LỖI
```

✅ **Mới:**
```yaml
kafka:
  depends_on:
    - zookeeper  # Chỉ chờ container khởi động
  command: bash -c "sleep 10 && ..."  # Delay 10s để ZooKeeper sẵn sàng
```

### 3. **Loại Bỏ Health Checks Không Cần Thiết cho Kafka**

❌ **Cũ:**
```yaml
healthcheck:
  test: ["CMD-SHELL", "kafka-broker-api-versions.sh --bootstrap-server=localhost:9092"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 40s
```

✅ **Mới:** Bỏ health check - chỉ dùng `service_started`

### 4. **Thêm Timeout Cho Kafka-Zookeeper Connection**

```yaml
environment:
  KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS: 30000         # Session timeout
  KAFKA_ZOOKEEPER_CONNECTION_TIMEOUT_MS: 30000      # Connection timeout
```

---

## 📊 Sơ Đồ Vấn Đề & Giải Pháp

```
┌─────────────────────────────────────────────────────────┐
│               PROBLEM: ZooKeeper Won't Start              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ 1. Complex Configuration (Server ID, Peers, etc.)       │
│    └─> Causes: Port binding conflicts                  │
│    └─> Causes: Invalid configuration parsing           │
│                                                          │
│ 2. Complex Health Check                                 │
│    └─> Container health check can't pass               │
│    └─> Kafka can't wait for healthy status             │
│                                                          │
│ 3. No Startup Delay                                     │
│    └─> Kafka starts immediately                        │
│    └─> ZooKeeper not ready yet                         │
│    └─> Connection refused                              │
│                                                          │
│ 4. Corrupted Data from Previous Runs                    │
│    └─> Len error 1650553704 (invalid data)            │
│    └─> Need to cleanup volumes                         │
│                                                          │
├─────────────────────────────────────────────────────────┤
│              SOLUTION: Simplify Everything               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ 1. ✅ Remove complex ZK config                          │
│    └─> Keep: CLIENT_PORT + TICK_TIME only             │
│                                                          │
│ 2. ✅ Remove health checks                              │
│    └─> Use simple depends_on with delay                │
│                                                          │
│ 3. ✅ Add startup delay to Kafka                        │
│    └─> sleep 10 before kafka-server-start              │
│                                                          │
│ 4. ✅ Clean volumes before startup                      │
│    └─> docker-compose down -v --remove-orphans        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 🔑 Root Cause (Nguyên Nhân Gốc)

### Chính:
1. **Over-configuration of ZooKeeper**
   - Thêm quá nhiều tham số không cần thiết cho single-node setup
   - Server ID, Peer communication config không cần cho standalone mode

2. **Misuse of Health Checks**
   - Dùng `service_healthy` condition nhưng health check không reliable
   - Health check command (`echo ruok | nc ...`) không hoạt động trong container

3. **Race Condition**
   - Kafka bắt đầu trước khi ZooKeeper fully initialized
   - Không có delay buffer

### Phụ:
- Data corruption từ previous runs
- Timeout settings quá ngắn
- Reuse of corrupted volumes

---

## 📝 Checklist Cho Lần Sau

- [ ] Keep ZooKeeper config minimal (only CLIENT_PORT + TICK_TIME)
- [ ] Avoid health checks nếu không absolutely necessary
- [ ] Use `depends_on: - service` thay vì `service_healthy` condition
- [ ] Add startup delay (`sleep N`) cho dependent services
- [ ] Clean volumes before running: `docker-compose down -v --remove-orphans`
- [ ] Use `service_started` instead of `service_healthy` in depends_on
- [ ] Add connection timeout configs for message queue clients

---

## 🎯 Final Working Configuration

```yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.4.0
  container_name: fs-zookeeper
  ports: ["2181:2181"]
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
  volumes: [zookeeper_data:/var/lib/zookeeper/data]
  networks: [flashsale-net]
  # NO HEALTH CHECKS!

kafka:
  image: confluentinc/cp-kafka:7.4.0
  container_name: fs-kafka
  depends_on:
    - zookeeper  # Simple dependency
  ports: ["9092:9092"]
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    # ... other settings ...
    KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS: 30000
    KAFKA_ZOOKEEPER_CONNECTION_TIMEOUT_MS: 30000
  volumes: [kafka_data:/var/lib/kafka/data]
  networks: [flashsale-net]
  command: bash -c "sleep 10 && rm -f /var/lib/kafka/data/meta.properties && rm -rf /var/lib/kafka/data/log-* && /etc/confluent/docker/run"
  # NO HEALTH CHECKS!
```

---

**Bài Học:** 
> *Simplicity is better than complexity. Less configuration = fewer things to break.*

**Thời Gian Sửa:** 2026-04-06 (2+ giờ debugging)  
**Độ Khó:** 8/10 (vì health check + race condition interactions)

