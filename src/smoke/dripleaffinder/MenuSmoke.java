package dripleaffinder;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

final class MenuSmoke {
    private static final String SEED = "1234567", MORE = "89", FILTER = "1.20", PICK = "1.20.1";
    private static final int STEPS = 8, PAUSE = 30, TOASTS_GONE = 20 * 15;

    private final List<String> shots = new ArrayList<>();
    private int step, wait = TOASTS_GONE;
    private Screen screen;
    private Menu menu;
    String failure;

    boolean running(Minecraft mc) {
        if (failure != null || step >= STEPS) return false;
        if (wait-- > 0) return true;
        wait = PAUSE;
        try {
            run(mc, step++);
        } catch (RuntimeException e) {
            failure = "step " + (step - 1) + ": " + e;
        }
        return failure == null && step < STEPS;
    }

    String summary() {
        return "opened with Right Shift, typed seed " + SEED + MORE + ", picked " + PICK + " through \"" + FILTER
            + "\", radius " + menu.model.radius() + ", memory " + menu.model.memoryGb() + " GB; screenshots " + String.join(", ", shots);
    }

    private void run(Minecraft mc, int step) {
        switch (step) {
            case 0 -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_RSHIFT));
            case 1 -> {
                expect(SmokeCompat.screen(mc) instanceof Menu, "Right Shift did not open the menu");
                screen = SmokeCompat.screen(mc);
                menu = (Menu) screen;
                shoot(mc, "open");
                click(menu.seed, 0.5);
                expect(screen.getFocused() == menu.seed, "clicking the seed box did not focus it");
                menu.seed.insertText(SEED);
                press(InputConstants.KEY_RETURN);
                expect(SEED.equals(menu.model.seed()), "Enter did not save the seed: " + menu.model.seed());
            }
            case 2 -> {
                shoot(mc, "seed");
                click(menu.version, 0.5);
            }
            case 3 -> {
                expect(menu.picking(), "the version button did not open the picker");
                shoot(mc, "picker");
                expect(screen.getFocused() == menu.filter, "the filter box is not focused");
                menu.filter.insertText(FILTER);
                expect(!menu.shown.isEmpty() && menu.shown.stream().allMatch(v -> v.contains(FILTER)), "filter shows " + menu.shown);
            }
            case 4 -> {
                shoot(mc, "filtered");
                click(menu.choices.get(menu.shown.indexOf(PICK)), 0.5);
                expect(!menu.picking() && PICK.equals(menu.model.version()), "picking did not set " + PICK + ": " + menu.model.version());
            }
            case 5 -> {
                shoot(mc, "picked");
                int radius = menu.model.radius();
                click(menu.radius, 0.75);
                click(menu.memory, 0.99);
                expect(menu.model.radius() != radius && menu.model.radius() % MenuModel.RADIUS_STEP == 0,
                    "the radius slider gave " + menu.model.radius());
                expect(menu.model.memoryGb() == menu.model.maxMemoryGb(), "the memory slider gave " + menu.model.memoryGb());
            }
            case 6 -> {
                shoot(mc, "sliders");
                click(menu.seed, 0.9);
                menu.seed.insertText(MORE);
                press(InputConstants.KEY_ESCAPE);
            }
            case 7 -> {
                expect(SmokeCompat.screen(mc) == null, "Escape did not close the menu");
                expect((SEED + MORE).equals(menu.model.seed()), "closing did not save the seed: " + menu.model.seed());
            }
            default -> throw new IllegalStateException();
        }
    }

    private void shoot(Minecraft mc, String name) {
        fits();
        SmokeCompat.screenshot(mc);
        shots.add(name);
    }

    private void fits() {
        Font font = screen.getFont();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible || widget instanceof EditBox) continue;
            int text = font.width(widget.getMessage());
            expect(text <= widget.getWidth() - 8, "\"" + widget.getMessage().getString() + "\" is " + text + " px in a " + widget.getWidth() + " px widget");
        }
        for (Menu.Label label : menu.labels()) {
            int right = label.x() + font.width(label.text());
            expect(label.x() >= 0 && right <= screen.width, "\"" + label.text() + "\" spans " + label.x() + ".." + right + " of " + screen.width);
        }
    }

    private void click(AbstractWidget widget, double across) {
        MouseButtonEvent event = new MouseButtonEvent(widget.getX() + across * widget.getWidth(), widget.getY() + widget.getHeight() / 2.0,
            new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private void press(int key) {
        screen.keyPressed(new KeyEvent(key, 0, 0));
    }

    private void expect(boolean ok, String what) {
        if (!ok) throw new IllegalStateException(what);
    }
}
