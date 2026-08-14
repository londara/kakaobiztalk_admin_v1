import { useEffect, useRef, useState } from 'react';
import { AuthApiError, login } from '../../api/authApi';

/**
 * 로그인 화면. / Login screen.
 *
 * req: FR-LOGIN-001, FR-LOGIN-022, NFR-USE-L01, NFR-USE-L02
 * source: apm_0001_01_view.jsp, apm_0001_01.js
 *
 * 레거시 화면 구성을 유지한다 — 아이디·비밀번호·OTP 를 한 화면에서 한 번에 제출하고,
 * 아이디저장 체크박스와 OTP 등록 링크를 제공한다(NFR-USE-L01: 단일 화면·단일 제출).
 *
 * The legacy layout is preserved: id, password and OTP submitted together on one screen,
 * with the remember-id checkbox and the OTP enrolment link.
 */

/** 저장된 아이디의 localStorage 키. / localStorage key for the remembered id. */
// source: apm_0001_01.js — $.cookie('ap.eml')
// req: FR-LOGIN-022 — 이메일만 저장한다. 비밀번호·OTP 는 절대 저장하지 않는다.
//      Only the email is stored; never the password or OTP.
const REMEMBERED_EMAIL_KEY = 'iris.auth.rememberedEmail';

interface Props {
  /** 비밀번호 변경이 필요할 때 호출된다. / Called when a password change is required. */
  onPasswordChangeRequired: (email: string) => void;
  /** 로그인 성공 시 호출된다. / Called on successful login. */
  onAuthenticated: (operator: boolean, displacedSession: boolean) => void;
  /** OTP 등록으로 이동할 때 호출된다. / Called to navigate to OTP enrolment. */
  onNeedOtpRegistration: (email: string) => void;
}

/**
 * 로그인 화면 컴포넌트. / The login screen component.
 */
export function LoginPage({
  onPasswordChangeRequired,
  onAuthenticated,
  onNeedOtpRegistration,
}: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [rememberEmail, setRememberEmail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);

  // 저장된 아이디 복원 / restore the remembered id
  // source: apm_0001_01.js — onload cookie restore
  useEffect(() => {
    const saved = localStorage.getItem(REMEMBERED_EMAIL_KEY);
    if (saved) {
      setEmail(saved);
      setRememberEmail(true);
    }
  }, []);

  // 오류 발생 시 포커스를 옮긴다. aria-live 만으로는 키보드 사용자가 오류 위치를
  // 찾기 어렵다 (WCAG 2.1 AA — 3.3.1 Error Identification).
  // Move focus on error: aria-live alone leaves keyboard users hunting for the message.
  useEffect(() => {
    if (error) {
      errorRef.current?.focus();
    }
  }, [error]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const result = await login(email, password, otpCode);

      if (rememberEmail) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, email);
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY);
      }

      if (result.passwordChangeRequired) {
        onPasswordChangeRequired(email);
        return;
      }
      onAuthenticated(result.operator, result.displacedSession);
    } catch (e) {
      if (e instanceof AuthApiError) {
        // OTP 미등록은 오류가 아니라 등록 화면으로의 유도다.
        // A missing OTP enrolment is a redirect, not an error.
        if (e.code === 'OTP_NOT_REGISTERED') {
          onNeedOtpRegistration(email);
          return;
        }
        setError(e.message);
      } else {
        setError('서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.');
      }
      // 실패 시 OTP 코드만 비운다. 코드는 1회용이므로(TM-L004) 재사용할 수 없고,
      // 아이디·비밀번호를 함께 지우면 사용자가 전부 다시 입력해야 한다.
      // Only the OTP is cleared: codes are single-use (TM-L004) so it cannot be reused,
      // while clearing everything would force the user to retype all three fields.
      setOtpCode('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-wrap">
      <h1 className="login-logo">IRIS BizTalk Portal</h1>

      <form className="login-box" onSubmit={handleSubmit} noValidate>
        <fieldset>
          <legend>로그인</legend>

          {/*
            aria-live="assertive" — 오류는 즉시 읽혀야 한다.
            role="alert" 로 스크린리더가 현재 읽던 내용을 중단하고 전달한다.
          */}
          <div
            ref={errorRef}
            role="alert"
            aria-live="assertive"
            tabIndex={-1}
            className={error ? 'field-error visible' : 'field-error'}
          >
            {error}
          </div>

          <label htmlFor="login-email">아이디 (이메일)</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="username"
            maxLength={50}
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="아이디 입력"
          />

          <label htmlFor="login-password">비밀번호</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            /*
              maxLength 를 128 로 둔다 — 레거시는 15 였다(결함 L9).
              maxLength is 128; the legacy capped it at 15 (defect L9).
            */
            maxLength={128}
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호 입력"
          />

          <label htmlFor="login-otp">OTP 코드</label>
          <input
            id="login-otp"
            name="otpCode"
            /*
              type="text" + inputMode="numeric" — type="number" 는 스피너가 붙고
              선행 0 이 사라지는 브라우저가 있어 6자리 코드에 부적합하다.
              type="number" adds spinners and drops leading zeros in some browsers, which
              breaks a zero-padded six-digit code.
            */
            type="text"
            inputMode="numeric"
            pattern="\d{6}"
            maxLength={6}
            autoComplete="one-time-code"
            required
            value={otpCode}
            onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
            placeholder="6자리 숫자"
            aria-describedby="login-otp-help"
          />
          <p id="login-otp-help" className="field-help">
            Google Authenticator 앱에 표시된 6자리 숫자를 입력하세요.
          </p>

          <div className="login-options">
            <label className="checkbox">
              <input
                type="checkbox"
                checked={rememberEmail}
                onChange={(e) => setRememberEmail(e.target.checked)}
              />
              아이디저장
            </label>
            <button
              type="button"
              className="link-button"
              onClick={() => onNeedOtpRegistration(email)}
            >
              OTP 등록
            </button>
          </div>

          <button type="submit" className="primary" disabled={submitting}>
            {submitting ? '로그인 중…' : '로그인'}
          </button>
        </fieldset>
      </form>
    </main>
  );
}
