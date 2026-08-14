import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Vite 설정. / Vite configuration.
 *
 * req: ADR-001 — 프론트엔드는 별도 산출물로 배포되며 REST 로만 백엔드와 통신한다.
 *      The frontend deploys as a separate artifact and talks to the backend only over REST.
 *
 * 개발 프록시가 있는 이유 / why the dev proxy exists:
 *   세션 쿠키는 HttpOnly + SameSite 로 설정된다(ADR-LOGIN-012). 개발 중 프론트엔드와
 *   백엔드가 서로 다른 오리진이면 쿠키가 전송되지 않아 로그인이 동작하지 않는다.
 *   프록시로 동일 오리진을 만들면 운영(리버스 프록시 뒤 단일 오리진)과 같은 조건이 된다.
 *
 *   The session cookie is HttpOnly + SameSite (ADR-LOGIN-012). With different origins in
 *   development the cookie would not be sent and login would silently fail. Proxying
 *   creates a single origin, matching production behind the reverse proxy.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.IRIS_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
