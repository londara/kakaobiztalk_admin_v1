import { useState } from 'react';
import { AuthApiError, changePassword } from '../../api/authApi';

/**
 * 비밀번호 변경 화면. / Password change screen.
 *
 * req: FR-PWD-001, FR-PWD-002, FR-PWD-003, FR-LOGIN-014, FR-LOGIN-015
 * source: apa_0010_04.act — 레거시 비밀번호 변경 팝업
 *
 * 강제 변경 흐름에서 진입한다. 이 시점에는 세션이 확립되지 않았으므로 아이디·현재
 * 비밀번호·OTP 를 다시 제출한다 — 인증을 건너뛰는 것이 아니라 요청 단위로 수행한다.
 *
 * Entered from the forced-change flow. No session exists at this point, so id, current
 * password and OTP are submitted again: authentication is performed per request rather
 * than skipped.
 */

interface Props {
  /** 대상 이메일 (로그인 화면에서 전달) / the account email, carried from login */
  email: string;
  /** 변경 완료 후 호출 / called after a successful change */
  onChanged: () => void;
  /** 취소 시 호출 / called on cancel */
  onCancel: () => void;
}

/**
 * 비밀번호 변경 화면 컴포넌트. / The password change component.
 */
export function PasswordChangePage({ email, onChanged, onCancel }: Props) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [violations, setViolations] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setViolations([]);

    // 확인란 불일치는 서버에 보내지 않고 여기서 잡는다 — 왕복을 아끼고, 서버는
    // 확인란의 존재를 알 필요가 없다(API 계약이 단순해진다).
    // The confirmation mismatch is caught here rather than sent: it saves a round trip, and
    // the server need not know the confirmation field exists, keeping the contract simple.
    if (newPassword !== confirmPassword) {
      setError('새 비밀번호와 확인 값이 일치하지 않습니다.');
      return;
    }

    setSubmitting(true);
    try {
      await changePassword(email, currentPassword, otpCode, newPassword);
      onChanged();
    } catch (e) {
      if (e instanceof AuthApiError) {
        if (e.violations.length > 0) {
          // 정책 위반은 전체 목록을 보여준다. 한 번에 모두 고칠 수 있어야 한다.
          // Show every violation so the user can fix them all at once.
          setViolations(e.violations);
        } else {
          setError(e.message);
        }
      } else {
        setError('서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.');
      }
      // OTP 는 1회용이므로 반드시 비운다 (TM-L004).
      // The OTP is single-use, so it must be cleared (TM-L004).
      setOtpCode('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-wrap">
      <h1 className="login-logo">비밀번호 변경</h1>

      <form className="login-box" onSubmit={handleSubmit} noValidate>
        <fieldset>
          <legend>비밀번호 변경</legend>

          <p className="field-help">
            비밀번호를 변경해야 로그인할 수 있습니다. 계정: <strong>{email}</strong>
          </p>

          <div role="alert" aria-live="assertive" className={error ? 'field-error visible' : 'field-error'}>
            {error}
          </div>

          {violations.length > 0 && (
            <ul role="alert" aria-live="assertive" className="violations">
              {violations.map((v) => (
                <li key={v}>{v}</li>
              ))}
            </ul>
          )}

          <label htmlFor="pwd-current">현재 비밀번호</label>
          <input
            id="pwd-current"
            type="password"
            autoComplete="current-password"
            maxLength={128}
            required
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />

          <label htmlFor="pwd-otp">OTP 코드</label>
          <input
            id="pwd-otp"
            type="text"
            inputMode="numeric"
            pattern="\d{6}"
            maxLength={6}
            autoComplete="one-time-code"
            required
            value={otpCode}
            onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
            placeholder="6자리 숫자"
          />

          <label htmlFor="pwd-new">새 비밀번호</label>
          <input
            id="pwd-new"
            type="password"
            autoComplete="new-password"
            minLength={12}
            maxLength={128}
            required
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            aria-describedby="pwd-new-help"
          />
          <p id="pwd-new-help" className="field-help">
            12자 이상, 영문 대문자·소문자·숫자·특수문자 중 3종류 이상을 포함해야 합니다.
            아이디를 포함하거나 최근 사용한 비밀번호는 사용할 수 없습니다.
          </p>

          <label htmlFor="pwd-confirm">새 비밀번호 확인</label>
          <input
            id="pwd-confirm"
            type="password"
            autoComplete="new-password"
            maxLength={128}
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />

          <button type="submit" className="primary" disabled={submitting}>
            {submitting ? '변경 중…' : '변경'}
          </button>
          <button type="button" className="link-button" onClick={onCancel}>
            취소
          </button>
        </fieldset>
      </form>
    </main>
  );
}
