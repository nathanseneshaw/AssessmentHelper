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

Sign-in simulates a short request delay and rejects other credentials. Email matching ignores case and surrounding spaces; passwords must match exactly. Successful sign-in currently displays the user's role without navigating to a dashboard. It does not persist a session across refreshes.

`frontend/src/auth/mockAuth.ts` contains the demo service and shared user/role types. `LoginPage` exposes an optional `onLogin(user)` callback for dashboard navigation. These accounts are for frontend development only and must be replaced with backend authentication before real use.

Run the mock authentication tests from `frontend/` with `bun test src/auth/mockAuth.test.ts`.
