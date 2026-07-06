# 01 — Project Context

## Tech Stack

| Layer | Tech |
|-------|------|
| Core | Java 21 · Spring Boot 4.1.0 · Gradle |
| ORM | Spring Data JPA · Hibernate |
| DB | PostgreSQL 16 · Flyway |
| Auth | Spring Security · JWT (jjwt) — Phase 3 |
| Validation | Jakarta Bean Validation |
| Mapping | Thủ công (Entity → DTO), chưa dùng MapStruct |
| Test | JUnit 5 |
| Dev | Lombok |

## Project Structure

```
src/main/java/com/vibecode/antijob/
├── config/       # DataInitializer, SecurityConfig (Phase 3)
├── entity/       # JPA entities
├── enums/        # Role, AppointmentStatus, Gender
├── repository/   # Spring Data JPA repositories
├── dto/          # Request/Response DTOs (flat, chưa tách request/response package)
├── service/      # SlotService (core), AppointmentService
├── controller/   # REST endpoints
└── exception/    # ApiResponse + GlobalExceptionHandler
```

## Commands

```bash
./gradlew build                          # Compile + test
./gradlew test                           # Unit tests
./gradlew test --tests "*.SlotService*"  # Specific test
./gradlew bootRun                        # Run app
docker run -d --name dental-db -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=dental_db -p 5432:5432 postgres:16   # Start Postgres
```

## Key Files

| File | Vai trò |
|------|---------|
| `service/SlotService.java` | ⭐ Core: tính slot trống |
| `service/AppointmentService.java` | Đặt/huỷ lịch hẹn |
| `entity/Appointment.java` | Entity trung tâm |
| `entity/WorkSchedule.java` | Lịch làm việc bác sĩ |
| `exception/GlobalExceptionHandler.java` | Error handling tập trung |
| `config/DataInitializer.java` | Seed data khi app khởi động lần đầu |
