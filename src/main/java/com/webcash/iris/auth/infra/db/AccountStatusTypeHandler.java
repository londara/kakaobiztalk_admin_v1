package com.webcash.iris.auth.infra.db;

import com.webcash.iris.auth.domain.AccountStatus;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * {@link AccountStatus} 를 레거시 코드값으로 매핑하는 타입 핸들러.
 * Type handler mapping {@link AccountStatus} to and from the legacy code values.
 *
 * <h2>왜 필요한가 / why this exists</h2>
 * <p>MyBatis 는 기본적으로 enum 을 <b>이름</b>으로 매핑한다. 그러나 {@code JNNG_STTS} 컬럼에
 * 저장된 값은 {@code '1'}, {@code '2'} 같은 <b>코드</b>이므로 기본 매핑은
 * {@code No enum constant AccountStatus.1} 로 실패한다.</p>
 * <p>MyBatis maps enums by <b>name</b> by default, but {@code JNNG_STTS} stores codes such as
 * {@code '1'}, so the default mapping fails outright.</p>
 *
 * <p>대안으로 SQL 에서 {@code CASE JNNG_STTS WHEN '1' THEN 'ACTIVE' ...} 로 변환할 수도
 * 있었으나 채택하지 않았다 — 코드↔이름 대응이 enum 과 SQL <b>두 곳</b>에 존재하게 되고,
 * 한쪽만 바뀌는 것이 이 프로젝트에서 반복 확인된 결함 유형이다. 대응 관계는
 * {@link AccountStatus#fromCode(String)} 한 곳에만 둔다.</p>
 * <p>Translating in SQL with a {@code CASE} was rejected: it would place the code-to-name mapping
 * in two places, and one of them drifting is the defect pattern this project has hit repeatedly.
 * The mapping stays solely in {@link AccountStatus#fromCode(String)}.</p>
 *
 * <p>실측 확인: {@code a_user_ldgr.jnng_stts} 에는 {@code '1'}(6건)과 {@code '2'}(1건)가
 * 존재한다.</p>
 * <p>Verified against the live table: values {@code '1'} and {@code '2'} are present.</p>
 *
 * // source: IDO.USER_LDGR_R006 — JNNG_STTS, apc_login_proc_act.jsp
 * // req: FR-LOGIN-002, FR-LOGIN-013
 */
@MappedTypes(AccountStatus.class)
public class AccountStatusTypeHandler extends BaseTypeHandler<AccountStatus> {

    /**
     * enum 을 코드값으로 기록한다. / Writes the enum as its code value.
     *
     * @param ps        준비된 문 / the statement
     * @param i         파라미터 위치 / the parameter index
     * @param parameter 상태 / the status
     * @param jdbcType  JDBC 타입 / the JDBC type
     * @throws SQLException JDBC 오류 / on JDBC failure
     */
    // req: FR-LOGIN-002
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    AccountStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.code());
    }

    /**
     * 컬럼명으로 읽는다. / Reads by column name.
     *
     * @param rs         결과 집합 / the result set
     * @param columnName 컬럼명 / the column name
     * @return 상태 / the status
     * @throws SQLException JDBC 오류 / on JDBC failure
     */
    // req: FR-LOGIN-002
    @Override
    public AccountStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toStatus(rs.getString(columnName));
    }

    /**
     * 컬럼 순번으로 읽는다. / Reads by column index.
     *
     * @param rs          결과 집합 / the result set
     * @param columnIndex 컬럼 순번 / the column index
     * @return 상태 / the status
     * @throws SQLException JDBC 오류 / on JDBC failure
     */
    // req: FR-LOGIN-002
    @Override
    public AccountStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toStatus(rs.getString(columnIndex));
    }

    /**
     * 저장 프로시저 결과에서 읽는다. / Reads from a callable statement.
     *
     * @param cs          호출 문 / the callable statement
     * @param columnIndex 컬럼 순번 / the column index
     * @return 상태 / the status
     * @throws SQLException JDBC 오류 / on JDBC failure
     */
    // req: FR-LOGIN-002
    @Override
    public AccountStatus getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        return toStatus(cs.getString(columnIndex));
    }

    /**
     * 코드값을 상태로 변환한다. 공백은 null 로 둔다.
     * Converts a code to a status; blanks become null.
     *
     * <p>미인식 코드는 <b>예외</b>로 처리한다. 조용히 null 로 두면 계정 상태 검사가
     * 통과해 버릴 수 있다 — 알 수 없는 상태는 거부되어야 한다.</p>
     * <p>An unrecognised code throws: silently returning null could let the account-status check
     * pass, and an unknown status must be refused.</p>
     *
     * @param code 코드값 / the code
     * @return 상태, 값이 없으면 null / the status, or null when absent
     */
    // req: FR-LOGIN-002, FR-LOGIN-013
    private AccountStatus toStatus(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return AccountStatus.fromCode(code.trim());
    }
}
