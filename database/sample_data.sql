-- CivicTrack sample data
-- PREREQUISITE: start the backend at least once so the demo users are created
-- (admin@civictrack.com, officer@civictrack.com, citizen@civictrack.com).
-- Passwords are BCrypt-hashed by the backend, so users are NOT inserted here.
-- Run this file ONCE (running it twice duplicates status history rows).

USE civictrack;

INSERT IGNORE INTO departments (name, description) VALUES
 ('Roads & Public Works',        'Potholes, road damage, footpaths, bridges'),
 ('Water Supply',                'Water leakage, supply interruption, contamination'),
 ('Sanitation & Waste',          'Garbage collection, public toilets, cleanliness'),
 ('Electricity & Street Lights', 'Power faults, street light repairs'),
 ('Drainage & Sewerage',         'Blocked drains, sewage overflow, flooding'),
 ('Parks & Environment',         'Gardens, trees, pollution, public spaces'),
 ('Public Safety',               'Hazards, encroachment, unsafe structures');

SET @citizen = (SELECT id FROM users WHERE email = 'citizen@civictrack.com');
SET @admin   = (SELECT id FROM users WHERE email = 'admin@civictrack.com');
SET @officer = (SELECT id FROM users WHERE email = 'officer@civictrack.com');
SET @roads   = (SELECT id FROM departments WHERE name = 'Roads & Public Works');
SET @water   = (SELECT id FROM departments WHERE name = 'Water Supply');
SET @sanit   = (SELECT id FROM departments WHERE name = 'Sanitation & Waste');
SET @elec    = (SELECT id FROM departments WHERE name = 'Electricity & Street Lights');
SET @drain   = (SELECT id FROM departments WHERE name = 'Drainage & Sewerage');

INSERT IGNORE INTO complaints
 (tracking_id, title, description, category, priority, status, latitude, longitude, address,
  citizen_id, department_id, officer_id, created_at, updated_at, resolved_at) VALUES
 ('CT2026-DEMO01','Large pothole on main road','A deep pothole near the bus stop is causing accidents for two-wheelers.','ROADS','HIGH','RESOLVED',18.5204,73.8567,'Main Road, near Bus Stop',@citizen,@roads,@officer,DATE_SUB(NOW(),INTERVAL 120 DAY),DATE_SUB(NOW(),INTERVAL 115 DAY),DATE_SUB(NOW(),INTERVAL 115 DAY)),
 ('CT2026-DEMO02','Water pipeline leakage','Clean water has been leaking from the main pipeline for three days.','WATER_SUPPLY','MEDIUM','IN_PROGRESS',18.5314,73.8446,'Lane 4, Market Area',@citizen,@water,NULL,DATE_SUB(NOW(),INTERVAL 40 DAY),DATE_SUB(NOW(),INTERVAL 38 DAY),NULL),
 ('CT2026-DEMO03','Garbage not collected for a week','Garbage has piled up near the community ground and smells bad.','SANITATION','HIGH','ASSIGNED',18.5074,73.8077,'Community Ground Road',@citizen,@sanit,NULL,DATE_SUB(NOW(),INTERVAL 20 DAY),DATE_SUB(NOW(),INTERVAL 19 DAY),NULL),
 ('CT2026-DEMO04','Street light not working','The street light outside the school has been off for two weeks.','STREET_LIGHTS','MEDIUM','RESOLVED',18.5590,73.7868,'School Road',@citizen,@elec,NULL,DATE_SUB(NOW(),INTERVAL 95 DAY),DATE_SUB(NOW(),INTERVAL 90 DAY),DATE_SUB(NOW(),INTERVAL 90 DAY)),
 ('CT2026-DEMO05','Blocked drain causing waterlogging','Drain is blocked and water enters shops after every rain.','DRAINAGE','CRITICAL','IN_PROGRESS',18.4967,73.8553,'Old Market Street',@citizen,@drain,NULL,DATE_SUB(NOW(),INTERVAL 10 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY),NULL),
 ('CT2026-DEMO06','Broken bench in public park','Two benches in the central park are broken and unsafe.','PARKS','LOW','SUBMITTED',18.5362,73.8940,'Central Park',@citizen,NULL,NULL,DATE_SUB(NOW(),INTERVAL 3 DAY),DATE_SUB(NOW(),INTERVAL 3 DAY),NULL),
 ('CT2026-DEMO07','Open manhole on footpath','An uncovered manhole is a serious danger to pedestrians at night.','PUBLIC_SAFETY','CRITICAL','UNDER_REVIEW',18.5123,73.8412,'Station Road footpath',@citizen,NULL,NULL,DATE_SUB(NOW(),INTERVAL 2 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),NULL),
 ('CT2026-DEMO08','Road surface caved in','Part of the road surface collapsed after heavy rain.','ROADS','HIGH','RESOLVED',18.5679,73.9143,'Ring Road, Sector 5',@citizen,@roads,@officer,DATE_SUB(NOW(),INTERVAL 65 DAY),DATE_SUB(NOW(),INTERVAL 60 DAY),DATE_SUB(NOW(),INTERVAL 60 DAY)),
 ('CT2026-DEMO09','Frequent power cuts','Power goes off several times daily in our locality.','ELECTRICITY','HIGH','REJECTED',18.4800,73.8200,'Green Colony',@citizen,NULL,NULL,DATE_SUB(NOW(),INTERVAL 30 DAY),DATE_SUB(NOW(),INTERVAL 28 DAY),NULL),
 ('CT2026-DEMO10','Dirty water in taps','Tap water is yellow and smells of chemicals since Monday.','WATER_SUPPLY','HIGH','RESOLVED',18.5450,73.8300,'Lakeview Society',@citizen,@water,NULL,DATE_SUB(NOW(),INTERVAL 150 DAY),DATE_SUB(NOW(),INTERVAL 146 DAY),DATE_SUB(NOW(),INTERVAL 146 DAY)),
 ('CT2026-DEMO11','Overflowing public dustbin','The dustbin near the vegetable market overflows daily.','SANITATION','LOW','SUBMITTED',18.5250,73.8750,'Vegetable Market',@citizen,NULL,NULL,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),NULL),
 ('CT2026-DEMO12','Footpath encroachment','Vendors have blocked the entire footpath, forcing people onto the road.','OTHER','MEDIUM','ASSIGNED',18.5160,73.8480,'Temple Road',@citizen,@roads,@officer,DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY),NULL);

