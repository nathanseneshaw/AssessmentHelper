import { afterEach, describe, expect, mock, spyOn, test } from "bun:test";
import { API_URL, ApiError, apiFetch } from "./client";

function respondWith(response: Response) {
  return spyOn(globalThis, "fetch").mockResolvedValue(response);
}

function setCookie(cookie: string) {
  // biome-ignore lint/suspicious/noDocumentCookie: the client under test reads its CSRF token from document.cookie
  document.cookie = cookie;
}

function sentHeaders(call: Parameters<typeof fetch> | undefined): Headers {
  return new Headers(call?.[1]?.headers);
}

afterEach(() => {
  mock.restore();
  setCookie("XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT");
});

describe("apiFetch", () => {
  test("sends the request to the backend with cookies and returns the parsed JSON", async () => {
    const fetchSpy = respondWith(Response.json([{ professorRank: "FULL" }]));

    expect(await apiFetch("/api/evaluation-policies")).toEqual([
      { professorRank: "FULL" },
    ]);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(fetchSpy.mock.calls[0]?.[0]).toBe(
      `${API_URL}/api/evaluation-policies`,
    );
    expect(fetchSpy.mock.calls[0]?.[1]?.credentials).toBe("include");
    expect(sentHeaders(fetchSpy.mock.calls[0]).has("X-XSRF-TOKEN")).toBe(false);
  });

  test("sends the CSRF token from the cookie on requests that change data", async () => {
    setCookie("XSRF-TOKEN=from-cookie");
    const fetchSpy = respondWith(new Response(null, { status: 204 }));

    await apiFetch("/api/auth/logout", { method: "POST" });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(sentHeaders(fetchSpy.mock.calls[0]).get("X-XSRF-TOKEN")).toBe(
      "from-cookie",
    );
  });

  test("asks the backend for a CSRF token when there is no cookie yet", async () => {
    const fetchSpy = spyOn(globalThis, "fetch").mockImplementation((async (
      input,
    ) => {
      if (String(input).endsWith("/api/auth/csrf")) {
        setCookie("XSRF-TOKEN=fresh");
      }
      return new Response(null, { status: 204 });
    }) as typeof fetch);

    await apiFetch("/api/auth/logout", { method: "POST" });

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(fetchSpy.mock.calls[0]?.[0]).toBe(`${API_URL}/api/auth/csrf`);
    expect(sentHeaders(fetchSpy.mock.calls[1]).get("X-XSRF-TOKEN")).toBe(
      "fresh",
    );
  });

  test("throws an ApiError with the detail from a Spring problem response", async () => {
    respondWith(
      Response.json(
        { title: "Not Found", status: 404, detail: "No professor with id 99" },
        {
          status: 404,
          headers: { "Content-Type": "application/problem+json" },
        },
      ),
    );

    const error = await apiFetch("/api/professors/99/evaluations").catch(
      (e: unknown) => e,
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 404,
      message: "No professor with id 99",
    });
  });

  test("falls back to the status code when the error body is not JSON", async () => {
    respondWith(new Response("Bad Gateway", { status: 502 }));

    const error = await apiFetch("/api/evaluation-policies").catch(
      (e: unknown) => e,
    );
    expect(error).toMatchObject({
      status: 502,
      message: "Request failed with status 502",
    });
  });

  test("returns undefined for an empty 204 response", async () => {
    respondWith(new Response(null, { status: 204 }));

    expect(await apiFetch("/api/evaluation-policies/FULL")).toBeUndefined();
  });
});
