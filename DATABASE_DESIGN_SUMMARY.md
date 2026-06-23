# 📐 DATABASE ARCHITECTURE DESIGN - EXECUTIVE SUMMARY

## Hệ thống Đặt lịch Nha khoa (Dental Booking System)
### Modular Monolith Architecture - Ready for Microservices Migration

**Version:** 1.0  
**Date:** 2025-06-23  
**Architecture:** Modular Monolith (No hard FOREIGN KEYs between modules)

---

## 📊 OVERVIEW

This document completes the comprehensive database design for a dental appointment booking system with the following characteristics:

- ✅ **Modular Separation:** 4 independent business modules
- ✅ **Concurrency Safe:** Redis Locks + Optimistic Locking (version fields)
- ✅ **Performance Optimized:** Strategic composite indexes
- ✅ **Microservices Ready:** No hard database dependencies between modules
- ✅ **Flexible Medical Records:** MongoDB for dental patient history
- ✅ **Enterprise Scale:** Built for high availability and scalability

---

## 📁 DELIVERABLES

### 1. **DATABASE_DESIGN.md** ⭐
Complete specification of all tables, columns, constraints, and indexes

**Includes:**
- 4 Core Modules with 12 MySQL tables
- 2 MongoDB collections
- Redis cache & lock strategy
- Concurrency protection workflow
- Query optimization guidelines
- Performance monitoring metrics

### 2. **schema-init.sql**
Production-ready MySQL initialization script

**Includes:**
- CREATE DATABASE statement
- All 12 tables with constraints
- Composite indexes with comments
- Inline documentation
- Cleanup scripts

### 3. **mongodb-schema.js**
MongoDB collections with schema validation

**Includes:**
- patient_dental_records (nested treatment history)
- dental_materials_inventory
- clinical_templates (optional)
- Full index strategy

### 4. **REDIS_LOCK_CONFIG.md**
Redis configuration for concurrency & caching

**Includes:**
- Spring Boot Redis configuration (YAML)
- Redisson setup for distributed locks
- Cache key naming convention
- Lock acquisition/release patterns (Java code)
- Cache warmer & monitoring

---

## 🏗️ MODULE ARCHITECTURE

```
┌─────────────────────────────────────────────────────────┐
│        MODULAR MONOLITH - 4 INDEPENDENT MODULES        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─ MODULE 1: USER/PATIENT                              │
│  │  ├─ users (PK: user_id)                              │
│  │  ├─ patients (user_id reference)                     │
│  │  └─ patient_addresses                                │
│  │                                                      │
│  ┌─ MODULE 2: DOCTOR & SCHEDULE                         │
│  │  ├─ doctors (user_id reference)                      │
│  │  ├─ doctor_schedules (doctor_id reference)           │
│  │  └─ doctor_schedule_exceptions                       │
│  │                                                      │
│  ┌─ MODULE 3: SLOT/AVAILABILITY ⭐ CRITICAL             │
│  │  ├─ appointment_slots (doctor_id reference)          │
│  │  └─ slot_templates                                   │
│  │                                                      │
│  ┌─ MODULE 4: BOOKING/APPOINTMENT ⭐ TRANSACTIONAL      │
│  │  ├─ bookings (patient_id, doctor_id, slot_id ref)   │
│  │  ├─ booking_status_history                          │
│  │  ├─ appointment_notes                               │
│  │  └─ booking_cancellations                           │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### NO Hard FOREIGN KEYs Between Modules ✅
- All cross-module references use ID fields only
- Enables independent database per module (Phase 2 microservices)
- Integration via event-driven architecture

---

## ⭐ CRITICAL DESIGN FEATURES

### 1. **CONCURRENCY PROTECTION (Preventing Double-Booking)**

| Layer | Mechanism | How It Works |
|-------|-----------|------------|
| **L1: Application** | Redis Distributed Lock | Atomic slot reservation |
| **L2: Database** | Optimistic Locking (`version` field) | Detect version mismatch |
| **L3: Database** | CHECK constraints | Enforce data validity |

**Lock Flow:**
```
1. Client requests slot S
   ↓
