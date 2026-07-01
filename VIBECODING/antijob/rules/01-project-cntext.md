# 01 — Project Context

## Tech Stack

| Layer | Tech |
|-------|------|
| Core | Java 17 · Spring Boot 3.x · Maven |
| ORM | Spring Data JPA · Hibernate |
| DB | PostgreSQL 16 |
| Cache | Redis 7 · Spring Cache |
| Auth | Spring Security · JWT (jjwt) |
| Validation | Jakarta Bean Validation |
| Mapping | MapStruct |
| Docs | SpringDoc OpenAPI (Swagger) |
| Test | JUnit 5 · Mockito · Testcontainers |
| Dev | Lombok · DevTools |

## Project Structure

```
src/main/java/com/dental/booking/
├── config/          # SecurityConfig, RedisConfig, CorsConfig, OpenApiConfig
├── security/        # JwtTokenProvider, JwtAuthFilter, CustomUserDetailsService
├── entity/          # JPA entities + enums/ (Role, AppointmentStatus, Gender)
├── repository/      # Spring Data repos + custom queries
├── dto/request/     # Input DTOs (validated)
├── dto/response/    # Output DTOs (mapped via MapStruct)
├── mapper/          # MapStruct mappers
├── service/         # Business logic — SlotService (core), AppointmentService
├── controller/      # REST endpoints
├── exception/       # GlobalExceptionHandler + custom exceptions
└── util/            # SlotCalculator, DateTimeUtil
```

## Commands

```bash
mvn clean compile                              # Build
mvn test                                       # Unit tests
mvn test -Dtest=SlotServiceTest                # Specific test
mvn verify -P integration-test                 # Integration tests
mvn spring-boot:run -Dspring-boot.run.profiles=dev  # Run dev
docker-compose up -d postgres redis            # Start infra
```

Swagger UI → `http://localhost:8080/swagger-ui.html`

## Key Files

| File | Vai trò |
|------|---------|
| `service/SlotService.java` | ⭐ Core: tính slot trống |
| `service/AppointmentService.java` | Đặt/huỷ/update lịch hẹn |
| `entity/Appointment.java` | Entity trung tâm |
| `entity/WorkSchedule.java` | Lịch làm việc bác sĩ |
| `security/JwtTokenProvider.java` | JWT token |
| `exception/GlobalExceptionHandler.java` | Error handling tập trung |
| `config/SecurityConfig.java` | Phân quyền endpoint |
| `docker-compose.yml` | PostgreSQL + Redis |
