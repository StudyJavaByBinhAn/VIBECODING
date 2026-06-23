// ====================================================================
// DENTAL BOOKING SYSTEM - Redis Lock & Cache Configuration
// Purpose: Concurrency control and performance optimization
// ====================================================================

// ====================================================================
// REDIS CONFIGURATION FOR SPRING BOOT
// ====================================================================

// File: application-redis.yml or application.yml

spring:
  redis:
    # Connection settings
    host: localhost           # or your Redis server IP
    port: 6379
    password: null            # if auth required
    database: 0               # default database
    timeout: 2000ms
    
    # Connection pool (Jedis or Lettuce)
    jedis:
      pool:
        max-active: 20        # Maximum active connections
        max-idle: 10          # Maximum idle connections
        min-idle: 2           # Minimum idle connections
        max-wait: -1ms        # Block indefinitely when pool exhausted
    
    # Or use Lettuce (recommended for Spring Boot 3+)
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 2
      shutdown-timeout: 100ms

  # Cache configuration
  cache:
    type: redis
    redis:
      time-to-live: 3600000   # Default TTL: 1 hour (in milliseconds)

// ====================================================================
// REDISSON CONFIGURATION (For Distributed Locks)
// ====================================================================

// File: application-redisson.yml

spring:
  redisson:
    config: |
      singleServerConfig:
        address: "redis://127.0.0.1:6379"
        password: null
        connectionMinimumIdleSize: 10
        connectionPoolSize: 32
        subscriptionConnectionPoolSize: 50
        timeout: 3000
        retryAttempts: 3
        retryInterval: 1500
      
      threads: 16
      nettyThreads: 32
      codec: !<org.redisson.codec.JsonJacksonCodec> {}
      
      # For Cluster/Sentinel, adjust configuration accordingly


// ====================================================================
// SPRING BOOT DEPENDENCY: pom.xml
// ====================================================================

<!-- Redis -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
  <version>3.2.0</version>
</dependency>

<!-- Redisson for Distributed Locks -->
<dependency>
  <groupId>org.redisson</groupId>
  <artifactId>redisson-spring-boot-starter</artifactId>
  <version>3.27.0</version>
</dependency>

<!-- Lettuce (Redis client for Spring Data) -->
<dependency>
  <groupId>io.lettuce</groupId>
  <artifactId>lettuce-core</artifactId>
</dependency>


// ====================================================================
// REDIS CACHE KEYS (Naming Convention)
// ====================================================================

/*
 * KEY NAMING CONVENTION: {domain}:{entity_id}:{action}:{qualifier}
 * TTL (Time To Live) values for different data types
 */

// 1. SLOT AVAILABILITY CACHE
// ✅ TTL: 5 minutes (short, as slots change frequently)

slots:doctor:{doctor_id}:date:{slot_date}
  - Type: Redis List (ZSET with score as start_time)
  - Value: [slot_id, slot_id, ...]
  - TTL: 5 minutes
  - Usage: Fast lookup for available slots on a date
  - Invalidate: When any slot status changes

slots:doctor:{doctor_id}:week:{week_start_date}
  - Type: Redis Hash
  - Value: { "2025-06-23": [slot_ids], "2025-06-24": [slot_ids], ... }
  - TTL: 30 minutes
  - Usage: Weekly availability view
  - Invalidate: Daily refresh

slots:available:count:{doctor_id}:{slot_date}
  - Type: Redis String (Integer)
  - Value: "15" (number of available slots)
  - TTL: 5 minutes
  - Usage: Quick availability indicator
  - Invalidate: Real-time on booking


// 2. PATIENT BOOKINGS CACHE
// ✅ TTL: 1 hour (moderate, user data doesn't change frequently)

bookings:patient:{patient_id}
  - Type: Redis Set
  - Value: { booking_id, booking_id, ... }
  - TTL: 1 hour
  - Usage: List of all bookings for patient
  - Invalidate: After new booking or cancellation

