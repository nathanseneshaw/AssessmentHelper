export type UserRole = "faculty" | "assessment-committee";

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  professorId?: number;
}

export type LoginResult =
  | { success: true; user: AuthUser }
  | { success: false; error: string };

// Demo credentials
const accounts: (AuthUser & { password: string })[] = [
  {
    id: "demo-faculty",
    // Carol in data.sql has demo sections and no current-cycle signup.
    professorId: 3,
    name: "Demo Faculty",
    email: "faculty@utdallas.edu",
    role: "faculty",
    password: "Faculty123!",
  },
  {
    id: "demo-ac",
    name: "Demo AC Member",
    email: "ac@utdallas.edu",
    role: "assessment-committee",
    password: "Committee123!",
  },
  {
    id: "demo-requests",
    professorId: 4,
    name: "Dave",
    email: "requests@utdallas.edu",
    role: "faculty",
    password: "Faculty123!",
  },
];

export async function mockLogin(
  email: string,
  password: string,
): Promise<LoginResult> {
  await new Promise((resolve) => setTimeout(resolve, 600));

  const account = accounts.find(
    (candidate) =>
      candidate.email === email.trim().toLowerCase() &&
      candidate.password === password,
  );

  if (!account) {
    return { success: false, error: "Incorrect email or password." };
  }

  return {
    success: true,
    user: {
      id: account.id,
      name: account.name,
      email: account.email,
      role: account.role,
      professorId: account.professorId,
    },
  };
}
