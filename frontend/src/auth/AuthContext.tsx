import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useState,
} from "react";
import {
  getCurrentUser,
  login as signIn,
  logout as signOut,
  type User,
} from "../api/auth";
import { switchAccount as switchDemoAccount } from "../api/demo";

interface AuthValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  switchAccount: (userId: number) => Promise<User>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getCurrentUser()
      .catch(() => null)
      .then((current) => {
        if (!cancelled) {
          setUser(current);
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = async (email: string, password: string) => {
    const signedIn = await signIn(email, password);
    setUser(signedIn);
    return signedIn;
  };

  const logout = async () => {
    await signOut().catch(() => undefined);
    setUser(null);
  };

  const switchAccount = async (userId: number) => {
    const switched = await switchDemoAccount(userId);
    setUser(switched);
    return switched;
  };

  return (
    <AuthContext.Provider
      value={{ user, loading, login, logout, switchAccount }}
    >
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