-- Status history generated from the complaints above
INSERT INTO status_history (complaint_id, status, remarks, changed_by, changed_at)
  SELECT id, 'SUBMITTED', 'Complaint registered', citizen_id, created_at FROM complaints WHERE tracking_id LIKE 'CT2026-DEMO%';
INSERT INTO status_history (complaint_id, status, remarks, changed_by, changed_at)
  SELECT id, 'UNDER_REVIEW', 'Complaint is being reviewed', @admin, DATE_ADD(created_at, INTERVAL 1 DAY) FROM complaints WHERE tracking_id LIKE 'CT2026-DEMO%' AND status = 'UNDER_REVIEW';
INSERT INTO status_history (complaint_id, status, remarks, changed_by, changed_at)
  SELECT id, 'ASSIGNED', 'Assigned to department', @admin, DATE_ADD(created_at, INTERVAL 1 DAY) FROM complaints WHERE tracking_id LIKE 'CT2026-DEMO%' AND department_id IS NOT NULL;
INSERT INTO status_history (complaint_id, status, remarks, changed_by, changed_at)
  SELECT id, 'IN_PROGRESS', 'Work has started', IFNULL(officer_id, @admin), DATE_ADD(created_at, INTERVAL 2 DAY) FROM complaints WHERE tracking_id LIKE 'CT2026-DEMO%' AND status IN ('IN_PROGRESS','RESOLVED');
INSERT INTO status_history (complaint_id, status, remarks, changed_by, changed_at)
  SELECT id, 'RESOLVED', 'Issue fixed and verified', IFNULL(officer_id, @admin), resolved_at FROM complaints WHERE tracking_id LIKE 'CT2026-DEMO%' AND status = 'RESOLVED';
INSERT INTO status_history (complaint_id, status, remarks, changed_by, changed_at)
  SELECT id, 'REJECTED', 'Not a municipal responsibility - please contact the electricity utility', @admin, updated_at FROM complaints WHERE tracking_id LIKE 'CT2026-DEMO%' AND status = 'REJECTED';
