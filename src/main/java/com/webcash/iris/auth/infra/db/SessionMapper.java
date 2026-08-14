package com.webcash.iris.auth.infra.db;

import com.webcash.iris.auth.session.SessionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 세션 레지스트리 매퍼. / Session registry mapper.
 *
 * // source: apc_login_proc_act.jsp — UserSessionDAO
 * // req: FR-LOGIN-016, FR-LOGIN-017, FR-LOGIN-023, ADR-LOGIN-012
 */
@Mapper
public interface SessionMapper {

    /**
     * 계정의 활성 세션을 조회한다. / Finds the active session for an account.
     *
     * @param email 사용자 이메일 / the user's email
     * @return 세션 또는 null / the session, or null when none
     */
    // req: FR-LOGIN-016
    SessionRecord findByEmail(@Param("email") String email);

    /**
     * 세션 식별자로 조회한다. / Finds a session by its id.
     *
     * @param sessionId 세션 식별자 / the session id
     * @return 세션 또는 null / the session, or null when absent
     */
    SessionRecord findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 세션을 등록한다. / Registers a session.
     *
     * @param record 세션 정보 / the session record
     */
    // req: FR-LOGIN-017
    void insert(@Param("r") SessionRecord record);

    /**
     * 계정의 모든 세션을 삭제한다. / Deletes all sessions for an account.
     *
     * @param email 사용자 이메일 / the user's email
     */
    // req: FR-LOGIN-016
    void deleteByEmail(@Param("email") String email);

    /**
     * 세션 식별자로 삭제한다. / Deletes a session by its id.
     *
     * @param sessionId 세션 식별자 / the session id
     */
    // req: FR-LOGIN-023
    void deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 지정 시각보다 오래된 세션을 정리한다. / Reaps sessions older than the given cutoff.
     *
     * <p>인스턴스가 정상 종료되지 못한 경우 남는 고아 세션을 제거한다(ADR-LOGIN-012 §4.3).</p>
     * <p>Removes orphaned sessions left behind when an instance dies uncleanly.</p>
     *
     * @param cutoffEpochSeconds 기준 시각(epoch 초) / cutoff as epoch seconds
     * @return 삭제 건수 / the number of rows removed
     */
    int deleteStale(@Param("cutoffEpochSeconds") long cutoffEpochSeconds);
}
