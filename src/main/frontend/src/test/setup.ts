import '@testing-library/jest-dom/vitest';

/**
 * 테스트 공통 설정. / Shared test setup.
 *
 * req: TEST-PLAN-LOGIN §1.3
 */

/*
  ─────────────────────────────────────────────────────────────────────────────
  localStorage 보정 / repairing localStorage
  ─────────────────────────────────────────────────────────────────────────────
  이 환경의 Node(v25)는 전역 `localStorage` 를 스스로 노출하는데, 저장 파일 경로가 주어지지
  않아 `setItem` 도 `clear` 도 없는 빈 객체다(실행 시 "--localstorage-file was provided
  without a valid path" 경고가 함께 나온다). 그 전역이 jsdom 이 만든 정상적인 Storage 를
  가리므로, 로그인 화면의 '아이디저장'(localStorage 사용)을 다루는 테스트가 코드와 무관하게
  TypeError 로 실패한다. 같은 이유로 `sessionStorage` 는 영향을 받지 않는다 — 그쪽은 jsdom
  것이 그대로 남는다.

  Node (v25) in this environment exposes its own global `localStorage`, and without a storage-file
  path it is an empty object with no `setItem` and no `clear` — the run also prints
  "--localstorage-file was provided without a valid path". That global shadows the working Storage
  jsdom provides, so tests touching the login screen's remember-me feature fail with a TypeError
  that has nothing to do with the code under test. `sessionStorage` is unaffected for the same
  reason: jsdom's is still the one in place.

  여기서 고치는 대상은 <b>테스트 환경</b>이며 제품 동작이 아니다. 브라우저에는 실제
  localStorage 가 있다.
  What is repaired here is the test environment, not product behaviour: a browser has a real
  localStorage.
*/
/*
  항목을 <b>열거 가능한 자기 속성</b>으로 들고 있는 것이 중요하다. 브라우저의 Storage 도
  그렇게 동작하므로 `JSON.stringify(localStorage)` 가 저장된 항목을 보여 준다 — 어떤 테스트는
  "이메일은 저장되고 비밀번호는 저장되지 않았다" 를 그 방식으로 확인한다. 항목을 Map 에
  숨기면 그 검사가 항상 빈 객체를 보게 되어, 검증이 통과하는 대신 <b>무의미해진다</b>.
  Items are held as enumerable own properties because that is how a browser's Storage behaves:
  `JSON.stringify(localStorage)` shows the stored items, and a test proves "the email was stored,
  the password was not" that way. Hiding items in a Map would make that check see an empty object
  — the assertion would not fail, it would stop meaning anything.
*/
function createMemoryStorage(): Storage {
  const storage: Record<string, string> = {};

  const method = (name: string, value: unknown) =>
    Object.defineProperty(storage, name, {
      value,
      enumerable: false,
      configurable: true,
      writable: true,
    });

  method('getItem', (key: string) =>
    Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : null,
  );
  method('setItem', (key: string, value: string) => {
    storage[String(key)] = String(value);
  });
  method('removeItem', (key: string) => {
    delete storage[String(key)];
  });
  method('clear', () => {
    for (const key of Object.keys(storage)) {
      delete storage[key];
    }
  });
  method('key', (index: number) => Object.keys(storage)[index] ?? null);
  Object.defineProperty(storage, 'length', {
    get: () => Object.keys(storage).length,
    enumerable: false,
    configurable: true,
  });

  // 항목과 메서드가 한 객체에 섞여 있으므로 구조적으로는 Storage 가 아니다. 동작은 같다.
  // Items and methods share one object, so it is not structurally a Storage; it behaves as one.
  return storage as unknown as Storage;
}

const candidate = globalThis.localStorage as Partial<Storage> | undefined;
if (typeof candidate?.setItem !== 'function' || typeof candidate?.clear !== 'function') {
  Object.defineProperty(globalThis, 'localStorage', {
    value: createMemoryStorage(),
    configurable: true,
    writable: true,
  });
}
