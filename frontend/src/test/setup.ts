import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// React Testing Library only auto-cleans when the test runner exposes
// globals; with explicit vitest imports we register cleanup ourselves.
afterEach(() => {
  cleanup();
});
