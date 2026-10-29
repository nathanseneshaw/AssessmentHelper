# AssessmentHelper

AssessmentHelper is a faculty evaluation management system developed for the UT Dallas Department of Computer Science. It simplifies the process of scheduling faculty evaluations, matching observers with observees, collecting observation feedback, and tracking evaluation progress.

## Tech Stack

- **Frontend:** React, TypeScript, Bun
- **Backend:** Java 25, Spring Boot
- **Database:** H2 (in-memory)
- **API Documentation:** Swagger UI

## Prerequisites

Before running the application, install:

- [Bun](https://bun.sh/) (version 1.4.0 or newer)
- [JDK 25](https://adoptium.net/)

## Installation and Setup

1. Clone the repository:

   ```bash
   git clone https://github.com/nathanseneshaw/AssessmentHelper.git
   cd AssessmentHelper
   ```

2. Install frontend dependencies:

   ```bash
   cd frontend
   bun install
   ```

3. Build the backend:

   ```bash
   cd ../backend
   ./gradlew build
   ```

   On Windows, use `.\gradlew.bat build`.

## Running the Application

Start the frontend and backend in separate terminals.

**Frontend:**

```bash
cd frontend
bun run dev
```

The frontend runs at http://localhost:3000.

**Backend:**

```bash
cd backend
./gradlew bootRun
```

On Windows, use `.\gradlew.bat bootRun`.

The backend runs at http://localhost:8080.

Additional development tools:

- **API Documentation:** http://localhost:8080/swagger-ui.html
- **H2 Database Console:** http://localhost:8080/h2-console

## Main Features

**Faculty Members**
- Sign up for evaluations by selecting a course and section.
- View eligible observers and send observation requests.
- Complete and review observation forms.
- Submit feedback surveys and view previous submissions.

**Assessment Committee Members**
- Monitor faculty evaluations and deadlines.
- View faculty due or overdue for evaluation.
- Manage evaluation cycle deadlines.
- Review completed observations and evaluation records.
- Track evaluation and observation progress.

**System Functionality**
- Faculty evaluation scheduling based on academic rank.
- Observer eligibility matching using teaching history and availability.
- Observation confirmation and sign-off workflows.
- Record locking and audit logging.

## Demo Accounts

The application includes sample accounts for testing. Open the drop down under the login to view login informaton.

## Development Notes

- The application currently uses an in-memory H2 database. Data resets whenever the backend restarts.
- Email notifications are simulated and do not send actual emails.
- The backend must be running for authentication and API functionality.
- API endpoints can be explored using Swagger UI.