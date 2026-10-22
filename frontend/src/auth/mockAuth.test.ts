import { describe, expect, test } from "bun:test";
import { mockLogin } from "./mockAuth";

describe("mockLogin", () => {
  test("returns the faculty user and normalizes email without exposing a password", async () => {
    const result = await mockLogin("  FACULTY@utdallas.edu  ", "Faculty123!");
    expect(result.success).toBe(true);
    if (!result.success) throw new Error("Expected successful login");
    expect(result.user.role).toBe("faculty");
    expect(result.user.professorId).toBe(3);
    expect(result.user.email).toBe("faculty@utdallas.edu");
    expect(result.user).not.toHaveProperty("password");
  });

  test("returns the assessment committee role", async () => {
    const result = await mockLogin("ac@utdallas.edu", "Committee123!");
    expect(result.success).toBe(true);
    if (!result.success) throw new Error("Expected successful login");
    expect(result.user.role).toBe("assessment-committee");
  });

  test("rejects incorrect passwords, including changed case or extra spaces", async () => {
    for (const password of ["wrong", "faculty123!", "Faculty123! "]) {
      expect(await mockLogin("faculty@utdallas.edu", password)).toEqual({
        success: false,
        error: "Incorrect email or password.",
      });
    }
  });

  test("rejects unknown accounts with the same generic error", async () => {
    expect(await mockLogin("unknown@utdallas.edu", "Faculty123!")).toEqual({
      success: false,
      error: "Incorrect email or password.",
    });
  });
});
