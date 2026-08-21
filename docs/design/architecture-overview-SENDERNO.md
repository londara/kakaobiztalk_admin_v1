# Architecture Overview — 발신번호 (Sender Number Management)

> **Version**: 1.1 — write-path pass, 2026-08-20 (§4.3 added)
> **Date**: 2026-08-17 (v1.0) · 2026-08-20 (v1.1)
> **Predecessor**: [REQUIREMENTS-SPEC-SENDERNO.md](../requirements/REQUIREMENTS-SPEC-SENDERNO.md) v1.1 — **G1 APPROVED 2026-08-21**
> **Siblings**: [architecture-overview.md](architecture-overview.md) (문자내역), [architecture-overview-LOGIN.md](architecture-overview-LOGIN.md), [architecture-overview-INSTITUTION.md](architecture-overview-INSTITUTION.md)
> **ADRs**: [ADR-SND-017](adr/ADR-SND-017-senderno-lifecycle.md), [ADR-SND-018](adr/ADR-SND-018-encrypted-number-uniqueness.md), [ADR-SND-019](adr/ADR-SND-019-senderno-read-audit.md), [ADR-SND-020](adr/ADR-SND-020-write-dialog-presentation.md), [ADR-SND-021](adr/ADR-SND-021-barred-number-list.md)

---

## 1. Scope

Four legacy screens collapse into one React screen with three dialogs, over a single Spring Boot service package. The stack is settled by [ADR-001](adr/ADR-001-tech-stack.md) and not revisited: Java 17 / Spring Boot 3.x / MyBatis / React, PostgreSQL `BIZTALK_DB`.

What is new in this slice is not technology. It is that **this module writes a table two other applications read and write**, and the correctness of the whole slice depends on respecting that.

## 2. The shared-table picture

This is the fact that shapes every decision below.

```mermaid
flowchart LR
  subgraph new["IRIS BizTalk Portal — this project"]
    api["SenderNumberController"] --> svc["SenderNumberService"]
    svc --> map["SenderNumberMapper"]
  end

  subgraph legacy["Legacy applications — not ours"]
    aoa["AOA_ADMIN<br/>same 4 screens<br/>target: BIZTALK_DB"]
    kko["KAKAOTALK send runtime<br/>5 of 6 send paths validate<br/>target: BIZ_DB"]
  end

  subgraph db[("BIZTALK_DB")]
    ldgr[["KKB_DPNO_LDGR<br/>live numbers"]]
    arcv[["KKB_DPNO_ARCV<br/>NEW — deleted rows"]]
    his[["KKB_DPNO_HIS<br/>append-only events"]]
    ftis[["FT_FTIS_INFO<br/>institution master, read-only"]]
  end

  map --> ldgr
  map --> arcv
  map --> his
  map -.read.-> ftis
  aoa --> ldgr
  aoa --> his
  kko -.->|"select dp_no … where decrypt(dp_no)=:n<br/>absent ⇒ send rejected"| ldgr

  style arcv stroke-dasharray: 4 4
  style kko stroke-width:2px
```

Three consequences, each of which drove an ADR:

1. **`KAKAOTALK` treats presence in `KKB_DPNO_LDGR` as authorization to send**, and reads no status column. Deletion must therefore remove the row, not mark it — [ADR-SND-017](adr/ADR-SND-017-senderno-lifecycle.md).
2. **`AOA_ADMIN` writes the same table**, so no application-level rule this project enforces is a global rule. Uniqueness must live in the database — [ADR-SND-018](adr/ADR-SND-018-encrypted-number-uniqueness.md).
3. **`KAKAOTALK` declares `BIZ_DB` where the admin consoles declare `BIZTALK_DB`.** These are datasource aliases; whether they resolve to the same physical database cannot be determined from source. If they do not, the send path is validating against a *replica or a different instance* and every conclusion above needs revisiting. **This is verified before Sprint S2 starts** — RISK-S01.

## 3. Component structure

Follows the established package layout (`api` / `domain` / `infra.db`) and reuses the cross-cutting components delivered by earlier slices without modification.

```mermaid
flowchart TD
  subgraph web["React SPA"]
    ui["SenderNumberScreen<br/>+ Register / Detail / Delete dialogs"]
  end

  subgraph api["com.webcash.iris.biztalk.api"]
    ctl["SenderNumberController"]
  end

  subgraph domain["com.webcash.iris.biztalk.domain"]
    svc["SenderNumberService<br/>read path"]
    wsvc["SenderNumberWriteService<br/>register · delete (S2a)"]
    val["SenderNumberValidator<br/>format, special numbers, length"]
    barred["BarredNumbers<br/>loaded resource (S2a)"]
    ref["SenderNumberRef<br/>opaque identity"]
    row["SenderNumberRow / SenderNumberCriteria"]
  end

  subgraph infra["com.webcash.iris.biztalk.infra.db"]
    mapper["SenderNumberMapper"]
  end

  subgraph shared["reused unchanged"]
    tenant["TenantContext<br/>common.tenant"]
    audit["AuditService<br/>common.audit"]
    paged["PagedResult<br/>biztalk.domain"]
    inst["InstitutionService<br/>biztalk.domain"]
  end

  ui --> ctl --> svc
  ctl --> wsvc
  wsvc --> val
  val --> barred
  svc --> ref
  wsvc --> ref
  svc --> mapper
  wsvc --> mapper
  svc --> tenant
  wsvc --> tenant
  svc --> audit
  wsvc --> audit
  wsvc -.->|기관명 only| inst
  mapper --> row
  svc --> paged
```

