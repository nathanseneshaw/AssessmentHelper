import { useState } from "react";
import type { AuthUser } from "./auth/mockAuth";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import "./index.css";
import { AppLayout } from "./layouts/AppLayout";
import { HOME_PATH } from "./navConfig";
import { AcFacultyEvaluations } from "./pages/ac/FacultyEvaluations";
import { AcFormTemplate } from "./pages/ac/FormTemplate";
import { AcObservations } from "./pages/ac/Observations";
import { AcObserverMatching } from "./pages/ac/ObserverMatching";
import { AcOverview } from "./pages/ac/Overview";
import { AcReports } from "./pages/ac/Reports";
import { AcSignups } from "./pages/ac/Signups";
import { Forbidden } from "./pages/Forbidden";
import { FacultyDashboard } from "./pages/faculty/Dashboard";
import { FacultyEvaluationSignup } from "./pages/faculty/EvaluationSignup";
import { FacultyFeedback } from "./pages/faculty/Feedback";
import { FacultyHistory } from "./pages/faculty/History";
import { FacultyObservationDetails } from "./pages/faculty/ObservationDetails";
import { FacultyObservationsForMe } from "./pages/faculty/ObservationsForMe";
import { FacultyObservationsForOthers } from "./pages/faculty/ObservationsForOthers";
import { FacultyObservers } from "./pages/faculty/Observers";
import { Login } from "./pages/Login";
import { NotFound } from "./pages/NotFound";

export function App() {
  const [user, setUser] = useState<AuthUser | null>(null);

  if (!user) {
    return <LoginPage onLogin={setUser} />;
  }

  return <DashboardPage user={user} onSignOut={() => setUser(null)} />;
}

export default App;
