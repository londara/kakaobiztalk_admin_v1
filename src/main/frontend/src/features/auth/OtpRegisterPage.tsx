import { useState } from 'react';
import { AuthApiError, beginOtpRegistration, confirmOtpRegistration } from '../../api/authApi';
import { QrCode } from './QrCode';

/**
 * OTP 등록 화면. / OTP enrolment screen.
 *
 * req: FR-OTP-001…006, NFR-SEC-PII-L01
 * source: apm_1001_03_view.jsp (회원정보 확인), apm_1001_02_view.jsp (내 키 · 코드 입력)
 *
 * 레거시의 2단계 구조를 유지한다: ① 아이디·비밀번호로 신원 확인 → ② 비밀키 표시 후
 * 코드 입력. 이 분리는 편의가 아니라 보안 요구다 — 비밀번호를 확인하지 않고 비밀키를
 * 발급하면 비밀번호만 훔친 공격자가 자신의 단말을 등록할 수 있다(TM-L006).
 *
 * The legacy's two steps are preserved: identity confirmation, then key display and code
 * entry. The split is a security requirement rather than a convenience — issuing a secret
 * without verifying the password would let an attacker holding only a stolen password
 * enrol their own device (TM-L006).
 */

type Step = 'identity' | 'enrol' | 'done';

interface Props {
  /** 초기 이메일 (로그인 화면에서 전달) / initial email, carried from the login screen */
  initialEmail: string;
  /** 로그인 화면으로 돌아갈 때 호출 / called to return to the login screen */
  onBackToLogin: () => void;
}

/**
 * OTP 등록 화면 컴포넌트. / The OTP enrolment component.
 */
export function OtpRegisterPage({ initialEmail, onBackToLogin }: Props) {
  const [step, setStep] = useState<Step>('identity');
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [secret, setSecret] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleIdentity(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await beginOtpRegistration(email, password);
      setSecret(result.secret);
      setOtpauthUri(result.otpauthUri);
      setStep('enrol');
    } catch (e) {
      setError(e instanceof AuthApiError ? e.message : '서버에 연결할 수 없습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleConfirm(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await confirmOtpRegistration(code);
      // 비밀키를 화면 상태에서 즉시 제거한다. 등록이 끝난 뒤에도 메모리에 남길 이유가 없고,
      // 서버도 이후 어떤 API 로도 반환하지 않는다(NFR-SEC-PII-L01).
      // The secret is dropped from component state at once: there is no reason to retain it
      // after enrolment, and the server never returns it again either.
      setSecret('');
      setOtpauthUri('');
      setStep('done');
    } catch (e) {
      setError(e instanceof AuthApiError ? e.message : '서버에 연결할 수 없습니다.');
      setCode('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-wrap">
      <h1 className="login-logo">OTP 등록</h1>

      <div className="login-box">
        <div role="alert" aria-live="assertive" className={error ? 'field-error visible' : 'field-error'}>
          {error}
        </div>

        {/* ── 1단계: 회원정보 확인 / step 1: identity confirmation ── */}
        {step === 'identity' && (
          <form onSubmit={handleIdentity} noValidate>
            <fieldset>
              <legend>회원정보 확인</legend>
              <p className="field-help">
                본인 확인을 위해 아이디와 비밀번호를 입력하세요.
              </p>

              <label htmlFor="otp-email">아이디 (이메일)</label>
              <input
                id="otp-email"
                type="email"
                autoComplete="username"
                maxLength={50}
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="아이디 입력"
              />

              <label htmlFor="otp-password">비밀번호</label>
              <input
                id="otp-password"
                type="password"
                autoComplete="current-password"
                maxLength={128}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호 입력"
              />

              <button type="submit" className="primary" disabled={submitting}>
                {submitting ? '확인 중…' : '다음'}
              </button>
              <button type="button" className="link-button" onClick={onBackToLogin}>
                이전 페이지
              </button>
            </fieldset>
          </form>
        )}

        {/* ── 2단계: 키 등록 / step 2: enrolment ── */}
        {step === 'enrol' && (
          <form onSubmit={handleConfirm} noValidate>
            <fieldset>
              <legend>Google Authenticator 등록</legend>

              <ol className="steps">
                <li>Google Authenticator 앱을 설치하세요.</li>
                <li>앱에서 <strong>계정 추가 → 설정 키 입력</strong>을 선택하세요.</li>
                <li>아래 <strong>내 키</strong>를 입력하거나 QR 링크를 사용하세요.</li>
                <li>앱에 표시된 6자리 숫자를 입력하고 <strong>등록</strong>을 누르세요.</li>
              </ol>

              <label htmlFor="otp-secret">내 키</label>
              {/*
                readOnly + 선택 가능 — 사용자가 복사해야 하지만 편집하면 안 된다.
                disabled 로 하면 일부 브라우저에서 복사가 불가능해진다.
                readOnly rather than disabled: the user must copy it but must not edit it,
                and disabled inputs cannot be copied in some browsers.
              */}
              <input
                id="otp-secret"
                type="text"
                readOnly
                value={secret}
                onFocus={(e) => e.currentTarget.select()}
                className="secret"
                aria-describedby="otp-secret-help"
              />
              <p id="otp-secret-help" className="field-help">
                이 키는 <strong>지금 한 번만</strong> 표시됩니다. 등록을 완료하기 전에 앱에
                입력하세요. 화면을 벗어나면 다시 확인할 수 없습니다.
              </p>

              {otpauthUri && (
                <>
                  {/*
                    QR 은 브라우저 안에서 생성된다 — 비밀키가 외부 호스트에 도달하지
                    않는다(FR-OTP-004, 결함 L4).
                    The QR is generated in-browser, so the secret reaches no external host.
                  */}
                  <QrCode value={otpauthUri} label="OTP 등록용 QR 코드" />
                  <p className="field-help">
                    <a href={otpauthUri}>모바일에서 앱으로 바로 등록</a>
                  </p>
                </>
              )}

              <label htmlFor="otp-confirm-code">인증 코드</label>
              <input
                id="otp-confirm-code"
                type="text"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                autoComplete="one-time-code"
                required
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="6자리 숫자"
              />

              <button type="submit" className="primary" disabled={submitting}>
                {submitting ? '등록 중…' : '등록'}
              </button>
            </fieldset>
          </form>
        )}

        {/* ── 완료 / done ── */}
        {step === 'done' && (
          <div>
            <h2>등록이 완료되었습니다</h2>
            <p className="field-help">
              이제 아이디·비밀번호와 함께 OTP 코드로 로그인할 수 있습니다.
            </p>
            <button type="button" className="primary" onClick={onBackToLogin}>
              로그인 화면으로
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