2. App acquires Redis lock for S
   lock_key = "lock:slot:book:{doctor_id}:{date}:{time}"
   ↓
3. Check slot available (double-check within lock)
   ↓
4. Update slot (version = version + 1) 
   ↓
5. Create booking record
   ↓
6. Release lock
   ↓
7. If version conflict → retry with back-off
```

### 2. **CRITICAL INDEXES** (Query Performance)

| Index | Table | Purpose | Usage |
|-------|-------|---------|-------|
| `idx_slot_doctor_date_status` | appointment_slots | Find available slots | **Main Query** |
| `idx_booking_patient_id` | bookings | List patient bookings | SELECT |
| `idx_booking_doctor_date_status` | bookings | Doctor's schedule | SELECT |
| `idx_booking_slot_id` | bookings | Prevent duplicate bookings | UNIQUE |
| `idx_user_email` | users | Authentication | LOGIN |
| `idx_doctor_specialization` | doctors | Find doctors by specialty | SEARCH |

**Performance Target:**
- Slot search query: **< 100ms**
- Booking transaction: **< 500ms**
- Lock contention: **< 5%**

### 3. **VERSION FIELD (Optimistic Locking)**

```sql
-- appointment_slots table
version BIGINT DEFAULT 0  -- Incremented on every update

-- bookings table
version BIGINT DEFAULT 0  -- For booking status updates

-- JPA Code:
@Version
private Long version;  // Auto-managed by Hibernate

// Update with optimistic locking:
UPDATE appointment_slots 
SET status = 'BOOKED', version = version + 1 
WHERE slot_id = ? AND version = ?

// If version mismatch → OptimisticLockingFailureException
// → Retry booking flow
```

### 4. **COMPOSITE INDEXES**

**Most Important Index:**
```sql
CREATE INDEX idx_slot_doctor_date_status 
ON appointment_slots(doctor_id, slot_date, status, start_time);

-- Query using this index:
SELECT * FROM appointment_slots 
WHERE doctor_id = 123 
  AND slot_date = '2025-06-25' 
  AND status = 'AVAILABLE'
ORDER BY start_time;

-- This query is executed for every slot search request
-- MUST use index to avoid full table scan
```

---

## 🗄️ DATA FLOW DIAGRAM

### Booking Creation Flow

```
Patient (Frontend)
    ↓
[1. GET /api/slots?doctor=123&date=2025-06-25]
    ↓
DoctorService.getAvailableSlots()
    ├─ Check Redis cache: "slots:doctor:123:date:2025-06-25"
    │  ├─ HIT → Return cached slots (5-min TTL)
    │  └─ MISS → Query DB using idx_slot_doctor_date_status
    └─ Return [slot1, slot2, slot3, ...]
    ↓
Patient clicks on slot (e.g., 09:00-09:30)
    ↓
[2. POST /api/bookings]
    ├─ Acquire Redis Lock: "lock:slot:book:123:2025-06-25:09:00"
    ├─ Verify slot status = AVAILABLE (double-check)
    ├─ BEGIN TRANSACTION
    │  ├─ UPDATE appointment_slots: status = BOOKED, version++
    │  ├─ INSERT INTO bookings
    │  ├─ INSERT INTO booking_status_history
    │  └─ COMMIT
    ├─ Invalidate cache
    └─ Release lock
    ↓
