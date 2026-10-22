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

## Course and section selection (issue #37)

Start both servers, sign in with `faculty@utdallas.edu` / `Faculty123!`, and open
**Sign Up for Evaluation** (`/faculty/signup`). Sign out and back in once if you
have a session saved before this feature. The demo account maps to seeded
professor 3 (Carol), who teaches CS 101 sections 002/003 and CS 3345 section 001
in Fall 2026. Existing observation records and their IDs are unchanged.

Choose a semester, course, and section, review the meeting details, and save.
Only that professor's teaching sections appear. Reopening the semester restores
the saved selection. A pending selection can be changed; a matched/completed
signup cannot be changed through this form. Changing the course or semester
clears the dependent selection. Loading failures offer a retry, and a failed
save retains the selection without showing success.

The catalog endpoints needed by #78 are included:

| Endpoint | Result |
| --- | --- |
| `GET /api/semesters` | All semesters; optional `signupOpen=true` limits to semesters with an OPEN cycle |
| `GET /api/courses` | Seeded courses |
| `GET /api/sections` | Sections; optional `semesterId`, `courseId`, and `professorId` filters combine |
| `GET /api/professors/{professorId}/evaluation-signups/{semesterId}` | Saved signup, or 204 if none exists |
| `PUT /api/professors/{professorId}/evaluation-signups/{semesterId}` | Save `{ "sectionId": 9 }`; return the saved signup |

The save endpoint checks teaching ownership, section/semester consistency,
professor activity, and a single OPEN evaluation cycle. It creates the observee
record when needed. Repeated requests reuse the signup, and a professor-row lock
serializes concurrent selections. Non-pending signups retain their section and
observation links. Deadline and eligibility enforcement are separate policies;
this feature uses the cycle's OPEN status rather than adding a deadline rule.

Authentication is still the project's mock login: `professorId` is a demo mapping,
not authenticated proof of identity. Production authorization must derive the
professor from the server-side authenticated user. The API validates teaching
ownership against the supplied professor, consistent with the existing demo APIs.
Saved selections persist across browser refreshes but reset when the in-memory
backend database restarts.

Validation: `bun test src/pages/faculty/EvaluationSignup.test.tsx` in `frontend/`
and `./gradlew test --tests '*EvaluationSignupIntegrationTests'` in `backend/`
(use `.\gradlew.bat` on Windows).

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
