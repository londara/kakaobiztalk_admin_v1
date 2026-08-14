import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';

/**
 * QR 코드 렌더링 컴포넌트. / QR code rendering component.
 *
 * <h2>레거시 결함 L4 대응의 완성 / Completes the fix for legacy defect L4</h2>
 * <p>레거시 {@code GoogleOTP.getQRBarcodeURL()} 은 다음 URL 을 만들었다:</p>
 * <pre>http://chart.apis.google.com/chart?cht=qr&amp;chl=otpauth://...%3Fsecret%3D&lt;SECRET&gt;</pre>
 * <p>즉 <b>OTP 공유 비밀키를 평문 HTTP 로 제3자에게 전송</b>했다. Sprint L3 에서 서버는
 * URI 문자열만 만들도록 고쳤고, 이 컴포넌트가 나머지 절반을 담당한다 — QR 이미지를
 * <b>사용자의 브라우저 안에서</b> 생성한다. 비밀키는 어떤 외부 호스트에도 도달하지 않는다.</p>
 * <p>The legacy transmitted the shared OTP secret in cleartext over HTTP to a third party.
 * Sprint L3 fixed the server to emit only the URI string; this component supplies the other
 * half by rendering the QR image <b>inside the user's own browser</b>. The secret reaches no
 * external host.</p>
 *
 * <p><b>canvas 에 그리는 이유:</b> {@code <img src="data:...">} 로 하면 데이터 URL 이 DOM
 * 속성에 남아 개발자 도구·확장 프로그램·스크린샷 도구에 노출될 여지가 커진다. canvas 는
 * 픽셀만 남긴다.</p>
 * <p><b>Why canvas:</b> an {@code <img src="data:...">} leaves the payload in a DOM
 * attribute, more exposed to devtools, extensions and screenshot tooling. A canvas leaves
 * only pixels.</p>
 *
 * // source: com/common/irisadmin/util/GoogleOTP.java — getQRBarcodeURL()
 * // req: FR-OTP-003, FR-OTP-004, NFR-SEC-PII-L01
 */

interface Props {
  /** otpauth:// URI / the otpauth URI */
  value: string;
  /** 접근성 대체 텍스트 / accessible description */
  label: string;
}

/**
 * QR 코드를 로컬에서 렌더링한다. / Renders a QR code locally.
 */
export function QrCode({ value, label }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !value) {
      return;
    }
    // 로컬 생성 — 네트워크 호출이 일어나지 않는다.
    // Generated locally; no network call takes place.
    QRCode.toCanvas(canvas, value, { width: 200, margin: 2 })
      .then(() => setError(null))
      .catch(() => {
        // QR 을 못 그려도 등록은 계속 가능해야 한다 — 사용자는 키를 직접 입력할 수 있다.
        // Enrolment must survive a QR failure: the user can type the key instead.
        setError('QR 코드를 표시할 수 없습니다. 아래 키를 직접 입력하세요.');
      });
  }, [value]);

  return (
    <div className="qr-wrap">
      {/*
        canvas 는 스크린리더에 의미가 없으므로 role/aria-label 로 대체 정보를 준다.
        비밀키 자체를 aria-label 에 넣지는 않는다 — 보조기술에 노출할 이유가 없다.
        A canvas is meaningless to a screen reader, so role/aria-label carry the alternative.
        The secret itself is not placed in aria-label: no reason to expose it to assistive tech.
      */}
      <canvas ref={canvasRef} role="img" aria-label={label} />
      {error && (
        <p role="alert" className="field-error visible">
          {error}
        </p>
      )}
    </div>
  );
}
