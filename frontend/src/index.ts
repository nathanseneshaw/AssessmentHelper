import { serve } from "bun";
import index from "./index.html";

async function overdueApi(request: Request) {
  const url = new URL(request.url);
  try {
    return await fetch(
      new URL(
        url.pathname + url.search,
        process.env.BACKEND_URL || "http://localhost:8080",
      ),
      {
        method: request.method,
        headers: { "Content-Type": "application/json" },
        body: request.method === "GET" ? undefined : await request.text(),
      },
    );
  } catch {
    return Response.json(
      {
        message:
          "Evaluation service unavailable. Start the backend and try again.",
      },
      { status: 503 },
    );
  }
}

const server = serve({
  routes: {
    "/api/overdue-evaluations": overdueApi,
    "/api/overdue-evaluations/*": overdueApi,
    // Serve index.html for all unmatched routes.
    "/*": index,
  },

  development: process.env.NODE_ENV !== "production" && {
    // Enable browser hot reloading in development
    hmr: true,

    // Echo console logs from the browser to the server
    console: true,
  },
});

console.log(`🚀 Server running at ${server.url}`);
