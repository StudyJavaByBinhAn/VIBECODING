# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

---

## Project: Dental Clinic Booking API (`antijob/antijob/`)

**Stack:** Java 21 · Spring Boot 4.1.0 · Gradle · Lombok  
**Planned additions (Phase 3):** JPA · PostgreSQL · Redis · Spring Security · JWT  
**Package root:** `com.vibecode.antijob`

---

## Commands

All commands run from `antijob/antijob/`:

```bash
./gradlew build                          # Compile + test
./gradlew test                           # Run all tests
./gradlew test --tests "*.SlotService*"  # Run a specific test class
./gradlew bootRun                        # Start the app
```

> Do NOT say "done" until `./gradlew build` passes.

---

## Architecture

```
entity/        # POJO entities (no JPA yet)
enums/         # Role · AppointmentStatus · Gender
dto/           # AppointmentRequest / AppointmentResponse
service/       # SlotService (slot calculation) · AppointmentService (book/cancel)
controller/    # AppointmentController
exception/     # ApiResponse wrapper · GlobalExceptionHandler
```

**Core services:**
- `SlotService` — calculates available time slots (currently mocked in-memory; no DB).
- `AppointmentService` — book/cancel appointments (in-memory store; no DB yet).

All controllers return `ApiResponse<T>` — success and error alike.

---

## Domain Rules

**Slot formula:** `Available slots = Work schedule − Booked appointments − Lunch break − Buffer time`

- Slot duration comes from the service's `durationMinutes`, not a fixed 30 min.
- `buffer_minutes` between appointments is configured in `ClinicSettings`.
- Never return past slots or slots outside a dentist's `WorkSchedule`.

**Appointment status flow:**
```
PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
   ↓           ↓
CANCELLED   CANCELLED / NO_SHOW
```

**Cancellation:** only `PENDING` or `CONFIRMED`; must be N hours ahead (configured).  
**Auto-assign dentist:** pick the dentist with fewest appointments that day.

**Race condition (Phase 3):** use `@Lock(PESSIMISTIC_WRITE)` on `WorkSchedule` before checking conflicts in `bookAppointment`.

---

## Coding Rules

- **Entity → DTO manually** (no MapStruct yet). Never return an entity from a controller.
- **Constructor injection** via `@RequiredArgsConstructor`. No `@Autowired` on fields.
- **`@Transactional`** on every write operation (applies once JPA is added).
- **No hardcoding** — put config in `application.yaml` or `ClinicSettings`.
- Validate at two layers: DTO annotations + service business logic.
- Use `Optional.orElseThrow()`, never bare `.get()`.
- Log with SLF4J (`log.info/error/debug`). No `System.out.println`.
- Catch specific exceptions; never catch bare `Exception`.

**Phase 3 entity conventions (for when JPA lands):**
- All entities extend `BaseEntity` (id, createdAt, updatedAt).
- `@ManyToOne` always `FetchType.LAZY`; eager-load via `@EntityGraph` / `JOIN FETCH`.
- No `CascadeType.ALL` unless impact is fully understood.
- Enums mapped with `@Enumerated(EnumType.STRING)`.

**Caching (Phase 3):**  
Use `@Cacheable` / `@CacheEvict` / `@CachePut` — never Redis client directly.  
Key pattern: `slots:{date}:{serviceId}` · `dentists:active` · `services:active`  
Evict on every booking/cancellation or dentist/service change.

---

## Entity Reference

| Entity | Key fields |
|--------|-----------|
| `User` | id, email, password, role, createdAt |
| `Patient` | user, fullName, phone, dateOfBirth, gender, address, medicalHistory |
| `Dentist` | user, fullName, specialization, licenseNumber, isActive |
| `DentalService` | name, durationMinutes, price, isActive |
| `WorkSchedule` | dentist, dayOfWeek, startTime, endTime, isActive |
| `Appointment` | patient, dentist, service, appointmentDate, startTime, endTime, status, notes |
| `ClinicSettings` | openTime, closeTime, slotDurationMinutes, maxAdvanceBookingDays |
