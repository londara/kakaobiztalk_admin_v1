# A2-05 사전 조사 — 벤더 전송 계층은 어떻게 동작하는가

> **Date**: 2026-08-19 · **Role**: `legacy-analyst`
> **계기**: PM 질의 — *"can implement sending?"*
> **결론**: **spike A1-02 의 상당 부분이 레거시 소스로 해결된다.** 벤더에게 물어야 할 것이
> 줄었다. 다만 설계(ADR-ATK-025)가 전제하지 않았던 것이 셋 드러났다.

---

## 1. 전송 경로 전체 / the full transport path

레거시가 `imoCon.execute(imoIn)` 한 줄로 감춰 두었던 것을 펼치면 이렇다.

```
biztalk_admin_50_s001_act.jsp
  └─ IMO ADV_KKO_AT_SEND  (rule: 계약 필드들)
       └─ target COOCON_ALERT
            └─ resource user/jex.impl.OAuthHTTPConnection   ← 커스텀 OAuth 전송
                 ├─ [1] 토큰 확인·발급   IMO OAUTH_TOKEN_ISSUE → POST <base>/oauth/2.0/token
                 └─ [2] 본 발송         POST <base>/advising/kakao/at_send
                          Authorization: <token_type> <access_token>
                          Content-Type: application/json
```

| 항목 | 값 | 출처 |
|------|-----|------|
| 기본 URL | `FINChannel.getChannelInfo("KAKAOBIZTALK", JEX.id).getRsrc()` — **DB 에서 읽는다** | `OAuthHTTPConnection:53-57` |
| 경로 접미 | `JexSystemConfig.get("oauth", <IMO id>)` | 동 :57 |
| 토큰 발급 | `POST <base>/oauth/2.0/token`, 요청 필드는 **`is_cd` 하나** | `IMO.OAUTH_TOKEN_ISSUE` |
| 토큰 응답 | `access_token`(200), `token_type`(100), `expires_in`(15, 숫자), `scope`(100), `error`(200), `error_description`(500) | 동상 |
| 인증 헤더 | `Authorization: <token_type> <access_token>` | `OAuthHTTPConnection:79` |
| 본문 | 마샬링된 IMO 바이트 (`setBytesData`), `Content-Type: application/json`, charset utf8 | 동 :145, `jex.iris_admin.xml:134-145` |
| 타임아웃 | connect / read / wait 모두 **60000 ms** | `jex.iris_admin.xml:135-137` |
| 발송 계약 | `RSMS` 단일 필드(길이 선언 없음) → 응답 `CSTM_RSMS` | `IMO.ADV_KKO_AT_SEND2` |

## 2. 설계가 전제하지 않았던 것 셋 / three things the design did not assume

### ① 인증이 이용기관별 OAuth 다 / authentication is per-institution OAuth

ADR-ATK-025 는 발신프로필키만 자격증명으로 다뤘다. 실제로는 **기관별 OAuth 액세스 토큰**이 더
필요하고, 그 토큰은 만료(`expires_in`)되므로 캐시와 재발급이 필요하다. `SenderProfileKeyResolver`
하나로는 자격증명 이야기가 끝나지 않는다.

ADR-ATK-025 treated only the sender profile key as credential material. In reality a **per-institution
OAuth access token** is also required, it expires, and so caching plus re-issue is part of the send path.

### ② OAuth 클라이언트 자격증명이 DB 에 있다 / the OAuth client credential lives in a database

토큰 발급 요청이 실어 보내는 것은 `is_cd` **하나뿐**이다. 클라이언트 식별·비밀은 IMO 계약에 없고,
`FINChannel.getChannelInfo("KAKAOBIZTALK", …)` 가 읽는 **채널 테이블**에서 온다.

즉 이 시스템의 벤더 자격증명은 **두 곳**에 있다: 발신프로필키(레거시는 JSP 하드코딩 — D-A24)와
OAuth 클라이언트 자격증명(DB 채널 테이블). 후자는 이 슬라이스가 아직 조사하지 않은 영역이며,
A2-05 를 구현하려면 그 테이블의 소유자와 스키마를 알아야 한다. **새 미해결 항목으로 올린다
(AMB-A10).**

