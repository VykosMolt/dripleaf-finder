package dripleaffinder;

import autismclient.util.AutismClientMessaging;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

final class Compat {
    private static final Pattern COORDINATES = Pattern.compile("\\bat\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\b");
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
        MutableComponent body = AutismClientMessaging.themedBody(text);
        Matcher coordinates = COORDINATES.matcher(text);
        if (coordinates.find()) {
            String copy = coordinates.group(1) + " " + coordinates.group(2) + " " + coordinates.group(3);
            ClickEvent click = new ClickEvent.CopyToClipboard(copy);
            HoverEvent hover = new HoverEvent.ShowText(Component.literal("Click to copy " + copy));
            clickable(body, click, hover);
        }
        AutismClientMessaging.send(Component.empty().append(AutismClientMessaging.themedTag("AUTISM")).append(body));
    }

    private static void clickable(MutableComponent component, ClickEvent click, HoverEvent hover) {
        Style style = component.getStyle();
        if (style.getClickEvent() == null) style = style.withClickEvent(click);
        if (style.getHoverEvent() == null) style = style.withHoverEvent(hover);
        component.setStyle(style);
        for (Component sibling : component.getSiblings()) {
            if (sibling instanceof MutableComponent mutable) clickable(mutable, click, hover);
        }
    }

    static void toast(Minecraft mc, String text) {
        SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            Component.literal("Dripleaf Finder"), Component.literal(text));
    }

    private Compat() {
    }
}
