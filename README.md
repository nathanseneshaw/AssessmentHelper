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

## Observer eligibility and requests

Faculty signup selects one of the professor's current-semester teaching assignments.
Meeting days/times are read-only. The observation window starts automatically on
the day after the signup deadline, must contain a class meeting, and must end by
the observation deadline. Signup closes at the configured
signup deadline (inclusive), using America/Chicago dates.

Signing up automatically adds an active professor to that cycle's observer pool.
Candidates must be active, signed up in the same cycle, belong to the same department,
and teach the same numbered course level in that semester (3345 means 3000-level).
A candidate must be free for the entire class period on at least one meeting day in
the observation window. Overlapping teaching assignments block that day; adjacent
classes do not. Incomplete schedules are excluded instead of assumed available.

Signup automatically creates up to five random, persisted candidates. New signups
fill incomplete, unreleased lists without reshuffling existing candidates. Released
lists remain fixed. AC can review and send a list immediately, independently of
the signup deadline. Only zero-candidate signups appear in the unmatched alert.
The AC dashboard and faculty status refresh every five seconds and on window focus.

Faculty can view the list after AC sends it, select multiple observers, and send
in-app requests. The Observer Requests screen shows the class schedule, accepts or
declines incoming requests, and retains accepted requests. Each acceptance creates
its own observation record with status ACCEPTED, without inventing a calendar date.
There is no acceptance limit or final single-observer selection. Requests expire at
the end of the observation window; releases and responses close at the cycle's
observation deadline. Duplicate requests are not sent again. Notifications are
stored in-app with dated timestamps in a scrollable history; no email provider is contacted.

The feature API is `/api/observer-eligibility`. The older observer-request API routes
managed signups through the same eligibility/release rules. The existing demo-only
security still applies: professor ids are demo identities, not authenticated claims.
The faculty dashboard provides a labeled demo identity selector to exercise both
sides of the workflow. The default faculty login uses professor 1 (Alice).

For the Fall 2026 demo, the signup deadline is October 31 and observation deadline
is November 15. Professors 1 and 3?12 have new current-semester teaching assignments;
Bob (professor 2), Dave, Frank, Henry, Jack, and Liam already have signups.
Their schedules leave Alice free to choose from six eligible observers; five are
randomly displayed. Sign up as Alice, then sign in as AC to review/release her list.
Switch faculty identity to inspect requests. Section numbers use three digits
(for example, CS 1340.001).
The in-memory database resets on backend restart, as described above.
