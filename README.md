# 📍 CivicTrack v2 – Civic Issue Reporting & Management System

React 18 + Vite · Java 21 + Spring Boot 3.2.5 · Spring Security + JWT · JPA/Hibernate · MySQL **or embedded H2** · Leaflet + OpenStreetMap

> **Honest status:** this upgrade was written and syntax-checked (all 32 frontend source files parse with esbuild; all 53 Java files pass a javac parse check)
> but it was **NOT compiled or run** in the environment that produced it (no Maven / npm registry access).
> Run `build-standalone.bat` once; if Maven or Vite prints an error, send me the exact message and I will fix it.
> The test table at the bottom is a **checklist for you to run**, not results.

## Two ways to run

### A) Standalone program (recommended – no MySQL needed)
One JAR serves the website *and* the API on **http://localhost:8080**. Data lives in `backend\data`, photos in `backend\uploads`.
Requirements for the one-time build: JDK 21, Maven, Node.js (with internet). Running afterwards needs only Java 21.
```cmd
cd C:\Projects\civictrack
build-standalone.bat
run-standalone.bat
```
To hand the finished program to another PC: copy `backend\target\civictrack-backend-1.0.0.jar` and `run-standalone.bat`'s command
(`java -jar civictrack-backend-1.0.0.jar --spring.profiles.active=standalone`). Only Java 21 is needed there.

### B) Developer mode with MySQL (hot reload)
```cmd
:: terminal 1 - backend (MySQL must be running)
setx DB_PASSWORD "your_mysql_password"      (then open a NEW cmd window)
cd C:\Projects\civictrack\backend
mvn spring-boot:run
:: terminal 2 - frontend
cd C:\Projects\civictrack\frontend
npm install
npm run dev
```
Open http://localhost:5173. Or run the built JAR against MySQL with `run-mysql.bat`.

**Existing MySQL database?** Nothing is deleted. On start the backend automatically (a) converts the old `ENUM` status/category
columns to `VARCHAR` (needed for the new statuses), (b) adds the new columns/tables, (c) back-fills SLA deadlines and photos.
`database\migration_v2.sql` does the same by hand. **Back up first:** `mysqldump -u root -p civictrack > backup.sql`.
Note: officers now see only complaints **assigned to them**; assign your old complaints to officers from the admin screen.

## Accounts & secure setup
Demo accounts are created on first start **only when** `SEED_DEMO_ACCOUNTS` is not `false`:

| Role | Email | Default password |
|---|---|---|
| Admin | admin@civictrack.com | Admin@123 |
| Officer (Roads) | officer@civictrack.com | Officer@123 |
| Officer (Water) | officer2@civictrack.com | Officer@123 |
| Citizen | citizen@civictrack.com | Citizen@123 |

Choose your own passwords before the first start: `setx DEMO_ADMIN_PASSWORD "..."`, `DEMO_OFFICER_PASSWORD`, `DEMO_CITIZEN_PASSWORD`
(8+ chars, upper+lower+digit). For real use set `SEED_DEMO_ACCOUNTS=false`, create the first admin yourself, and change/disable the demo users.
**JWT secret:** set `JWT_SECRET` (32+ chars). If unset, a random secret is generated once into `backend\data\jwt.secret` – keep that file private.
Create more officers in **Admin → Users & Officers**.

## What changed (summary)
* **Security:** BCrypt, JWT with `jti` + server-side **logout revocation**, login brute-force lock (5 fails → 15 min, per email+IP, plus per-IP),
  same error for unknown email/wrong password (no account enumeration), password policy (8+, upper/lower/digit), role check re-validated against DB
  on every request (deactivated users are locked out immediately), JSON 401/403, no stack traces/messages leaked, upload validation by **file content**
  (JPG/PNG/GIF/WEBP, ≤5 MB, ≤5 files, random names), citizens can no longer choose priority, no generic status endpoint (explicit workflow endpoints only).
* **Roles:** Citizen sees only own complaints; **Officer sees only complaints assigned to them** (was: whole department); Admin everything.
* **Workflow:** SUBMITTED → UNDER_REVIEW → ASSIGNED → *officer accepts* → IN_PROGRESS → RESOLUTION_SUBMITTED → admin approve → RESOLVED
  (admin reject → back to IN_PROGRESS with remarks). Admin can also REJECT / REOPEN a complaint. Officers can never set RESOLVED.
  Every change is stored in `status_history` (who, when, remarks).
* **Departments:** category→department mapping table (editable in *Departments*), auto-assigned on submit, admin must pick an active officer of that department.
* **Location:** place search + address autocomplete (backend Nominatim proxy: 1 req/s, cache, User-Agent, errors), reverse geocoding, click/drag, GPS optional, address saved.
* **Notifications:** in-app bell with unread count, history page; citizens notified at submit, department, officer, work start, resolution submitted/approved/rejected, resolved.
* **Admin analytics:** all cards/charts/tables from live DB with date/department/category filters; officer performance; Excel + PDF export.
* **Extras:** SLA deadlines by priority + hourly-style escalation scheduler (every 10 min), duplicate suggestions (≤150 m + text similarity), feedback & rating, audit history.
* **Frontend:** separate Citizen / Officer / Admin dashboards and colour themes, workflow stepper, photo galleries, resolution proof view, filters, responsive layout.

