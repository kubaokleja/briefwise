// Runs before every test file (vite.config.ts `test.setupFiles`).
import '@testing-library/jest-dom/vitest'; // matchers such as toBeInTheDocument()
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup(); // unmount rendered components (needed because Vitest globals are off)
  vi.restoreAllMocks();
});
