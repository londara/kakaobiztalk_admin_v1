import java.time.*; import java.util.*;
public class PolicyDriver {
  static int pass=0, fail=0;
  static void check(String n, boolean ok){ if(ok){pass++;System.out.println("PASS "+n);} else {fail++;System.out.println("FAIL "+n);} }
  static LocalDate TODAY = LocalDate.of(2026,8,14);
  static Clock FIXED = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  static UserAccount acc(int la,int of,AccountStatus st,LocalDate ll,LocalDate lp,boolean init){
    return new UserAccount("user@example.com","$argon2id$h",null,"SECRET",la,of,st,ll,lp,init,false,"IS001");
  }
  public static void main(String[] a){
    AccountPolicy p = new AccountPolicy(FIXED,90,90);

    // lockout
    boolean threw=false; try{p.assertNotLocked(acc(5,0,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(1),false));}catch(AuthenticationException e){threw=e.reason()==AuthFailureReason.ACCOUNT_LOCKED;}
    check("locksAt5PasswordFailures", threw);
    threw=false; try{p.assertNotLocked(acc(0,5,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(1),false));}catch(AuthenticationException e){threw=true;}
    check("locksAt5OtpFailures", threw);
    threw=false; try{p.assertNotLocked(acc(4,4,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(1),false));}catch(AuthenticationException e){threw=true;}
    check("doesNotLockAt4", !threw);

    // dormancy boundary
    check("dormant89=false", !p.isDormant(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(89),TODAY.minusDays(1),false)));
    check("dormant90=true",   p.isDormant(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(90),TODAY.minusDays(1),false)));
    check("firstEverLoginNotDormant", !p.isDormant(acc(0,0,AccountStatus.ACTIVE,null,TODAY.minusDays(1),false)));

    // status
    for (AccountStatus s : new AccountStatus[]{AccountStatus.AWAITING_APPROVAL,AccountStatus.APPLICATION_PENDING,AccountStatus.SUSPENDED,AccountStatus.TERMINATED}) {
      threw=false; try{p.assertUsable(acc(0,0,s,TODAY.minusDays(1),TODAY.minusDays(1),false));}catch(AuthenticationException e){threw=e.reason()==AuthFailureReason.STATUS_BLOCKED;}
      check("statusBlocked["+s+"]", threw);
    }
    threw=false; try{p.assertUsable(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(1),false));}catch(AuthenticationException e){threw=true;}
    check("activePasses", !threw);
    threw=false; try{AccountStatus.fromCode("7");}catch(IllegalArgumentException e){threw=true;}
    check("unknownStatusFailsClosed", threw);

    // password age
    check("pwdAge89=false", !p.passwordChangeRequired(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(89),false)));
    check("pwdAge90=true",   p.passwordChangeRequired(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(90),false)));
    check("initialPwdForcesChange", p.passwordChangeRequired(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(1),TODAY.minusDays(1),true)));
    check("nullPwdDateForcesChange", p.passwordChangeRequired(acc(0,0,AccountStatus.ACTIVE,TODAY.minusDays(1),null,false)));

    // PasswordPolicy — L6 / L9 regressions
    PasswordPolicy pp = new PasswordPolicy(3);
    PasswordPolicy.PasswordMatcher m = (raw,h) -> ("hash::"+raw).equals(h);
    String email="jaemin.nam@example.com";
    check("compliantPasses", pp.validate("Tr0ubled-Kettle!9", email, List.of(), m).isEmpty());
    String seventy="Tr0ubled-Kettle!9-Windward-Lantern#42-Quiet-Harbour~Bramble7-Ossify99";
    check("L9_accepts70chars", seventy.length()>64 && pp.validate(seventy,email,List.of(),m).isEmpty());
    check("rejectsTooShort", !pp.validate("Sh0rt!Pass",email,List.of(),m).isEmpty());
    check("L6_rejects1class", !pp.validate("onlylowercaseletters",email,List.of(),m).isEmpty());
    check("L6_rejects2class", !pp.validate("lowercaseandnumbers12",email,List.of(),m).isEmpty());
    check("L6_rejectsWeakList", !pp.validate("MyPassword123!",email,List.of(),m).isEmpty());
    check("L6_rejectsContainsId", !pp.validate("Jaemin.Nam-2026!",email,List.of(),m).isEmpty());
    check("L6_rejectsSequential", pp.hasSequentialRun("Qw3rty-abcd-Zx!"));
    List<String> hist=List.of("hash::Tr0ubled-Kettle!9","hash::Windward-Lantern#42","hash::Quiet-Harbour~7");
    check("rejectsRecentReuse", !pp.validate("Tr0ubled-Kettle!9",email,hist,m).isEmpty());
    List<String> hist4=new ArrayList<>(List.of("hash::Aardvark-Tumble!1","hash::Bramble-Ossify!2","hash::Cormorant-Vex!3","hash::Tr0ubled-Kettle!9"));
    check("allowsBeyondDepth3", pp.validate("Tr0ubled-Kettle!9",email,hist4,m).isEmpty());
    check("noEchoOfPassword", pp.validate("sekrit",email,List.of(),m).stream().noneMatch(v->v.contains("sekrit")));
    check("classCount4", pp.characterClasses("abcDEF1!")==4);
    check("rejectsNull", !pp.validate(null,email,List.of(),m).isEmpty());

    System.out.println("\n=== pass="+pass+" fail="+fail+" ===");
    if(fail>0) System.exit(1);
  }
}
