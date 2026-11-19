/**
 * This file is the entry point for the React app, it sets up the root
 * element and renders the App component to the DOM.
 *
 * It is included in `src/index.html`.
 */

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { ScreenshotMarks } from "./components/ScreenshotMarks";
import { DEMO_MODE } from "./demo/demoMode";

const elem = document.getElementById("root");
if (!elem) {
  throw new Error("Root element #root not found");
}
const app = (
  <StrictMode>
    <App />
    {(process.env.NODE_ENV !== "production" || DEMO_MODE) && (
      <ScreenshotMarks />
    )}
  </StrictMode>
);

// https://bun.com/docs/bundler/hot-reloading#import-meta-hot-data
const root = import.meta.hot.data.root ?? createRoot(elem);
import.meta.hot.data.root = root;
root.render(app);