**Read and write are separate services (v1.1).** `SenderNumberService` keeps the list; `SenderNumberWriteService` owns register and delete. This follows the `InstitutionService` / `InstitutionWriteService` split from the 이용기관관리 slice, and the reason is the same in both: the read path is `@Transactional(readOnly = true)` and the write path needs a real transaction with per-statement result checks (D-S7). Putting both behind one class means the transaction annotation stops describing the class, which is how a read-only default ends up silently applied to a write — or, worse, how a write's rollback boundary ends up wider than anyone intended.

`BarredNumbers` is the loaded-resource holder behind the validator ([ADR-SND-021](adr/ADR-SND-021-barred-number-list.md)). It is a separate component rather than a static field so that its **fail-loud startup validation** has somewhere to live — a `Set.of(...)` cannot refuse to exist.

**Nothing in `shared` is modified.** `TenantContext.effectiveInstitutionCode()` already implements exactly what FR-AZ-D03 requires — an operator may name an institution, a client-company user's requested value is ignored in favour of their own. `AuditService` already writes `REQUIRES_NEW` and already records denials. This slice consumes both as they are; its authorization requirements are new only in that the legacy had none.

**`InstitutionService` is consumed for 기관명 only.** D-S18 exists because the legacy registration popup called the institution *detail* service and pulled 인증키 into the browser. The new dependency is deliberately narrow — FR-SNDC-002 — and the response shape is asserted by TC-S002-12 rather than left to convention.

## 4. Request flows

### 4.1 List (UC-SND-001)

```mermaid
sequenceDiagram
  participant U as Operator
  participant C as SenderNumberController
  participant S as SenderNumberService
  participant T as TenantContext
  participant M as SenderNumberMapper
  participant A as AuditService

  U->>C: GET /api/senderno?institution=&page=
  C->>S: list(criteria)
  S->>T: require().effectiveInstitutionCode(requested)
  alt not entitled
    T-->>S: scope mismatch
    S->>A: record(senderno.list, DENIED)
    S-->>C: 403
  else entitled
    S->>M: findPage(scope, offset, limit)  %% ORDER BY RGDT DESC
    M-->>S: rows + total
    S->>A: record(senderno.list, SUCCESS, count)
    S-->>C: PagedResult<SenderNumberRow>
  end
```

Two things the legacy did not do: the scope is resolved from the session before the query is built (FR-AZ-D03), and the query is ordered and bounded (FR-SND-003/004). The audit write carries a count, never the numbers — [ADR-SND-019](adr/ADR-SND-019-senderno-read-audit.md).

### 4.2 Delete (UC-SND-004) — the flow that was broken

```mermaid
sequenceDiagram
  participant U as Operator
  participant S as SenderNumberService
  participant M as SenderNumberMapper
  participant DB as BIZTALK_DB

  U->>S: delete([ref…], reason)
  Note over S: one transaction — ADR-002
  loop each ref
    S->>M: findLive(ref)
    alt no live row
      M-->>S: empty
      S-->>U: 409 — explicit failure, never "success"
    else
      S->>M: archive(row, actor, reason)
      M->>DB: INSERT KKB_DPNO_ARCV
      S->>M: deleteLive(ref)
      M->>DB: DELETE KKB_DPNO_LDGR
      S->>M: history(ref, 'D', reason)
      M->>DB: INSERT KKB_DPNO_HIS
    end
  end
  Note over S: commit — all or none
```

The row is located by `ref` (opaque, [ADR-SND-018](adr/ADR-SND-018-encrypted-number-uniqueness.md)), not by a display string — this is the structural fix for D-S1. The zero-match case is an explicit 409 (FR-SNDD-002); the whole batch is one transaction (FR-SNDD-005); each number gets its own history row built from that iteration's value, not from the request payload (FR-SNDD-004, fixing D-S5).

Once the row leaves `KKB_DPNO_LDGR`, `KAKAOTALK`'s existing check rejects the number with no change on its side.

