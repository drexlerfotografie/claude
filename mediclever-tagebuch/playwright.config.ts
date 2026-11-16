import { defineConfig } from '@playwright/test';

// Abläufe und Screenshots gegen das lokale WordPress (scripts/wp.sh start). Ein Worker, weil alle Tests dieselbe Datenbank nutzen.
export default defineConfig({
  testDir: 'tests/e2e',
  workers: 1,
  fullyParallel: false,
  timeout: 90_000,
  expect: { timeout: 8_000 },
  reporter: [['list']],
  outputDir: 'test-results',
  use: {
    baseURL: 'http://127.0.0.1:8888',
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    viewport: { width: 412, height: 915 },
    launchOptions: { args: ['--lang=de-DE'] },
    trace: 'off',
  },
});
