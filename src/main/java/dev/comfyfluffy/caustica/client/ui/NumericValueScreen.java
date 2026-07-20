package dev.comfyfluffy.caustica.client.ui;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Small modal used by every Caustica numeric slider for exact keyboard entry. */
public final class NumericValueScreen extends Screen {
    private final Screen parent;
    private final Component label;
    private final double initialValue;
    private final DoubleConsumer apply;
    private EditBox valueBox;
    private Component error = Component.empty();

    public NumericValueScreen(Screen parent, Component label, double initialValue, DoubleConsumer apply) {
        super(Component.translatable("caustica.options.numericEntry.title"));
        this.parent = parent;
        this.label = label;
        this.initialValue = initialValue;
        this.apply = apply;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        valueBox = new EditBox(font, left, height / 2 - 12, 200, 20, label);
        valueBox.setValue(String.format(Locale.ROOT, "%.6f", initialValue).replaceFirst("\\.?0+$", ""));
        valueBox.setMaxLength(32);
        addRenderableWidget(valueBox);
        setInitialFocus(valueBox);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> submit())
                .bounds(left, height / 2 + 18, 97, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + 103, height / 2 + 18, 97, 20).build());
    }

    private void submit() {
        try {
            double value = Double.parseDouble(valueBox.getValue().trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            apply.accept(value);
            minecraft.setScreenAndShow(parent);
        } catch (NumberFormatException ignored) {
            error = Component.translatable("caustica.options.numericEntry.invalid");
            valueBox.setFocused(true);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 52, 0xFFFFFFFF);
        graphics.centeredText(font, label, width / 2, height / 2 - 35, 0xFFCCCCCC);
        if (!error.getString().isEmpty()) {
            graphics.centeredText(font, error, width / 2, height / 2 + 44, 0xFFFF7777);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
