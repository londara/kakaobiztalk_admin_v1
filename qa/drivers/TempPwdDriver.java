import java.util.*;
public class TempPwdDriver {
  public static void main(String[] a){
    TemporaryPasswordGenerator g = new TemporaryPasswordGenerator();
    PasswordPolicy p = new PasswordPolicy(3);
    PasswordPolicy.PasswordMatcher m = (raw,h) -> false;
    int n=200000, violating=0, classFail=0, seqFail=0, other=0;
    Map<String,Integer> reasons = new TreeMap<>();
    for(int i=0;i<n;i++){
      String pw = g.generate();
      List<String> v = p.validate(pw, "operator@example.com", List.of(), m);
      if(!v.isEmpty()){
        violating++;
        for(String r : v){ reasons.merge(r.substring(0, Math.min(28, r.length())), 1, Integer::sum); }
        if(p.hasSequentialRun(pw)) seqFail++;
        else if(p.characterClasses(pw)<3) classFail++;
        else other++;
      }
    }
    System.out.println("samples          : " + n);
    System.out.println("policy-violating : " + violating + String.format("  (%.4f%%)", violating*100.0/n));
    System.out.println("  sequential run : " + seqFail);
    System.out.println("  class shortfall: " + classFail);
    System.out.println("  other          : " + other);
    reasons.forEach((k,v2)->System.out.println("  reason: " + k + " x" + v2));
    // 재시도 20회로 실패할 확률 추정 / probability that 20 retries all fail
    double pv = violating/(double)n;
    System.out.println(String.format("P(all 20 retries fail) = %.3e", Math.pow(pv,20)));
    System.out.println(violating>0 ? "\n=> The retry loop is NECESSARY." : "\n=> No violations observed in this sample.");
  }
}
