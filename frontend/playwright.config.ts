import { defineConfig } from '@playwright/test';

const previewPort = 4450;

export default defineConfig({
  testDir: './tests',
  use: { baseURL: `http://127.0.0.1:${previewPort}` },
  webServer: {
    command: `npm run build && npm run preview -- --host 127.0.0.1 --port ${previewPort} --strictPort`,
    port: previewPort,
    reuseExistingServer: !process.env.CI,
  },
});
