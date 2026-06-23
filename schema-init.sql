-- ====================================================================
-- DENTAL BOOKING SYSTEM - MySQL Schema Initialization Script
-- Database: dental_booking_system
-- Version: 1.0
-- Created: 2025-06-23
-- ====================================================================

-- Create Database
CREATE DATABASE IF NOT EXISTS dental_booking_system 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE dental_booking_system;

-- ====================================================================
-- MODULE 1: USER/PATIENT
-- ====================================================================

-- Table: users
-- Purpose: Centralized user authentication and basic information
CREATE TABLE users (
  user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique user identifier',
  email VARCHAR(255) UNIQUE NOT NULL COMMENT 'Email for login',
  password_hash VARCHAR(255) NOT NULL COMMENT 'Bcrypt password hash',
  full_name VARCHAR(255) NOT NULL COMMENT 'Full name',
  phone_number VARCHAR(20) UNIQUE COMMENT 'Contact phone number',
  user_type ENUM('PATIENT', 'DOCTOR', 'ADMIN') NOT NULL COMMENT 'User role',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
  is_active BOOLEAN DEFAULT TRUE COMMENT 'Account active status',
  last_login TIMESTAMP NULL COMMENT 'Last login timestamp',
  
  KEY idx_user_email (email),
  KEY idx_user_phone (phone_number),
  KEY idx_user_type (user_type),
  KEY idx_user_active_created (is_active, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Core user table for all roles';

-- Table: patients
-- Purpose: Detailed patient information (extends users)
CREATE TABLE patients (
  patient_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Patient identifier',
  user_id BIGINT NOT NULL UNIQUE COMMENT 'Reference to users.user_id (NO FK)',
  date_of_birth DATE COMMENT 'Date of birth',
  gender ENUM('MALE', 'FEMALE', 'OTHER') COMMENT 'Gender',
  address VARCHAR(500) COMMENT 'Primary address',
  city VARCHAR(100) COMMENT 'City/Province',
  allergies TEXT COMMENT 'Medical allergies (JSON format)',
  insurance_number VARCHAR(50) COMMENT 'Health insurance number',
  emergency_contact VARCHAR(100) COMMENT 'Emergency contact name/phone',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY idx_patient_user_id (user_id),
  KEY idx_patient_city (city),
  KEY idx_patient_dob (date_of_birth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Patient-specific data';

-- Table: patient_addresses
-- Purpose: Multiple addresses per patient (optional)
CREATE TABLE patient_addresses (
  address_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL COMMENT 'Reference to patients.patient_id (NO FK)',
  address_type ENUM('HOME', 'WORK', 'OTHER') NOT NULL,
  street VARCHAR(255) NOT NULL,
  city VARCHAR(100) NOT NULL,
  postal_code VARCHAR(20),
  is_primary BOOLEAN DEFAULT FALSE COMMENT 'Primary address flag',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_patient_addresses_patient_id (patient_id),
  KEY idx_patient_addresses_primary (patient_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Multiple addresses per patient';

-- ====================================================================
-- MODULE 2: DOCTOR & SCHEDULE
-- ====================================================================

-- Table: doctors
-- Purpose: Doctor information (extends users)
CREATE TABLE doctors (
  doctor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE COMMENT 'Reference to users.user_id (NO FK)',
  license_number VARCHAR(50) UNIQUE NOT NULL COMMENT 'Medical license number',
  specialization VARCHAR(100) NOT NULL COMMENT 'Specialty (Implant, Orthodontic, etc.)',
  experience_years INT DEFAULT 0 COMMENT 'Years of experience',
  office_location VARCHAR(255) COMMENT 'Clinic location/room number',
  max_patients_per_day INT DEFAULT 20,
  consultation_duration_minutes INT DEFAULT 30 COMMENT 'Appointment duration in minutes',
  bio TEXT COMMENT 'Doctor biography',
  avatar_url VARCHAR(500),
  is_available BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY idx_doctor_user_id (user_id),
  KEY idx_doctor_specialization (specialization),
  KEY idx_doctor_available (is_available),
  KEY idx_doctor_location (office_location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Doctor profile information';

-- Table: doctor_schedules
-- Purpose: Recurring work schedule (e.g., Mon-Fri 8-17)
CREATE TABLE doctor_schedules (
  schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doctor_id BIGINT NOT NULL COMMENT 'Reference to doctors.doctor_id (NO FK)',
  day_of_week ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY') NOT NULL,
  start_time TIME NOT NULL COMMENT 'Work start time (08:00)',
  end_time TIME NOT NULL COMMENT 'Work end time (17:00)',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY idx_doctor_schedule_day (doctor_id, day_of_week, is_active),
  KEY idx_doctor_schedule_lookup (doctor_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recurring doctor work schedule';

-- Table: doctor_schedule_exceptions
-- Purpose: Day-off, extended hours, holidays
CREATE TABLE doctor_schedule_exceptions (
  exception_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doctor_id BIGINT NOT NULL COMMENT 'Reference to doctors.doctor_id (NO FK)',
  exception_date DATE NOT NULL,
  exception_type ENUM('DAY_OFF', 'EXTENDED_HOURS', 'HOLIDAY') NOT NULL,
  start_time TIME COMMENT 'Override start time if applicable',
  end_time TIME COMMENT 'Override end time if applicable',
  reason VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_schedule_exception_date (doctor_id, exception_date),
  KEY idx_schedule_exception_type (exception_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Schedule exceptions and overrides';

-- ====================================================================
-- MODULE 3: SLOT/AVAILABILITY ⭐ CRITICAL
-- ====================================================================

-- Table: appointment_slots
-- Purpose: Available time slots for booking (CORE TO CONCURRENCY)
CREATE TABLE appointment_slots (
  slot_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Slot identifier',
  doctor_id BIGINT NOT NULL COMMENT 'Reference to doctors.doctor_id (NO FK)',
  slot_date DATE NOT NULL COMMENT 'Date of the slot',
  start_time TIME NOT NULL COMMENT 'Slot start time',
  end_time TIME NOT NULL COMMENT 'Slot end time',
  status ENUM('AVAILABLE', 'BOOKED', 'RESERVED', 'CANCELLED') DEFAULT 'AVAILABLE' 
    COMMENT 'AVAILABLE: free | BOOKED: taken | RESERVED: hold | CANCELLED: unavailable',
  version BIGINT DEFAULT 0 COMMENT '⭐ OPTIMISTIC LOCKING: increment on update',
  booked_by BIGINT COMMENT 'Reference to patients.patient_id if BOOKED (NO FK)',
  reserved_until TIMESTAMP NULL COMMENT 'Reservation expiry time',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  CONSTRAINT chk_slot_time CHECK (start_time < end_time),
  CONSTRAINT chk_slot_booked CHECK (
    (status = 'BOOKED' AND booked_by IS NOT NULL) OR
    (status != 'BOOKED' AND booked_by IS NULL)
  ),
  
  -- ⭐ CRITICAL INDEXES
  KEY idx_slot_doctor_date_status (doctor_id, slot_date, status, start_time) COMMENT '⭐ Main query for finding available slots',
  KEY idx_slot_booked_by (booked_by),
  KEY idx_slot_date_range (slot_date),
  KEY idx_slot_reserved_until (status, reserved_until) WHERE status = 'RESERVED',
  KEY idx_slot_lookup (doctor_id, slot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='⭐ CORE: Appointment slot management with concurrency control';

-- Table: slot_templates
-- Purpose: Template for batch slot generation
CREATE TABLE slot_templates (
  template_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doctor_id BIGINT NOT NULL COMMENT 'Reference to doctors.doctor_id (NO FK)',
  day_of_week ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY') NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  slot_duration_minutes INT DEFAULT 30 COMMENT 'Duration of each generated slot',
  break_time_start TIME COMMENT 'Break start time (optional)',
  break_time_end TIME COMMENT 'Break end time (optional)',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_template_doctor (doctor_id, is_active),
  KEY idx_template_day (doctor_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Template for generating recurring appointment slots';

-- ====================================================================
-- MODULE 4: BOOKING/APPOINTMENT ⭐ CRITICAL
-- ====================================================================

-- Table: bookings
-- Purpose: Main booking/appointment transaction record
CREATE TABLE bookings (
  booking_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Booking identifier',
  patient_id BIGINT NOT NULL COMMENT 'Reference to patients.patient_id (NO FK)',
  doctor_id BIGINT NOT NULL COMMENT 'Reference to doctors.doctor_id (NO FK)',
  slot_id BIGINT NOT NULL COMMENT 'Reference to appointment_slots.slot_id (NO FK)',
  booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When booking was made',
  appointment_date DATE NOT NULL COMMENT 'Date of appointment',
  appointment_time TIME NOT NULL COMMENT 'Start time of appointment',
  appointment_end_time TIME NOT NULL COMMENT 'End time of appointment',
  status ENUM('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW') 
    DEFAULT 'SCHEDULED' COMMENT 'Appointment status',
  visit_type ENUM('CONSULTATION', 'TREATMENT', 'FOLLOW_UP', 'EMERGENCY') DEFAULT 'CONSULTATION',
  notes TEXT COMMENT 'Patient notes',
  cancellation_reason VARCHAR(255),
  cancelled_at TIMESTAMP NULL,
  cancelled_by BIGINT COMMENT 'Reference to users.user_id (NO FK)',
  version BIGINT DEFAULT 0 COMMENT 'Optimistic locking version',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  CONSTRAINT chk_booking_time CHECK (appointment_time < appointment_end_time),
  CONSTRAINT chk_booking_unique_slot UNIQUE (slot_id) COMMENT 'One booking per slot',
  CONSTRAINT chk_booking_future_date CHECK (appointment_date >= CURDATE()),
  
  -- ⭐ CRITICAL INDEXES
  KEY idx_booking_patient_id (patient_id, appointment_date),
  KEY idx_booking_doctor_date_status (doctor_id, appointment_date, status),
  UNIQUE KEY idx_booking_slot_id (slot_id) COMMENT 'Prevent double-booking',
  KEY idx_booking_appointment_date (appointment_date),
  KEY idx_booking_status (status, appointment_date),
  KEY idx_booking_cancelled (cancelled_by, cancelled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='⭐ CORE: Main booking transaction table';

-- Table: booking_status_history
-- Purpose: Audit trail for booking status changes
CREATE TABLE booking_status_history (
  history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL COMMENT 'Reference to bookings.booking_id (NO FK)',
  old_status ENUM('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'),
  new_status ENUM('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW') NOT NULL,
  changed_by BIGINT COMMENT 'Reference to users.user_id (NO FK)',
  reason VARCHAR(255),
  changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_history_booking_id (booking_id, changed_at),
  KEY idx_history_changed_by (changed_by, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Booking status change audit trail';

-- Table: appointment_notes
-- Purpose: Clinical notes and follow-up information
CREATE TABLE appointment_notes (
  note_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL COMMENT 'Reference to bookings.booking_id (NO FK)',
  doctor_notes TEXT COMMENT 'Doctor clinical notes',
  patient_feedback VARCHAR(500),
  recommended_treatment TEXT COMMENT 'Treatment recommendations',
  follow_up_date DATE COMMENT 'Recommended follow-up date',
  follow_up_slot_id BIGINT COMMENT 'Reference to appointment_slots.slot_id for follow-up (NO FK)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY idx_note_booking_id (booking_id),
  KEY idx_note_follow_up (follow_up_date, follow_up_slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Appointment clinical notes';

-- Table: booking_cancellations
-- Purpose: Detailed cancellation tracking
CREATE TABLE booking_cancellations (
  cancellation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE COMMENT 'Reference to bookings.booking_id (NO FK)',
  cancelled_by_type ENUM('PATIENT', 'DOCTOR', 'ADMIN', 'SYSTEM') NOT NULL,
  cancelled_by_id BIGINT COMMENT 'Reference to users.user_id (NO FK)',
  cancellation_reason VARCHAR(255) NOT NULL,
  refund_status ENUM('PENDING', 'PROCESSED', 'REJECTED') DEFAULT 'PENDING',
  refund_amount DECIMAL(10, 2),
  cancelled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_cancellation_booking_id (booking_id),
  KEY idx_cancellation_type (cancelled_by_type, cancelled_at),
  KEY idx_cancellation_refund (refund_status, cancelled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Booking cancellation details';

-- ====================================================================
-- INDEXES FOR PERFORMANCE (Summary)
-- ====================================================================

-- Verify critical indexes exist
-- Run this query to check index status:
-- SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME 
-- FROM INFORMATION_SCHEMA.STATISTICS 
-- WHERE TABLE_SCHEMA = 'dental_booking_system' 
-- ORDER BY TABLE_NAME, INDEX_NAME;

-- ====================================================================
-- SAMPLE DATA (for development/testing)
-- ====================================================================

-- Insert sample user (Patient)
INSERT INTO users (email, password_hash, full_name, phone_number, user_type, is_active)
VALUES 
  ('patient001@example.com', '$2a$10$...(bcrypt hash)...', 'Nguyễn Văn A', '0123456789', 'PATIENT', TRUE),
  ('doctor001@example.com', '$2a$10$...(bcrypt hash)...', 'Dr. Trần Thị B', '0987654321', 'DOCTOR', TRUE),
  ('admin@example.com', '$2a$10$...(bcrypt hash)...', 'Admin System', '0111111111', 'ADMIN', TRUE);

-- ====================================================================
-- CLEANUP & UTILITIES
-- ====================================================================

-- To drop all tables (for reset):
-- DROP TABLE IF EXISTS booking_cancellations;
-- DROP TABLE IF EXISTS appointment_notes;
-- DROP TABLE IF EXISTS booking_status_history;
-- DROP TABLE IF EXISTS bookings;
-- DROP TABLE IF EXISTS slot_templates;
-- DROP TABLE IF EXISTS appointment_slots;
-- DROP TABLE IF EXISTS doctor_schedule_exceptions;
-- DROP TABLE IF EXISTS doctor_schedules;
-- DROP TABLE IF EXISTS doctors;
-- DROP TABLE IF EXISTS patient_addresses;
-- DROP TABLE IF EXISTS patients;
-- DROP TABLE IF EXISTS users;

-- ====================================================================
-- END OF SCHEMA INITIALIZATION
-- ====================================================================
