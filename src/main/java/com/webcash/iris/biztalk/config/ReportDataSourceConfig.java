package com.webcash.iris.biztalk.config;

import com.webcash.iris.biztalk.infra.db.bulk.BulkAggregateMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 대량 발송 집계 데이터소스 설정. / Configuration for the bulk-send aggregate datasource.
 *
 * <h2>설정 파일을 건드리지 않는 이유 / why no configuration file is touched here</h2>
 * <p>이 프로젝트의 보안 규칙(SEC-001)은 {@code *.yml} 을 비롯한 비밀 보유 설정 파일의
 * 열람·수정을 금지한다. 그래서 이 클래스는 {@code application.yml} 을 수정하지 않고
 * <b>필요한 속성 키만 선언</b>한다. 운영 담당자가 아래 키를 배포 환경에 채우면 빈이
 * 만들어지고, 채우지 않으면 만들어지지 않는다.</p>
 * <p>The project's security rules (SEC-001) forbid reading or editing secret-bearing
 * configuration such as {@code *.yml}. This class therefore <b>declares the property keys it
 * needs</b> instead of editing {@code application.yml}: an operator supplies them in the
 * deployment and the beans appear; without them they do not.</p>
 *
 * <pre>
 * iris.report.bulk.enabled:  true
 * iris.report.bulk.jdbc-url: &lt;BIZTALK_BULK_DB 접속 URL&gt;
 * iris.report.bulk.username: &lt;계정&gt;
 * iris.report.bulk.password: &lt;비밀번호 — 자격증명 저장소에서 주입&gt;
 * </pre>
 *
 * <h2>설정이 없을 때의 동작 / behaviour when unconfigured</h2>
 * <p>빈이 없으면 {@code ReportService} 가 {@code Optional.empty()} 를 받고, 결과를 <b>부분
 * 결과로 표시</b>한다(FR-RPTS-005). 조용히 API 분만 돌려주지 않는다 — 그것이 이 프로그램이
 * 네 슬라이스 연속으로 만난 실패 방식이고, 이 화면에서는 고객사 발송량이 실제보다 적게
 * 보이는 결과가 된다.</p>
 * <p>Without the beans, {@code ReportService} receives {@code Optional.empty()} and <b>marks the
 * result incomplete</b> (FR-RPTS-005) rather than quietly returning API figures alone — that
 * silent-partial shape is the failure mode this programme has met four times, and here it would
 * under-report a customer's volume.</p>
 *
 * <h2>기본 데이터소스는 건드리지 않는다 / the primary datasource is left alone</h2>
 * <p>{@code @Primary} 를 옮기거나 자동 설정을 대체하지 않는다. 앞선 세 슬라이스가 모두 기본
 * 데이터소스 위에서 동작하고 있으므로, 여기서 우선순위를 바꾸면 이 슬라이스와 무관한 화면이
 * 조용히 다른 데이터베이스를 보게 된다.</p>
 * <p>No {@code @Primary} is moved and no auto-configuration is replaced. All three earlier slices
 * run on the primary datasource, and reassigning priority here would silently point unrelated
 * screens at a different database.</p>
 *
 * // req: FR-RPTS-001, FR-RPTS-005, ADR-RPT-021, SEC-001
 */
@Configuration
@ConditionalOnProperty(prefix = "iris.report.bulk", name = "enabled", havingValue = "true")
public class ReportDataSourceConfig {

    /** 대량 매퍼 XML 의 위치. / Where the bulk mapper XML lives. */
    private static final String BULK_MAPPER_LOCATION =
            "classpath*:mybatis/mapper/biztalk/bulk/*.xml";

    /**
     * 대량 집계 데이터소스를 만든다. / Creates the bulk aggregate datasource.
     *
     * <p>읽기 전용이다. 이 슬라이스는 어떤 집계에도 쓰지 않는다(CONST-DATA-R01).</p>
     * <p>Read-only: this slice writes to no aggregate (CONST-DATA-R01).</p>
     *
     * @return 데이터소스 / the datasource
     */
    // req: FR-RPTS-001, CONST-DATA-R01
    @Bean
    @ConfigurationProperties(prefix = "iris.report.bulk")
    public DataSource bulkAggregateDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 대량 데이터소스 전용 세션 팩토리를 만든다.
     * Creates the session factory dedicated to the bulk datasource.
     *
     * @param bulkAggregateDataSource 대량 데이터소스 / the bulk datasource
     * @return 세션 팩토리 / the session factory
     * @throws Exception 매퍼 리소스를 읽지 못할 때 / when the mapper resources cannot be read
     */
    // req: FR-RPTS-001
    @Bean
    public SqlSessionFactory bulkSqlSessionFactory(DataSource bulkAggregateDataSource)
            throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(bulkAggregateDataSource);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources(BULK_MAPPER_LOCATION));
        return factory.getObject();
    }

    /**
     * 대량 세션 템플릿을 만든다. / Creates the bulk session template.
     *
     * @param bulkSqlSessionFactory 대량 세션 팩토리 / the bulk session factory
     * @return 세션 템플릿 / the session template
     */
    // req: FR-RPTS-001
    @Bean
    public SqlSessionTemplate bulkSqlSessionTemplate(SqlSessionFactory bulkSqlSessionFactory) {
        return new SqlSessionTemplate(bulkSqlSessionFactory);
    }

    /**
     * 대량 매퍼를 <b>명시적으로</b> 등록한다. / Registers the bulk mapper <b>explicitly</b>.
     *
     * <p>{@code @Mapper} 스캔에 맡기지 않는 이유: 자동 설정은 이 인터페이스를 기본
     * 데이터소스에 묶어 버린다. 그러면 대량 질의가 API 데이터베이스로 날아가 <b>같은 데이터가
     * 두 번 더해지고</b>, 오류는 나지 않는다 — 합계만 두 배가 된다.</p>
     * <p>Not left to {@code @Mapper} scanning because auto-configuration would bind this
     * interface to the primary datasource, sending bulk queries to the API database so that
     * <b>the same data is summed twice</b> with no error — merely doubled totals.</p>
     *
     * @param bulkSqlSessionTemplate 대량 세션 템플릿 / the bulk session template
     * @return 매퍼 팩토리 빈 / the mapper factory bean
     */
    // req: FR-RPTS-001, FR-RPTS-003
    @Bean
    public MapperFactoryBean<BulkAggregateMapper> bulkAggregateMapper(
            SqlSessionTemplate bulkSqlSessionTemplate) {
        MapperFactoryBean<BulkAggregateMapper> factory =
                new MapperFactoryBean<>(BulkAggregateMapper.class);
        factory.setSqlSessionTemplate(bulkSqlSessionTemplate);
        return factory;
    }
}
