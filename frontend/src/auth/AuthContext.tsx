import { createContext, type ReactNode, useContext, useState } from "react";
import type { Role } from "../navConfig";

export interface User {
  role: Role;
  name: string;
}

interface AuthValue {
  user: User | null;
  login: (role: Role) => void;
  logout: () => void;
}

const STORAGE_KEY = "assessment-helper-demo-user";

const DEMO_USERS: Record<Role, User> = {
  ac: { role: "ac", name: "AC Member" },
  professor: { role: "professor", name: "Professor 1" },
};

function loadUser(): User | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as User) : null;
  } catch {
    return null;
  }
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(loadUser);

  const login = (role: Role) => {
    const next = DEMO_USERS[role];
    setUser(next);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem(STORAGE_KEY);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return ctx;
}
