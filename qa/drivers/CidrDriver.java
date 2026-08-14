import java.util.*;
public class CidrDriver {
  static int pass=0, fail=0;
  static void check(String n, boolean ok){ if(ok){pass++;System.out.println("PASS "+n);} else {fail++;System.out.println("FAIL "+n);} }
  public static void main(String[] a){
    // /24
    CidrMatcher m24 = new CidrMatcher(List.of("192.168.10.0/24"));
    check("/24 first address",  m24.matches("192.168.10.0"));
    check("/24 mid address",    m24.matches("192.168.10.77"));
    check("/24 last address",   m24.matches("192.168.10.255"));
    check("/24 just below",    !m24.matches("192.168.9.255"));
    check("/24 just above",    !m24.matches("192.168.11.0"));

    // /8
    CidrMatcher m8 = new CidrMatcher(List.of("10.0.0.0/8"));
    check("/8 lower bound",  m8.matches("10.0.0.0"));
    check("/8 upper bound",  m8.matches("10.255.255.255"));
    check("/8 outside",     !m8.matches("11.0.0.0"));
    check("/8 outside low", !m8.matches("9.255.255.255"));

    // /32 exact host
    CidrMatcher m32 = new CidrMatcher(List.of("203.0.113.7/32"));
    check("/32 exact",      m32.matches("203.0.113.7"));
    check("/32 neighbour", !m32.matches("203.0.113.8"));

    // /0 matches everything — the tricky shift case
    CidrMatcher m0 = new CidrMatcher(List.of("0.0.0.0/0"));
    check("/0 matches any",   m0.matches("8.8.8.8"));
    check("/0 matches other", m0.matches("192.168.1.1"));

    // /31 boundary
    CidrMatcher m31 = new CidrMatcher(List.of("198.51.100.4/31"));
    check("/31 low",   m31.matches("198.51.100.4"));
    check("/31 high",  m31.matches("198.51.100.5"));
    check("/31 out",  !m31.matches("198.51.100.6"));

    // no prefix = /32
    CidrMatcher bare = new CidrMatcher(List.of("172.16.0.1"));
    check("bare address exact",  bare.matches("172.16.0.1"));
    check("bare address other", !bare.matches("172.16.0.2"));

    // multiple ranges
    CidrMatcher multi = new CidrMatcher(List.of("10.0.0.0/8", "192.168.0.0/16"));
    check("multi first",  multi.matches("10.1.2.3"));
    check("multi second", multi.matches("192.168.55.9"));
    check("multi none",  !multi.matches("172.16.0.1"));

    // empty / blank handling
    check("empty list isEmpty", new CidrMatcher(List.of()).isEmpty());
    check("null list isEmpty",  new CidrMatcher(null).isEmpty());
    check("blank entries skipped", new CidrMatcher(List.of("", "  ")).isEmpty());
    check("empty matcher denies", !new CidrMatcher(List.of()).matches("10.0.0.1"));

    // malformed input must FAIL AT CONSTRUCTION, not be silently skipped
    for (String bad : new String[]{"10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/abc", "not-an-ip/24"}) {
      boolean threw=false;
      try { new CidrMatcher(List.of(bad)); } catch (IllegalArgumentException e) { threw=true; }
      check("malformed rejected["+bad+"]", threw);
    }

    // unparseable candidate address is denied, not admitted
    check("null candidate denied",  !m24.matches(null));
    check("blank candidate denied", !m24.matches("  "));
    check("garbage candidate denied", !m24.matches("999.999.999.999"));

    System.out.println("\n=== pass="+pass+" fail="+fail+" ===");
    if(fail>0) System.exit(1);
  }
}
