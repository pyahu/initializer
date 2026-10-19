import { defineConfig, devices } from '@playwright/test';

// The page against a running web and service: CI starts both images and points BASE_URL at the web.
export default defineConfig({
  testDir: 'e2e',
  outputDir: 'test-results',
  forbidOnly: true,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3001',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
