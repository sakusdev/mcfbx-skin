package dev.codex.armatureskin.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ArmatureSkinSelectorScreen extends Screen {
    private static final Component TITLE = Component.translatable("screen.armature_fbx_skin.selector.title");
    private static final Component EMPTY = Component.translatable("screen.armature_fbx_skin.selector.empty");
    private static final Component SELECTED = Component.translatable("screen.armature_fbx_skin.selector.selected");
    private static final Component RELOAD = Component.translatable("screen.armature_fbx_skin.selector.reload");
    private static final Component OPEN_FOLDER = Component.translatable("screen.armature_fbx_skin.selector.open_folder");
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component LOADED = Component.translatable("screen.armature_fbx_skin.selector.loaded");
    private static final Component RELOADED = Component.translatable("screen.armature_fbx_skin.selector.reloaded");
    private static final Component LOAD_FAILED = Component.translatable("screen.armature_fbx_skin.selector.load_failed");

    private static final int ROW_HEIGHT = 34;
    private static final int LIST_TOP = 42;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 36;
    private static final int SIDE_MARGIN = 32;

    private final Screen parent;
    private final ArmatureSkinSelectionApi api;
    private final List<ArmatureSkinSelectionApi.SkinEntry> skins = new ArrayList<>();

    private Button reloadButton;
    private Button openFolderButton;
    private Button backButton;
    private String selectedId = "";
    private Component statusMessage = Component.empty();
    private double scrollAmount;

    public ArmatureSkinSelectorScreen(Screen parent, ArmatureSkinSelectionApi api) {
        super(TITLE);
        this.parent = parent;
        this.api = api;
    }

    @Override
    protected void init() {
        reloadButton = Button.builder(RELOAD, button -> reloadSkins())
                .bounds(width / 2 - 154, height - 28, 98, BUTTON_HEIGHT)
                .build();
        openFolderButton = Button.builder(OPEN_FOLDER, button -> openSkinFolder())
                .bounds(width / 2 - 49, height - 28, 98, BUTTON_HEIGHT)
                .build();
        backButton = Button.builder(BACK, button -> onClose())
                .bounds(width / 2 + 56, height - 28, 98, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(reloadButton);
        addRenderableWidget(openFolderButton);
        addRenderableWidget(backButton);
        refreshSkinList(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        renderSkinList(guiGraphics, mouseX, mouseY);
        renderStatus(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideList(mouseX, mouseY)) {
            int index = rowAt(mouseY);
            if (index >= 0 && index < skins.size()) {
                selectSkin(skins.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInsideList(mouseX, mouseY) || skins.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollAmount = clampScroll(scrollAmount - scrollY * ROW_HEIGHT);
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void renderSkinList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = listLeft();
        int right = listRight();
        int top = LIST_TOP;
        int bottom = listBottom();

        guiGraphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xAA000000);
        guiGraphics.fill(left, top, right, bottom, 0x66000000);

        if (skins.isEmpty()) {
            guiGraphics.drawCenteredString(font, EMPTY, width / 2, top + (bottom - top) / 2 - 4, 0xA0A0A0);
            return;
        }

        scrollAmount = clampScroll(scrollAmount);
        guiGraphics.enableScissor(left, top, right, bottom);
        int rowY = top - (int) scrollAmount;
        for (int i = 0; i < skins.size(); i++) {
            renderRow(guiGraphics, skins.get(i), left, right, rowY, mouseX, mouseY);
            rowY += ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
    }

    private void renderRow(GuiGraphics guiGraphics, ArmatureSkinSelectionApi.SkinEntry skin, int left, int right, int y, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < LIST_TOP || y > listBottom()) {
            return;
        }

        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT;
        boolean selected = skin.id().equals(selectedId);
        int background = selected ? 0xAA4F8CFF : hovered ? 0x663A3A3A : 0x22000000;
        guiGraphics.fill(left + 2, y + 2, right - 2, y + ROW_HEIGHT - 2, background);

        Component name = selected ? Component.empty().append(SELECTED).append(" ").append(skin.displayName()) : skin.displayName();
        guiGraphics.drawString(font, trim(name, right - left - 24), left + 10, y + 7, 0xFFFFFF);

        Path path = skin.path();
        if (path != null) {
            Component pathText = Component.literal(path.toString()).withStyle(ChatFormatting.GRAY);
            guiGraphics.drawString(font, trim(pathText, right - left - 24), left + 10, y + 19, 0xA0A0A0);
        }
    }

    private void renderStatus(GuiGraphics guiGraphics) {
        if (statusMessage == null || statusMessage.getString().isBlank()) {
            return;
        }
        guiGraphics.drawCenteredString(font, trim(statusMessage, width - SIDE_MARGIN * 2), width / 2, height - 45, 0xA0FFA0);
    }

    private void refreshSkinList(boolean announce) {
        try {
            skins.clear();
            skins.addAll(api.listSkins());
            Optional<ArmatureSkinSelectionApi.SkinEntry> selected = api.selectedSkin();
            selectedId = selected.map(ArmatureSkinSelectionApi.SkinEntry::id).orElse("");
            scrollAmount = clampScroll(scrollAmount);
            if (announce) {
                statusMessage = Component.translatable("screen.armature_fbx_skin.selector.count", skins.size());
            }
        } catch (RuntimeException ex) {
            skins.clear();
            statusMessage = failureMessage(ex);
        }
    }

    private void reloadSkins() {
        try {
            api.reloadSkins();
            refreshSkinList(false);
            statusMessage = RELOADED;
        } catch (RuntimeException ex) {
            statusMessage = failureMessage(ex);
        }
    }

    private void openSkinFolder() {
        try {
            api.openSkinsFolder();
        } catch (RuntimeException ex) {
            statusMessage = failureMessage(ex);
        }
    }

    private void selectSkin(ArmatureSkinSelectionApi.SkinEntry skin) {
        try {
            api.selectSkin(skin);
            selectedId = skin.id();
            statusMessage = Component.empty().append(LOADED).append(" ").append(skin.displayName());
        } catch (RuntimeException ex) {
            statusMessage = failureMessage(ex);
        }
    }

    private Component failureMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return Component.empty().append(LOAD_FAILED).append(": ").append(message == null ? ex.getClass().getSimpleName() : message);
    }

    private Component trim(Component component, int maxWidth) {
        String text = component.getString();
        if (font.width(text) <= maxWidth) {
            return component;
        }
        return Component.literal(font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...");
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= listLeft() && mouseX <= listRight() && mouseY >= LIST_TOP && mouseY <= listBottom();
    }

    private int rowAt(double mouseY) {
        return (int) ((mouseY - LIST_TOP + scrollAmount) / ROW_HEIGHT);
    }

    private int listLeft() {
        return SIDE_MARGIN;
    }

    private int listRight() {
        return width - SIDE_MARGIN;
    }

    private int listBottom() {
        return Math.max(LIST_TOP + ROW_HEIGHT, height - FOOTER_HEIGHT - 18);
    }

    private double clampScroll(double value) {
        return Math.max(0.0D, Math.min(maxScroll(), value));
    }

    private double maxScroll() {
        return Math.max(0, skins.size() * ROW_HEIGHT - (listBottom() - LIST_TOP));
    }
}
