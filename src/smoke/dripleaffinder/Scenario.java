package dripleaffinder;

import net.minecraft.client.Minecraft;

interface Scenario {
    void tick(Minecraft mc, Finder finder);
}
