import { GlobalRegistrator } from "@happy-dom/global-registrator";

// Give component tests a browser-like window, document, history and localStorage.
GlobalRegistrator.register({ url: "http://localhost:3000/" });
