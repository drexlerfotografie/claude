import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';

// Baut die Web-App direkt in das Plugin. Keine externen CDNs, keine Tracker.
export default defineConfig({
  root: 'app',
  base: './',
  plugins: [preact()],
  build: {
    outDir: '../plugin/mediclever-tagebuch/build',
    emptyOutDir: true,
    manifest: true,
    target: 'es2020',
    rollupOptions: { input: 'app/src/main.tsx' },
  },
});
