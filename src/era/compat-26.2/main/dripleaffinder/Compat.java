package dripleaffinder;

import autismclient.util.AutismClientMessaging;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

final class Compat {
    static LevelChunk loadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    static boolean overworld(ClientLevel level) {
        return level.dimension() == Level.OVERWORLD;
    }

    static String serverAddress(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        return server == null ? null : server.ip;
    }

    static String singleplayerWorld(Minecraft mc) {
        if (mc.getSingleplayerServer() == null) return null;
        Path folder = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        try {
            return folder.toRealPath().toString();
        } catch (IOException e) {
            return folder.toString();
        }
    }

    static boolean hudHidden(Minecraft mc) {
        return mc.gui.hud.isHidden();
    }

    static int renderDistance(Minecraft mc) {
        return mc.options.getEffectiveRenderDistance();
    }

    static void chat(Minecraft mc, String text) {
        AutismClientMessaging.sendPrefixed(text);
    }

    static void toast(Minecraft mc, String text) {
        SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            Component.literal("Dripleaf Finder"), Component.literal(text));
    }

    private Compat() {
    }
}