The token request carries **only** `is_cd`; the client identity and secret are not in the IMO contract but
come from the **channel table** read by `FINChannel`. So vendor credentials live in **two** places, and the
second is unexamined. Raised as **AMB-A10**.

### ③ 60초 타임아웃은 아웃박스 설계에 직접 영향을 준다

connect·read 모두 60초다. ADR-ATK-023 이 "재시도해도 되는가" 를 *증명 가능한 미전달*로 갈랐는데,
**60초 read 타임아웃은 전달 여부를 알 수 없는 구간을 60초까지 만든다.** 디스패처의 클레임 시간과
`@Scheduled` 주기가 그보다 짧으면 같은 행을 두 번 보내려 시도할 수 있다. 클레임 만료 시간은
**타임아웃보다 길게** 잡아야 한다 — 설계 문서에 이 제약이 없었다.

Both connect and read timeouts are 60 s, which creates an up-to-60-second window in which delivery is
unknown. The dispatcher's claim expiry must therefore exceed the read timeout, a constraint the design did
not state.

## 3. 새 결함 — D-A39 (High): OAuth 토큰 캐시에 경쟁 조건이 있다

`jex/impl/OAuthToken.java` 는 `HashMap<String, OAuthTokenInfo> tokenMap` 을 **동기화 없이** 읽는다.

| 메서드 | 락 | 동작 |
|--------|-----|------|
| `issueToken(iscd)` | 취함 | `tokenMap.put(...)` — **쓴다** |
| `getAccessToken(iscd)` | **없음** | `tokenMap.containsKey` / `get` — 읽는다 |
| `isValidToken(iscd)` | **없음** | 동상 |
| `getTokenType`·`getScope`·`getExpireTime` 등 | **없음** | 동상 |

`ReentrantLock tokenLock` 이 있는데 **읽는 쪽이 그것을 취하지 않는다.** 플레인 `HashMap` 을
한쪽이 쓰면서 다른 쪽이 읽으면 가시성 보장이 없고, 리해시 중 읽기는 잘못된 값이나 무한 루프로
이어질 수 있다.

**결과**: 여러 기관이 동시에 발송할 때 토큰이 뒤섞이거나 누락될 수 있다. 토큰이 **다른 기관의
것으로** 사용되면 발송이 잘못된 기관 명의로 나간다 — 조용히. 레거시 D-A24(모든 기관 한 키)와 같은
결과를 경쟁 조건으로 재현하는 셈이다.

**Consequence**: with several institutions sending concurrently, tokens can be missed or crossed. A token
used under the **wrong institution** sends in the wrong institution's name, silently — reproducing D-A24's
outcome by way of a race.

우리 구현은 `ConcurrentHashMap` + 기관별 원자적 재발급으로 이 형태를 재현하지 않는다.

## 4. 남는 미확인 / what remains unverified

| 항목 | 상태 |
|------|------|
| `RSMS` 마샬링의 정확한 형태 — `{"RSMS": "<json 문자열>"}` 인지 중첩 객체인지 | **미확인.** jex IMO JSON 마샬러가 결정하며 그 코드는 여기 없다 |
| `FINChannel` 채널 테이블의 스키마·소유자 (AMB-A10) | **미확인** |
| `CSTM_RSMS` 응답 본문의 구조와 오류 코드 집합 | **미확인** |
| 벤더의 `(is_cd, tran_id)` 멱등성 (spike A1-03) | **미확인 — 재시도 안전성의 전제** |
| `expires_in` 의 단위 (초로 가정) | 관례상 초. 미확인 |

**따라서 A2-05 는 "구현 가능하지만 검증 불가" 다.** 위 다섯 중 첫 세 개는 벤더 문서나 실제 호출
없이는 확정할 수 없다. 코드는 그 경계를 한 곳(`CooconAlertClient`)에 모아 두고, 가정을 주석과
테스트로 고정한다 — 확정되면 한 곳만 바꾸면 되도록.

**A2-05 is therefore implementable but not verifiable.** The code confines that boundary to one class and
pins the assumptions in tests, so that confirmation changes exactly one place.
