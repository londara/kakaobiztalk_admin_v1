// 로컬 모델(ollama) 교차검증 드라이버 — 코드는 이 머신을 떠나지 않는다.
// Local-model cross-validation driver. Nothing leaves this machine.
const fs = require('fs');
const path = require('path');
const DIR = __dirname;
const MODEL = process.argv[2] || 'qwen2.5-coder:7b';

const CLAIMS = [
  { id: 'A', file: 'A-ordering.sql.txt', lang: 'xml',
    claim: `MyBatis 매퍼의 findMessages 질의(ORDER BY A.REQDATE DESC, A.MSGKEY DESC)는 전순서(total order)가 아니다. 정렬 대상 A 는 QUE 테이블과 LOG 테이블의 UNION ALL 이고 TABLE_TYPE 이 정렬 키에 없다. 따라서 같은 (REQDATE, MSGKEY) 짝이 QUE 와 LOG 양쪽에 존재하면, LIMIT/OFFSET 페이징에서 행이 중복되거나 누락될 수 있다.` },
  { id: 'B', file: 'B-exception-handler.java.txt', lang: 'java',
    claim: `이 GlobalExceptionHandler 에는 TalkExportService/TalkDetailService/TransactionSerial 이 던지는 도메인 예외들에 대한 @ExceptionHandler 가 없다. 따라서 그 예외들은 전부 handleUnexpected 로 떨어져 HTTP 500 이 되고, 예외 메시지가 담고 있던 정보(허용 범위, 지원하지 않는 거래 구분 등)가 응답 계층에서 소실된다.` },
  { id: 'C1', file: 'C-detail-authz.java.txt', lang: 'java',
    claim: `상세 조회 경로가 findTransactionOwner 로 얻은 기관코드를 호출자(principal)의 범위와 교차 검증하지 않는다. principal 은 감사 기록에만 쓰이고, 조회된 기관이 그대로 하위 질의로 흘러간다.` },
  { id: 'C2', file: 'C-detail-exceptions.java.txt', lang: 'java',
    claim: `이 파일에 정의된 예외들은 전부 RuntimeException 을 상속하며 @ResponseStatus 애노테이션이 없다.` },
  { id: 'F', file: 'F-formula-injection.java.txt', lang: 'java',
    claim: `이 엑셀 작성기는 셀 문자열의 선행 문자 = + - @ 를 무력화하지 않는다. 따라서 CSV/엑셀 수식 주입(formula injection)에 취약하다.` },
];

const PROMPT = (c, code) => `당신은 독립적인 코드 검증자입니다. 다른 리뷰어가 아래 "주장"을 했습니다.
당신의 임무는 그 주장을 **반증(refute)** 하려고 시도하는 것입니다. 동의를 위한 동의는 하지 마십시오.
오직 아래 제시된 코드만 근거로 판단하십시오. 코드에 없는 것을 가정하지 마십시오.

## 주장
${c.claim}

## 코드
\`\`\`${c.lang}
${code}
\`\`\`

## 답변 형식 (반드시 이 형식만)
판정: CONFIRMED | REFUTED | UNCERTAIN
근거: (코드의 구체적 줄/식별자를 인용하여 2~4문장)
반론: (이 주장이 틀릴 수 있는 조건이 있다면 1~2문장, 없으면 "없음")`;

(async () => {
  const out = [];
  for (const c of CLAIMS) {
    const p = path.join(DIR, c.file);
    if (!fs.existsSync(p)) { console.log(`SKIP ${c.id} (no ${c.file})`); continue; }
    const code = fs.readFileSync(p, 'utf8');
    const body = JSON.stringify({ model: MODEL, prompt: PROMPT(c, code), stream: false,
      options: { temperature: 0, num_predict: 400 } });
    process.stdout.write(`[${c.id}] querying ${MODEL} ... `);
    const t0 = Date.now();
    let text;
    try {
      const r = await fetch('http://localhost:11434/api/generate', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
      const j = await r.json();
      text = j.response || JSON.stringify(j);
    } catch (e) { text = 'ERROR: ' + e.message; }
    const secs = ((Date.now() - t0) / 1000).toFixed(1);
    console.log(`${secs}s`);
    out.push(`\n### 주장 ${c.id}\n\n**주장**: ${c.claim}\n\n**로컬 모델(${MODEL}) 응답**:\n\n\`\`\`\n${text.trim()}\n\`\`\`\n`);
  }
  fs.writeFileSync(path.join(DIR, 'local-model-responses.md'),
    `# 로컬 모델 교차검증 원본 응답\n\n> 모델: \`${MODEL}\` (ollama, localhost) · 외부 전송 없음\n${out.join('\n')}`);
  console.log('\n-> qa/xval-bundle/local-model-responses.md');
})();
