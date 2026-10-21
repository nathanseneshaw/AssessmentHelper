import { afterEach, describe, expect, mock, spyOn, test } from "bun:test";
import { API_URL, ApiError, apiFetch } from "./client";

function respondWith(response: Response) {
  return spyOn(globalThis, "fetch").mockResolvedValue(response);
}

afterEach(() => {
  mock.restore();
});

describe("apiFetch", () => {
  test("sends the request to the backend and returns the parsed JSON", async () => {
    const fetchSpy = respondWith(Response.json([{ professorRank: "FULL" }]));

    expect(await apiFetch("/api/evaluation-policies")).toEqual([
      { professorRank: "FULL" },
    ]);
    expect(fetchSpy).toHaveBeenCalledWith(
      `${API_URL}/api/evaluation-policies`,
      undefined,
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
