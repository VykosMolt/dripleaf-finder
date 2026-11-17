package dripleaffinder;

final class Guidance {
    static final String FOLIA_ODDS = "1 in 2";

    static String cutter(String below) {
        if (below.equals("cave_air")) return "a mineshaft or trial chamber corridor";
        if (below.matches("air|amethyst_block|budding_amethyst|calcite|smooth_basalt|.*amethyst_bud|amethyst_cluster")) return "a geode";
        if (below.matches(".*tuff.*|.*copper.*|vault|trial_spawner")) return "a trial chamber";
        if (below.matches("oak_planks|oak_fence|.*rail|cobweb|dark_oak_.*|spruce_.*")) return "a mineshaft";
        if (below.matches("water|lava")) return "a spring or lake";
        return below.replace('_', ' ');
    }

    static boolean anyOrder(String first, String then) {
        return known(first) && first.equals(then);
    }

    static boolean known(String chunk) {
        return chunk != null && chunk.matches("-?\\d+,-?\\d+");
    }

    static String text(int x, int z, String below, String first, String then, boolean folia) {
        String cut = "cut by " + cutter(below);
        if (anyOrder(first, then)) return cut + "; real in any chunk order";
        if (folia) return cut + "; Folia: about " + FOLIA_ODDS + " (random chunk order)";
        if (!known(first) || !known(then)) return cut + "; the chunk order it needs is unknown";
        int[] a = chunk(first), b = chunk(then);
        return cut + "; real only if " + name(x, z, a) + " generated before " + name(x, z, b) + " (in unexplored land: walk in from the "
            + direction(a[0] - b[0], a[1] - b[1]) + ")";
    }

    static double[] approach(String first, String then) {
        if (anyOrder(first, then) || !known(first) || !known(then)) return null;
        int[] a = chunk(first), b = chunk(then);
        double dx = a[0] - b[0], dz = a[1] - b[1], length = Math.hypot(dx, dz);
        return new double[] {dx / length, dz / length};
    }

    private static String name(int x, int z, int[] chunk) {
        int dx = chunk[0] - (x >> 4), dz = chunk[1] - (z >> 4);
        return dx == 0 && dz == 0 ? "its own chunk" : "the chunk to its " + direction(dx, dz);
    }

    static String direction(int dx, int dz) {
        String ns = dz < 0 ? "north" : dz > 0 ? "south" : "", ew = dx < 0 ? "west" : dx > 0 ? "east" : "";
        return ns.isEmpty() || ew.isEmpty() ? ns + ew : ns + "-" + ew;
    }

    private static int[] chunk(String text) {
        String[] f = text.split(",");
        return new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1])};
    }

    private Guidance() {
    }
}
