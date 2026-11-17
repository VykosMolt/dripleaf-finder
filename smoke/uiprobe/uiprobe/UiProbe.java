package uiprobe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class UiProbe implements ClientModInitializer {
    private Path dir, commands, log;
    private int next, wait, ticks;
    private final ArrayDeque<Integer> typing = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        dir = FabricLoader.getInstance().getGameDir().resolve("uiprobe");
        commands = dir.resolve("cmds.txt");
        log = dir.resolve("log.txt");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void write(String line) {
        try {
            Files.createDirectories(dir);
            Files.writeString(log, ticks + " " + line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private void tick(Minecraft mc) {
        ticks++;
        if (!typing.isEmpty()) {
            if (mc.gui.screen() != null) mc.gui.screen().charTyped(new CharacterEvent(typing.poll()));
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            if (!Files.exists(commands)) return;
            List<String> lines = Files.readAllLines(commands);
            if (next >= lines.size()) return;
            String command = lines.get(next++).trim();
            if (!command.isEmpty()) run(mc, command);
        } catch (Exception e) {
            write("error " + e);
        }
    }

    private void run(Minecraft mc, String command) throws Exception {
        String[] f = command.split(" ", 2);
        String arg = f.length > 1 ? f[1] : "";
        write("> " + command);
        Screen screen = mc.gui.screen();
        switch (f[0]) {
            case "keyp" -> screen.keyPressed(new KeyEvent(Integer.parseInt(arg), 0, 0));
            case "type" -> arg.codePoints().forEach(typing::add);
            case "click" -> {
                String[] xy = arg.split(" ");
                double x = Double.parseDouble(xy[0]), y = Double.parseDouble(xy[1]);
                MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
                move(mc, x, y);
                screen.mouseClicked(event, false);
                screen.mouseReleased(event);
            }
            case "move" -> {
                String[] xy = arg.split(" ");
                move(mc, Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
            }
            case "scroll" -> {
                String[] xyd = arg.split(" ");
                screen.mouseScrolled(Double.parseDouble(xyd[0]), Double.parseDouble(xyd[1]), 0, Double.parseDouble(xyd[2]));
            }
            case "shot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> write("shot " + arg));
            case "open2" -> mc.gui.setScreen((Screen) Class.forName(arg).getConstructor(Screen.class).newInstance((Object) null));
            case "close" -> mc.gui.setScreen(null);
            case "leave" -> mc.disconnect(new TitleScreen(), false);
            case "join" -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(arg),
                new ServerData(arg, arg, ServerData.Type.OTHER), false, null);
            case "wait" -> wait = Integer.parseInt(arg);
            case "mark" -> write("mark " + arg + " screen " + (screen == null ? "none" : screen.getClass().getName())
                + " world " + (mc.level != null) + " scale " + mc.getWindow().getGuiScale());
            case "popup" -> {
                Object popup = dropdown(screen, Collections.newSetFromMap(new IdentityHashMap<>()));
                if (popup == null || !(Boolean) field(popup, "init", boolean.class)) {
                    write("popup closed");
                    break;
                }
                Object bounds = field(popup, "load", Class.forName("autismclient.gui.f.NetHelper"));
                write("popup " + field(bounds, "init", int.class) + " " + field(bounds, "load", int.class) + " "
                    + field(bounds, "read", int.class) + " " + field(bounds, "write", int.class) + " "
                    + field(popup, "load", int.class) + " " + field(popup, "read", int.class) + " "
                    + ((List<?>) field(popup, "init", List.class)).size());
            }
            case "quit" -> mc.stop();
            default -> write("unknown " + command);
        }
    }

    private static Object field(Object object, String name, Class<?> type) throws Exception {
        for (Field field : object.getClass().getDeclaredFields()) {
            if (!field.getName().equals(name) || field.getType() != type) continue;
            field.setAccessible(true);
            return field.get(object);
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name + ":" + type.getName());
    }

    private static Object dropdown(Object object, Set<Object> seen) throws Exception {
        if (object == null || !seen.add(object)) return null;
        if (object.getClass().getName().equals("autismclient.gui.f.b.RefQueue")) return object;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            if (!type.getName().startsWith("autismclient.gui.")) continue;
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !field.getType().getName().startsWith("autismclient.gui.")) continue;
                field.setAccessible(true);
                Object found = dropdown(field.get(object), seen);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void move(Minecraft mc, double x, double y) throws Exception {
        Method onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        onMove.setAccessible(true);
        double scale = mc.getWindow().getGuiScale();
        onMove.invoke(mc.mouseHandler, mc.getWindow().handle(), x * scale, y * scale);
    }
}
