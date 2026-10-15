import { useState } from "react";
import type { AuthUser } from "./auth/mockAuth";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import "./index.css";

export function App() {
  const [user, setUser] = useState<AuthUser | null>(null);

  if (!user) {
    return <LoginPage onLogin={setUser} />;
  }

  return <DashboardPage user={user} onSignOut={() => setUser(null)} />;
}

export default App;
