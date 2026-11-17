package dripleaffinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

final class MenuScreen extends Menu {
    MenuScreen(MenuModel model) {
        super(model);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        for (Label label : labels()) graphics.text(font, label.text(), label.x(), label.y(), label.color());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return event.key() == InputConstants.KEY_RETURN && enter() || super.keyPressed(event);
    }
}
