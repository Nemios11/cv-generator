import { defineConfig } from '@playwright/test';

const previewPort = 4450;
// E2E_BASE_URL: run against an already deployed environment (e.g.
// http://localhost:8280) instead of starting the Vite preview.
const external = process.env.E2E_BASE_URL;

export default defineConfig({
  testDir: './tests',
  use: { baseURL: external ?? `http://127.0.0.1:${previewPort}` },
  webServer: external
    ? undefined
    : {
        command: `npm run build && npm run preview -- --host 127.0.0.1 --port ${previewPort} --strictPort`,
        port: previewPort,
        reuseExistingServer: !process.env.CI,
      },
});
