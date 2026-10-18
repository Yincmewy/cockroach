package yincmewy.cockroach.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yincmewy.cockroach.entity.CockroachEntity;
import yincmewy.cockroach.network.SizeNetwork;

public final class SizeEditorScreen extends Screen {
    private final int entityId;
    private final float originalSize;
    private EditBox value;
    private Button apply;

    public SizeEditorScreen(CockroachEntity entity) {
        super(Component.translatable("screen.cockroach.size.title"));
        entityId = entity.getId();
        originalSize = entity.getCockroachSize();
    }
    @Override
    protected void init() {
        int x = width / 2, y = height / 2;
        value = new EditBox(font, x - 100, y - 20, 200, 20, Component.translatable("screen.cockroach.size.value"));
        value.setMaxLength(16);
        value.setValue(Float.toString(originalSize));
        addRenderableWidget(value);
        apply = addRenderableWidget(Button.builder(Component.translatable("screen.cockroach.size.apply"), button -> applySize())
                .bounds(x - 100, y + 48, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(x + 4, y + 48, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.cockroach.size.reset"), button -> value.setValue("1.0"))
                .bounds(x - 100, y + 22, 200, 20).build());
        value.setResponder(text -> apply.active = parsedSize() != null);
        setInitialFocus(value);
    }
    private Float parsedSize() {
        try {
            float size = Float.parseFloat(value.getValue().trim());
            return CockroachEntity.isValidSize(size) ? size : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    private void applySize() {
        Float size = parsedSize();
        if (size == null) return;
        SizeNetwork.CHANNEL.sendToServer(new SizeNetwork.SetSize(entityId, size));
        onClose();
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && parsedSize() != null) { applySize(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.cockroach.size.hint"), width / 2, height / 2 - 40, 0xCCCCCC);
        if (parsedSize() == null) graphics.drawCenteredString(font, Component.translatable("screen.cockroach.size.invalid"), width / 2, height / 2 + 6, 0xFF7777);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override
    public boolean isPauseScreen() { return false; }
}
