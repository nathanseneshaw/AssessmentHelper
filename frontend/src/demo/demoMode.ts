// Bun inlines BUN_PUBLIC_DEMO_MODE when it bundles the frontend. Like BUN_PUBLIC_API_URL in api/client.ts, an unset
// variable leaves a reference to `process`, which does not exist in the browser.
function configuredDemoMode(): string | undefined {
  try {
    return process.env.BUN_PUBLIC_DEMO_MODE;
  } catch {
    return undefined;
  }
}

export const DEMO_MODE = configuredDemoMode() === "true";
