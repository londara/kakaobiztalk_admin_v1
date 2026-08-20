# A2-02 사전 조사 — 기존 스키마로 아웃박스를 대신할 수 있는가

> **Date**: 2026-08-19 · **Role**: `legacy-analyst`
> **계기 / trigger**: PM 질의 — *"why need A2-02 outbox DDL? because I have database connect and existing table."*
> **결론**: **질의가 옳았다.** ADR-ATK-023 이 요구한 DDL 두 건 중 **한 건은 불필요하다.**
> 나머지 한 건은 여전히 필요하지만, 범위가 설계보다 **훨씬 작다.**
>
> **Conclusion**: the challenge was **correct**. One of the two DDL items ADR-ATK-023 asked for is
> **unnecessary**. The other is still needed, but its scope is **much smaller** than designed.

---

## 1. RISK-A06 점검 결과 — PASS

RISK-A06 은 구체적 점검을 요구했다: *"`KKB_ADMIN_SEND_HIS` 를 `SELECT *` 로 읽는 레거시 소비자가
없는지 확인한다 — 위치 기반이나 와일드카드 읽기는 컬럼 추가가 보이지 않는 유일한 예외다."*

**전수 조사 결과, 소비자는 둘뿐이다.**

| 소비자 | 종류 | 컬럼 참조 방식 |
|--------|------|---------------|
| `IDO.KKB_ADMIN_SEND_HIS_C001` | INSERT | 명시 — `(IS_CD, SERIALNUM, SEND_ID, SEND_NM, RGDT)` |
| `IDO.KKB_ADMIN_SEND_HIS_L001` | SELECT | 명시 — `SERIALNUM, SEND_NM, RGDT, IS_CD` |

`SELECT *` **0건**, 위치 기반 읽기 **0건**. 컬럼을 추가해도 두 소비자 모두 영향을 받지 않는다.
**공유 테이블에 대한 가산 DDL 은 안전하다** — 이 축에 한해서는.

`SELECT *`: **none**. Positional reads: **none**. An added column is invisible to both consumers.

## 2. 그런데 그 컬럼은 애초에 필요하지 않다 / but that column is not needed anyway

ADR-ATK-023 은 `KKB_ADMIN_SEND_HIS` 에 **상태 컬럼**을 추가하려 했다. 조사 결과 **상태는 이미
존재한다** — 다른 표에, 그리고 **더 올바른 단위로**.

`IDO.KKB_ADMIN_SEND_HIS_L001` 이 그것을 드러낸다:

```sql
SEND_HIS AS A LEFT OUTER JOIN
(SELECT STATUS, SERIALNUM, ID, MSG, decrypt(PHONE) AS PHONE FROM KKO_MSG_LOG) AS B
  ON A.SERIALNUM = B.SERIALNUM
...
, count(CASE WHEN STATUS = '3' THEN 1 END) AS AT_SCS_CNT
, count(CASE WHEN STATUS != '3' THEN 1 END) AS AT_FT_CNT
```

`IDO.KKO_MSG_LOG_L001` 이 컬럼을 더 보여준다:

| 컬럼 | 의미 |
|------|------|
| `MSGKEY` | 메시지 키 |
| `STATUS` | 전달 상태 (`'3'` = 성공) |
| `RSLT`, `MSG_RSLT` | 벤더 결과 코드 |
| `CALLBACK` | 발신번호 — **암호화 저장** (`decrypt(CALLBACK)`) |
| `PHONE` | 수신번호 — **암호화 저장** (`decrypt(PHONE)`) |
| `REQDATE` / `SENTDATE` / `REPORTDATE` | 요청 / 발송 / 수신결과 시각 |
| `SERIALNUM` | `KKB_ADMIN_SEND_HIS.SERIALNUM` 과 조인되는 상관 키 |

**`KKO_MSG_LOG` 는 이미 아웃박스의 모양을 하고 있다** — 메시지 한 건에 한 행, 상태 기계
(`REQDATE → SENTDATE → REPORTDATE`), 벤더 결과 코드, 저장 시 암호화된 PII.

`KKB_ADMIN_SEND_HIS` 는 **한 행이 발송 <b>행위</b> 하나**다(누가 눌렀는가: `SEND_ID`, `SEND_NM`).
상태를 여기에 두면 단위가 어긋난다 — 한 번의 발송 행위에 메시지 수천 건이 딸리는데 상태는 하나뿐이
된다. **상태는 이미 올바른 단위에 있다.**

## 3. 그렇다면 아웃박스는 왜 여전히 필요한가 / so why is an outbox still needed

**`KKO_MSG_LOG` 에 쓰는 주체가 우리가 아니기 때문이다.**

전수 조사: `INSERT INTO KKO_MSG_LOG` 또는 `UPDATE KKO_MSG_LOG` **0건**. IRIS_ADMIN 은 이 표를
**읽기만 한다**. 쓰는 것은 실제 전달을 수행하는 메시지 게이트웨이다.

An exhaustive search finds **no** `INSERT INTO KKO_MSG_LOG` and **no** `UPDATE KKO_MSG_LOG`. IRIS_ADMIN
only **reads** it; the message gateway writes it.

그래서 `KKO_MSG_LOG` 는 **게이트웨이가 접수한 뒤**의 이야기만 한다. 아웃박스가 덮으려는 구간은 그
앞이다:

```
[우리가 보내기로 결정] ──?── [게이트웨이가 접수] ── KKO_MSG_LOG 에 행이 생김 ──> 전달
        └──────── 이 구간에는 아무 기록도 없다 ────────┘
                  no record exists across this gap
```

