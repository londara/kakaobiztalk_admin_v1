/*
 * 문자내역 조회 부하 테스트 (k6). / 문자내역 search load test (k6).
 *
 * req: NFR-PERF-01, NFR-PERF-02, NFR-PERF-03, NFR-PERF-04
 * source: IDO.KKB_MSG_L002.xml — 8-way UNION ALL with decrypt() per row
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * 실행되지 않았다 / NOT EXECUTED
 * ─────────────────────────────────────────────────────────────────────────────
 * 이 스크립트는 <실행 가능한 애플리케이션>을 요구한다. 현재 환경에는 Maven·PostgreSQL·
 * IRIS_OTP_SECRET_KEY 가 없고 DDL 도 적용되지 않았으므로 애플리케이션을 기동할 수 없다.
 * 따라서 NFR-PERF-01/03 은 SPECIFIED_NOT_RUN 이다 — 작성되었으나 실행 증거가 없다.
 * 로그인 모듈의 login-load.js 와 같은 상태다.
 *
 * This script requires a running application. Maven, PostgreSQL, IRIS_OTP_SECRET_KEY and the
 * applied DDL are all absent here, so NFR-PERF-01/03 remain SPECIFIED_NOT_RUN: written, with no
 * execution evidence. Same status as the login module's login-load.js.
 *
 * 실행 방법 / how to run:
 *   k6 run -e BASE_URL=https://host -e USER=... -e PASS=... -e OTP=... \
 *          qa/load/message-history-load.js
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * 왜 이 슬라이스의 부하 특성이 특별한가 / why this slice's load profile is unusual:
 *
 *   레거시 조회 1회는 8개 테이블(라이브 4 + _LOG 4)의 UNION ALL 이며, 각 행마다
 *   decrypt() 가 두 번(CALLBACK, PHONE) 호출된다. 게다가 레거시는 서버 페이징이 주석
 *   처리되어 있었으므로(D7) 조건에 맞는 <모든> 행을 복호화했다. 신규 구현은 LIMIT 을
 *   적용하지만 COUNT(*) 는 여전히 전체 UNION 을 스캔한다 — 그것이 이 테스트가 측정해야
 *   하는 주된 비용이다.
 *
 *   One search is an 8-table UNION ALL with two decrypt() calls per row. The legacy had paging
 *   commented out (D7), so it decrypted every matching row. The new implementation applies a
 *   LIMIT, but COUNT(*) still scans the whole union — that is the cost this test must measure.
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

const searchLatency = new Trend('search_latency', true);
const countHeavyLatency = new Trend('search_latency_wide_window', true);
const exportLatency = new Trend('export_latency', true);
const detailLatency = new Trend('detail_latency', true);
const tenantLeak = new Rate('tenant_leak');

/*
 * SLA — TEST-PLAN.md §부하 기준의 2배 부하에서 측정한다.
 * NFR-PERF-01: 조회 P95 < 3s   (레거시 기준선 없음 — 실행 불가하므로 목표치다)
 * NFR-PERF-03: 동시 50 사용자에서 오류율 < 1%
 * NFR-PERF-04: 31일 전체 기간 조회가 타임아웃(30s) 없이 완료
 */
export const options = {
  scenarios: {
    // 1. 통상 조회 — 최근 1일, 기본 페이지 크기
    typical_search: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 25 },
        { duration: '2m', target: 50 },   // NFR-PERF-03 의 2배 부하
        { duration: '30s', target: 0 },
      ],
      exec: 'typicalSearch',
    },
    // 2. 광폭 조회 — 31일 상한. COUNT(*) 가 8테이블 전체를 스캔하는 최악 경로.
    wide_window: {
      executor: 'constant-vus',
      vus: 5,
      duration: '3m',
      startTime: '30s',
      exec: 'wideWindow',
    },
    // 3. 상세 조회 — 4-way 라우팅. 인덱스가 (MSGKEY, REQDATE) 를 덮는지 확인한다.
    detail_lookup: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 20,
      startTime: '1m',
      exec: 'detailLookup',
    },
    // 4. 내보내기 — 상한 5,000건. CSV 를 문자열로 조립하므로 힙 사용이 급증한다.
    csv_export: {
      executor: 'constant-vus',
      vus: 2,
      duration: '1m',
      startTime: '2m',
      exec: 'csvExport',
    },
  },
  thresholds: {
    'search_latency': ['p(95)<3000'],                    // NFR-PERF-01
    'search_latency_wide_window': ['p(95)<10000', 'max<30000'], // NFR-PERF-04
    'detail_latency': ['p(95)<1000'],
    'export_latency': ['p(95)<15000'],
    'http_req_failed': ['rate<0.01'],                    // NFR-PERF-03
    // 테넌트 격리는 성능 저하 상황에서도 무너지지 않아야 한다. 부하 중 캐시·커넥션
    // 재사용이 개입하면서 격리가 깨지는 것은 실제로 발생하는 결함 유형이다.
    // Isolation must hold under load: it breaking as connections and caches are reused is a real
    // failure mode, not a hypothetical one.
    'tenant_leak': ['rate==0'],
  },
};

