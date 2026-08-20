package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.webcash.iris.biztalk.infra.db.InstitutionAdminMapper;
import com.webcash.iris.biztalk.infra.db.InstitutionAdminMapper.InstitutionEntity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link InstitutionService} 단위 테스트 — 결함 D-I5 회귀.
 * Unit tests for {@link InstitutionService} — defect D-I5 regression.
 *
 * // source: biztalk_admin_00.js — getData(), drawGrid()
 * // req: FR-INST-001, FR-INST-003, FR-INST-006, FR-ATK-002
 */
@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    /**
     * 합성된 인증키 표본 — 레거시 키와 같은 모양(20자 영숫자)이되 실제 값이 아니다.
     * A synthetic sample key: the shape of a legacy key (20 alphanumerics), not a real value.
     *
     * <p>여기에는 운영 화면에서 관찰된 값이 그대로 들어 있었다. 마스킹을 시험하는 데 실제
     * 자격증명은 필요하지 않으며, 저장소에 두면 그것 자체가 노출 경로다 — gitleaks L1 훅도
     * 이 값을 시크릿으로 탐지했다(SI2a-01, NFR-SEC-CRED-I01).</p>
     * <p>This held a value observed on a live screen. Testing the mask needs no real credential, and
     * keeping one in the repository is itself an exposure path — the gitleaks L1 hook flagged it as a
     * secret (SI2a-01, NFR-SEC-CRED-I01).</p>
     */
    private static final String RAW_KEY = "SAMPLEsampleSAMPLE01";

    @Mock private InstitutionAdminMapper mapper;
    @InjectMocks private InstitutionService service;

    private InstitutionEntity entity(String code, String name, String status) {
        return new InstitutionEntity(code, name, "COOCON_Business1", "1234567890",
                RAW_KEY, status, "설명", "20210401120000", "20260721133000");
    }

    private InstitutionSearchCriteria criteria() {
        return InstitutionSearchCriteria.of(null, null, 0, 20);
    }

    @Test
    @DisplayName("인증키가 마스킹되어 반환된다 / the 인증키 is returned masked")
        // req: FR-ATK-002, D-I5, TM-I003
    void masksAuthKey() {
        when(mapper.count(any())).thenReturn(1);
        when(mapper.search(any())).thenReturn(List.of(entity("K00001", "쿠콘_마이데이터사업1본부", "Y")));

        var result = service.search(criteria());

        // 레거시 목록은 전 기관의 인증키를 평문 컬럼으로 노출했다 — 화면 캡처 한 장이면
        // 모든 고객사 키가 함께 나간다.
        // The legacy list exposed every institution's key in plaintext: one screenshot took the
        // whole set with it.
        assertThat(result.rows()).singleElement()
                .extracting(InstitutionRow::authKeyMasked)
                .isEqualTo("****************LE01");
    }

    @Test
    @DisplayName("응답 어디에도 평문 인증키가 없다 / no plaintext 인증키 appears anywhere in the response")
        // req: FR-ATK-002, TM-I003
    void neverExposesPlaintextKey() {
        when(mapper.count(any())).thenReturn(1);
        when(mapper.search(any())).thenReturn(List.of(entity("K00001", "쿠콘", "Y")));

        var result = service.search(criteria());

        assertThat(result.rows().toString()).doesNotContain(RAW_KEY);
    }

    @Test
    @DisplayName("상태가 표시 라벨로 변환된다 / the status is converted to its display label")
        // req: FR-INST-006
    void mapsStatusLabel() {
        when(mapper.count(any())).thenReturn(2);
        when(mapper.search(any())).thenReturn(List.of(
                entity("K00001", "사용중", "Y"),
                entity("K00002", "중지됨", "N")));

        var result = service.search(criteria());

        assertThat(result.rows())
                .extracting(InstitutionRow::statusLabel)
                .containsExactly("사용", "미사용");
    }

    @Test
    @DisplayName("매핑되지 않는 상태는 원문 그대로 표시된다 / an unmapped status renders verbatim")
        // req: FR-INST-006
    void unmappedStatusRendersVerbatim() {
        when(mapper.count(any())).thenReturn(1);
        when(mapper.search(any())).thenReturn(List.of(entity("K00003", "이상값", "X")));

        var result = service.search(criteria());

        assertThat(result.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("X");
                    // 레거시는 'Y' 가 아닌 모든 값을 '미사용' 으로 표시해 데이터 이상을 감췄다.
                    // The legacy showed anything but 'Y' as 미사용, hiding the anomaly.
                    assertThat(row.statusLabel()).isEqualTo("X").isNotEqualTo("미사용");
                });
    }

    @Test
    @DisplayName("전체 건수가 페이지 크기와 무관하게 반환된다 / the total is independent of the page size")
        // req: FR-INST-003, D-I10
    void returnsTotalCountIndependentOfPage() {
        when(mapper.count(any())).thenReturn(55);
        when(mapper.search(any())).thenReturn(List.of(entity("K00001", "쿠콘", "Y")));

        var result = service.search(InstitutionSearchCriteria.of(null, null, 0, 10));

        // 레거시는 전량을 반환하고 클라이언트가 세었다. 서버 페이징에서는 전체 건수를
        // 별도 쿼리로 얻어야 페이지 수가 정확해진다.
        // The legacy returned everything and let the client count; server paging needs a
        // separate total for the page count to be right.
        assertThat(result.totalCount()).isEqualTo(55);
        assertThat(result.totalPages()).isEqualTo(6);
    }

    @Test
    @DisplayName("결과가 없으면 목록 쿼리를 실행하지 않는다 / no row query runs when the count is zero")
        // req: FR-INST-003
    void skipsRowQueryWhenEmpty() {
        when(mapper.count(any())).thenReturn(0);

        var result = service.search(criteria());

        assertThat(result.rows()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.isEmpty()).isTrue();
        verify(mapper, never()).search(any());
    }

    @Test
    @DisplayName("8개 그리드 컬럼이 모두 채워진다 / all eight grid columns are populated")
        // req: FR-INST-002
    void populatesAllGridColumns() {
        when(mapper.count(any())).thenReturn(1);
        when(mapper.search(any())).thenReturn(List.of(entity("K00001", "쿠콘", "Y")));

        assertThat(service.search(criteria()).rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.code()).isEqualTo("K00001");
                    assertThat(row.name()).isEqualTo("쿠콘");
                    assertThat(row.englishName()).isEqualTo("COOCON_Business1");
                    assertThat(row.businessNumber()).isEqualTo("1234567890");
                    assertThat(row.authKeyMasked()).isNotNull();
                    assertThat(row.statusLabel()).isEqualTo("사용");
                    assertThat(row.description()).isEqualTo("설명");
                    assertThat(row.registeredAt()).isEqualTo("20210401120000");
                    assertThat(row.lastModifiedAt()).isEqualTo("20260721133000");
                });
    }

    @Test
    @DisplayName("null 인증키도 안전하게 처리된다 / a null 인증키 is handled safely")
        // req: FR-ATK-002
    void handlesNullAuthKey() {
        when(mapper.count(any())).thenReturn(1);
        when(mapper.search(any())).thenReturn(List.of(new InstitutionEntity(
                "K00001", "쿠콘", "coocon", "1234567890",
                null, "Y", null, "20210401120000", "20210401120000")));

        assertThat(service.search(criteria()).rows()).singleElement()
                .extracting(InstitutionRow::authKeyMasked)
                .isNull();
    }

    @Test
    @DisplayName("상세조회도 인증키를 마스킹한다 / the detail read masks the 인증키 too")
        // req: FR-INSTC-010, FR-ATK-002, D-I20, TC-I002-21
    void masksAuthKeyOnDetail() {
        when(mapper.findByCode("K00001")).thenReturn(entity("K00001", "쿠콘", "Y"));

        // D-I20. 레거시 상세조회(biztalk_admin_01_l002)는 계약의 out 규칙에 ATK 를 선언하고
        // 평문을 돌려주었으며, 팝업이 그것을 입력칸에 넣었다. 목록(D-I5)·중복검사(D-I3)에
        // 이은 세 번째 노출 경로이고 첫 분석에서 기록되지 않았다.
        // D-I20. The legacy detail service declared ATK in its contract and returned the plaintext,
        // which the popup put into a field — the third exposure path after the list (D-I5) and the
        // duplicate check (D-I3), and unrecorded by the first analysis.
        var row = service.findByCode("K00001");

        assertThat(row.authKeyMasked()).isEqualTo("****************LE01");
        assertThat(row.toString()).doesNotContain(RAW_KEY);
    }

    @Test
    @DisplayName("없는 기관은 예외가 된다 / an absent institution raises")
        // req: FR-INSTC-004, ADR-INST-014
    void throwsWhenAbsent() {
        when(mapper.findByCode("K09999")).thenReturn(null);

        // null 을 돌려주면 호출자가 "빈 폼" 으로 해석할 여지가 생기고, 그 폼을 저장하면
        // 레거시의 UPSERT 와 같은 결과가 된다 — 없는 기관이 생겨난다(D-I6).
        // Returning null would let a caller read it as "an empty form", and saving that form would
        // reproduce the legacy upsert: an institution that did not exist comes into being (D-I6).
        assertThatThrownBy(() -> service.findByCode("K09999"))
                .isInstanceOf(InstitutionNotFoundException.class);
    }
}
