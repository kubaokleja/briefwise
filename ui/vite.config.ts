import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The app runs on 127.0.0.1:8080 in the `local` profile. Use the IPv4 address: Node may resolve "localhost" to ::1.
const API = 'http://127.0.0.1:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    // Same origin for the browser: /api and /actuator are forwarded to Spring Boot, so no CORS is involved (ADR 0007).
    proxy: {
      '/api': API,
      '/actuator': API,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
