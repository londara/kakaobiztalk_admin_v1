# ADR-SND-020 — How 등록 and 삭제 are presented, given "follow old logic"

> **Status**: ACCEPTED
> **Date**: 2026-08-20
> **Slice**: 발신번호 (screens 10/12/13) — Sprint S2a
> **Decides**: the container for legacy popups 12 and 13; the post-write refresh; where the delete set is enumerated
> **Requirements**: FR-SNDC-012, FR-SNDC-013, FR-SNDC-014, FR-SNDD-007, FR-SNDD-009, FR-SNDD-010, FR-SND-012, NFR-USE-D01
> **Related**: [ADR-SND-017](ADR-SND-017-senderno-lifecycle.md), [ADR-INST-015](ADR-INST-015-atk-credential-handling.md), [ADR-SND-021](ADR-SND-021-barred-number-list.md)

---

## Context

The PM directive for this sprint is **"follow old logic"** ([questions-log §41](../../requirements/questions-log.md)). The legacy reached both write operations by opening a second browser window:

```javascript
// biztalk_admin_10.js
$("#btn_register").click(function() {
    jex.openPopup("biztalk_admin_12", { IS_CD: $("#IS_CD").val() }, ...);
});
$("#btn_delete").click(function() {
    var checked = _gu.getCheckData(grid1, "DP_NO");      // the displayed value — D-S1
    jex.openPopup("biztalk_admin_13", { IS_CD: ..., DP_NO: checked.join(",") }, ...);
});
// …and on success, inside the popup:
opener.getDat();
```

Three properties of that arrangement are **logic** and must be preserved: the form cannot open without the list's `IS_CD`; the form owns the 이용기관 as read-only; and completing it refreshes the list behind it. One property is **transport**: it happened to be a separate `window`.

Sprint S1 left both buttons rendered and disabled, so the question is now unavoidable and has to be answered before any of FR-SNDC-012…014 or FR-SNDD-007/009/010 can be implemented.

There is also a new constraint the legacy never had. Sprint S1 introduced server-side paging to fix D-S14, and paging separates *selected* from *visible* — the legacy grid held the whole result set in the browser, so a checked row was always on screen. Wherever the delete confirmation lives, it must be able to enumerate rows that are no longer rendered (FR-SNDD-009).

## Decision

**A modal dialog in the same document, carrying the legacy field layout verbatim.**

- `SenderNumberRegisterDialog` — legacy screen 12's fields in legacy order (이용기관코드, 이용기관명 both read-only; 발신번호; 설명; 사유) with its three stated rules, and [등록] [닫기].
- `SenderNumberDeleteDialog` — legacy screen 13's layout: 이용기관코드 read-only, the 삭제번호 enumeration, 사유, and [삭제] [닫기].
- The list owns the selection; the delete dialog receives **the whole selected set as `SenderNumberRef` values**, never the rendered numbers, and renders every one of them.
- "Refresh the opener" becomes a query invalidation on the list's key (FR-SND-012), which also clears the selection.

The precedent is `InstitutionEditDialog` from the 이용기관관리 slice; this slice adds no new UI mechanism.

## Alternatives considered

| Option | Preserves the legacy logic? | Verdict |
|--------|----------------------------|---------|
| **A — `window.open` popup, literal parity** | The *shape*, yes. The **properties, no.** There is no `opener.getDat()` to call — the list's state lives in a React query cache in the first document, not in a global function. Passing the selection means serialising refs through a URL or `postMessage`; popup blockers make the primary action of the screen conditional on a browser setting; and focus, Esc and screen-reader behaviour all have to be rebuilt | **Rejected.** It reproduces the transport and loses all three properties that were actually load-bearing |
| **B — modal dialog in the same document (chosen)** | All three, directly: the dialog cannot be opened without the institution the list holds, the fields are read-only from it, and the refresh is a cache invalidation | **Accepted** |
| **C — a separate route (`/sender-numbers/new`, `/sender-numbers/delete`)** | Registration would work. **Deletion would not**: navigating away discards the selection, which *is* the delete operation's input. Recovering it means putting sender numbers in a URL — precisely what [SenderNumberPage](../../../src/main/frontend/src/features/biztalk/SenderNumberPage.tsx#L37-L43) deliberately refuses | **Rejected.** It breaks the operation whose defect this slice exists to fix |
| **D — inline panel expanded inside the grid** | No. Screen 12's field set does not fit a row, and NFR-USE-D01's "two steps" becomes ambiguous — an inline form makes it unclear whether the operator has confirmed anything | **Rejected** |

## Consequences

**Positive.**
- The one guarantee the legacy got from the runtime — "the popup cannot open without an `IS_CD`" — becomes a typed prop rather than a convention, so FR-SNDC-012 is structural.
- The delete dialog is the natural home for FR-SNDD-009's enumeration: it holds the selected set as data, so listing all of it and deleting exactly it are the same fact rather than two facts that have to agree.
- Testable without a browser popup harness — the S1 dialog tests (`InstitutionEditDialog.test.tsx`) already establish the pattern.

**Negative.**
- Modal accessibility is on us: focus trap, `aria-modal`, Esc-to-닫기, and focus returned to the invoking button. Not free, but bounded, and the S1 dialog already carries it.
- A modal cannot be left open next to the list for reference. The legacy popup could. Nobody appears to have used it that way — the popup's own 닫기 discarded everything.

**Neutral but worth stating.** This ADR interprets a PM directive rather than resolving a technical unknown. That interpretation is recorded in [questions-log §41](../../requirements/questions-log.md) as a table of what the directive governs and what it does not, so the reasoning is auditable and reversible without re-reading four legacy files.

## Verification

| Check | Test |
|-------|------|
| 등록 unavailable with no institution; 삭제 unavailable with no selection | TC-S002-24, TC-S004-23 |
| The form's 이용기관 cannot be altered; a body-supplied institution is refused | TC-S002-25 |
| Rejection keeps the form open with input intact | TC-S002-27 |
| The confirmation enumerates a selection made on another page | TC-S004-21 |
| The enumerated set equals the deleted set | TC-S004-22 |
| The list re-queries at the current page after a write | TC-S002-28, TC-S004-26 |
| Keyboard and screen-reader operation of both dialogs | E2E accessibility pass, per the S1 dialog baseline |
