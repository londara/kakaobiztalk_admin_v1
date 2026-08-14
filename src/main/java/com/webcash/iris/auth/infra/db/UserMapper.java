package com.webcash.iris.auth.infra.db;

import com.webcash.iris.auth.domain.UserAccount;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 계정 매퍼. / User account mapper.
 *
 * <p>레거시 IDO 정의를 1:1 로 대응시킨다. SQL 은 near-verbatim 으로 이식하고
 * 수정한 부분은 XML 안에서 {@code -- FIX Ln:} 주석으로 표시한다 — 레거시 소스가
 * 유일한 명세이므로(RISK-001) 변경점이 리뷰 가능해야 한다.</p>
 * <p>Maps one-to-one onto the legacy IDO definitions. The SQL is ported
 * near-verbatim and every correction is annotated in the XML with
 * {@code -- FIX Ln:}, because the legacy source is the only specification
 * (RISK-001) and the delta has to be reviewable.</p>
 *
 * <table>
 *   <caption>레거시 IDO 대응 / legacy IDO correspondence</caption>
 *   <tr><th>Method</th><th>Legacy IDO</th></tr>
 *   <tr><td>{@code findByEmail}</td><td>{@code USER_LDGR_R006}</td></tr>
 *   <tr><td>{@code incrementLoginAttempt}</td><td>{@code USER_LDGR_LOGIN_ATTEMPT_U001}</td></tr>
 *   <tr><td>{@code incrementOtpFailCount}</td><td>{@code USER_LDGR_U006}</td></tr>
 *   <tr><td>{@code resetFailureCounters}</td><td>{@code USER_LDGR_U009}</td></tr>
 *   <tr><td>{@code touchLastLogin}</td><td>{@code USER_LDGR_U010}</td></tr>
 *   <tr><td>{@code isOperator}</td><td>{@code USER_GRP_JNNG_INFM_R001}</td></tr>
 * </table>
 *
 * // source: IDO.USER_LDGR_R006 and siblings, via apc_login_proc_act.jsp
 * // req: FR-LOGIN-003/004/005/010/018/020, CONST-DATA-L01
 */
@Mapper
public interface UserMapper {

    /**
     * 이메일로 계정을 조회한다. / Finds an account by email.
     *
     * @param email 로그인 이메일 / the login email
     * @return 계정 또는 null / the account, or null when no such account
     */
    // req: FR-LOGIN-002
    UserAccount findByEmail(@Param("email") String email);

    /**
     * 비밀번호 실패 횟수를 1 증가시킨다. / Increments the password failure counter by one.
     *
     * <p>애플리케이션이 계산한 값을 쓰지 않고 DB 에서 증가시킨다. 동시 시도에서
     * 값이 유실되면 잠금이 무력화되므로(TM-L010) 원자적 증가가 필요하다.</p>
     * <p>Incremented in the database rather than writing an application-computed
     * value. A lost update under concurrent attempts would defeat the lockout
     * (TM-L010), so the increment has to be atomic.</p>
     *
     * @param email 사용자 이메일 / the user's email
     * @return 증가 후 값 / the value after incrementing
     */
    // source: IDO.USER_LDGR_LOGIN_ATTEMPT_U001
    // req: FR-LOGIN-003
    int incrementLoginAttempt(@Param("email") String email);

    /**
     * OTP 실패 횟수를 1 증가시킨다. / Increments the OTP failure counter by one.
     *
     * @param email 사용자 이메일 / the user's email
     * @return 증가 후 값 / the value after incrementing
     */
    // source: IDO.USER_LDGR_U006
    // req: FR-LOGIN-010
    int incrementOtpFailCount(@Param("email") String email);

    /**
     * 실패 카운터를 0 으로 초기화한다. / Resets both failure counters to zero.
     *
     * @param email 사용자 이메일 / the user's email
     */
    // source: IDO.USER_LDGR_U009
    // req: FR-LOGIN-004
    void resetFailureCounters(@Param("email") String email);

    /**
     * 최종 로그인 일시를 갱신한다. / Updates the last-login timestamp.
     *
     * @param email 사용자 이메일 / the user's email
     */
    // source: IDO.USER_LDGR_U010
    // req: FR-LOGIN-020
    void touchLastLogin(@Param("email") String email);

