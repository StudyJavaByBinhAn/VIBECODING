# 01 — Project Context

## Tech Stack

| Layer | Tech |
|-------|------|
| Core | Java 21 · Spring Boot 4.1.0 · Gradle |
| ORM | Spring Data JPA · Hibernate |
| DB | PostgreSQL 16 · Flyway |
| Auth | Spring Security · JWT (jjwt) — Phase 3 |
| Cache | Redis (Phase 4) |
| Validation | Jakarta Bean Validation (Phase 7) |
| Mapping | MapStruct (`mapper/` package) |
| Test | JUnit 5 · Mockito |
| Dev | Lombok |

## Project Structure

```
src/main/java/com/vibecode/antijob/
├── config/       # DataInitializer, SecurityConfig, CacheConfig, TimeConfig
├── entity/       # JPA entities
├── enums/        # Role, AppointmentStatus, Gender
├── repository/   # Spring Data JPA repositories
├── dto/          # Request/Response DTOs (flat, chưa tách request/response package)
├── mapper/       # MapStruct @Mapper interface (Entity ↔ DTO)
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
docker run -d --name dental-redis -p 6379:6379 redis:7               # Start Redis (bắt buộc — app dùng @Cacheable)
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
