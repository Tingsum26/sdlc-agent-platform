import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  use: { baseURL: "http://127.0.0.1:4173", trace: "retain-on-failure" },
  webServer: [
    { command: ".\\mvnw.cmd -q -pl apps/workflow-service spring-boot:run -Dspring-boot.run.profiles=fake", url: "http://127.0.0.1:8080/actuator/health", reuseExistingServer: false, timeout: 120000 },
    { command: "pnpm --filter @sdlc/web-ui dev --host 127.0.0.1", url: "http://127.0.0.1:4173", reuseExistingServer: false, timeout: 120000 }
  ]
});
