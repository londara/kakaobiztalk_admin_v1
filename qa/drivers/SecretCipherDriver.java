import java.util.Base64;
public class SecretCipherDriver {
  static int pass=0, fail=0;
  static void check(String name, boolean ok){ if(ok){pass++;System.out.println("PASS "+name);} else {fail++;System.out.println("FAIL "+name);} }
  public static void main(String[] a) {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    SecretCipher c = new SecretCipher(key);
    String secret = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U";

    String e1 = c.encrypt(secret);
    check("roundTrip", secret.equals(c.decrypt(e1)));
    check("ciphertextDoesNotContainPlaintext", !e1.contains(secret));
    String e2 = c.encrypt(secret);
    check("differentCiphertextEachTime", !e1.equals(e2));
    check("bothDecrypt", secret.equals(c.decrypt(e2)));

    byte[] raw = Base64.getDecoder().decode(e1);
    raw[raw.length-1] ^= 0x01;
    String tampered = Base64.getEncoder().encodeToString(raw);
    boolean threw=false;
    try { c.decrypt(tampered); } catch (IllegalStateException ex) { threw=true; check("tamperMsgNoPlaintext", !String.valueOf(ex.getMessage()).contains(secret)); }
    check("tamperedRejected", threw);

    byte[] ok2 = new byte[32]; ok2[0]=0x7F;
    SecretCipher other = new SecretCipher(Base64.getEncoder().encodeToString(ok2));
    threw=false; try { other.decrypt(e1); } catch (IllegalStateException ex) { threw=true; }
    check("differentKeyCannotDecrypt", threw);

    threw=false; try { c.decrypt(Base64.getEncoder().encodeToString(new byte[8])); } catch (IllegalStateException ex) { threw=true; }
    check("truncatedRejected", threw);

    for (String bad : new String[]{"", "not-base64!!", "c2hvcnQ="}) {
      threw=false; try { new SecretCipher(bad); } catch (IllegalStateException ex) { threw=true; }
      check("malformedKeyRejected["+bad+"]", threw);
    }
    threw=false; try { new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])); } catch (IllegalStateException ex) { threw = String.valueOf(ex.getMessage()).contains("32 bytes"); }
    check("keyMustBe32Bytes", threw);

    System.out.println("\n=== pass=" + pass + " fail=" + fail + " ===");
    if (fail>0) System.exit(1);
  }
}
