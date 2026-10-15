import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { APITester } from "./APITester";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { RequireRole } from "./auth/RequireRole";
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

function RootRedirect() {
  const { user } = useAuth();
  return <Navigate to={user ? HOME_PATH[user.role] : "/login"} replace />;
}

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/login" element={<Login />} />
          <Route path="/forbidden" element={<Forbidden />} />
          <Route path="/dev/api-tester" element={<APITester />} />

          <Route element={<RequireRole allow="ac" />}>
            <Route path="/ac" element={<AppLayout />}>
              <Route index element={<AcOverview />} />
              <Route path="evaluations" element={<AcFacultyEvaluations />} />
              <Route path="signups" element={<AcSignups />} />
              <Route path="matching" element={<AcObserverMatching />} />
              <Route path="observations" element={<AcObservations />} />
              <Route path="form-template" element={<AcFormTemplate />} />
              <Route path="reports" element={<AcReports />} />
              <Route path="*" element={<NotFound />} />
            </Route>
          </Route>

          <Route element={<RequireRole allow="professor" />}>
            <Route element={<AppLayout />}>
              <Route path="/faculty" element={<FacultyDashboard />}>
                <Route path="signup" element={<FacultyEvaluationSignup />} />
                <Route path="observers" element={<FacultyObservers />} />
                <Route path="history" element={<FacultyHistory />} />
                <Route
                  path="observations/me"
                  element={<FacultyObservationsForMe />}
                />
                <Route
                  path="observations/others"
                  element={<FacultyObservationsForOthers />}
                />
                <Route
                  path="observations/:id"
                  element={<FacultyObservationDetails />}
                />
                <Route path="feedback" element={<FacultyFeedback />} />
              </Route>
              <Route path="/faculty/*" element={<NotFound />} />
            </Route>
          </Route>

          <Route path="*" element={<NotFound />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
