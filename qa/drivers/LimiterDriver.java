import java.time.*;
public class LimiterDriver {
  static int pass=0, fail=0;
  static void check(String n, boolean ok){ if(ok){pass++;System.out.println("PASS "+n);} else {fail++;System.out.println("FAIL "+n);} }
  static class MutableClock extends Clock {
    Instant now; MutableClock(Instant i){now=i;}
    public ZoneId getZone(){return ZoneOffset.UTC;}
    public Clock withZone(ZoneId z){return this;}
    public Instant instant(){return now;}
    void plus(Duration d){ now = now.plus(d); }
  }
  static boolean limited(Runnable r){ try{ r.run(); return false; } catch(AuthenticationException e){ return e.reason()==AuthFailureReason.RATE_LIMITED; } }
  public static void main(String[] a){
    MutableClock c = new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));

    // --- per-account limit = 3, per-source = 100 ---
    RateLimiter rl = new RateLimiter(c, 3, 100);
    check("attempt1 allowed", !limited(()->rl.checkAndRecord("u@x.com","10.0.0.1")));
    check("attempt2 allowed", !limited(()->rl.checkAndRecord("u@x.com","10.0.0.1")));
    check("attempt3 allowed", !limited(()->rl.checkAndRecord("u@x.com","10.0.0.1")));
    check("attempt4 BLOCKED", limited(()->rl.checkAndRecord("u@x.com","10.0.0.1")));

    // case-insensitivity: must not multiply the allowance
    check("caseVariantAlsoBlocked", limited(()->rl.checkAndRecord("U@X.COM","10.0.0.1")));

    // different account, same source: still allowed (source limit is 100)
    check("otherAccountAllowed", !limited(()->rl.checkAndRecord("v@x.com","10.0.0.1")));

    // sliding window: after 61s the earliest entries fall out
    c.plus(Duration.ofSeconds(61));
    check("allowedAfterWindow", !limited(()->rl.checkAndRecord("u@x.com","10.0.0.1")));

    // --- per-source limit ---
    RateLimiter rs = new RateLimiter(c, 1000, 2);
    check("src1 allowed", !limited(()->rs.checkAndRecord("a@x.com","10.0.0.9")));
    check("src2 allowed", !limited(()->rs.checkAndRecord("b@x.com","10.0.0.9")));
    check("src3 BLOCKED_differentAccounts", limited(()->rs.checkAndRecord("c@x.com","10.0.0.9")));
    check("otherSourceAllowed", !limited(()->rs.checkAndRecord("d@x.com","10.0.0.10")));

    // eviction
    MutableClock c2 = new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
    RateLimiter re = new RateLimiter(c2, 5, 5);
    re.checkAndRecord("e@x.com","10.1.1.1");
    c2.plus(Duration.ofSeconds(120));
    check("evictionRemovesKeys", re.evictExpired() >= 2);

    // --- OtpReplayGuard (TM-L004) ---
    MutableClock c3 = new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
    OtpReplayGuard g = new OtpReplayGuard(c3);
    check("firstUseAccepted",  g.tryConsume("u@x.com","123456"));
    check("REPLAY_REJECTED",  !g.tryConsume("u@x.com","123456"));
    check("caseVariantAlsoRejected", !g.tryConsume("U@X.COM","123456"));
    check("differentCodeAccepted", g.tryConsume("u@x.com","654321"));
    check("differentAccountAccepted", g.tryConsume("v@x.com","123456"));
    check("sizeTracks", g.size()==3);
    c3.plus(Duration.ofSeconds(121));
    check("evictionClears", g.evictExpired()==3 && g.size()==0);
    check("reusableAfterRetention", g.tryConsume("u@x.com","123456"));

    System.out.println("\n=== pass="+pass+" fail="+fail+" ===");
    if(fail>0) System.exit(1);
  }
}
