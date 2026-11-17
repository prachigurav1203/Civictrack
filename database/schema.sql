-- CivicTrack database schema (MySQL 8+) - NEW installations only.
-- OPTIONAL: the backend creates/updates these tables automatically (spring.jpa.hibernate.ddl-auto=update).
-- Existing database? Do NOT run this file; start the backend (it upgrades safely) or use migration_v2.sql.
-- This file creates the CORE tables; the v2 tables (resolution_details, complaint_photos, notifications, feedback,
-- category_departments, officer_assignments) are appended at the bottom.

CREATE DATABASE IF NOT EXISTS civictrack CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE civictrack;

CREATE TABLE IF NOT EXISTS departments (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(500)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password      VARCHAR(255) NOT NULL,
  phone         VARCHAR(20),
  role          VARCHAR(20)  NOT NULL,            -- CITIZEN | ADMIN | OFFICER
  department_id BIGINT NULL,
  active        BIT(1) NOT NULL DEFAULT b'1',
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS complaints (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tracking_id   VARCHAR(30)  NOT NULL UNIQUE,
  title         VARCHAR(200) NOT NULL,
  description   TEXT         NOT NULL,
  category      VARCHAR(30)  NOT NULL,
  priority      VARCHAR(20)  NOT NULL,            -- LOW | MEDIUM | HIGH | CRITICAL
  status        VARCHAR(30)  NOT NULL,            -- SUBMITTED | UNDER_REVIEW | ASSIGNED | IN_PROGRESS | RESOLUTION_SUBMITTED | RESOLVED | REJECTED | REOPENED
  latitude      DOUBLE,
  longitude     DOUBLE,
  address       VARCHAR(500),
  image_path    VARCHAR(255),
  citizen_id    BIGINT NOT NULL,
  department_id BIGINT NULL,
  officer_id    BIGINT NULL,
  created_at    DATETIME(6),
  updated_at    DATETIME(6),
  resolved_at   DATETIME(6),
  accepted_at   DATETIME(6),
  sla_due_at    DATETIME(6),
  escalated_at  DATETIME(6),
  CONSTRAINT fk_complaints_citizen    FOREIGN KEY (citizen_id)    REFERENCES users(id),
  CONSTRAINT fk_complaints_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT fk_complaints_officer    FOREIGN KEY (officer_id)    REFERENCES users(id),
  INDEX idx_complaints_status (status),
  INDEX idx_complaints_created (created_at),
  INDEX idx_complaints_officer (officer_id),
  INDEX idx_complaints_department (department_id),
  INDEX idx_complaints_category (category),
  INDEX idx_complaints_geo (latitude, longitude)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS status_history (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id BIGINT NOT NULL,
  status       VARCHAR(30) NOT NULL,
  remarks      VARCHAR(1000),
  changed_by   BIGINT NULL,
  changed_at   DATETIME(6) NOT NULL,
  CONSTRAINT fk_history_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE,
  CONSTRAINT fk_history_user      FOREIGN KEY (changed_by)   REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------- v2 tables
CREATE TABLE IF NOT EXISTS resolution_details (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id  BIGINT NOT NULL,
  submitted_by  BIGINT NOT NULL,
  remarks       VARCHAR(2000) NOT NULL,
  submitted_at  DATETIME(6) NOT NULL,
  status        VARCHAR(20) NOT NULL,            -- PENDING | APPROVED | REJECTED
  verified_by   BIGINT NULL,
  verified_at   DATETIME(6) NULL,
  admin_remarks VARCHAR(1000) NULL,
  CONSTRAINT fk_res_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE,
  CONSTRAINT fk_res_submitter FOREIGN KEY (submitted_by) REFERENCES users(id),
  CONSTRAINT fk_res_verifier  FOREIGN KEY (verified_by)  REFERENCES users(id),
  INDEX idx_resolution_complaint (complaint_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS complaint_photos (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id  BIGINT NOT NULL,
  resolution_id BIGINT NULL,
  url           VARCHAR(255) NOT NULL,
  uploaded_at   DATETIME(6) NOT NULL,
  CONSTRAINT fk_photo_complaint  FOREIGN KEY (complaint_id)  REFERENCES complaints(id) ON DELETE CASCADE,
  CONSTRAINT fk_photo_resolution FOREIGN KEY (resolution_id) REFERENCES resolution_details(id) ON DELETE CASCADE,
  INDEX idx_photos_complaint (complaint_id),
  INDEX idx_photos_resolution (resolution_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  complaint_id BIGINT NULL,
  type         VARCHAR(40) NOT NULL,
  message      VARCHAR(500) NOT NULL,
  is_read      BIT(1) NOT NULL DEFAULT b'0',
  created_at   DATETIME(6) NOT NULL,
  CONSTRAINT fk_notif_user      FOREIGN KEY (user_id)      REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notif_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE,
  INDEX idx_notifications_user (user_id, is_read, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS feedback (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id BIGINT NOT NULL,
  citizen_id   BIGINT NOT NULL,
  rating       INT NOT NULL,
  comment      VARCHAR(1000) NULL,
  created_at   DATETIME(6) NOT NULL,
  CONSTRAINT uk_feedback_complaint UNIQUE (complaint_id),
  CONSTRAINT fk_fb_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE,
  CONSTRAINT fk_fb_citizen   FOREIGN KEY (citizen_id)   REFERENCES users(id),
  CONSTRAINT chk_fb_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS category_departments (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  category      VARCHAR(30) NOT NULL,
  department_id BIGINT NOT NULL,
  CONSTRAINT uk_category_dept UNIQUE (category),
  CONSTRAINT fk_catdept_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS officer_assignments (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id  BIGINT NOT NULL,
  officer_id    BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  assigned_by   BIGINT NOT NULL,
  assigned_at   DATETIME(6) NOT NULL,
  CONSTRAINT fk_oa_complaint  FOREIGN KEY (complaint_id)  REFERENCES complaints(id) ON DELETE CASCADE,
  CONSTRAINT fk_oa_officer    FOREIGN KEY (officer_id)    REFERENCES users(id),
  CONSTRAINT fk_oa_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT fk_oa_admin      FOREIGN KEY (assigned_by)   REFERENCES users(id),
  INDEX idx_assignments_complaint (complaint_id)
) ENGINE=InnoDB;

