package dripleaffinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class Menu extends Screen {
    static final int COLUMN = 150, GAP = 8, ROW = 24, HEIGHT = 20, CHOICE = 48, PICKER_ROWS = 5;
    static final int STATUS = 164, BOTTOM = 178, PANEL = 198;
    static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFA0A0A0;

    record Label(String text, int x, int y, int color) {
    }

    final MenuModel model;
    private final List<Runnable> refreshers = new ArrayList<>();
    final List<Button> choices = new ArrayList<>();
    List<String> shown = List.of();
    private boolean picking, pageChanged;
    private String seedDraft;
    int left, right, top;
    EditBox seed, filter;
    Button version, search;
    Slider radius, memory;

    Menu(MenuModel model) {
        super(Component.literal("Dripleaf Finder"));
        this.model = model;
        seedDraft = Objects.requireNonNullElse(model.seed(), "");
    }

    @Override
    protected void init() {
        refreshers.clear();
        choices.clear();
        left = width / 2 - GAP / 2 - COLUMN;
        right = width / 2 + GAP / 2;
        top = Math.max(4, (height - PANEL) / 2);
        if (picking) initPicker();
        else initMain();
        refresh();
    }

    private void initMain() {
        toggle(0, "Enabled", model::enabled, model::setEnabled);
        toggle(1, "Masked Server", model::masked, model::setMasked);
        toggle(2, "Lush Cave Pings", model::lush, model::setLush);
        toggle(3, "Azalea Trees", model::azalea, model::setAzalea);
        button(left, row(4), COLUMN, "Nearest Predictions", () -> closeThen(model::listPredictions));
        button(left, row(5), COLUMN, "Nearest Lush Caves", () -> closeThen(model::listLush));
        int label = font.width("Seed") + 6;
        seed = addRenderableWidget(new EditBox(font, right + label, row(0), COLUMN - label, HEIGHT, Component.literal("World Seed")));
        seed.setMaxLength(64);
        seed.setValue(seedDraft);
        seed.setResponder(text -> seedDraft = text);
        version = button(right, row(1), COLUMN, "", this::openPicker);
        radius = addRenderableWidget(new Slider(right, row(2), "Radius", "", MenuModel.MIN_RADIUS, MenuModel.MAX_RADIUS,
            MenuModel.RADIUS_STEP, model.radius(), model::setRadius));
        memory = addRenderableWidget(new Slider(right, row(3), "Memory", " GB", 1, model.maxMemoryGb(), 1, model.memoryGb(),
            model::setMemoryGb));
        memory.active = model.maxMemoryGb() > 1;
        search = button(left, top + BOTTOM, COLUMN, "", this::searchOrStop);
        button(right, top + BOTTOM, COLUMN, "Done", this::onClose);
        refreshers.add(() -> {
            boolean inWorld = model.inWorld();
            seed.active = inWorld;
            seed.setEditable(inWorld);
            version.active = inWorld;
            version.setMessage(text("Version: " + model.version(), COLUMN));
            search.active = inWorld;
            search.setMessage(text(model.searching() ? "Stop" : "Search", COLUMN));
        });
    }

    private void initPicker() {
        int span = 2 * COLUMN + GAP;
        filter = addRenderableWidget(new EditBox(font, left, row(0), span, HEIGHT, Component.literal("Filter versions")));
        filter.setResponder(text -> showChoices());
        int columns = Math.max(1, (width - 12) / (CHOICE + 4)), x = (width - columns * (CHOICE + 4) + 4) / 2;
        int slots = Math.min(columns * PICKER_ROWS, model.versions().size());
        for (int i = 0; i < slots; i++) {
            int index = i;
            choices.add(button(x + i % columns * (CHOICE + 4), row(1 + i / columns), CHOICE, "", () -> pick(shown.get(index))));
        }
        button(left, top + BOTTOM, span, "Back", this::closePicker);
        setInitialFocus(filter);
        showChoices();
    }

    private void showChoices() {
        String typed = filter.getValue().trim().toLowerCase(Locale.ROOT);
        shown = model.versions().stream().filter(v -> v.toLowerCase(Locale.ROOT).contains(typed)).toList();
        String current = model.version();
        for (int i = 0; i < choices.size(); i++) {
            Button choice = choices.get(i);
            choice.visible = i < shown.size();
            if (!choice.visible) continue;
            choice.setMessage(text(shown.get(i), CHOICE));
            choice.active = !shown.get(i).equals(current);
        }
    }

    private void toggle(int row, String name, BooleanSupplier on, Consumer<Boolean> set) {
        Button button = button(left, row(row), COLUMN, "", () -> set.accept(!on.getAsBoolean()));
        refreshers.add(() -> button.setMessage(text(name + ": " + (on.getAsBoolean() ? "ON" : "OFF"), COLUMN)));
    }

    private Button button(int x, int y, int width, String label, Runnable action) {
        return addRenderableWidget(Button.builder(text(label, width), b -> action.run()).bounds(x, y, width, HEIGHT).build());
    }

    private int row(int index) {
        return top + 16 + index * ROW;
    }

    private Component text(String label, int width) {
        return Component.literal(fit(label, width - 8));
    }

    String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private Label centered(String text, int y, int color) {
        String fitted = fit(text, width - 8);
        return new Label(fitted, (width - font.width(fitted)) / 2, y, color);
    }

    List<Label> labels() {
        List<Label> labels = new ArrayList<>();
        int span = 2 * COLUMN + GAP;
        if (picking) {
            labels.add(centered("World Version", top + 4, WHITE));
            int hidden = shown.size() - choices.size();
            if (shown.isEmpty()) labels.add(centered("No version matches", row(1) + 6, GRAY));
            if (hidden > 0) labels.add(centered(hidden + " more: type to narrow the list", top + STATUS, GRAY));
            return labels;
        }
        labels.add(centered("Dripleaf Finder", top + 4, WHITE));
        boolean inWorld = model.inWorld();
        labels.add(new Label("Seed", right, row(0) + 6, inWorld ? WHITE : GRAY));
        if (!inWorld) labels.add(new Label(fit("Join a world to set these", COLUMN), right, row(4) + 6, GRAY));
        labels.add(new Label(fit(model.status(), span), left, top + STATUS, WHITE));
        return labels;
    }

    @Override
    public void tick() {
        if (pageChanged) {
            pageChanged = false;
            rebuildWidgets();
        }
        refresh();
    }

    private void refresh() {
        refreshers.forEach(Runnable::run);
    }

    boolean enter() {
        if (picking || seed == null || getFocused() != seed) return false;
        saveSeed();
        return true;
    }

    private void saveSeed() {
        String typed = seedDraft.trim();
        String value = typed.isEmpty() ? null : typed;
        if (model.inWorld() && !Objects.equals(value, model.seed())) model.setSeed(value);
    }

    private void searchOrStop() {
        if (model.searching()) {
            model.stop();
            return;
        }
        saveSeed();
        model.search();
    }

    private void closeThen(Runnable action) {
        onClose();
        action.run();
    }

    private void openPicker() {
        picking = true;
        pageChanged = true;
    }

    private void closePicker() {
        picking = false;
        pageChanged = true;
    }

    private void pick(String choice) {
        model.setVersion(choice);
        closePicker();
    }

    boolean picking() {
        return picking;
    }

    @Override
    public void onClose() {
        if (picking) closePicker();
        else super.onClose();
    }

    @Override
    public void removed() {
        saveSeed();
    }

    final class Slider extends AbstractSliderButton {
        private final String name, unit;
        private final int min, max, step;
        private final IntConsumer set;

        Slider(int x, int y, String name, String unit, int min, int max, int step, int current, IntConsumer set) {
            super(x, y, COLUMN, HEIGHT, Component.empty(), max == min ? 0 : (current - min) / (double) (max - min));
            this.name = name;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            updateMessage();
        }

        int current() {
            return Math.min(max, min + (int) Math.round(value * (max - min) / step) * step);
        }

        @Override
        protected void updateMessage() {
            setMessage(text(name + ": " + current() + unit, COLUMN));
        }

        @Override
        protected void applyValue() {
            set.accept(current());
        }
    }
}
