# CivicTrack v2 – Civic Issue Reporting & Management System

CivicTrack is a web-based civic issue management platform that allows citizens to report local problems, track complaint status, and receive updates. It helps administrators and officers manage complaints through a structured workflow.

## Tech Stack

- **Frontend:** React 18, Vite, JavaScript
- **Backend:** Java 21, Spring Boot 3.2.5
- **Security:** Spring Security, JWT, BCrypt
- **Database:** MySQL / H2
- **ORM:** JPA / Hibernate
- **Maps:** Leaflet, OpenStreetMap

## Key Features

- Citizen registration and secure login
- Submit complaints with descriptions, locations, and photos
- Automatic department assignment based on complaint category
- Admin dashboard for complaint and user management
- Officer dashboard with assigned complaints
- Complaint status tracking and workflow management
- Secure role-based access control
- Location search, GPS support, and interactive maps
- In-app notifications and complaint history
- Resolution proof with photos and remarks
- Admin analytics with Excel and PDF exports
- Feedback and rating system

## User Roles

| Role | Responsibilities |
|---|---|
| Citizen | Register, report issues, track complaints, and provide feedback |
| Officer | Manage assigned complaints and submit resolution details |
| Admin | Manage users, departments, assignments, complaints, and analytics |

## Running the Project

**Option 1: Standalone Mode (Recommended)**

Requirements: Java 21, Maven, Node.js (for the initial build).

```bash
build-standalone.bat
run-standalone.bat
```

Open: http://localhost:8080

Standalone mode uses an embedded H2 database and does not require MySQL.

**Option 2: Developer Mode**

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend (open another terminal):

```bash
cd frontend
npm install
npm run dev
```

Open: http://localhost:5173

Developer mode requires MySQL configuration.

## Project Structure

```text
CivicTrack/
├── backend/
│   ├── src/
│   ├── data/
│   └── uploads/
├── frontend/
│   ├── src/
│   └── public/
├── database/
├── build-standalone.bat
├── run-standalone.bat
└── README.md
```

## Security Highlights

- JWT-based authentication
- BCrypt password encryption
- Role-based authorization
- Login rate limiting
- Server-side token revocation on logout
- File upload validation
- Restricted complaint access based on user roles

## Project Status

**Development Version – CivicTrack v2**

The upgraded source code has been syntax-checked, but a complete build and runtime verification is still pending. Please run the application and complete the manual testing checklist before treating it as production-ready.

## Future Enhancements

- Email and SMS notifications
- Advanced complaint analytics
- Production-grade geocoding service
- Improved deployment and scalability

---

**CivicTrack – Making civic issue reporting and resolution more transparent, organized, and accessible.**