bookings:patient:{patient_id}:upcoming
  - Type: Redis Sorted Set (score = appointment_timestamp)
  - Value: { booking_id: 1719115200, booking_id: 1719201600, ... }
  - TTL: 1 hour
  - Usage: Next 5 upcoming appointments
  - Invalidate: Daily or on status change

bookings:patient:{patient_id}:{booking_id}
  - Type: Redis String (JSON)
  - Value: { "bookingId": "123", "doctorId": "456", "status": "CONFIRMED", ... }
  - TTL: 2 hours
  - Usage: Booking details view
  - Invalidate: On status/detail update


// 3. DOCTOR INFORMATION CACHE
// ✅ TTL: 24 hours (rarely changes)

doctor:{doctor_id}:profile
  - Type: Redis String (JSON)
  - Value: { "doctorId": "789", "name": "Dr. Nguyễn", "specialization": "Implant", ... }
  - TTL: 24 hours
  - Usage: Doctor profile display
  - Invalidate: On profile update or manual flush

doctor:{doctor_id}:schedule
  - Type: Redis String (JSON)
  - Value: { "Monday": { "start": "08:00", "end": "17:00" }, ... }
  - TTL: 24 hours
  - Usage: Doctor's work schedule
  - Invalidate: On schedule update


// 4. SESSION & AUTHENTICATION CACHE
// ✅ TTL: 24 hours

session:{session_id}
  - Type: Redis String (JSON)
  - Value: { "userId": "123", "roles": ["PATIENT", "ADMIN"], "email": "...", ... }
  - TTL: 24 hours (or JWT expiry time)
  - Usage: User session data
  - Invalidate: On logout or token expiry

user:{user_id}:roles
  - Type: Redis Set
  - Value: { "PATIENT", "DOCTOR" }
  - TTL: 24 hours
  - Usage: Quick role lookup for authorization
  - Invalidate: On role change


// ====================================================================
// REDIS DISTRIBUTED LOCKS
// ====================================================================

/*
 * LOCK KEY: lock:slot:book:{doctor_id}:{slot_date}:{start_time}
 * 
 * Purpose: Prevent race condition when 2+ users try to book same slot
 * 
 * Lock Strategy:
 * 1. Try to acquire lock with 30-second timeout
 * 2. If acquired, book the slot within transaction
 * 3. Release lock immediately after
 * 
 * Fallback: Optimistic Locking in database (version field)
 */

// Example Java Code:

// REDIS LOCK ACQUISITION & RELEASE

@Service
@RequiredArgsConstructor
public class AppointmentSlotService {
  
  private final RedissonClient redissonClient;
  private final BookingRepository bookingRepository;
  
