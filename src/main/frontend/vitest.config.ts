import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * Vitest 설정. / Vitest configuration.
 *
 * req: TEST-PLAN-LOGIN §1.3 — 프론트엔드 컴포넌트 테스트는 매 커밋 실행된다.
 *      Frontend component tests run on every commit.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.tsx', 'src/**/*.test.ts'],
  },
});
