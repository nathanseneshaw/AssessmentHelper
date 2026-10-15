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

The backend asks for a login. The username is `user` and the password is printed in the console when it starts.

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

## Frontend mock sign-in

The login page currently uses local demo accounts; the backend does not need to be running.

| Role | Email | Password |
| --- | --- | --- |
| Faculty | `faculty@utdallas.edu` | `Faculty123!` |
| Assessment Committee | `ac@utdallas.edu` | `Committee123!` |

Sign-in simulates a short request and rejects other credentials. Email matching ignores case and surrounding spaces, but passwords must match exactly. Successful sign-in displays the Faculty Dashboard or Assessment Committee Dashboard based on the user's role. Both dashboards are placeholders with a Sign Out button that returns to a fresh login form. The current view is managed by React state; it does not change the URL or persist a session across refreshes.

`frontend/src/auth/mockAuth.ts` contains the demo service and shared user/role types. `App` uses the `LoginPage` callback `onLogin(user)` to keep the authenticated user in memory and render `DashboardPage`. These accounts and frontend role checks are for frontend development only; real authentication and permission enforcement must be provided by the backend before real use.

Run the mock authentication tests from `frontend/` with `bun test src/auth/mockAuth.test.ts`.
