/**
 * 인증 API 클라이언트. / Authentication API client.
 *
 * req: FR-LOGIN-001, FR-OTP-001…006, FR-PWD-001/002
 * source: apm_0001_01.js — jex.createAjaxUtil("apc_login_proc")
 *
 * 레거시는 Jex 프레임워크의 ajax 유틸을 사용했고 서버 응답의 내부 코드
 * ({@code WCI00018}, {@code ADM_00003})를 그대로 화면 로직에 노출했다. 신규 백엔드는
 * 안정적인 코드 집합을 반환하므로 그것만 다룬다.
 *
 * The legacy used the Jex ajax utility and exposed internal server codes such as
 * WCI00018 and ADM_00003 directly to screen logic. The new backend returns a stable set
 * of codes, so only those are handled here.
 */

import { csrfHeader } from './csrf';

/** 서버 오류 응답. / An error response from the server. */
export interface ApiError {
  code: string;
  message: string;
  violations?: string[];
}

/** 로그인 결과. / The login result. */
export interface LoginResult {
  passwordChangeRequired: boolean;
  operator: boolean;
  displacedSession: boolean;
}

/** OTP 등록 시작 결과. / The OTP enrolment begin result. */
export interface OtpBeginResult {
  secret: string;
  otpauthUri: string;
}

/**
 * 세션 확인 결과. / The session probe result.
 *
 * <p>{@link LoginResult} 와 달리 로그인이라는 <b>사건</b>에 속한 값(비밀번호 변경 필요,
 * 기존 세션 종료)은 없다. 서버가 그렇게 응답하기 때문이며, 이유는 서버 쪽
 * {@code SessionResponse} 에 적혀 있다.</p>
 * <p>Unlike {@link LoginResult} this carries nothing belonging to the <b>act</b> of logging in;
 * the server answers that way, and its {@code SessionResponse} records why.</p>
 */
export interface SessionResult {
  operator: boolean;
}

/**
 * API 호출 실패. / Raised when an API call fails.
 *
 * 서버가 제공한 코드를 보존하여 화면이 분기할 수 있게 한다.
 * Preserves the server-supplied code so screens can branch on it.
 */
export class AuthApiError extends Error {
  readonly code: string;
  readonly violations: string[];

  constructor(error: ApiError) {
    super(error.message);
    this.name = 'AuthApiError';
    this.code = error.code;
    this.violations = error.violations ?? [];
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    // CSRF 토큰을 헤더로 되돌려 보낸다(CR-01, ADR-014). 병합 중 유실되어 복원함.
    // Echoes the CSRF token (CR-01, ADR-014); restored after a merge dropped it.
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    // 세션 쿠키를 반드시 포함한다. 기본값(same-origin)에 의존하지 않고 명시한다 —
    // 누락되면 OTP 등록의 2단계가 대기 상태를 찾지 못한다(서버는 세션에 보관).
    // Credentials are stated explicitly rather than relying on the default: omitting them
    // would break OTP enrolment's second step, whose pending state lives in the session.
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new AuthApiError({
      code: (payload as ApiError).code ?? 'UNKNOWN',
      message: (payload as ApiError).message ?? '요청을 처리할 수 없습니다.',
      violations: (payload as { violations?: string[] }).violations,
    });
  }

  return payload as T;
}

/**
 * 로그인한다. / Logs in.
 *
 * req: FR-LOGIN-001 — 이메일·비밀번호·OTP 를 한 요청으로 제출한다.
 */
export function login(email: string, password: string, otpCode: string): Promise<LoginResult> {
  return post<LoginResult>('/api/auth/login', { email, password, otpCode });
}

/** 로그아웃한다. / Logs out. req: FR-LOGIN-023 */
export function logout(): Promise<void> {
  return post<void>('/api/auth/logout', {});
}

/**
 * 현재 세션이 살아 있는지 서버에 묻는다. / Asks the server whether the session is alive.
 *
 * req: FR-LOGIN-018, ADR-001
 *
 * <p>세션 쿠키는 {@code HttpOnly} 이므로 JS 가 읽을 수 없다(ADR-LOGIN-012). 새로고침하면
 * 자바스크립트 상태는 사라지지만 쿠키와 서버 세션은 남는다 — 이 호출이 없으면 클라이언트는
 * 그 사실을 알 방법이 없어 매번 로그인 화면으로 되돌아간다.</p>
 * <p>The session cookie is {@code HttpOnly} and unreadable from JS. A refresh discards JavaScript
 * state while the cookie and server session survive; without this call the client has no way to
 * learn that and returns to the login screen every time.</p>
 *
 * <p>실패하면 <b>던지지 않고 null</b> 을 돌려준다. "세션이 없다" 는 오류가 아니라 정상적인
 * 답이며(미인증 요청에는 403 이 온다), 그것을 예외로 만들면 화면마다 오류 처리를 붙여야 한다.</p>
 * <p>Returns {@code null} instead of throwing: "no session" is a normal answer rather than a
 * failure — an unauthenticated request answers 403 — and making it an exception would push error
 * handling into every screen.</p>
 *
 * <p>판별이 불가능한 응답도 null 로 본다(fail closed). 세션이 있는지 확신할 수 없을 때
 * 있다고 가정하면, 로그인된 것처럼 보이는 화면에서 모든 조회가 403 이 되는 상태가 만들어진다.</p>
 * <p>An unreadable answer is also null — fail closed. Assuming a session when it cannot be
 * confirmed produces a screen that looks signed in while every search returns 403.</p>
 *
 * @returns 세션 정보, 세션이 없으면 null / the session, or null when there is none
 */
export async function fetchSession(): Promise<SessionResult | null> {
  const response = await fetch('/api/auth/session', {
    method: 'GET',
    credentials: 'same-origin',
  });

  if (!response.ok) {
    return null;
  }

  const payload = await response.json().catch(() => null);
  if (payload === null || typeof (payload as SessionResult).operator !== 'boolean') {
    return null;
  }
  return payload as SessionResult;
}

/**
 * OTP 등록을 시작한다. / Begins OTP enrolment.
 *
 * req: FR-OTP-001 — 비밀번호로 신원을 확인한 뒤에만 비밀키를 발급한다(TM-L006).
 * source: apm_1001_03_view.jsp — 회원정보 확인 단계
 */
export function beginOtpRegistration(email: string, password: string): Promise<OtpBeginResult> {
  return post<OtpBeginResult>('/api/auth/otp/registration/begin', { email, password });
}

/**
 * OTP 등록을 확인한다. / Confirms OTP enrolment.
 *
 * req: FR-OTP-005 — 코드가 검증되어야만 비밀키가 저장된다.
 * source: apm_1001_02_view.jsp — 코드 입력 단계
 */
export function confirmOtpRegistration(code: string): Promise<void> {
  return post<void>('/api/auth/otp/registration/confirm', { code });
}

/**
 * 비밀번호를 변경한다. / Changes the password.
 *
 * req: FR-PWD-001/002 — 현재 비밀번호와 OTP 를 함께 요구한다(TM-L018).
 * source: apa_0010_04.act — 레거시 비밀번호 변경 팝업
 */
export function changePassword(
  email: string,
  currentPassword: string,
  otpCode: string,
  newPassword: string,
): Promise<void> {
  return post<void>('/api/auth/password/change', {
    email,
    currentPassword,
    otpCode,
    newPassword,
  });
}