  /**
   * Book an appointment slot with distributed lock protection
   * 
   * Concurrency Flow:
   * 1. Acquire Redis lock (30s timeout)
   * 2. Verify slot still available (double-check)
   * 3. Book slot in transaction
   * 4. Release lock
   * 5. Fallback: Optimistic locking in DB if version mismatch
   */
  public BookingResponse bookSlot(BookSlotRequest request) throws Exception {
    
    // 1. Create lock key
    String lockKey = String.format(
      "lock:slot:book:%d:%s:%s",
      request.getDoctorId(),
      request.getSlotDate(),
      request.getStartTime()
    );
    
    // 2. Get distributed lock from Redisson
    RLock lock = redissonClient.getLock(lockKey);
    
    // 3. Try to acquire lock (wait max 5 seconds, hold for 30 seconds)
    boolean lockAcquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
    
    if (!lockAcquired) {
      throw new SlotUnavailableException("Slot is being booked by another user. Please try again.");
    }
    
    try {
      // 4. Within lock scope: CRITICAL SECTION
      // This code runs atomically for this doctor_id + slot_date + start_time
      
      // 4a. Double-check slot is still available
      AppointmentSlot slot = appointmentSlotRepository.findByDoctorIdAndSlotDateAndStartTime(
        request.getDoctorId(),
        request.getSlotDate(),
        request.getStartTime()
      ).orElseThrow(() -> new SlotNotFoundException("Slot not found"));
      
      if (!slot.getStatus().equals(SlotStatus.AVAILABLE)) {
        throw new SlotAlreadyBookedException("Slot was just booked by someone else");
      }
      
      // 4b. Start database transaction
      // 4c. Update slot status with optimistic locking
      slot.setStatus(SlotStatus.BOOKED);
      slot.setBookedBy(request.getPatientId());
      // version is auto-incremented by @Version
      appointmentSlotRepository.save(slot);
      
      // 4d. Create booking record
      Booking booking = Booking.builder()
        .patientId(request.getPatientId())
        .doctorId(request.getDoctorId())
        .slotId(slot.getSlotId())
        .appointmentDate(request.getSlotDate())
        .appointmentTime(request.getStartTime())
        .appointmentEndTime(request.getEndTime())
        .status(BookingStatus.SCHEDULED)
        .visitType(VisitType.CONSULTATION)
        .build();
      
      bookingRepository.save(booking);
      
      // 4e. Create booking status history
      bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
        .bookingId(booking.getBookingId())
        .oldStatus(null)
        .newStatus(BookingStatus.SCHEDULED)
        .changedBy(request.getPatientId())
        .reason("Initial booking")
        .build());
      
      // 4f. Invalidate cache for this doctor's slots
      invalidateSlotCache(request.getDoctorId(), request.getSlotDate());
      
      // Transaction commits here
      
      return BookingResponse.from(booking);
      
    } catch (OptimisticLockingFailureException e) {
      // Retry logic: another thread updated the version
      throw new SlotAlreadyBookedException("Slot is no longer available", e);
      
    } finally {
      // 5. Release lock immediately
      lock.unlock();
    }
  }
  
  /**
   * Reserve a slot temporarily (hold for payment)
   * - Lock slot for 15 minutes
   * - Used for payment processing flow
   */
  public SlotReservation reserveSlot(Long slotId, Long patientId) throws Exception {
    String lockKey = String.format("lock:slot:reserve:%d", slotId);
    RLock lock = redissonClient.getLock(lockKey);
    
    if (!lock.tryLock(3, 15, TimeUnit.MINUTES)) {
      throw new SlotUnavailableException("Cannot reserve slot at this moment");
    }
    
    try {
      AppointmentSlot slot = appointmentSlotRepository.findById(slotId)
        .orElseThrow(() -> new SlotNotFoundException("Slot not found"));
      
      if (!slot.getStatus().equals(SlotStatus.AVAILABLE)) {
        throw new SlotAlreadyBookedException("Slot is not available");
      }
      
      slot.setStatus(SlotStatus.RESERVED);
      slot.setReservedUntil(LocalDateTime.now().plusMinutes(15));
      slot.setBookedBy(patientId);
      appointmentSlotRepository.save(slot);
      
      invalidateSlotCache(slot.getDoctorId(), slot.getSlotDate());
      
      return SlotReservation.from(slot);
      
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Release expired reservations (scheduled task)
   */
  @Scheduled(fixedRate = 300000) // Every 5 minutes
  public void releaseExpiredReservations() {
    List<AppointmentSlot> expiredSlots = appointmentSlotRepository
      .findByStatusAndReservedUntilBefore(SlotStatus.RESERVED, LocalDateTime.now());
    
    for (AppointmentSlot slot : expiredSlots) {
      slot.setStatus(SlotStatus.AVAILABLE);
      slot.setReservedUntil(null);
      slot.setBookedBy(null);
      appointmentSlotRepository.save(slot);
      
      invalidateSlotCache(slot.getDoctorId(), slot.getSlotDate());
    }
  }
  
  private void invalidateSlotCache(Long doctorId, LocalDate slotDate) {
    String cacheKey = String.format("slots:doctor:%d:date:%s", doctorId, slotDate);
    redisTemplate.delete(cacheKey);
    
    String countKey = String.format("slots:available:count:%d:%s", doctorId, slotDate);
    redisTemplate.delete(countKey);
  }
}


// ====================================================================
// REDIS CACHE CONFIGURATION (JAVA)
// ====================================================================

@Configuration
@EnableCaching
public class CacheConfiguration {
  
  /**
   * Configure Redis cache manager with custom TTL values
   */
  @Bean
  public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    
    // Short-lived cache (5 minutes) - Slot availability
    cacheConfigurations.put("slots", RedisCacheConfiguration
      .defaultCacheConfig()
      .entryTtl(Duration.ofMinutes(5))
      .serializeValuesWith(
        RedisSerializationContext.SerializationPair
          .fromSerializer(new GenericJackson2JsonRedisSerializer())
      )
    );
    
    // Medium TTL (1 hour) - Bookings
    cacheConfigurations.put("bookings", RedisCacheConfiguration
      .defaultCacheConfig()
      .entryTtl(Duration.ofHours(1))
      .serializeValuesWith(
        RedisSerializationContext.SerializationPair
          .fromSerializer(new GenericJackson2JsonRedisSerializer())
      )
    );
    
    // Long TTL (24 hours) - Doctor profiles
    cacheConfigurations.put("doctors", RedisCacheConfiguration
      .defaultCacheConfig()
      .entryTtl(Duration.ofHours(24))
      .serializeValuesWith(
        RedisSerializationContext.SerializationPair
          .fromSerializer(new GenericJackson2JsonRedisSerializer())
      )
    );
    
    return RedisCacheManager
      .builder(connectionFactory)
      .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(1)))
      .withInitialCacheConfigurations(cacheConfigurations)
      .build();
  }
  
  /**
   * Redisson client bean for distributed locks
   */
  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer()
      .setAddress("redis://127.0.0.1:6379")
      .setConnectionPoolSize(32)
      .setConnectionMinimumIdleSize(10);
    
    return Redisson.create(config);
  }
}


