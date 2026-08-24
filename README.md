## 🎬 Netflix — Event-Driven Video Streaming Platform

> A production-inspired Netflix-style video streaming backend built with Java, Spring Boot, Kafka, Redis, PostgreSQL, S3 and FFmpeg.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-Event--Driven-black?logo=apachekafka)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-Cache-red?logo=redis)](https://redis.io/)
[![AWS S3](https://img.shields.io/badge/AWS-S3-orange?logo=amazonaws)](https://aws.amazon.com/s3/)
[![FFmpeg](https://img.shields.io/badge/FFmpeg-Video%20Processing-green)](https://ffmpeg.org/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-blue?logo=docker)](https://www.docker.com/)

---

## ⭐ Overview

This project is a backend-focused implementation of a **Netflix-inspired video streaming platform**.

The goal is not to reproduce Netflix's frontend. The goal is to explore the backend engineering problems involved in building a video platform:

- asynchronous video processing
- event-driven microservices
- object storage
- video transcoding
- HLS streaming
- caching
- service isolation
- horizontal scalability
- load testing
- performance analysis

The platform separates latency-sensitive APIs from CPU-intensive video encoding so that each workload can evolve and scale independently.

---

# 🏗️ Architecture

```text
                                      ┌────────────────────┐
                                      │       CLIENT       │
                                      └──────────┬─────────┘
                                                 │
                              ┌──────────────────┴──────────────────┐
                              │                                     │
                              ▼                                     ▼
                    ┌──────────────────┐                   ┌──────────────────┐
                    │ CONTENT SERVICE  │                   │  VIDEO SERVICE   │
                    │    :8081         │                   │     :8082        │
                    └────────┬─────────┘                   └────────┬─────────┘
                             │                                      │
                             ▼                                      ▼
                    ┌──────────────────┐                    ┌───────────────┐
                    │   PostgreSQL     │                    │      S3       │
                    └──────────────────┘                    └───────┬───────┘
                                                                     │
                                                                     │ VideoUploadedEvent
                                                                     ▼
                                                              ┌───────────────┐
                                                              │     Kafka     │
                                                              └───────┬───────┘
                                                                      │
                                                                      ▼
                                                            ┌──────────────────┐
                                                            │ ENCODING SERVICE  │
                                                            │     + FFmpeg      │
                                                            │      :8083        │
                                                            └────────┬─────────┘
                                                                     │
                                                                     │ Encoded assets
                                                                     ▼
                                                                    S3
                                                                     │
                                                                     │
                                                                     ▼
                                                            ┌──────────────────┐
                                                            │ STREAMING SERVICE │
                                                            │      :8084        │
                                                            └────────┬─────────┘
                                                                     │
                                                                     ▼
                                                                   Redis
                                                                     │
                                                                     ▼
                                                                  Client
```

---

# 🧩 Services

| Service | Responsibility | Main Dependencies |
|---|---|---|
| **Content Service** | Movie catalog and metadata | PostgreSQL |
| **Video Service** | Video ingestion and upload | PostgreSQL, S3, Kafka |
| **Encoding Service** | Transcoding and HLS generation | Kafka, S3, FFmpeg |
| **Streaming Service** | Playback metadata and streaming URLs | Redis, S3, Kafka |

---

# 🎞️ Content Service

The Content Service manages the movie catalog and metadata.

### Responsibilities

- movie creation
- movie retrieval
- title search
- genre filtering
- catalog persistence

### Request flow

```text
Client
  │
  ▼
Content Service
  │
  ▼
Spring Data JPA
  │
  ▼
PostgreSQL
  │
  ▼
Movie Response
```

### Example endpoints

```text
GET /api/v1/movies/{movieId}
GET /api/v1/movies
GET /api/v1/movies/search?title=<title>
GET /api/v1/movies/genre/{genre}
```

---

# 📤 Video Service

The Video Service handles video ingestion.

The upload request does **not** synchronously wait for video transcoding.

Instead:

```text
Client
  │
  ▼
Video Service
  │
  ├──────────────► S3
  │
  └──────────────► Kafka
                       │
                       ▼
                 Encoding Service
```

This separates ingestion latency from the much slower encoding workload.

### Responsibilities

- accept video uploads
- store original video in S3
- persist metadata
- publish processing events
- initiate asynchronous encoding

---

# 📨 Event-Driven Processing

Kafka connects the ingestion, encoding and streaming stages.

## Upload event

```text
Video Service
      │
      │ VideoUploadedEvent
      ▼
    Kafka
      │
      ▼
Encoding Service
```

## Encoding completion event

```text
Encoding Service
      │
      │ VideoEncodedEvent
      ▼
    Kafka
      │
      ▼
Streaming Service
```

### Why Kafka?

Using asynchronous events provides:

- loose coupling
- asynchronous processing
- workload buffering
- failure isolation
- independent scaling
- the ability to add additional consumers
- separation between request latency and encoding latency

---

# ⚙️ Encoding Service

The Encoding Service consumes video processing events and uses FFmpeg to create multiple representations.

## Output profiles

| Resolution | Video Bitrate |
|---|---:|
| 1080p | 5 Mbps |
| 720p | 2.8 Mbps |
| 480p | 1.2 Mbps |
| 360p | 0.8 Mbps |

Total video bitrate across the four representations is approximately:

```text
5.0 Mbps
+ 2.8 Mbps
+ 1.2 Mbps
+ 0.8 Mbps
----------------
9.8 Mbps
```

Audio is additional.

## Encoding pipeline

```text
Original Video
      │
      ▼
    FFmpeg
      │
      ├──────► 1080p
      ├──────► 720p
      ├──────► 480p
      └──────► 360p
                 │
                 ▼
             HLS Assets
                 │
                 ▼
                 S3
```

Encoding is isolated because FFmpeg is CPU-intensive and has a very different scaling profile from normal REST APIs.

---

# 📺 Streaming Service

The Streaming Service handles playback-related requests.

The application is intentionally **not the media delivery layer**.

Instead of sending large video files through Spring Boot:

```text
Client
  │
  ▼
Streaming Service
  │
  ▼
Presigned S3 URL
  │
  ▼
S3
  │
  ▼
Video segments
```

This keeps large media payloads away from application instances and allows object storage/CDN infrastructure to handle the actual media transfer.

---

# ⚡ Redis Caching

Redis caches frequently accessed streaming metadata.

```text
Client
  │
  ▼
Streaming Service
  │
  ▼
Redis
  │
  ├──────── HIT ───────► Return cached data
  │
  └──────── MISS
               │
               ▼
              S3
               │
               ▼
             Redis
               │
               ▼
             Client
```

The objective is to reduce repeated S3 access and improve response latency for frequently accessed content.

---

# 📺 HLS Streaming

The encoder generates HLS output with multiple quality levels.

```text
Master Playlist
       │
       ├── 1080p
       │      └── media segments
       │
       ├── 720p
       │      └── media segments
       │
       ├── 480p
       │      └── media segments
       │
       └── 360p
              └── media segments
```

The client can select an appropriate representation based on available bandwidth and playback conditions.

---

# 🔄 End-to-End Video Lifecycle

```text
1. Client uploads video
          │
          ▼
2. Video Service
          │
          ▼
3. Original video → S3
          │
          ▼
4. Publish VideoUploadedEvent
          │
          ▼
5. Kafka
          │
          ▼
6. Encoding Service
          │
          ▼
7. FFmpeg transcoding
          │
          ├── 1080p
          ├── 720p
          ├── 480p
          └── 360p
          │
          ▼
8. Generate HLS assets
          │
          ▼
9. Upload encoded assets → S3
          │
          ▼
10. Publish VideoEncodedEvent
          │
          ▼
11. Streaming Service
          │
          ▼
12. Cache metadata → Redis
          │
          ▼
13. Client requests playback
          │
          ▼
14. Presigned URL
          │
          ▼
15. S3 delivers media
```

---

# 🧠 Key Engineering Decisions

## Why microservices?

The workloads have fundamentally different scaling characteristics.

Content APIs are latency-sensitive and database-bound.

Video encoding is CPU-intensive and potentially long-running.

Streaming metadata access is cache-heavy.

Keeping them separate allows each service to scale independently.

---

## Why Kafka instead of synchronous HTTP?

A video may take significantly longer to encode than a normal API request.

A synchronous design would look like:

```text
Upload
  │
  ▼
Encode
  │
  ▼
Wait...
  │
  ▼
Response
```

The current design is:

```text
Upload
  │
  ▼
S3
  │
  ▼
Kafka
  │
  ▼
Encode asynchronously
```

The upload path therefore does not need to remain blocked while FFmpeg works.

---

## Why S3?

Video objects are large and are better handled by object storage than a relational database.

S3 provides:

- large-object storage
- durability
- independent scaling
- presigned URLs
- separation of storage and compute

---

## Why Redis?

Streaming metadata can be requested repeatedly.

Redis provides low-latency access and reduces repeated work against slower dependencies.

---

## Why HLS?

HLS splits media into segments and supports multiple quality levels.

This makes adaptive bitrate playback possible and allows clients to choose an appropriate representation.

---

# 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Application Framework | Spring Boot |
| REST | Spring MVC |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Messaging | Apache Kafka |
| Cache | Redis |
| Object Storage | AWS S3 |
| Video Processing | FFmpeg |
| Streaming Format | HLS |
| Containers | Docker |
| Load Testing | k6 |

---

# 📁 Repository Structure

```text
netflix-practice/
│
├── content-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── ...
│
├── video-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── ...
│
├── encoding-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── ...
│
├── streaming-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── ...
│
├── docker-compose.yml
├── benchmark.js
└── README.md
```

---

# 🐳 Local Development

## Prerequisites

Install:

- Java 17+
- Docker
- Docker Compose
- Maven
- FFmpeg for non-containerized encoding
- k6 for load testing

---

# 🚀 Running the Platform

Clone the repository:

```bash
git clone https://github.com/likhithrajuuu/netflix-practice.git
cd netflix-practice
```

Build each service:

```bash
cd content-service
./mvnw clean package -DskipTests

cd ../video-service
./mvnw clean package -DskipTests

cd ../streaming-service
./mvnw clean package -DskipTests

cd ../encoding-service
./mvnw clean package -DskipTests

cd ..
```

Start the complete stack:

```bash
docker compose up --build
```

Run detached:

```bash
docker compose up -d --build
```

Stop:

```bash
docker compose down
```

Stop and remove persistent volumes:

```bash
docker compose down -v
```

---

# 🔌 Service Ports

| Component | Port |
|---|---:|
| Content Service | `8081` |
| Video Service | `8082` |
| Encoding Service | `8083` |
| Streaming Service | `8084` |
| PostgreSQL | `5432` |
| Redis | `6379` |
| Kafka | `9092` |
| LocalStack S3 | `4566` |

Inside Docker, services communicate using Compose DNS names:

```text
postgres
redis
kafka
localstack
```

rather than `localhost`.

---

# 🐳 Docker Architecture

```text
                         Docker Compose
                              │
       ┌──────────────────────┼──────────────────────┐
       │                      │                      │
       ▼                      ▼                      ▼
   PostgreSQL               Redis                  Kafka
       │                                             │
       │                                             │
       └──────────────┐                ┌─────────────┘
                      │                │
                      ▼                ▼
                Content Service    Video Service
                                       │
                                       ▼
                                   LocalStack
                                      S3
                                       │
                                       ▼
                                   Encoding
                                       │
                                       ▼
                                      S3
                                       │
                                       ▼
                                   Streaming
                                       │
                                       ▼
                                     Redis
```

For local development, LocalStack can emulate S3 so the complete pipeline can be tested without requiring real AWS resources.

---

# 🧪 Testing

Run unit tests for a service:

```bash
./mvnw test
```

Build without tests:

```bash
./mvnw clean package -DskipTests
```

---

# 📊 Performance & Benchmarking

The project includes a k6-based load-testing workflow.

The benchmark measures:

- throughput
- requests per second
- average latency
- P50 latency
- P95 latency
- P99 latency
- concurrent virtual users
- error rate
- endpoint saturation

For infrastructure-aware testing, CPU and memory utilization should also be observed from Docker.

## Run benchmark

```bash
k6 run benchmark.js
```

For the Content Service:

```bash
CONTENT_URL=http://localhost:8081 \
MOVIE_ID=1 \
k6 run benchmark.js
```

## Metrics

Example benchmark output:

```text
Throughput       <measured>
Average latency  <measured>
P50              <measured>
P95              <measured>
P99              <measured>
Error rate       <measured>
Peak VUs         <measured>
```

> **Important:** Performance numbers in this README should only be populated from actual benchmark runs. The repository does not claim theoretical RPS or latency figures as measured production performance.

---

# 🔬 Benchmark Strategy

Different services require different benchmark models.

## Content Service

Measure:

```text
GET movie
Search movie
Get catalog
```

Metrics:

```text
RPS
P50
P95
P99
Error rate
Database CPU
Database connections
```

## Streaming Service

Separate:

```text
Redis cache hit
Redis cache miss
Playlist generation
```

This distinction is important because a Redis cache hit and an S3-backed request have very different latency profiles.

## Video Service

Measure:

```text
Concurrent uploads
Upload throughput
S3 latency
Request duration
Error rate
```

Large video uploads should not be represented purely as RPS because payload size strongly affects the workload.

## Encoding Service

Encoding should be measured using:

```text
Videos/hour
Video-minutes/hour
Encoding time/video
CPU utilization
Queue depth
Failed encoding jobs
```

RPS is not an appropriate primary metric for FFmpeg processing.

---

# 📈 Scalability Model

## API scaling

```text
                    Load Balancer
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
       Content #1   Content #2   Content #3
            │            │            │
            └────────────┼────────────┘
                         ▼
                     PostgreSQL
```

## Encoding scaling

```text
                       Kafka
                         │
             ┌───────────┼───────────┐
             ▼           ▼           ▼
         Encoder #1  Encoder #2  Encoder #3
             │           │           │
             └───────────┼───────────┘
                         ▼
                         S3
```

Kafka allows encoding workers to consume work independently.

With additional partitions and consumers, the encoding layer can process multiple videos concurrently.

---

# ⚠️ Current Performance Boundaries

The current implementation has several areas that should be addressed before claiming production-scale capacity.

### 1. Catalog retrieval

An unbounded `findAll()` style catalog operation can become expensive as the number of movies grows.

Future improvement:

- pagination
- cursor-based pagination
- projections
- limits

### 2. Title search

A `ContainingIgnoreCase` query can become expensive for a large catalog.

Potential improvements:

- PostgreSQL trigram indexes
- full-text search
- Elasticsearch/OpenSearch

### 3. Video uploads

Synchronous multipart uploads can hold application resources while transferring large files.

Potential improvements:

- S3 multipart upload
- presigned upload URLs
- direct browser-to-S3 upload

### 4. Encoding parallelism

Multiple quality levels can be CPU-intensive.

Potential improvements:

- dedicated encoding workers
- Kafka partitioning
- parallel encoding jobs
- worker autoscaling
- GPU encoding where appropriate

### 5. Streaming

The application should avoid proxying video bytes.

The current architecture uses presigned object-storage access to keep media delivery outside the application layer.

---

# 🔐 Reliability Improvements

Future production-oriented improvements include:

- retries
- dead-letter queues
- idempotent event processing
- circuit breakers
- timeout policies
- backpressure
- health checks
- graceful shutdown
- Kafka consumer monitoring
- distributed tracing

---

# 📊 Observability Roadmap

A production deployment should expose:

```text
Application Metrics
       │
       ├── Request rate
       ├── Error rate
       ├── Latency
       └── JVM metrics
              │
              ▼
          Prometheus
              │
              ▼
            Grafana
```

Recommended metrics:

### API

```text
http_requests_total
http_request_duration_seconds
http_requests_failed_total
```

### Kafka

```text
consumer_lag
messages_processed_total
messages_failed_total
```

### Encoding

```text
encoding_duration
encoding_jobs_total
encoding_failures_total
encoding_queue_depth
```

### Redis

```text
cache_hits
cache_misses
cache_hit_ratio
redis_latency
```

---

# 🗺️ Roadmap

## Phase 1 — Core Platform

- [x] Content Service
- [x] Video Service
- [x] Video upload
- [x] S3 integration
- [x] Kafka event pipeline
- [x] Encoding Service
- [x] FFmpeg processing
- [x] HLS generation
- [x] Streaming Service
- [x] Redis caching
- [x] Docker development environment

## Phase 2 — Production Engineering

- [ ] API Gateway
- [ ] Authentication
- [ ] Authorization
- [ ] Rate limiting
- [ ] Pagination
- [ ] Retry policies
- [ ] Dead-letter queues
- [ ] Idempotency
- [ ] Circuit breakers
- [ ] Distributed tracing
- [ ] Prometheus metrics
- [ ] Grafana dashboards

## Phase 3 — Scale

- [ ] Kafka partition-based encoding workers
- [ ] Horizontal encoder scaling
- [ ] Kubernetes
- [ ] Autoscaling
- [ ] CDN integration
- [ ] Multipart S3 uploads
- [ ] Direct-to-S3 uploads
- [ ] Search optimization
- [ ] Distributed load testing
- [ ] Chaos testing

---

# 💡 What This Project Demonstrates

This project is primarily an exploration of:

### Backend Engineering

- Java
- Spring Boot
- REST APIs
- JPA
- PostgreSQL

### Distributed Systems

- microservices
- asynchronous processing
- Kafka
- event-driven architecture
- independent scaling
- workload isolation

### Video Engineering

- FFmpeg
- HLS
- adaptive bitrate representations
- video segmentation
- object storage

### Performance Engineering

- Redis caching
- load testing
- latency analysis
- throughput analysis
- bottleneck identification

### Infrastructure

- Docker
- Docker Compose
- LocalStack
- containerized services

---

# 🎯 Design Goals

The project is built around a few core principles:

### 1. Keep APIs responsive

Long-running video processing should never unnecessarily block normal API traffic.

### 2. Move large media outside application servers

S3 should handle large video objects rather than Spring Boot.

### 3. Decouple workloads

Encoding should be independently scalable from metadata and streaming APIs.

### 4. Cache frequently accessed data

Redis reduces repeated dependency calls.

### 5. Measure instead of guessing

Capacity should be established using load tests and infrastructure metrics rather than arbitrary RPS claims.

---

# 🤝 Contributing

Contributions are welcome.

```bash
git checkout -b feature/your-feature
```

Make your changes, test them, and open a pull request.

Good contribution areas include:

- performance optimization
- Kafka reliability
- database optimization
- observability
- testing
- Kubernetes
- streaming improvements
- encoding optimization

---

# ⭐ Support the Project

If this project helped you understand:

- Spring Boot
- Kafka
- Redis
- Microservices
- Distributed systems
- Video streaming
- FFmpeg
- System design

consider giving the repository a ⭐.

It helps other developers discover the project.

---

# 👨‍💻 Author

**Likhith Raju**

Software Engineer focused on backend engineering, distributed systems and scalable applications.

GitHub:  
https://github.com/likhithrajuuu

---

# 📜 License

This project is intended for educational and engineering experimentation purposes.
