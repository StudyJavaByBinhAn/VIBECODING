# 📐 VIBECODING - DATABASE ARCHITECTURE INDEX

## 🎯 PROJECT: Dental Booking System (Hệ thống Đặt lịch Nha khoa)

**Architecture:** Modular Monolith → Microservices (Strangler Fig Pattern)  
**Database Design Status:** ✅ **COMPLETE**  
**Last Updated:** 2025-06-23

---

## 📂 COMPLETE DELIVERABLES (7 Files / 100 KB)

### 🔷 DESIGN DOCUMENTS

#### 1. **DATABASE_DESIGN.md** (36 KB) ⭐ MAIN SPECIFICATION
**Comprehensive database design document covering:**
- 4 Independent Modules (User, Doctor, Slot, Booking)
- 12 MySQL Tables with columns, constraints, indexes
- 2 MongoDB Collections with nested structure
- Redis Cache & Lock Strategy
- Concurrency protection workflow
- Query optimization examples
- Performance monitoring setup
- Database initialization scripts

**Content:**
- ✅ Part 1: User/Patient Module (3 tables)
- ✅ Part 2: Doctor & Schedule Module (3 tables)
- ✅ Part 3: Slot/Availability Module (2 tables) ⭐ **CRITICAL**
- ✅ Part 4: Booking/Appointment Module (4 tables) ⭐ **TRANSACTIONAL**
- ✅ Part 5: MongoDB Collections (2 collections)
- ✅ Part 6: Redis Locks (Configuration)
- ✅ Part 7: Data Consistency & Transactions
- ✅ Part 8: Database Initialization Scripts
- ✅ Part 9: Optimization Strategies
- ✅ Part 10: Monitoring & Alerting

**Read This First** ➡️ [DATABASE_DESIGN.md](DATABASE_DESIGN.md)

---

#### 2. **DATABASE_DESIGN_SUMMARY.md** (15 KB) ⭐ EXECUTIVE SUMMARY
**Quick reference & implementation guide:**
- Overview of all 4 modules
- Module architecture diagram
- Critical design features
- Concurrency test scenarios
- Scaling considerations (Phase 1 → Phase 2)
- Setup instructions
- Monitoring metrics
- Developer checklist

**Read This For:** Quick understanding & developer onboarding

---

### 🔧 IMPLEMENTATION SCRIPTS

#### 3. **schema-init.sql** (16 KB) ✅ MYSQL INITIALIZATION
**Production-ready MySQL database initialization**

**Contains:**
- CREATE DATABASE statement
- All 12 tables with:
  - Column definitions with data types
  - PRIMARY KEY constraints
  - UNIQUE constraints
  - CHECK constraints
  - Inline documentation (comments)
  
- All indexes:
  - 24 indexes across all tables
  - Composite indexes optimized for queries
  - UNIQUE indexes to prevent duplicates
  
- Sample data insert statements
- Cleanup/reset scripts

**Usage:**
```bash
mysql -u root -p < schema-init.sql
mysql -u root -p dental_booking_system
mysql> SHOW TABLES;  # Verify
mysql> DESCRIBE appointment_slots;  # Check structure
```

---

#### 4. **mongodb-schema.js** (13 KB) ✅ MONGODB INITIALIZATION
**MongoDB collections with schema validation**

**Contains:**
- Collection definitions with $jsonSchema validation
- patient_dental_records (main collection)
  - Nested tooth_chart structure
  - Nested treatment_history array
  - Flexible medical_conditions
  - Radiographs storage
  
- dental_materials_inventory collection
- clinical_templates collection (optional)

- Full index strategy for each collection
- Sample queries
- Verification scripts

**Usage:**
```bash
mongosh
load("mongodb-schema.js")
db.patient_dental_records.findOne()  # Verify
```

---

#### 5. **REDIS_LOCK_CONFIG.md** (17 KB) 🔒 REDIS & CACHE CONFIGURATION
**Complete Redis setup for concurrency & caching**

