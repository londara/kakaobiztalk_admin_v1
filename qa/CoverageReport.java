import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.tools.ExecFileLoader;

/**
 * JaCoCo exec 파일을 읽어 클래스별 커버리지를 표로 출력한다.
 * Reads a JaCoCo exec file and prints per-class coverage as a table.
 *
 * <p>Maven 이 없어 {@code jacoco:report} 를 쓸 수 없다(RISK-A12). JaCoCo 는 에이전트로 붙일 수
 * 있으므로 측정 자체는 가능하고, 리포트 생성만 없다 — 그 부분을 여기서 채운다.</p>
 * <p>Maven is unavailable so {@code jacoco:report} cannot run (RISK-A12). The agent still attaches, so
 * measurement works and only report generation is missing; this supplies it.</p>
 *
 * <p>클래스별로 나누어 출력하는 이유: Sprint A1 반복 3에서 <b>합계 85.5 %</b> 가
 * {@code AlimTalkController} 의 <b>0 %</b> 를 가리고 있었다. 합계만 보면 그런 구멍이 보이지 않는다.</p>
 * <p>Why per class: in Sprint A1 iteration 3 an <b>85.5 % aggregate</b> concealed {@code AlimTalkController}
 * at <b>0 %</b>. An aggregate alone does not show that kind of hole.</p>
 *
 * <p>사용 / usage: {@code java CoverageReport <exec> <classesDir> [minLine] [minBranch]}</p>
 *
 * // req: TEST-PLAN-ALIMTALK §2, RISK-A12
 */
public final class CoverageReport {

    private CoverageReport() {
    }

    /**
     * 리포트를 출력하고 기준 미달이면 0 이 아닌 코드로 끝낸다.
     * Prints the report and exits non-zero when a threshold is missed.
     *
     * @param args exec 경로, 클래스 디렉터리, 선택적 최소 line·branch 백분율
     * @throws IOException 파일을 읽을 수 없을 때 / when a file cannot be read
     *
     * // req: TEST-PLAN-ALIMTALK §2
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: CoverageReport <exec> <classesDir> [minLine] [minBranch]");
            System.exit(2);
        }
        double minLine = args.length > 2 ? Double.parseDouble(args[2]) : 0;
        double minBranch = args.length > 3 ? Double.parseDouble(args[3]) : 0;

        ExecFileLoader loader = new ExecFileLoader();
        loader.load(new File(args[0]));

        CoverageBuilder builder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), builder);
        List<Path> classFiles = new ArrayList<>();
        try (var walk = Files.walk(Path.of(args[1]))) {
            walk.filter(p -> p.toString().endsWith(".class")).forEach(classFiles::add);
        }
        for (Path p : classFiles) {
            try (FileInputStream in = new FileInputStream(p.toFile())) {
                analyzer.analyzeClass(in, p.toString());
            }
        }

        System.out.printf("%-52s %8s %8s%n", "CLASS", "LINE", "BRANCH");
        System.out.println("-".repeat(70));

        int lineCovered = 0;
        int lineTotal = 0;
        int branchCovered = 0;
        int branchTotal = 0;

        List<IClassCoverage> classes = new ArrayList<>(builder.getClasses());
        classes.sort((a, b) -> a.getName().compareTo(b.getName()));

        for (IClassCoverage c : classes) {
            // 테스트 클래스 자신은 대상이 아니다 / the test classes themselves are not the subject
            if (c.getName().endsWith("Test") || c.getName().contains("Test$")) {
                continue;
            }
            ICounter line = c.getLineCounter();
            ICounter branch = c.getBranchCounter();
            lineCovered += line.getCoveredCount();
            lineTotal += line.getTotalCount();
            branchCovered += branch.getCoveredCount();
            branchTotal += branch.getTotalCount();

            String simple = c.getName().substring(c.getName().lastIndexOf('/') + 1);
            System.out.printf("%-52s %7s %8s%n", simple, pct(line), pct(branch));
        }

        System.out.println("-".repeat(70));
        double linePct = lineTotal == 0 ? 100 : 100.0 * lineCovered / lineTotal;
        double branchPct = branchTotal == 0 ? 100 : 100.0 * branchCovered / branchTotal;
        System.out.printf("%-52s %6.1f%% %7.1f%%%n", "TOTAL", linePct, branchPct);
        System.out.printf("   lines %d/%d, branches %d/%d%n",
                lineCovered, lineTotal, branchCovered, branchTotal);

        if (linePct < minLine || branchPct < minBranch) {
            System.out.printf("%nBELOW THRESHOLD: require line >= %.1f%%, branch >= %.1f%%%n",
                    minLine, minBranch);
            System.exit(1);
        }
    }

    /**
     * 카운터를 백분율 문자열로 만든다. / Renders a counter as a percentage string.
     *
     * @param c 카운터 / the counter
     * @return 백분율, 대상이 없으면 {@code -} / the percentage, or {@code -} when there is nothing to cover
     *
     * // req: TEST-PLAN-ALIMTALK §2
     */
    private static String pct(ICounter c) {
        if (c.getTotalCount() == 0) {
            return "-";
        }
        return String.format("%.1f%%", 100.0 * c.getCoveredCount() / c.getTotalCount());
    }
}
