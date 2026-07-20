package dev.comfyfluffy.caustica.client.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A tree node whose header controls the visibility of its child layout. */
public final class CollapsibleLayout implements Layout {
    private static final int CONTENT_GAP = 4;
    private final TreeHeader header;
    private final BooleanSupplier collapsed;
    private LayoutElement content;
    private int x;
    private int y;

    public CollapsibleLayout(int width, Component title, BooleanSupplier collapsed, Runnable toggle) {
        this.collapsed = Objects.requireNonNull(collapsed);
        this.header = new TreeHeader(width, title, collapsed, Objects.requireNonNull(toggle));
    }

    public void setContent(LayoutElement content) {
        if (this.content != null) throw new IllegalStateException("Collapsible tree content already set");
        this.content = Objects.requireNonNull(content);
    }

    @Override
    public void arrangeElements() {
        header.setX(x);
        header.setY(y);
        if (content == null) return;
        content.setX(x);
        content.setY(y + header.getHeight() + CONTENT_GAP);
        if (content instanceof Layout layout) layout.arrangeElements();
        if (collapsed.getAsBoolean()) setHidden(content);
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        visitor.accept(header);
        if (content != null) visitor.accept(content);
    }

    @Override
    public void removeChildren() {
        content = null;
    }

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return header.getWidth();
    }

    @Override
    public int getHeight() {
        int contentHeight = collapsed.getAsBoolean() || content == null ? 0 : content.getHeight();
        return header.getHeight() + (contentHeight == 0 ? 0 : CONTENT_GAP + contentHeight);
    }

    private static void setHidden(LayoutElement element) {
        if (element instanceof AbstractWidget widget) {
            widget.visible = false;
            widget.active = false;
        }
        if (element instanceof Layout layout) layout.visitChildren(CollapsibleLayout::setHidden);
    }

    /** Clickable tree header with a compact disclosure marker. */
    public static final class TreeHeader extends AbstractButton {
        private final BooleanSupplier collapsed;
        private final Runnable toggle;

        public TreeHeader(int width, Component title, BooleanSupplier collapsed, Runnable toggle) {
            super(0, 0, width, 18, title);
            this.collapsed = collapsed;
            this.toggle = toggle;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            toggle.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int y = getY();
            g.fill(getX(), y + 2, getX() + 3, getBottom() - 2, 0xC0B9D9FF);
            g.fill(getX() + 3, y + 2, getRight(), getBottom() - 2,
                    isHoveredOrFocused() ? 0x48FFFFFF : 0x26000000);
            Component marker = Component.literal(collapsed.getAsBoolean() ? "> " : "v ");
            g.text(Minecraft.getInstance().font, marker, getX() + 9, y + 5, CausticaWidgets.TEXT);
            g.text(Minecraft.getInstance().font, getMessage(), getX() + 22, y + 5,
                    isHoveredOrFocused() ? CausticaWidgets.ACCENT : CausticaWidgets.TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.HINT,
                    Component.literal(collapsed.getAsBoolean() ? "Collapsed" : "Expanded"));
        }
    }
}