## Bugs found & fixed (root causes)
1. **Login "accepts" wrong/unregistered email** – the server already rejected them (401). The real defect was the Axios 401 handler: if an old token existed in
   the browser it hard-reloaded the page on *every* 401, wiping the error and making it look as if login did something; the app also silently re-logged the previous
   user from the stored token. Fixed handler, real logout (token revoked), rate limiting, uniform error.
2. **Officer login/work not working** – officers were scoped by *department*, and the demo officer only got a department if that department already existed, so officers saw
   nothing. Now officers are scoped by assignment and departments are created idempotently.
3. **Build blocker:** `pom.xml` contained an invalid `<n>` element instead of `<name>` – fixed.
4. **Status enum break:** Hibernate 6 creates MySQL `ENUM` columns, which would reject the new statuses – automatic safe conversion + `@JdbcTypeCode(VARCHAR)`.
5. Status could be set to anything (even RESOLVED by an officer) via one generic endpoint – replaced by guarded workflow endpoints.
6. Resolution proof did not exist (no remarks/photo storage) – new `resolution_details` + photos, shown to admin/officer and (approved only) to citizen/public tracking.
7. Location: direct browser calls to Nominatim, single result, no autocomplete – replaced.
8. Dashboard features that were not backed by data (pending/under review/assigned splits, officer performance, filters) – now computed server-side.

## Remaining limitations (please read)
* Not compiled/run by me – see the status note above.
* Nominatim's public server forbids heavy use and officially does not support "search-as-you-type"; this app debounces (800 ms), caches and limits to 1 req/s.
  For production traffic use your own Nominatim/Photon/LocationIQ and set `app.geocoder.base-url`. Set `GEOCODER_CONTACT` (your email) as required by the usage policy.
* Login lock-out, token revocation and geocoder cache are **in-memory** (reset on restart; single server only).
* Uploaded photos are served by an unguessable random file name, not behind login (needed for the public tracking page).
* Notifications are in-app only (no email/SMS) – that needs SMTP/SMS credentials.
* Roles are an enum on `users` (not a separate table); `officer_assignments` records every (re)assignment but has no UI.
* The H2 standalone database is for small/demo deployments; use MySQL for multi-user production.

## Manual test checklist (run these)
1 Register citizen · 2 duplicate email rejected (409) · 3 login OK · 4 wrong password rejected · 5 unknown email rejected (same message) · 6 admin login · 7 officer login ·
8 citizen→`/admin/users` redirected + API 403 · 9 officer same · 10 submit complaint (with 2 photos) · 11 department shown automatically · 12 admin assigns officer (dept + officer) ·
13 officer sees it (and NOT another officer's) · 14 accept → start → add progress · 15 submit resolution with photo · 16 admin approves · 17 citizen sees RESOLVED + proof + can rate ·
18 bell shows unread count at each step · 19 search "Pune" / click map / drag marker / deny GPS · 20 `curl http://localhost:8080/api/complaints` → 401; citizen token on `/api/admin/users` → 403 ·
6 wrong logins → 429 · reject a resolution → officer notified, status back to In Progress · Admin export Excel/PDF · reopen a resolved complaint.

## REST API (all under /api; everything except login/register/track/departments needs a Bearer token)
| Endpoint | Role |
|---|---|
| POST auth/register, auth/login · POST auth/logout · GET auth/me | public / any |
| POST complaints (multipart: title, description, category, latitude, longitude, address, images[]) | CITIZEN |
| GET complaints (search,status,category,priority,departmentId,officerId,from,to,overdue,page,size,sort) · GET complaints/{id} · GET complaints/stats | scoped by role |
| GET complaints/track/{id} | public |
| POST complaints/{id}/feedback | CITIZEN (own, resolved) |
| POST complaints/{id}/review · assign · reject · reopen · priority · verify · GET …/duplicates | ADMIN |
| POST complaints/{id}/accept · start · progress · resolution (multipart: remarks, photos[]) | OFFICER (assigned to them) |
| GET notifications · GET notifications/unread-count · POST notifications/{id}/read · POST notifications/read-all | any (own) |
| GET geo/search?q= · GET geo/reverse?lat=&lon= | any logged-in |
| GET/POST/PUT/DELETE departments · GET departments/category-map | public GET / ADMIN write |
| GET admin/users · officers · POST admin/officers · PUT admin/officers/{id} · POST admin/officers/{id}/password · PATCH admin/users/{id}/active · GET/PUT admin/category-departments | ADMIN |
| GET analytics/summary · analytics/export/excel · analytics/export/pdf | ADMIN |

## Troubleshooting
* `Access denied for user 'root'` → wrong MySQL password (`setx DB_PASSWORD ...`, new cmd window). `release version 21 not supported` → Maven uses an older JDK (set `JAVA_HOME`).
* Port 8080 busy → `netstat -ano | findstr :8080` then `taskkill /PID <pid> /F`.
* Blank map / no suggestions → needs internet (OpenStreetMap). GPS only works on `localhost` or HTTPS and needs browser permission (optional anyway).
* `npm install` peer errors → `npm install --legacy-peer-deps`.
* Reset standalone data (**deletes everything**) → stop the program and delete `backend\data`.
