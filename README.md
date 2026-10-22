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

The shared frontend API client sends requests to the backend at http://localhost:8080.

## Overdue evaluation notifications

The Faculty Evaluations page loads all active faculty from
`GET /api/evaluation-eligibility`. Due This Term, Overdue, and rank filters use
the same server-calculated classifications as the dashboard overlays; All
Faculty also includes upcoming evaluations.
The queue previews four matching professors on the page. View All opens the
complete filtered list in an overlay with vertical scrolling and fixed column headers.

The **Faculty Due for Evaluation** dashboard panel also uses calculated faculty
data. **View All** opens the due overlay with the next due semester, personalized
editable notifications, confirmation, and follow-up actions. Sending is simulated
and does not complete the evaluation. Due and overdue share the same scheduling
calculation and current-cycle notification history. The due API is
`GET /api/due-evaluations`, with notifications posted to
`/api/due-evaluations/{cycleId}/professors/{professorId}/notifications`.

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
Previously created files in `backend/data/` are no longer used. Due and overdue
requests use the shared frontend API client in `frontend/src/api/client.ts`.
The default backend is `http://localhost:8080`; configure `BUN_PUBLIC_API_URL`
when starting or building the frontend to use another backend.

## Demo accounts

The backend handles sign-in, so it must be running before you can sign in to the frontend. These accounts come from `backend/src/main/resources/data.sql`, and the in-memory database goes back to them every time the backend restarts.

| Role | Name | Email | Password |
| --- | --- | --- | --- |
| Assessment Committee | Alice Alice | `alice@utdallas.edu` | `Committee123!` |
| Faculty | Bob Bob | `bob@utdallas.edu` | `Faculty123!` |

Alice is an Assessment Committee member in the Erik Jonsson School of Engineering and Computer Science, so she can see the records of professors in that school but not in other schools. Bob can see only his own records.

Every `/api/**` endpoint except sign-in requires a signed-in session. To try the endpoints in Swagger UI, sign in through the frontend first, and Swagger UI in the same browser will use that session.
