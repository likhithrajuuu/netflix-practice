# Netflix Practice

A **Netflix-inspired distributed video streaming platform** built to explore backend architecture, microservices, video processing, encoding, and content delivery using **Java and Spring Boot**.

The project is focused on understanding how a large-scale video streaming platform can be decomposed into independent services responsible for managing content, processing videos, generating streaming-ready assets, and serving video content to clients.

> **Note:** This is an educational/practice project inspired by the architecture and engineering challenges of modern video streaming platforms. It is not affiliated with Netflix.

---

## Architecture

The platform is divided into multiple Spring Boot microservices, with each service owning a specific responsibility.

```text
                         ┌──────────────────┐
                         │      Client      │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │ Streaming Service│
                         └────────┬─────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
           ┌─────────────────┐        ┌─────────────────┐
           │ Content Service │        │  Video Service  │
           └─────────────────┘        └────────┬────────┘
                                               │
                                               ▼
                                      ┌──────────────────┐
                                      │ Encoding Service  │
                                      └──────────────────┘
```

### Services

| Service               | Responsibility                                                               |
| --------------------- | ---------------------------------------------------------------------------- |
| **Content Service**   | Manages video/content metadata and catalog-related operations                |
| **Encoding Service**  | Handles video encoding and preparation of media assets for streaming         |
| **Streaming Service** | Responsible for streaming-related operations and coordinating video delivery |
| **Video Service**     | Handles video-related operations and access to video assets                  |

---

## Core Concepts

This project is primarily focused on learning and implementing backend concepts involved in a video streaming platform:

* Microservice architecture
* Service decomposition
* RESTful APIs
* Spring Boot
* Spring Data JPA
* PostgreSQL
* Video processing and encoding
* Streaming workflows
* Content management
* Inter-service communication
* Separation of responsibilities
* Scalable backend architecture

---

## Tech Stack

### Backend

* Java 17
* Spring Boot
* Spring Web MVC
* Spring Data JPA
* Spring Validation
* Spring Actuator
* Maven
* Lombok

### Database

* PostgreSQL

### Architecture

* Microservices
* REST APIs
* Distributed service architecture
* Video processing pipeline

---

## Project Structure

```text
netflix-practice/
│
├── content-service/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── encoding-service/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── streaming-service/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── video-service/
│   ├── src/
│   ├── pom.xml
│   └── ...
```

---

## Service Responsibilities

### Content Service

The Content Service acts as the catalog/content management component of the platform.

It is responsible for operations around the metadata associated with videos and streaming content.

The service is implemented using Spring Boot, Spring Data JPA, PostgreSQL, validation, and Actuator.

---

### Encoding Service

The Encoding Service represents the media-processing portion of the platform.

A video uploaded to a streaming platform generally cannot be served directly in a single format. It needs to be processed into streaming-friendly representations.

This service is responsible for the encoding side of that workflow.

Conceptually:

```text
Original Video
      │
      ▼
Encoding Service
      │
      ├── Video Processing
      ├── Format Conversion
      └── Streaming-ready Assets
```

---

### Video Service

The Video Service is responsible for video-specific operations and acts as a dedicated boundary around video assets.

Separating video operations from content metadata allows the system to evolve the video-processing and delivery components independently from the content catalog.

---

### Streaming Service

The Streaming Service represents the client-facing streaming workflow.

Instead of coupling clients directly to individual backend components, the streaming layer provides a dedicated service boundary for streaming operations.

Conceptually:

```text
Client
  │
  ▼
Streaming Service
  │
  ├── Content Information
  │
  └── Video Access
          │
          ▼
      Video Service
```

---

## Video Processing Flow

A simplified video lifecycle looks like:

```text
             Video Upload
                  │
                  ▼
          ┌───────────────┐
          │ Video Service │
          └───────┬───────┘
                  │
                  ▼
          ┌───────────────┐
          │   Encoding    │
          │    Service    │
          └───────┬───────┘
                  │
                  ▼
        Streaming-ready Video
                  │
                  ▼
          ┌───────────────┐
          │   Streaming   │
          │    Service    │
          └───────┬───────┘
                  │
                  ▼
                Client
```

The architecture separates **content metadata**, **video processing**, and **streaming concerns** rather than placing the entire workflow inside a single application.

---

## Running the Services

Each service is an independent Spring Boot Maven application.

Navigate into the required service:

```bash
cd content-service
```

Run the application using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

Repeat the same process for the other services:

```bash
cd encoding-service
./mvnw spring-boot:run
```

```bash
cd streaming-service
./mvnw spring-boot:run
```

```bash
cd video-service
./mvnw spring-boot:run
```

---

## Building

To build an individual service:

```bash
./mvnw clean package
```

To run the test suite:

```bash
./mvnw test
```

---

## Design Goals

The primary goal of this project is not to reproduce the Netflix UI.

Instead, the project focuses on understanding the **backend engineering problems behind a video streaming platform**, including:

1. How to decompose a large system into independently deployable services.
2. How content metadata and video assets can be separated.
3. How video processing can be isolated from the streaming path.
4. How streaming requests can be handled independently from content management.
5. How services can evolve independently while maintaining clear boundaries.
6. How a video moves through the system from ingestion to streaming.

---

## Future Improvements

Potential extensions to the platform include:

* API Gateway
* Service discovery
* Centralized configuration
* Kafka-based asynchronous communication
* Event-driven video processing
* Object storage such as Amazon S3
* HLS/DASH adaptive bitrate streaming
* CDN integration
* Redis caching
* Authentication and authorization
* Rate limiting
* Distributed tracing
* Centralized logging
* Metrics and monitoring
* Docker containerization
* Kubernetes deployment
* CI/CD pipelines
* Resiliency patterns such as retries, circuit breakers, and timeouts

---

## Learning Objectives

This project is part of an ongoing exploration of backend and distributed systems engineering.

The main areas explored through this project are:

* **Java**
* **Spring Boot**
* **Microservices**
* **REST APIs**
* **Database design**
* **Video processing**
* **Streaming architecture**
* **Distributed systems**
* **Scalability**
* **Service boundaries**

---

## Disclaimer

Netflix is a trademark of Netflix, Inc.

This repository is an independent educational project created for learning and experimentation and is not affiliated with or endorsed by Netflix.