이 구간에서 프로세스가 죽거나 호출이 타임아웃되면 **아무 데도 흔적이 없다.** `KKO_MSG_LOG` 에는
행이 없고(게이트웨이가 못 받았을 수 있으므로), `KKB_ADMIN_SEND_HIS` 에는 "눌렀다"는 사실만 있다.
재시도해도 되는지 알 수 없다 — 이것이 아웃박스가 존재하는 이유의 전부다.

**따라서 아웃박스는 필요하되, 설계보다 훨씬 좁다.** `STATUS`·`RSLT`·`REPORTDATE` 를 복제할 이유가
없다 — 접수 이후는 게이트웨이가 이미 기록한다. 아웃박스는 **접수 여부가 확정될 때까지만** 살면 된다.

## 4. 새 결함 — D-A38 (High)

`SERIALNUM` 이 발송 이력 화면의 조인 키인데, 레거시의 `tran_id` 에는 **날짜 성분이 없다.**

| 경로 | 생성식 | 출처 |
|------|--------|------|
| 단건 | `"33" + hh24miss` | `biztalk_admin_50_s001_act.jsp:114` |
| 다건 | `hh24miss + apiNumber++` | 동 파일 :172 |

`hh24miss` 는 **매일 반복된다.** 그리고 `KKB_ADMIN_SEND_HIS.SERIALNUM = tran_id` 이며
`IDO.KKB_ADMIN_SEND_HIS_L001` 은 `RGDT BETWEEN :START_DT AND :END_DT` 로 **여러 날을 한 번에**
조회한 뒤 `A.SERIALNUM = B.SERIALNUM` 으로 조인한다.

**결과**: 조회 범위가 하루를 넘고 같은 초에 발송이 있었다면, 그 조인은 **카테시안 곱**이 된다.
`count(1) AS TOTAL_CNT`, `AT_SCS_CNT`, `AT_FT_CNT` 가 **부풀려진다.**

**Consequence**: whenever the query range spans more than one day and two sends share a second-of-day, the
join becomes a **cartesian product** and the history screen's counts are **inflated**.

이것은 중복 ID 가 "지저분하다"는 문제가 아니다. **발송 통계 화면이 틀린 수를 보고한다**는 문제이며,
관측 가능한 증상이 있다. D-A25(순번 충돌)의 결과를 구체적으로 특정한 것이므로 D-A25 의 하위가 아니라
**별도 결함**으로 올린다 — 영향 화면이 다르다(60 발송이력).

우리 쪽 `TranIdGenerator` 는 `환경(1) + yyMMdd(6) + base36 순번(3)` 이므로 날짜 성분을 갖고 이
결함을 재현하지 않는다. **그러나 마이그레이션 이후에도 레거시가 남긴 기존 행에는 이 문제가 남는다** —
이력 조회는 과거 데이터를 계속 읽기 때문이다. 조회 측 완화(날짜와 함께 조인)가 필요한지는 A2-14
(화면 50 폐기) 에서 결정해야 한다.

## 5. A2-02 에 대한 권고 / recommendation

| ADR-ATK-023 원안 | 권고 |
|------------------|------|
| `KKB_ADMIN_SEND_HIS` 에 상태 컬럼 추가 | ❌ **철회.** 상태는 `KKO_MSG_LOG` 에 이미, 더 올바른 단위로 있다. **RISK-A06 이 소멸한다** — 공유 테이블을 건드리지 않으므로 |
| `KKB_ATK_SEND_OUTBOX` 신규 테이블 | ✅ **유지, 범위 축소.** 접수 확정까지만 사는 표. `STATUS`/`RSLT`/`REPORTDATE` 복제 불필요 |

**이 권고가 받아들여지면 A2-02 는 공유 테이블을 전혀 건드리지 않는다.** 그러면 G1 이 판단해야 할
항목에서 RISK-A06 이 빠지고, 남는 것은 "새 표 하나를 만들어도 되는가" 뿐이다 — 이는 발신번호
슬라이스에서 **이미 승인된 선례**(CONFLICT-S01)와 같은 범위다.

If accepted, A2-02 touches **no shared table at all**. RISK-A06 then disappears from what G1 must weigh,
leaving only "may we create one new table" — the same scope **already granted** for the 발신번호 slice.

## 6. 확인하지 못한 것 / what this analysis cannot confirm

레거시 소스만으로 판단했다. 데이터베이스에 접속하지 않았다.

| 항목 | 왜 확인 못 했나 |
|------|----------------|
| `KKO_MSG_LOG` 의 실제 DDL — 컬럼 타입, 인덱스, 보존 기간 | 질의로부터 역추론했을 뿐이다 |
| 게이트웨이가 `SERIALNUM` 을 **항상** 채우는지 | `LEFT OUTER JOIN` 인 것은 채우지 않는 경우가 있음을 시사한다 |
| `STATUS` 값의 전체 집합 (`'3'` 외) | 질의는 `= '3'` 과 `!= '3'` 만 쓴다 |
| 우리가 `KKO_MSG_LOG` 를 **읽을** 권한이 있는지 | 별도 소유 시스템일 수 있다 |
| IRIS_ADMIN 밖의 소비자 | 이 저장소만 조사했다 |

**PM 이 실제 접속을 승인하면** 위 다섯 항목을 직접 확인할 수 있고, 그때 이 권고를 확정할 수 있다.
확인 전까지 §5 는 **레거시 소스에 근거한 권고**이지 검증된 사실이 아니다.