// ====================================================================
// CACHE USAGE IN SERVICE LAYER
// ====================================================================

@Service
@RequiredArgsConstructor
public class DoctorService {
  
  private final DoctorRepository doctorRepository;
  private final CacheManager cacheManager;
  
  /**
   * Get doctor profile with caching
   */
  @Cacheable(value = "doctors", key = "#doctorId")
  public DoctorDTO getDoctorProfile(Long doctorId) {
    Doctor doctor = doctorRepository.findById(doctorId)
      .orElseThrow(() -> new DoctorNotFoundException("Doctor not found"));
    
    return DoctorDTO.from(doctor);
  }
  
  /**
   * Update doctor profile and evict cache
   */
  @CacheEvict(value = "doctors", key = "#doctorId")
  public void updateDoctorProfile(Long doctorId, UpdateDoctorRequest request) {
    Doctor doctor = doctorRepository.findById(doctorId)
      .orElseThrow(() -> new DoctorNotFoundException("Doctor not found"));
    
    doctor.setSpecialization(request.getSpecialization());
    doctor.setBio(request.getBio());
    doctorRepository.save(doctor);
  }
}


// ====================================================================
// CACHE WARMER (Optional - Preload cache on startup)
// ====================================================================

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmer {
  
  private final DoctorRepository doctorRepository;
  private final DoctorService doctorService;
  
  @EventListener(ApplicationReadyEvent.class)
  public void warmUpCache() {
    log.info("Starting cache warm-up...");
    
    // Preload all active doctors into cache
    List<Doctor> doctors = doctorRepository.findByIsAvailableTrue();
    doctors.forEach(doctor -> {
      doctorService.getDoctorProfile(doctor.getDoctorId());
    });
    
    log.info("Cache warm-up completed: {} doctors loaded", doctors.size());
  }
}


// ====================================================================
// MONITORING REDIS PERFORMANCE
// ====================================================================

/**
 * Monitor Redis metrics with Spring Boot Actuator
 */

// application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

// Prometheus queries for Redis monitoring:
// - cache_gets_total
// - cache_puts_total
// - cache_evictions_total
// - cache_removals_total
// - cache_misses_total


// ====================================================================
// END OF REDIS CONFIGURATION
// ====================================================================