**Where the deleted set comes from (added v1.1).** The `[ref…]` entering this flow is the list screen's selection, which under server-side paging may include rows not currently rendered. Two separate guarantees cover that, and they are not interchangeable — see the note under T-T8 in the [threat model](threat-model-SENDERNO.md):

- **Client side, FR-SNDD-009**: the confirmation dialog enumerates every selected number, whichever page it was selected on, and the request carries exactly that set. This protects the operator from a stale selection.
- **Server side, T-T8**: each ref is re-resolved against the session's scope inside the transaction. `SenderNumberRef` is an identifier, not a capability — a crafted set naming another institution's rows fails the same way a non-existent ref does.

### 4.3 Register (UC-SND-002) — added v1.1

```mermaid
sequenceDiagram
  participant U as Operator
  participant S as SenderNumberService
  participant V as SenderNumberValidator
  participant I as InstitutionService
  participant M as SenderNumberMapper
  participant DB as BIZTALK_DB
  participant A as AuditService

  U->>S: openRegister(institution from the list)
  S->>I: nameOf(institution)
  I-->>S: 기관코드 + 기관명 only  %% never the detail service — D-S18
  U->>S: register(number, description, reason)
  Note over S: one transaction — ADR-002
  S->>V: validate(number)
  alt rejected
    V-->>S: NOT_NUMERIC / BARRED / TOO_SHORT / …
    S-->>U: 400 naming the field and the rule
  else accepted
    S->>M: findLiveAnywhere(number)   %% all institutions — D-S9
    alt already live
      S-->>U: 409 duplicate
    else
      S->>M: insertLedger(row, actor from session)
      M->>DB: INSERT KKB_DPNO_LDGR
      S->>M: history(number, 'C', reason)
      M->>DB: INSERT KKB_DPNO_HIS
      S->>A: record(senderno.register, SUCCESS)
      Note over S: commit — history failure fails the registration
    end
  end
```

Four legacy properties are inverted here, and each maps to a defect: validation happens on the server and every branch of it is reachable (D-S11, D-S13); the barred-number check exists at all, from loaded configuration (D-S12, [ADR-SND-021](adr/ADR-SND-021-barred-number-list.md)); the uniqueness lookup spans **all** institutions (D-S9); and the result of *each* statement is checked rather than the previous one's (D-S7). The institution never comes from the request body (FR-SNDC-012) — the write-path twin of D-S3.

**The uniqueness check reads live rows only.** That single choice is what makes FR-SNDD-008 (re-register a previously deleted number) fall out of the design instead of needing a special case: an archived number is not in the ledger, so it is not a duplicate. It is the same property that makes `KAKAOTALK` reject it.

## 5. Data model changes

| Object | Change | Basis |
|--------|--------|-------|
| `KKB_DPNO_ARCV` | **New table.** Full copy of the ledger columns plus `DEL_DT`, `DEL_ID`, `DEL_NM`, `REASON` | ADR-SND-017 |
| `KKB_DPNO_LDGR` | **Unique index** on the number — form settled by spike S1-01 | ADR-SND-018 |
| `KKB_DPNO_LDGR.DP_NO_IDX` | **Conditional** — added only if the spike shows `ENCRYPT` is non-deterministic | ADR-SND-018 |
| `KKB_DPNO_HIS` | Unchanged. `ACN` gains a third value for description edits (data, not schema) | FR-SNDU-004 |
| `FT_FTIS_INFO` | Read-only | — |

All DDL is additive. `KKB_DPNO_LDGR` is never altered in a way that changes what an existing reader sees — the basis on which CONFLICT-S01 is put to G1.

## 6. What this slice deliberately does not build

| Not built | Why |
|-----------|-----|
| Ownership verification (ARS/SMS OTP) | PM ruling AMB-S01; RESIDUAL-S01. The `COOCON_SMS` integration is not wired |
| 수수료 tab | No contract, no query, no behaviour to port (§2.7 of the spec) |
| Authentication | Inherited from the 로그인 slice unchanged |
| A cap on 발신번호 per institution *(v1.1)* | AMB-S07 stays open with working assumption A (no cap). Adding one later is a validation rule over a count query — no schema, no migration |
| Cascade from institution deletion *(v1.1)* | PM ruling AMB-S08 / CONST-BIZ-D04. Sending is barred one level up by institution state (ADR-INST-014), so moving the rows buys nothing. Legacy `KKB_DPNO_LDGR_D002` is not ported. Residual: RISK-S14 |
| A `window.open` popup for 등록/삭제 *(v1.1)* | [ADR-SND-020](adr/ADR-SND-020-write-dialog-presentation.md). The popup was the legacy's transport; its three load-bearing properties (institution supplied by the list, read-only on the form, opener refreshed on success) are preserved by a modal dialog and lost by a real popup |
| Changes to `KAKAOTALK` or `AOA_ADMIN` | Outside the project boundary. Their coexistence is handled by design and tracked as RISK-S01/S03/S05 |
