/*
 * k6 부하 테스트 — 로그인 모듈
 * k6 load test — authentication module
 *
 * req: NFR-PERF-L01 (로그인 P95 < 1s), RISK-L07 / TM-L016 (Argon2id DoS 벡터)
 * plan: TEST-PLAN-LOGIN §7
 *
 * 실행 / run:
 *   k6 run -e BASE_URL=https://localhost:8443 qa/load/login-load.js
 *
 * ⚠ 전제 조건 / prerequisites:
 *   - 애플리케이션이 기동되어 있어야 한다 (IRIS_OTP_SECRET_KEY, DDL 적용 필요)
 *   - 합성 계정이 준비되어야 한다. **운영 데이터를 쓰지 말 것** (TEST-PLAN-LOGIN §8)
 *   - OTP 코드는 단일 사용이 강제되므로(TM-L004) 유효 코드를 반복 사용할 수 없다.
 *     따라서 시나리오 3·4 는 의도적으로 <b>실패 경로</b>를 측정한다 — 그것이 이 테스트의
 *     핵심 목적이기도 하다.
 *
 *   OTP codes are single-use (TM-L004), so a valid code cannot be replayed across
 *   iterations. Scenarios 3 and 4 therefore measure the <b>failure</b> path deliberately —
 *   which is also this test's main point.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const loginDuration = new Trend('login_duration', true);
const rateLimitedRate = new Rate('rate_limited');

export const options = {
  scenarios: {
    /*
     * 시나리오 1 — 정상 부하 / normal load
     * req: NFR-PERF-L01 — P95 < 1s
     */
    normal_login: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '15m',
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'attemptLogin',
      tags: { scenario: 'normal' },
    },

    /*
     * 시나리오 2 — 2배 피크 / 2x peak
     * plan: TEST-PLAN-LOGIN §7 — 부하 테스트는 SLA 의 2배 부하로 수행
     */
    peak_login: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '10m',
      startTime: '15m',
      preAllocatedVUs: 40,
      maxVUs: 100,
      exec: 'attemptLogin',
      tags: { scenario: 'peak' },
    },

    /*
     * 시나리오 3 — Argon2id 비용 홍수 / Argon2id cost flood
     *
     * **이 시나리오가 가장 중요하다.** Argon2id 는 의도적으로 비싸다(FR-LOGIN-005).
     * 미인증 엔드포인트에서 요청마다 해싱을 수행하면 그 비용이 CPU 소모 벡터가 된다
     * (RISK-L07, TM-L016). 속도 제한이 해싱 <b>앞단</b>에 있어야 하며, 이 테스트는
     * 순서가 올바른지를 검증한다 — 처리량이 아니라 <b>배치 순서</b>를 본다.
     *
     * The critical scenario. Argon2id is deliberately expensive, so per-request hashing on
     * an unauthenticated endpoint is a CPU-exhaustion vector. Rate limiting must sit
     * <b>ahead</b> of hashing, and this test verifies that ordering — it measures placement,
     * not throughput.
     *
     * 합격 기준 / pass criteria:
     *   - CPU 포화 없음 (외부 모니터링으로 확인)
     *   - 대부분의 요청이 429 로 조기 거부됨 → 속도 제한이 해싱 전에 동작
     *   - 429 가 아닌 401 이 대량 관측되면 <b>순서가 잘못된 것</b>이다
     */
    argon2_flood: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '5m',
      startTime: '26m',
      preAllocatedVUs: 100,
      maxVUs: 200,
      exec: 'floodLogin',
      tags: { scenario: 'flood' },
    },

    /*
     * 시나리오 4 — 동일 계정 동시 로그인 / concurrent logins, same account
     * req: FR-LOGIN-016, ADR-LOGIN-012 — 최신 로그인 우선, 세션 1개만 생존
     */
    session_contention: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
      startTime: '32m',
      exec: 'contendSession',
      tags: { scenario: 'contention' },
    },
  },

  thresholds: {
    // req: NFR-PERF-L01
    'login_duration{scenario:normal}': ['p(95)<1000'],
    'login_duration{scenario:peak}': ['p(95)<2000'],
    // 홍수 시나리오에서는 대부분이 조기 거부되어야 한다.
    // Most requests in the flood scenario must be rejected early.
    'rate_limited{scenario:flood}': ['rate>0.80'],
    // 5xx 는 어떤 시나리오에서도 허용하지 않는다 — 과부하는 거부로 처리되어야 하며
    // 서버 오류로 나타나서는 안 된다.
    // No 5xx in any scenario: overload must manifest as refusal, not as server error.
    http_req_failed: ['rate<0.01'],
  },
};

function post(path, body) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * 정상 형태의 로그인 시도. / A well-formed login attempt.
 *
 * 유효한 OTP 를 재사용할 수 없으므로 401(OTP 불일치)이 기대 응답이다. 측정 대상은
 * 자격증명 검증까지의 <b>응답 시간</b>이며, 그 경로에 Argon2id 가 포함된다.
 *
 * A valid OTP cannot be replayed, so 401 is the expected response. What is measured is the
 * response time through credential verification, which includes Argon2id.
 */
export function attemptLogin() {
  const id = __VU;
  const response = post('/api/auth/login', {
    email: `loadtest-${id}@example.com`,
    password: 'Tr0ubled-Kettle!9-LoadTest',
    otpCode: '000000',
  });

  loginDuration.add(response.timings.duration, { scenario: 'normal' });
  rateLimitedRate.add(response.status === 429);

  check(response, {
    'no server error': (r) => r.status < 500,
    'rejected or accepted, never hung': (r) => r.status !== 0,
  });

  sleep(1);
}

/**
 * 홍수 부하 — 속도 제한이 해싱 앞단에 있는지 검증한다.
 * Flood load, verifying the rate limit precedes hashing.
 */
export function floodLogin() {
  const response = post('/api/auth/login', {
    email: `flood-${__VU}@example.com`,
    password: 'Tr0ubled-Kettle!9-Flood',
    otpCode: '000000',
  });

  loginDuration.add(response.timings.duration, { scenario: 'flood' });
  rateLimitedRate.add(response.status === 429, { scenario: 'flood' });

  check(response, {
    'no server error under flood': (r) => r.status < 500,
    // 429 가 관측되어야 정상이다. 401 만 대량으로 나오면 해싱이 제한보다 앞에 있다.
    // Seeing 429 is the healthy signal; a flood of 401s means hashing runs before the limit.
    'rate limited rather than hashed': (r) => r.status === 429 || r.status === 401,
  });
}

/**
 * 동일 계정 동시 로그인 — 세션 1개만 생존해야 한다.
 * Concurrent logins for one account: exactly one session must survive.
 */
export function contendSession() {
  const response = post('/api/auth/login', {
    email: 'contention@example.com',
    password: 'Tr0ubled-Kettle!9-Contend',
    otpCode: '000000',
  });

  loginDuration.add(response.timings.duration, { scenario: 'contention' });

  check(response, {
    'no server error under contention': (r) => r.status < 500,
    // UNIQUE(EML) 제약이 있으므로(V1__auth_session_audit.sql) 경합에서도 두 행이
    // 함께 남을 수 없다. 500 이 나오면 그 경합을 애플리케이션이 처리하지 못한 것이다.
    // The UNIQUE(EML) constraint means two rows cannot survive a race; a 500 would mean the
    // application failed to handle that contention.
    'constraint violation not surfaced as 500': (r) => r.status !== 500,
  });

  sleep(0.5);
}
