package rozov.nikita.linkd.utility;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class CodeGenerator {
    private PropertyUtil props;

    public String encode(long n) {
        n = scramble(n);
        char[] result = new char[props.getLength()];
        for (int i = props.getLength() - 1; i >= 0; i--) {
            result[i] = props.getChars().charAt((int) Long.remainderUnsigned(n, props.getBase()));
            n = Long.divideUnsigned(n, props.getBase());
        }
        return new String(result);
    }

    public long decode(String s) {
        long result = 0;
        for (char c : s.toCharArray()) {
            result = result * props.getBase() + props.getChars().indexOf(c);
        }
        return unscramble(result);
    }
    public long scramble(long id) {
        return id * props.getScrambleNumberPos();
    }

    public long unscramble(long scrambled) {
        return scrambled * props.getScrambleNumberNeg();
    }
}