    /**
     * 신규 스키마 비밀번호 해시를 저장한다. / Stores a new-scheme password hash.
     *
     * <p>레거시 {@code PWD} 컬럼은 <b>건드리지 않는다.</b> 신규 컬럼에만 쓰기 때문에
     * 레거시 시스템이 같은 테이블을 계속 읽어도 동작하며, 롤백 창 동안 구 해시가
     * 남아 있어 되돌릴 수 있다(RISK-L04).</p>
     * <p>The legacy {@code PWD} column is <b>left untouched.</b> Writing only to the
     * new column keeps the still-running legacy system working against the same
     * table, and leaves the old hash available during the rollback window (RISK-L04).</p>
     *
     * @param email 사용자 이메일 / the user's email
     * @param hash  Argon2id 해시 / the Argon2id hash
     */
    // req: FR-LOGIN-005, FR-PWD-006, CONST-DATA-L01, RISK-L04
    void updatePasswordHash(@Param("email") String email, @Param("hash") String hash);

    /**
     * 최근 비밀번호 해시 목록을 조회한다. / Returns recent password hashes, newest first.
     *
     * @param email 사용자 이메일 / the user's email
     * @param limit 조회 개수 / how many to return
     * @return 해시 목록 / the hashes
     */
    // req: FR-PWD-004
    List<String> findRecentPasswordHashes(@Param("email") String email, @Param("limit") int limit);

    /**
     * 비밀번호 이력에 해시를 추가한다. / Appends a hash to the password history.
     *
     * @param email 사용자 이메일 / the user's email
     * @param hash  추가할 해시 / the hash to append
     */
    // req: FR-PWD-004
    void insertPasswordHistory(@Param("email") String email, @Param("hash") String hash);

    /**
     * 운영자 초기화용 비밀번호 해시를 저장한다. 다음 로그인에서 변경을 강제한다.
     * Stores a password hash set by an operator, forcing a change at next login.
     *
     * <p>{@link #updatePasswordHash} 와의 차이는 {@code PWD_INIT_YN} 값 하나다.
     * 운영자가 설정한 임시 비밀번호는 <b>초기 비밀번호</b>로 표시되어
     * {@link com.webcash.iris.auth.domain.AccountPolicy#passwordChangeRequired}
     * 가 다음 로그인에서 변경을 강제한다. 운영자가 아는 비밀번호로 사용자가 계속
     * 로그인하는 상태를 남기지 않기 위한 것이다.</p>
     * <p>The only difference from {@link #updatePasswordHash} is the {@code PWD_INIT_YN}
     * value. A temporary password set by an operator is marked as an <b>initial</b>
     * password so that a change is forced at next login — leaving no state in which a
     * user keeps logging in with a credential their operator knows.</p>
     *
     * @param email 사용자 이메일 / the user's email
     * @param hash  Argon2id 해시 / the Argon2id hash
     */
    // req: FR-PWD-007, FR-LOGIN-015
    void resetPasswordHash(@Param("email") String email, @Param("hash") String hash);

    /**
     * OTP 비밀키를 저장한다 (암호화된 값). / Stores the OTP secret, already encrypted.
     *
     * @param email        사용자 이메일 / the user's email
     * @param encryptedKey 암호화된 비밀키 / the encrypted secret
     */
    // req: FR-OTP-005, NFR-SEC-PII-L01
    void updateOtpKey(@Param("email") String email, @Param("encryptedKey") String encryptedKey);

    /**
     * OTP 등록을 해제한다 (운영자 초기화). / Clears the OTP enrolment, for an operator reset.
     *
     * @param email 사용자 이메일 / the user's email
     */
    // req: FR-OTP-007
    void clearOtpKey(@Param("email") String email);

    /**
     * 운영자 그룹(GRP_0) 보유 여부를 조회한다. / Whether the account holds the operator group.
     *
     * @param email 사용자 이메일 / the user's email
     * @return 운영자 여부 / true when the account is in GRP_0
     */
    // source: IDO.USER_GRP_JNNG_INFM_R001 — GRP_0 → admin, GRP_1 → user
    // req: FR-LOGIN-018
    boolean isOperator(@Param("email") String email);
}
