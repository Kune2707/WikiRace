import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  timeout: 45000,
  use: {
    baseURL: "http://127.0.0.1:5175",
    viewport: { width: 1440, height: 1000 },
    ...(process.env.PLAYWRIGHT_CHANNEL
      ? { channel: process.env.PLAYWRIGHT_CHANNEL }
      : {}),
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command:
        "java -jar ../backend/target/wikirace-backend-0.0.1-SNAPSHOT.jar",
      url: "http://127.0.0.1:8082/api/health",
      env: {
        PORT: "8082",
        SPRING_PROFILES_ACTIVE: "dev,test",
        CORS_ALLOWED_ORIGINS: "http://127.0.0.1:5175",
        WEBSOCKET_ALLOWED_ORIGINS: "http://127.0.0.1:5175",
        PATH: process.env.JAVA_HOME
          ? `${process.env.JAVA_HOME}/bin:${process.env.PATH}`
          : (process.env.PATH ?? ""),
      },
      reuseExistingServer: false,
    },
    {
      command: "npm run dev -- --port 5175",
      url: "http://127.0.0.1:5175",
      env: { VITE_API_BASE_URL: "http://127.0.0.1:8082" },
      reuseExistingServer: false,
    },
  ],
});
