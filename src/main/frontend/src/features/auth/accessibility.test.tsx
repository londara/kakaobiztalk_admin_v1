import { render } from '@testing-library/react';
import axe from 'axe-core';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';
import { PasswordChangePage } from './PasswordChangePage';
import { OtpRegisterPage } from './OtpRegisterPage';

/**
 * 접근성 자동 검증. / Automated accessibility checks.
 *
 * req: TEST-PLAN-LOGIN §1.3 — axe-core, WCAG 2.1 AA
 *
 * <p>수동으로 접근성을 챙기는 것과 자동 검증은 다른 일이다. 손으로 넣은 라벨·aria 속성은
 * 리팩터링에서 조용히 사라지며, 그 사실은 실제 사용자가 막힐 때까지 드러나지 않는다.</p>
 * <p>Hand-crafted labels and aria attributes disappear quietly during refactoring, and nobody
 * learns of it until a real user is blocked. Automation is a different activity from care.</p>
 *
 * <p><b>axe 가 잡지 못하는 것:</b> 색상 대비는 jsdom 에 실제 렌더링이 없어 검사되지 않고,
 * 포커스 순서·스크린리더 낭독 품질도 자동으로는 확인되지 않는다. 이 테스트는 구조적
 * 위반(라벨 누락, 중복 id, 잘못된 role 중첩)을 잡는다.</p>
 * <p><b>What axe cannot catch here:</b> colour contrast is not evaluated because jsdom does no
 * real rendering, and neither focus order nor screen-reader quality is checked automatically.
 * These tests catch structural violations — missing labels, duplicate ids, invalid role
 * nesting.</p>
 */
describe('accessibility (axe-core)', () => {
  /**
   * 지정 요소에 axe 를 실행하고 위반을 반환한다.
   * Runs axe against the element and returns violations.
   */
  async function violationsOf(container: HTMLElement): Promise<axe.Result[]> {
    const results = await axe.run(container, {
      // jsdom 에서 의미 없는 규칙은 제외한다 — 통과 여부가 실제 접근성을 반영하지 않는다.
      // Rules meaningless under jsdom are excluded: their result would not reflect reality.
      rules: {
        'color-contrast': { enabled: false },
        region: { enabled: false },
      },
    });
    return results.violations;
  }

  function describeViolations(violations: axe.Result[]): string {
    return violations
      .map((v) => `${v.id} (${v.impact}): ${v.help} [${v.nodes.length} node(s)]`)
      .join('\n');
  }

  it('로그인 화면에 접근성 위반이 없다 / LoginPage has no violations', async () => {
    const { container } = render(
      <LoginPage
        onAuthenticated={vi.fn()}
        onPasswordChangeRequired={vi.fn()}
        onNeedOtpRegistration={vi.fn()}
      />,
    );
    const violations = await violationsOf(container);
    expect(describeViolations(violations)).toBe('');
  });

  it('비밀번호 변경 화면에 접근성 위반이 없다 / PasswordChangePage has no violations', async () => {
    const { container } = render(
      <PasswordChangePage email="user@example.com" onChanged={vi.fn()} onCancel={vi.fn()} />,
    );
    const violations = await violationsOf(container);
    expect(describeViolations(violations)).toBe('');
  });

  it('OTP 등록 화면에 접근성 위반이 없다 / OtpRegisterPage has no violations', async () => {
    const { container } = render(
      <OtpRegisterPage initialEmail="user@example.com" onBackToLogin={vi.fn()} />,
    );
    const violations = await violationsOf(container);
    expect(describeViolations(violations)).toBe('');
  });

  it('모든 입력에 접근 가능한 이름이 있다 / every input has an accessible name', async () => {
    // req: WCAG 2.1 AA 4.1.2 — 라벨 없는 입력은 스크린리더 사용자에게 무의미하다
    const { container } = render(
      <LoginPage
        onAuthenticated={vi.fn()}
        onPasswordChangeRequired={vi.fn()}
        onNeedOtpRegistration={vi.fn()}
      />,
    );
    const inputs = Array.from(container.querySelectorAll('input'));
    expect(inputs.length).toBeGreaterThan(0);

    inputs.forEach((input) => {
      const id = input.getAttribute('id');
      const labelled =
        (id && container.querySelector(`label[for="${id}"]`)) ||
        input.closest('label') ||
        input.getAttribute('aria-label');
      expect(labelled, `input[type=${input.type}] has no accessible name`).toBeTruthy();
    });
  });

  it('비밀번호 입력에 autocomplete 힌트가 있다 / password inputs carry autocomplete hints', () => {
    // WCAG 1.3.5 Identify Input Purpose. 비밀번호 관리자 호환성에도 필요하다
    const { container } = render(
      <PasswordChangePage email="user@example.com" onChanged={vi.fn()} onCancel={vi.fn()} />,
    );
    const passwords = Array.from(container.querySelectorAll('input[type="password"]'));
    expect(passwords.length).toBe(3);
    passwords.forEach((input) => {
      expect(input.getAttribute('autocomplete')).toMatch(/^(current-password|new-password)$/);
    });
  });

  it('OTP 입력에 one-time-code 힌트가 있다 / OTP inputs declare one-time-code', () => {
    // iOS/Android 가 SMS·앱 코드를 자동 채우기 위해 필요하다
    const { container } = render(
      <LoginPage
        onAuthenticated={vi.fn()}
        onPasswordChangeRequired={vi.fn()}
        onNeedOtpRegistration={vi.fn()}
      />,
    );
    const otp = container.querySelector('#login-otp');
    expect(otp?.getAttribute('autocomplete')).toBe('one-time-code');
  });
});