**Contains:**
- Spring Boot Redis configuration (YAML)
- Redisson setup for distributed locks
- Maven dependencies
- Cache key naming convention
- TTL strategy by data type

**Java Implementation Examples:**
- Distributed lock acquisition/release pattern
- Concurrency workflow (5-step process)
- Slot reservation with hold time
- Cache warmer for startup
- Monitoring setup

- CacheManager bean configuration
- @Cacheable/@CacheEvict annotations
- Cache invalidation strategy

**Redis Lock Pattern:**
```
Lock Key: lock:slot:book:{doctor_id}:{slot_date}:{start_time}
Timeout: 30 seconds (max hold)
Wait Time: 5 seconds
```

---

### 📋 REFERENCE DOCUMENTS

#### 6. **agent_skills_phase1_monolith.md** (2.7 KB)
Development guidelines & rules for Phase 1 (Modular Monolith)
- Persona definition
- Project context & technology
- Code generation rules
- Workflow process

#### 7. **agent_skills_phase2_microservices.md** (2.6 KB)
Architecture guidelines for Phase 2 (Microservices)
- Distributed system patterns
- Event-driven architecture
- Kafka integration
- DevOps & monitoring

---

## 🎨 ARCHITECTURE SUMMARY

### Module Separation (NO Hard Foreign Keys)

```
┌─────────────────────────────────────────────────┐
│ MODULE 1: USER/PATIENT (Independent)            │
│ ├─ users                                        │
│ ├─ patients                                     │
│ └─ patient_addresses                            │
└─────────────────────────────────────────────────┘
              ↓ (ID reference only)
┌─────────────────────────────────────────────────┐
│ MODULE 2: DOCTOR & SCHEDULE (Independent)       │
│ ├─ doctors                                      │
│ ├─ doctor_schedules                             │
│ └─ doctor_schedule_exceptions                   │
└─────────────────────────────────────────────────┘
              ↓ (ID reference only)
┌─────────────────────────────────────────────────┐
│ MODULE 3: SLOT/AVAILABILITY (Independent) ⭐   │
│ ├─ appointment_slots [WITH Optimistic Lock]    │
│ └─ slot_templates                               │
└─────────────────────────────────────────────────┘
              ↓ (ID reference only)
┌─────────────────────────────────────────────────┐
│ MODULE 4: BOOKING/APPOINTMENT (Core) ⭐         │
│ ├─ bookings [Transactional]                    │
│ ├─ booking_status_history                      │
│ ├─ appointment_notes                           │
│ └─ booking_cancellations                       │
└─────────────────────────────────────────────────┘
              ↓ (Async Events - Phase 2)
┌─────────────────────────────────────────────────┐
│ MONGODB: Flexible Medical Records               │
│ ├─ patient_dental_records [Nested structure]   │
│ └─ dental_materials_inventory                   │
└─────────────────────────────────────────────────┘
              ↓ (Concurrency Control)
┌─────────────────────────────────────────────────┐
│ REDIS: Locks & Cache                            │
│ ├─ Distributed Locks [Slot booking]            │
│ ├─ Slot Cache [5-min TTL]                      │
│ ├─ Booking Cache [1-hour TTL]                  │
│ └─ Doctor Cache [24-hour TTL]                  │
└─────────────────────────────────────────────────┘
```

---

## 🔒 CONCURRENCY PROTECTION (3-Layer Defense)

| Layer | Mechanism | Technology | Scope |
|-------|-----------|-----------|-------|
| **L1** | Distributed Lock | Redis (Redisson) | Atomic slot reservation |
| **L2** | Optimistic Locking | Database `version` field | Detect updates |
| **L3** | Data Constraints | MySQL CHECK, UNIQUE | Data validity |

### Race Condition Prevention:
1. **Redis Lock** → Prevents simultaneous access
2. **Double-check** → Verify slot status within lock
3. **Version increment** → Detect conflicts
4. **Retry logic** → Handle failures gracefully