/** 로그인하여 세션 쿠키를 얻는다. / Authenticates and returns the session cookie jar. */
function login() {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    email: __ENV.USER,
    password: __ENV.PASS,
    otpCode: __ENV.OTP,   // 실행 시 TOTP 를 생성해 주입해야 한다
  }), { headers: { 'Content-Type': 'application/json' } });

  check(res, { 'login 200': (r) => r.status === 200 });
  return res;
}

export function setup() {
  const res = login();
  return { ok: res.status === 200 };
}

function isoDaysAgo(days) {
  const d = new Date(Date.now() - days * 86400000);
  return d.toISOString().slice(0, 19);
}

function searchBody(days, extra) {
  return JSON.stringify(Object.assign({
    from: isoDaysAgo(days),
    to: isoDaysAgo(0),
    page: 0,
    size: 50,
  }, extra || {}));
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function typicalSearch() {
  group('typical search (1 day)', () => {
    const res = http.post(`${BASE}/api/message-history/search`, searchBody(1), {
      headers: JSON_HEADERS,
    });
    searchLatency.add(res.timings.duration);
    check(res, {
      'search 200': (r) => r.status === 200,
      'has rows array': (r) => Array.isArray((r.json() || {}).rows),
      // 마스킹이 부하 중에도 유지되는지 확인한다 — 평문 11자리 번호가 보이면 실패다.
      'phones masked': (r) => !/"(sender|recipient)Number":"\d{10,11}"/.test(r.body || ''),
    });

    // 이용기관 담당자 세션이 다른 이용기관 코드를 요청해도 무시되어야 한다(FR-TEN-001).
    const override = http.post(`${BASE}/api/message-history/search`,
      searchBody(1, { institutionCode: 'OTHER' }), { headers: JSON_HEADERS });
    const rows = ((override.json() || {}).rows) || [];
    const leaked = rows.some((r) => r.institutionCode === 'OTHER');
    tenantLeak.add(leaked);
  });
  sleep(1);
}

export function wideWindow() {
  group('wide window (31 days, the cap)', () => {
    const res = http.post(`${BASE}/api/message-history/search`, searchBody(31), {
      headers: JSON_HEADERS,
      timeout: '60s',
    });
    countHeavyLatency.add(res.timings.duration);
    check(res, { 'wide search 200': (r) => r.status === 200 });
  });
  sleep(2);
}

export function detailLookup() {
  // 먼저 한 페이지를 조회해 실제 키를 얻는다. 키를 하드코딩하면 인덱스 없는 경로를
  // 우연히 피해갈 수 있다.
  const list = http.post(`${BASE}/api/message-history/search`, searchBody(1), {
    headers: JSON_HEADERS,
  });
  const rows = ((list.json() || {}).rows) || [];
  if (rows.length === 0) {
    return;
  }
  const row = rows[Math.floor(Math.random() * rows.length)];

  const res = http.post(`${BASE}/api/message-history/detail`, JSON.stringify({
    messageType: row.messageType,
    tableType: row.tableType,
    messageKey: row.messageKey,
    requestDate: row.requestDate,
    status: row.status,
  }), { headers: JSON_HEADERS });

  detailLatency.add(res.timings.duration);
  check(res, { 'detail 200 or 404': (r) => r.status === 200 || r.status === 404 });
}

export function csvExport() {
  group('csv export (up to 5,000 rows)', () => {
    const res = http.post(`${BASE}/api/message-history/export`, searchBody(7), {
      headers: JSON_HEADERS,
      timeout: '60s',
    });
    exportLatency.add(res.timings.duration);
    check(res, {
      // 400 도 정상이다 — 5,000건 상한을 초과하면 거절되어야 하고, 그 거절이 확인 대상이다.
      // A 400 is a valid outcome: exceeding the cap must be refused, and that refusal is checked.
      'export 200 or 400': (r) => r.status === 200 || r.status === 400,
      'no plaintext phone in csv': (r) => !/[,^]\d{11},/.test(r.body || ''),
      'formula injection neutralised': (r) => !/(^|,)=/.test(r.body || ''),
    });
  });
  sleep(3);
}
