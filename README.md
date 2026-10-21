# AssessmentHelper

React frontend (`frontend/`, run with Bun) and Spring Boot backend (`backend/`, Java 25).

## Prerequisites

- [Bun](https://bun.sh) 1.4.0 or newer
- [JDK 25](https://adoptium.net/)

## Setup

```bash
git clone https://github.com/nathanseneshaw/AssessmentHelper.git
cd AssessmentHelper

cd frontend
bun install

cd ../backend
./gradlew build
```

## Run

Frontend at http://localhost:3000:

```bash
cd frontend
bun run dev
```

Backend at http://localhost:8080:

```bash
cd backend
./gradlew bootRun
```

While the backend is running, the API documentation is at http://localhost:8080/swagger-ui.html and the H2 database console is at http://localhost:8080/h2-console. The database URL to enter in the console is printed in the startup log.

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

The shared frontend API client sends requests to the backend at http://localhost:8080. To use a different address, set `BUN_PUBLIC_API_URL` when starting or building the frontend, for example `BUN_PUBLIC_API_URL=http://localhost:9090 bun run dev`. The backend only accepts browser requests from the origins listed in `app.cors.allowed-origins` in `backend/src/main/resources/application.properties`, which is `http://localhost:3000` by default. If the frontend runs at a different address, add it there or set the `APP_CORS_ALLOWED_ORIGINS` environment variable when starting the backend.

## Overdue evaluation notifications

Start both the backend and frontend, then open the Assessment Committee overview.
The overdue card loads actual faculty data; **View All** opens the overdue list.
**Notify** and **Follow Up** open personalized, editable email templates. Sending
simulates delivery and saves the recipient, edited subject/body, and timestamp;
it never contacts an email provider. A notification does not clear the overdue count.

The backend selects the semester containing today's date in America/Chicago.
Active assistants have annual evaluations; associate/full professors use their
two-year policies. Scheduling starts at the latest observation `completedAt` for
an observee marked `COMPLETED`, or at hire date when there is no completion.
A current-semester recurrence takes priority over a previously missed evaluation.
Missing/ambiguous semester or cycle configuration and missing completion dates
produce an actionable error instead of a fabricated count.

Each backend startup creates a fresh in-memory H2 database and loads the default
demo records from `data.sql`. Restarting the backend resets data and notification
statuses, including during development hot restarts. Notifications persist across
browser refreshes and are shared by committee users while the backend is running.
Previously created files in `backend/data/` are no longer used. The overdue feature currently uses a separate Bun server proxy, which forwards overdue API requests to
`http://localhost:8080`; set `BACKEND_URL` on the Bun server to use another backend.
The existing mock sign-in and permissive backend API security remain demo-only.

## Frontend mock sign-in

The login page currently uses local demo accounts; the backend does not need to be running.

| Role | Email | Password |
| --- | --- | --- |
| Faculty | `faculty@utdallas.edu` | `Faculty123!` |
| Assessment Committee | `ac@utdallas.edu` | `Committee123!` |

Sign-in simulates a short request and rejects other credentials. Email matching ignores case and surrounding spaces, but passwords must match exactly. Successful sign-in displays the Faculty Dashboard or Assessment Committee Dashboard based on the user's role. Both dashboards are temporary dashboards with a Sign Out button that returns to a fresh login form. Current view does not change the URL or persist a session across refreshes.

`frontend/src/auth/mockAuth.ts` contains the demo service and shared user/role types. `App` uses the `LoginPage` callback `onLogin(user)` to keep the authenticated user in memory and render `DashboardPage`. These accounts and frontend role checks are for frontend development only. Real authentication and permission enforcement must be provided by the backend before real use.

Run the mock authentication tests from `frontend/` with `bun test src/auth/mockAuth.test.ts`.
