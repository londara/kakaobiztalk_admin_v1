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
    // CSRF 토큰을 헤더로 되돌려 보낸다(CR-01, ADR-014). 이것이 없으면 로그인을 제외한
    // 모든 POST 가 403 이다 — 비밀번호 변경·OTP 등록·로그아웃 전부.
    // Echoes the CSRF token (CR-01, ADR-014); without it every POST except login is a 403.
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
