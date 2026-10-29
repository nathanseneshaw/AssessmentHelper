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

## Evaluation requests (issue #42)

Sign in with `requests@utdallas.edu` / `Faculty123!`, then click **View Generated
Observers** or open `/faculty/observers`. This demo account maps to Dave (professor
4), whose Fall 2026 signup has Alice as a proposed observer. Select Alice and click
**Send evaluation request**. The page shows the saved request, its seven-day
response window, and Pending / Confirmed / Expired status. Reopening the page
restores it; **Refresh status** reloads responses and applies expiration.

The page uses stored `observer_match` rows. Candidate generation, eligibility,
availability, and observer response screens remain separate work (#39–#41, #43).
New signups without matches display an explanation. No email provider is called.
The existing confirmation API remains available for testing a response:
`POST /api/observer-requests/{requestId}/confirm`.

| Endpoint | Result |
| --- | --- |
| `GET /api/professors/{professorId}/observer-requests` | That professor's signups, proposed candidates, request history and unavailable reasons |
| `POST /api/professors/{professorId}/observer-requests` | Send `{ "signupId": 31, "matchId": 2 }`; IDs must come from the GET response |
| `POST /api/observer-requests` | Existing `{ "signupId": 31, "observerId": 1 }` API, now with the same match and lifecycle validation |

Both POST endpoints accept an optional future `expirationDate` in server-local
ISO date/time; the default is seven days. Times use America/Chicago. The sender
must own the signup; the selected match must be proposed for that signup, with an
active observer and professor in the same cycle, excluding self-observation.
Closed, inactive, completed, or already scheduled evaluations reject new requests.
Up to five pending observer requests are allowed per signup. Faculty can select up to five observers at once; existing pending requests count toward this limit. Each observer can accept independently; acceptance records an observation for that observer and leaves the other requests unchanged until they are answered or expire. Repeated sends to the
same observer return the existing active request without extending its expiration.
After expiration, a new request may be sent and the old one remains in history.
Concurrent sends are serialized. Course selection becomes locked once candidates
or requests exist, preventing a request from silently changing its course.

As with the rest of the demo, supplied professor IDs are not authenticated
identities; server-side authentication/authorization remains necessary before
production use. Data resets on backend restart.

<<<<<<< HEAD
## Record locking and audit log

Both people sign off on an observation. The observer's submit is their sign-off (status
`SUBMITTED`): their answers are frozen and the observee is notified. The observee then
reads the form, optionally leaves comments, and signs off with
`POST /api/observations/{id}/observee-sign` and `{ "comments": "..." }`. Only the
observee can call it, and only on a `SUBMITTED` record. That sets the status to
`COMPLETED`, stores the comments and sign-off time, and locks the record: it is
read-only for everyone, and its detail response has `locked: true`. While a record is
`SUBMITTED`, the detail response has `canSign: true` for the observee.

Assessment Committee members can read `COMPLETED` records for observees in their own
school (`viewerRole: COMMITTEE`) but never edit them.

Every action on a record is appended to `observation_audit_log` (who, to whom, when):
`STARTED`, `EDITED`, `SUBMITTED`, `OBSERVEE_COMMENTED`, `OBSERVEE_SIGNED`, `LOCKED`, every
`VIEWED` of a completed record, and every `FORWARDED` link. The table is insert-only;
nothing in the application updates or deletes its rows. A committee member forwards a
completed record with `POST /api/observations/{id}/forward` and
`{ "recipientProfessorId": 4 }`. Forwarding logs the event and notifies the recipient but
does not grant access.
=======
## Observer teaching history and cycle deadlines

An observer may qualify by teaching the same course level in the same department
this semester or within the prior two years. The lookback is measured from the
evaluation semester's start date; a historical semester qualifies if it overlaps
that two-year window. Future semesters do not qualify. Schedule conflicts still
use only the current semester's teaching assignments. The other observer rules
(active faculty and observer, current-cycle signup, and no self-observation)
continue to apply.

On the AC dashboard, **Edit Deadlines** changes the current cycle's signup,
observation, and feedback deadlines. Changes apply to the whole cycle. Signup
must be on or after semester start, observation after signup, and feedback after
observation. The server rejects dates that exclude an existing observation or
leave an existing signup without an observation window. Default observation
windows follow the new deadline; custom end dates are preserved or shortened to
fit. Saved feedback is retained when deadlines change.

`PUT /api/observer-eligibility/cycles/{cycleId}/deadlines` accepts
`signupDeadline`, `observationDeadline`, and `feedbackDeadline` as ISO dates.
Only authenticated AC members can update deadlines.

>>>>>>> 0c4ef60 (Allow AC deadline editing and expand observer eligibility to two years of teaching history)
## Process feedback survey

Observees submit one survey per evaluation cycle from the faculty dashboard's
**Start Survey** action. The form contains six required 1–5 ratings and optional
comments (up to 4000 characters). Canceling does not save or submit responses.
Submitted surveys cannot be edited or submitted again.

The survey opens the day after the cycle's observation deadline and remains open
through its feedback deadline, using America/Chicago dates. No completed
observation is required beyond being an active or completed observee in that cycle.
Missing or invalid deadlines prevent submission. The dashboard shows upcoming
survey tasks with Start Survey disabled until the observation deadline passes.
Submitted surveys show a Submitted status and View Survey link, allowing observees
to reopen their saved ratings, comments, and submission date in a read-only view,
including after the feedback deadline. Unsubmitted expired tasks are removed.

`GET /api/feedback-surveys` returns the authenticated professor's cycles and survey
availability. `POST /api/feedback-surveys/{cycleId}` accepts
`{ "ratings": [1, 2, 3, 4, 5, 4], "comments": "Optional feedback" }`;
ratings follow the displayed question order. The server enforces ownership,
deadlines, rating validation, and one submission per professor per cycle.

Restart the backend after this schema change; the demo uses an in-memory database.

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