Return BookingResponse to patient
```

---

## 📦 MONGODB DOCUMENT STRUCTURE

### patient_dental_records Collection

**Nested Structure (Flexible):**
```json
{
  "_id": ObjectId(...),
  "patient_id": 123,
  "user_id": 456,
  "full_name": "Nguyễn Văn A",
  
  "tooth_chart": {
    "teeth": [
      { "tooth_id": "11", "status": "CAVITY", ... },
      { "tooth_id": "12", "status": "HEALTHY", ... }
    ]
  },
  
  "treatment_history": [
    {
      "date": ISODate("2025-05-20"),
      "doctor_id": 789,
      "treatment_type": "CAVITY_FILLING",
      "teeth_treated": ["11", "12"],
      "cost": 500000,
      "follow_up_date": ISODate("2025-06-20")
    },
    { ... }
  ],
  
  "treatment_plan": {
    "status": "ACTIVE",
    "estimated_cost": 5000000,
    "priority_treatments": [...]
  },
  
  "medical_conditions": {
    "allergies": ["Penicillin"],
    "chronic_diseases": ["Diabetes"]
  }
}
```

**Benefits:**
- ✅ Flexible schema for medical records
- ✅ Nested arrays for treatment history
- ✅ Easy to evolve without migrations
- ✅ Better denormalization for reporting

---

## 🔒 CONCURRENCY TEST SCENARIOS

### Scenario 1: Two Users Booking Same Slot

```
Timeline:
T1: User A requests slot S (09:00-09:30) on doctor D
    → Acquires lock: "lock:slot:book:D:2025-06-25:09:00"
    → Checks status = AVAILABLE ✓
    → Begins transaction

T2: User B requests same slot S
    → Tries to acquire same lock
    → Lock is already held by User A (WAIT...)

T3: User A completes booking
    → UPDATE slot: status = BOOKED, version = 1
    → INSERT booking for User A
    → COMMIT transaction
    → Release lock

T4: User B lock acquired
    → Checks status = BOOKED ✗ (no longer AVAILABLE)
    → Throws SlotAlreadyBookedException
    → Release lock
    → Return "Slot unavailable" to User B

Result: ✅ NO DOUBLE-BOOKING
```

### Scenario 2: Database-Level Conflict (Optimistic Locking)

```
If somehow lock is bypassed:

T1: User A: SELECT slot version = 5
T2: User B: SELECT slot version = 5
T3: User A: UPDATE slot SET version = 6 WHERE version = 5 → OK
T4: User B: UPDATE slot SET version = 6 WHERE version = 5 → FAIL!
            (version is now 6, not 5)
            → OptimisticLockingFailureException
            → Retry mechanism triggers
            → User B's booking is rolled back

Result: ✅ Database prevents corruption
```

---

## 📈 SCALING CONSIDERATIONS

### Phase 1 (Monolith - Current)
- Single MySQL database (master-slave replication for HA)
- Redis cluster for distributed locks
- MongoDB for flexible medical records

### Phase 2 (Microservices - Future)
- Extract each module to independent database
- API Gateway (Spring Cloud Gateway) for routing
- Kafka for event-driven communication
- Separate Redis per service
- Independent MongoDB instances per service

**Migration Strategy:** Strangler Fig Pattern
- Keep monolith running
- Gradually extract modules to services
- API Gateway routes traffic based on module
- Data synchronization via events

---

## 🔧 SETUP INSTRUCTIONS

### Step 1: MySQL Setup
```bash
# Run initialization script
mysql -u root -p < /mnt/e/gochocTap/schema-init.sql

# Verify tables created
mysql -u root -p dental_booking_system
mysql> SHOW TABLES;
mysql> DESCRIBE users;
mysql> SHOW INDEXES FROM appointment_slots;
```

### Step 2: MongoDB Setup
```bash
# Connect to MongoDB
mongosh

# Run schema initialization
load("/mnt/e/gochocTap/mongodb-schema.js")

# Verify collections created
show collections
db.patient_dental_records.getIndexes()
```

### Step 3: Redis Setup
```bash
# Start Redis server
redis-server

# Test connection
redis-cli ping
# Output: PONG

# Verify empty
redis-cli DBSIZE
```

### Step 4: Spring Boot Configuration
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dental_booking_system
    username: root
    password: your_password
  
  jpa:
    hibernate.ddl-auto: validate  # Don't auto-create, use schema-init.sql
  
  data:
    mongodb:
      uri: mongodb://localhost:27017/dental_booking_db
    redis:
      host: localhost
      port: 6379
```

---

## 📊 TABLE STATISTICS (Expected)

