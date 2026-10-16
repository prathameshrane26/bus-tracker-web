# Bus Tracker Web

A real-time bus tracking system backend built with Spring Boot, featuring live location updates via WebSockets and driver authentication. The system now powers a dedicated Android application.

## 🚀 Overview

The Bus Tracker application provides a platform for tracking transit buses in real-time. The backend serves the Android App, which includes interfaces for:
- **Passengers:** View live bus locations on a map via the Android App.
- **Drivers:** Log in and broadcast their current GPS coordinates via the Android App.
- **Administrators:** Manage bus schedules and driver assignments (API managed).

## 🛠 Tech Stack

- **Backend:** Java 21, Spring Boot 3.2.3
- **Frameworks:** Spring Web, Spring Data JPA, Spring Security, Spring WebSocket
- **Database:** File-based H2 (`./data/bustracker.mv.db`)
- **Messaging:** STOMP over WebSocket
- **Email:** Spring Mail (SMTP via Gmail)
- **Frontend:** Android Application (External)
- **Build Tool:** Maven
- **Containerization:** Docker

## 📋 Requirements

- **Java Development Kit (JDK) 21** or higher.
- **Maven 3.6+**
- **SMTP Server** (e.g., Gmail) for sending driver credentials.

## ⚙️ Setup & Installation

### 1. Database Configuration
No database server is required. H2 creates `data/bustracker.mv.db` in the project directory and persists accounts and schedules across restarts.
```properties
spring.datasource.url=jdbc:postgresql://<your-db-url>
spring.datasource.username=<your-username>
spring.datasource.password=<your-password>
```

### 2. Email Configuration
The system sends credentials via email. Configure your SMTP settings in `application.properties`:
```properties
spring.mail.username=<your-email>
spring.mail.password=<your-app-password>
```

### 3. Build the Project
```bash
mvn clean package
```

## 🏃 Running the Application

### Using Maven
```bash
mvn spring-boot:run
```

### Using Docker
```bash
docker build -t bus-tracker-web .
docker run -p 8080:8080 bus-tracker-web
```

The application will be available at `http://localhost:8080`.

## 📂 Project Structure

- `src/main/java/com/bustracker`: Core application logic.
    - `BusApplication.java`: Entry point.
    - `BusController.java`: REST API endpoints.
    - `WebSocketConfig.java`: Real-time communication setup.
    - `SecurityConfig.java`: Security and authentication rules.
- `src/main/resources/application.properties`: Application configuration.
- `Dockerfile`: Multi-stage build for containerization.

## 📡 Scripts & Endpoints

### Maven Scripts
- `mvn clean install`: Build the project and install to local repo.
- `mvn test`: Run automated tests (TODO: Implement comprehensive tests).

### Key Endpoints
- `POST /api/auth/signup`: Create a driver or passenger account.
- `POST /api/auth/login`: Log in a driver or passenger.
- `POST /api/auth/logout`: Invalidate the current login session.
- `GET /api/bus/active-locations`: Get all live bus positions.
- `GET /api/bus/schedules`: List all bus schedules.
- `WS /ws`: WebSocket endpoint for location updates.

## 🔐 Environment Variables
Currently, the application uses `application.properties`. It is recommended to use environment variables for production:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_MAIL_PASSWORD`

## ✅ TODOs
- [ ] Implement JUnit/Mockito tests for `BusTrackerService`.
- [ ] Secure WebSocket endpoints with JWT.
- [ ] Add interactive map frontend (Leaflet/Google Maps).

## 📄 License
This project's licensing information is not yet formally defined. (TODO: Add a LICENSE file)
