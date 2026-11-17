-- CivicTrack v2 - SAFE migration for an EXISTING database (MySQL 8+).
-- Nothing is dropped or deleted. Take a backup first:
--   mysqldump -u root -p civictrack > civictrack_backup.sql
--
-- NOTE: the backend runs the same steps automatically at start-up (SchemaMigrator), so this script is only
-- needed if you prefer to migrate by hand, or if the automatic upgrade logged an error.
-- Run it BEFORE starting the new backend only if you want full control; otherwise just start the backend.

USE civictrack;

-- 1) Hibernate 6 created ENUM columns; the new statuses / categories need VARCHAR (existing values are kept).
ALTER TABLE complaints      MODIFY COLUMN status   VARCHAR(30) NOT NULL;
ALTER TABLE complaints      MODIFY COLUMN category VARCHAR(30) NOT NULL;
ALTER TABLE complaints      MODIFY COLUMN priority VARCHAR(20) NOT NULL;
ALTER TABLE status_history  MODIFY COLUMN status   VARCHAR(30) NOT NULL;

-- 2) New columns on complaints (skip a line if you get "Duplicate column name": it means it already exists)
ALTER TABLE complaints ADD COLUMN accepted_at  DATETIME(6) NULL;
ALTER TABLE complaints ADD COLUMN sla_due_at   DATETIME(6) NULL;
ALTER TABLE complaints ADD COLUMN escalated_at DATETIME(6) NULL;

-- 3) New tables (the backend also creates them automatically)
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

-- 4) Extra department used by the PUBLIC_PROPERTY / OTHER categories
INSERT INTO departments (name, description)
SELECT 'Municipal Maintenance', 'Public buildings and property, general municipal issues'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Municipal Maintenance');

-- 5) Category -> department mapping (INSERT IGNORE keeps any mapping that already exists)
INSERT IGNORE INTO category_departments (category, department_id)
SELECT m.cat, d.id FROM (
  SELECT 'ROADS' cat, 'Roads & Public Works' dn UNION ALL SELECT 'WATER_SUPPLY','Water Supply'
  UNION ALL SELECT 'SANITATION','Sanitation & Waste' UNION ALL SELECT 'ELECTRICITY','Electricity & Street Lights'
  UNION ALL SELECT 'STREET_LIGHTS','Electricity & Street Lights' UNION ALL SELECT 'DRAINAGE','Drainage & Sewerage'
  UNION ALL SELECT 'PARKS','Parks & Environment' UNION ALL SELECT 'PUBLIC_SAFETY','Public Safety'
  UNION ALL SELECT 'PUBLIC_PROPERTY','Municipal Maintenance' UNION ALL SELECT 'OTHER','Municipal Maintenance'
) m JOIN departments d ON d.name = m.dn;

-- 6) Back-fill for complaints created before the upgrade
UPDATE complaints SET sla_due_at = DATE_ADD(created_at, INTERVAL CASE priority
    WHEN 'CRITICAL' THEN 24 WHEN 'HIGH' THEN 72 WHEN 'LOW' THEN 336 ELSE 168 END HOUR)
WHERE sla_due_at IS NULL AND created_at IS NOT NULL;

INSERT INTO complaint_photos (complaint_id, url, uploaded_at)
SELECT c.id, c.image_path, COALESCE(c.created_at, NOW()) FROM complaints c
WHERE c.image_path IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM complaint_photos p WHERE p.complaint_id = c.id AND p.resolution_id IS NULL);

-- 7) Complaints that officers could previously see by department only: nothing is changed here. Officers now see
--    only complaints assigned to THEM, so existing complaints need an officer assigned by an admin.