| Table | Rows/Day | Total (1 year) | Size (1 year) |
|-------|----------|----------------|---------------|
| users | 5-10 | 2K-4K | ~500 KB |
| patients | 2-5 | 1K-2K | ~200 KB |
| doctors | 1-2 | 500-1K | ~100 KB |
| **appointment_slots** | 500-1000 | 180K-360K | **~50 MB** |
| **bookings** | 100-200 | 36K-72K | **~10 MB** |
| booking_status_history | 100-200 | 36K-72K | ~5 MB |

**Note:** appointment_slots and bookings are the largest tables. Monitor with:
```sql
SELECT 
  TABLE_NAME, 
  ROUND(((data_length + index_length) / 1024 / 1024), 2) AS SizeMB 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'dental_booking_system'
ORDER BY SizeMB DESC;
```

---

## ⚠️ CRITICAL POINTS FOR DEVELOPERS

### ✅ DO:
1. **Always use indexes** in WHERE clauses
2. **Acquire locks** before slot booking
3. **Increment version** fields on updates
4. **Invalidate cache** after data changes
5. **Use Constructor Injection** (not @Autowired on fields)
6. **Write comprehensive comments** on concurrency code
7. **Test race conditions** with multiple threads

### ❌ DON'T:
1. ❌ Use FOREIGN KEYs between modules
2. ❌ Query appointment_slots without doctor_id or slot_date
3. ❌ Book slot without Redis lock
4. ❌ Ignore OptimisticLockingFailureException
5. ❌ Cache data with static TTL (use config)
6. ❌ Update without version checking
7. ❌ Log sensitive data (passwords, full payment info)

---

## 🔍 MONITORING & ALERTS

### Key Metrics to Monitor

```yaml
JMeter Load Test Setup:
- Simulate 100 concurrent users
- 50 booking requests/second
- Acceptable latency: < 500ms (p95)
- Error rate: < 0.1%

Prometheus Queries:
- histogram_quantile(0.95, http_request_duration_seconds) # P95 latency
- rate(booking_errors_total[5m]) # Error rate
- rate(redis_lock_timeouts[5m]) # Lock contention
- appointment_slots_available_count # Real-time availability
```

### Alert Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Booking latency (p95) | > 400ms | > 1000ms |
| Lock contention | > 3% | > 10% |
| Cache hit ratio | < 60% | < 40% |
| DB connection pool usage | > 80% | > 95% |

---

## 📚 REFERENCES

### Files in `/mnt/e/gochocTap/`:

1. **DATABASE_DESIGN.md** - Complete specification (this directory)
2. **schema-init.sql** - MySQL initialization
3. **mongodb-schema.js** - MongoDB initialization
4. **REDIS_LOCK_CONFIG.md** - Redis & lock configuration
5. **agent_skills_phase1_monolith.md** - Development guidelines
6. **agent_skills_phase2_microservices.md** - Migration strategy

---

## ✅ DESIGN VALIDATION CHECKLIST

- [x] All 4 modules separated cleanly
- [x] No hard FOREIGN KEYs between modules
- [x] Concurrency protection (Redis + DB)
- [x] Version fields for optimistic locking
- [x] Composite indexes for main queries
- [x] MongoDB for flexible medical records
- [x] Redis cache strategy documented
- [x] Monitoring metrics defined
- [x] Scaling strategy outlined
- [x] Code examples provided

---

## 🚀 NEXT STEPS

1. ✅ Create MySQL database using schema-init.sql
2. ✅ Initialize MongoDB collections using mongodb-schema.js
3. ✅ Configure Spring Boot with application.yml
4. ✅ Implement AppointmentSlotService with lock acquisition
5. ✅ Implement BookingService with concurrency protection
6. ✅ Create Unit tests for race conditions
7. ✅ Load test with JMeter
8. ✅ Monitor with Prometheus/Grafana
9. ✅ Document API endpoints with OpenAPI/Swagger
10. ✅ Prepare for Phase 2 microservices migration

---

**Design Status:** ✅ **COMPLETE & READY FOR DEVELOPMENT**

**Last Updated:** 2025-06-23  
**Next Review:** After Phase 1 implementation complete
