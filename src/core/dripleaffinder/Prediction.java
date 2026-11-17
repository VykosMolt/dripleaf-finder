package dripleaffinder;

import java.util.Arrays;
import net.minecraft.core.BlockPos;

record Prediction(BlockPos pos, String half, String below, String note, boolean survived, String first, String then) {
    static Prediction parse(String line) {
        int[] at = Data.ints(line, 3);
        String[] f = Arrays.stream(line.split("\t")).map(String::trim).toArray(String[]::new);
        if (at == null || f.length < 6) return null;
        boolean ordered = f.length > 9;
        return new Prediction(new BlockPos(at[0], at[1], at[2]), f[3], f[4], f[5], f.length <= 6 || f[6].equals("yes"),
            ordered ? f[8] : null, ordered ? f[9] : null);
    }

    String why() {
        return first == null ? note : Guidance.text(pos.getX(), pos.getZ(), below, first, then);
    }
}
