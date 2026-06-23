package rozov.nikita.linkd.utility;

public class CodeGenerator {
    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int LENGTH = 8;
    private static final int BASE = 62;

    public static String encode(long n) {
        n = scramble(n);
        char[] result = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            result[i] = CHARS.charAt((int)(n % BASE));
            n /= BASE;
        }
        return new String(result);
    }

    public static long decode(String s) {
        long result = 0;
        for (char c : s.toCharArray()) {
            result = result * BASE + CHARS.indexOf(c);
        }
        return unscramble(result);
    }
    public static long scramble(long id) {
        return id * 6364136223846793005L;
    }

    public static long unscramble(long scrambled) {
        return scrambled * -4576744217476464767L;
    }
}