---

## ⭐ CRITICAL INDEXES (Performance)

### Most Important Query:
```sql
-- Find available slots for a doctor on a date
SELECT * FROM appointment_slots 
WHERE doctor_id = 123 
  AND slot_date = '2025-06-25' 
  AND status = 'AVAILABLE'
ORDER BY start_time;

-- INDEX USED:
idx_slot_doctor_date_status (doctor_id, slot_date, status, start_time)
-- Without this index: FULL TABLE SCAN (slow!)
-- With this index: < 100ms response time ✅
```

### All 24 Indexes:
- `users`: 4 indexes (email, phone, type, active)
- `patients`: 3 indexes (user_id, city, dob)
- `doctors`: 4 indexes (user_id, specialization, available, location)
- `doctor_schedules`: 2 indexes (day_of_week, lookup)
- `appointment_slots`: 6 indexes (⭐ MOST CRITICAL)
- `bookings`: 6 indexes (patient, doctor, slot, status)
- Other tables: Helper indexes for sorting, filtering

---

## 📊 EXPECTED DATA VOLUMES

| Table | Rows/Day | Annual | Size |
|-------|----------|--------|------|
| users | 5-10 | 2K-4K | ~500 KB |
| appointment_slots | 500-1000 | 180K-360K | **~50 MB** |
| bookings | 100-200 | 36K-72K | **~10 MB** |
| booking_status_history | 100-200 | 36K-72K | ~5 MB |

**Monitor table sizes:**
```sql
SELECT TABLE_NAME, 
       ROUND(((data_length + index_length) / 1024 / 1024), 2) AS SizeMB 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'dental_booking_system'
ORDER BY SizeMB DESC;
```

---

## 🚀 QUICK START GUIDE

### Step 1: MySQL Setup
```bash
cd /mnt/e/gochocTap
mysql -u root -p < schema-init.sql
mysql -u root -p dental_booking_system -e "SHOW TABLES;"
```

### Step 2: MongoDB Setup
```bash
mongosh
load("/mnt/e/gochocTap/mongodb-schema.js")
db.patient_dental_records.find().limit(1)
```

### Step 3: Redis Setup
```bash
redis-server
redis-cli PING  # Should return PONG
```

### Step 4: Spring Boot App
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dental_booking_system
  jpa.hibernate.ddl-auto: validate
  data.mongodb.uri: mongodb://localhost:27017/dental_booking_db
  redis.host: localhost
```

---

## 📚 KEY CONCEPTS

### 1. Modular Monolith
- Single codebase, multiple domain modules
- Clear separation of concerns (User, Doctor, Slot, Booking)
- No cross-module database constraints
- **Benefit:** Easy to extract modules to microservices later

### 2. Distributed Locks
- Redis lock prevents concurrent slot booking
- Applied before database transaction
- Lock key includes doctor_id + slot_date + start_time
- **Benefit:** Atomic slot reservation across distributed systems

### 3. Optimistic Locking
- `version` field on critical tables
- Incremented on every update
- Detects concurrent modifications
- **Benefit:** Fallback protection if lock fails

### 4. Composite Indexes
- Multiple columns in single index
- Order matters (filter columns first, then sort)
- **Benefit:** Covers query → no additional lookups needed

### 5. Cache Invalidation
- Cache invalidated on INSERT/UPDATE/DELETE
- Different TTLs for different data types
- **Benefit:** Always-fresh data without performance penalty

---

## 🔍 VERIFICATION CHECKLIST

After setup, verify with:

```bash
# MySQL
mysql> SELECT COUNT(*) FROM users;
mysql> SHOW INDEXES FROM appointment_slots;
mysql> SELECT * FROM INFORMATION_SCHEMA.STATISTICS 
       WHERE TABLE_NAME = 'appointment_slots';

# MongoDB
mongosh> db.patient_dental_records.getIndexes()
mongosh> db.patient_dental_records.getCollectionStats()

