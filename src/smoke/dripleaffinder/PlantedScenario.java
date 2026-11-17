package dripleaffinder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

final class PlantedScenario implements Scenario {
    private final Smoke smoke;
    private final BlockPos target, negative;
    private final double[] camera;
    private int foundAt = -1, arrivedAt = -1;

    PlantedScenario(Smoke smoke, BlockPos target, BlockPos negative, double[] camera) {
        this.smoke = smoke;
        this.target = target;
        this.negative = negative;
        this.camera = camera;
    }

    @Override
    public void tick(Minecraft mc, Finder finder) {
        int ticks = smoke.ticks;
        if (foundAt < 0 && finder.found.contains(target)) foundAt = ticks;
        if (arrivedAt < 0 && mc.player.position().distanceToSqr(camera[0], camera[1], camera[2]) < 0.25) arrivedAt = ticks;
        if (foundAt >= 0 && arrivedAt >= 0 && ticks - Math.max(foundAt, arrivedAt) >= 60) {
            if (negative != null && finder.found.contains(negative)) {
                smoke.shoot(mc, "FAIL reported the half outside lush caves at " + negative.getX() + " " + negative.getY() + " " + negative.getZ());
                return;
            }
            smoke.shoot(mc, "PASS reported " + (foundAt - smoke.joinedAt) / 20 + " s after joining"
                + (negative != null ? "; the half outside lush caves was not" : ""));
        } else if (ticks - smoke.joinedAt > 20 * 90) {
            smoke.shoot(mc, "FAIL " + (foundAt < 0 ? "not reported" : "never teleported") + ": " + diagnose(mc, finder));
        }
    }

    private String diagnose(Minecraft mc, Finder finder) {
        BlockState state = mc.level.getBlockState(target), below = mc.level.getBlockState(target.below());
        return "block " + state + ", below " + below + ", lush near " + Splice.lushNear(mc.level, target) + ", covered "
            + Splice.covered(mc.level, target) + ", chunk loaded " + (Compat.loadedChunk(mc.level, target.getX() >> 4, target.getZ() >> 4) != null)
            + ", player at " + mc.player.position() + ", finder on " + finder.enabled() + ", found " + finder.found.contains(target)
            + ", settled " + finder.memory.settled.contains(target);
    }
}