# Redis
redis-cli INFO stats
redis-cli DBSIZE
```

---

## 🎓 FOR DEVELOPERS

### Before Coding:
1. ✅ Read [DATABASE_DESIGN_SUMMARY.md](DATABASE_DESIGN_SUMMARY.md) (10 min)
2. ✅ Review [DATABASE_DESIGN.md](DATABASE_DESIGN.md) sections for your module (30 min)
3. ✅ Study concurrency scenarios in SUMMARY
4. ✅ Understand lock acquisition flow in [REDIS_LOCK_CONFIG.md](REDIS_LOCK_CONFIG.md)

### During Coding:
1. ✅ Use Constructor Injection (never @Autowired on fields)
2. ✅ Write tests for race conditions
3. ✅ Add comments on concurrency logic
4. ✅ Use Java Records for DTOs
5. ✅ Apply SOLID principles

### After Coding:
1. ✅ Load test with JMeter (100 concurrent users)
2. ✅ Monitor locks in Redis
3. ✅ Check query performance (< 100ms)
4. ✅ Verify no double-bookings (race condition tests)

---

## 📞 CONTACT & ESCALATION

**For Database Questions:**
- Refer to DATABASE_DESIGN.md (sections 1-10)
- Check indexes first if queries are slow
- Verify locks are acquired before booking

**For Redis Issues:**
- Check Redis connection: `redis-cli PING`
- Verify Redisson bean configured in Spring Boot
- Monitor lock timeouts in logs

**For Concurrency Issues:**
- Enable debug logging on AppointmentSlotService
- Check for OptimisticLockingFailureException
- Verify redis lock acquisition is working

---

## 📈 NEXT PHASES

### Phase 1: Implementation ✅ (Design Complete)
- ✅ Database schema designed
- ⏳ Spring Boot service implementation
- ⏳ API endpoint development
- ⏳ Unit & integration tests
- ⏳ Load testing

### Phase 2: Microservices Migration
- Extract each module to independent service
- Use Strangler Fig pattern for gradual migration
- Implement Kafka for event-driven communication
- Set up API Gateway (Spring Cloud Gateway)
- Independent databases per service

---

## 📄 FILE LOCATIONS

```
/mnt/e/gochocTap/
├── DATABASE_DESIGN.md              ⭐ MAIN (36 KB)
├── DATABASE_DESIGN_SUMMARY.md      📋 Quick reference (15 KB)
├── schema-init.sql                 🗄️ MySQL script (16 KB)
├── mongodb-schema.js               🗄️ MongoDB script (13 KB)
├── REDIS_LOCK_CONFIG.md            🔒 Cache & locks (17 KB)
├── agent_skills_phase1_monolith.md 📘 Dev guidelines (2.7 KB)
├── agent_skills_phase2_microservices.md 📘 Migration guide (2.6 KB)
└── THIS FILE (DATABASE_INDEX.md)   📑 Index & overview
```

---

## ✅ DESIGN COMPLETION STATUS

| Component | Status | File |
|-----------|--------|------|
| MySQL Schema | ✅ Complete | schema-init.sql |
| MongoDB Schema | ✅ Complete | mongodb-schema.js |
| Redis Config | ✅ Complete | REDIS_LOCK_CONFIG.md |
| Concurrency Design | ✅ Complete | DATABASE_DESIGN.md (Part 7) |
| Performance Optimization | ✅ Complete | DATABASE_DESIGN.md (Part 9) |
| Monitoring Setup | ✅ Complete | DATABASE_DESIGN.md (Part 10) |
| Developer Guide | ✅ Complete | DATABASE_DESIGN_SUMMARY.md |

---

**🎉 DATABASE ARCHITECTURE DESIGN: 100% COMPLETE 🎉**

Ready for Java/Spring Boot development!

---

**Version:** 1.0  
**Last Updated:** 2025-06-23  
**Status:** Ready for Production Implementation
