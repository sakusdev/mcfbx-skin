package dev.sakusdev.armatureskin.screen;

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
    private static final Component SKINS = Component.translatable("screen.armature_fbx_skin.selector.skins");
    private static final Component PARTS = Component.translatable("screen.armature_fbx_skin.selector.parts");
    private static final Component PREVIEW = Component.translatable("screen.armature_fbx_skin.selector.preview");
    private static final Component EMPTY_SKINS = Component.translatable("screen.armature_fbx_skin.selector.empty");
    private static final Component EMPTY_PARTS = Component.translatable("screen.armature_fbx_skin.selector.empty_parts");
    private static final Component NO_PREVIEW = Component.translatable("screen.armature_fbx_skin.selector.no_preview");
    private static final Component SELECTED = Component.translatable("screen.armature_fbx_skin.selector.selected");
    private static final Component RELOAD = Component.translatable("screen.armature_fbx_skin.selector.reload");
    private static final Component OPEN_FOLDER = Component.translatable("screen.armature_fbx_skin.selector.open_folder");
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component LOADED = Component.translatable("screen.armature_fbx_skin.selector.loaded");
    private static final Component RELOADED = Component.translatable("screen.armature_fbx_skin.selector.reloaded");
    private static final Component LOAD_FAILED = Component.translatable("screen.armature_fbx_skin.selector.load_failed");
    private static final Component PART_ON = Component.translatable("screen.armature_fbx_skin.selector.part_on");
    private static final Component PART_OFF = Component.translatable("screen.armature_fbx_skin.selector.part_off");

    private static final int TOP = 38;
    private static final int ROW_HEIGHT = 28;
    private static final int SIDE_MARGIN = 24;
    private static final int GAP = 14;
    private static final int FOOTER_HEIGHT = 42;
    private static final int SECTION_LABEL_HEIGHT = 12;

    private final Screen parent;
    private final ArmatureSkinSelectionApi api;
    private final List<ArmatureSkinSelectionApi.SkinEntry> skins = new ArrayList<>();
    private final List<ArmatureSkinSelectionApi.PartEntry> parts = new ArrayList<>();

    private String selectedSkinId = "";
    private double skinScroll;
    private double partScroll;
    private Component statusMessage = Component.empty();

    public ArmatureSkinSelectorScreen(Screen parent, ArmatureSkinSelectionApi api) {
        super(TITLE);
        this.parent = parent;
        this.api = api;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(RELOAD, button -> reloadAll())
                .bounds(width / 2 - 154, height - 24, 96, 20)
                .build());
        addRenderableWidget(Button.builder(OPEN_FOLDER, button -> openFolder())
                .bounds(width / 2 - 48, height - 24, 96, 20)
                .build());
        addRenderableWidget(Button.builder(BACK, button -> onClose())
                .bounds(width / 2 + 58, height - 24, 96, 20)
                .build());
        refresh(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xE0101010);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        guiGraphics.drawString(font, SKINS, leftX(), skinTop() - SECTION_LABEL_HEIGHT, 0xD8D8D8);
        guiGraphics.drawString(font, PARTS, leftX(), partsTop() - SECTION_LABEL_HEIGHT, 0xD8D8D8);
        guiGraphics.drawString(font, PREVIEW, previewX(), skinTop() - SECTION_LABEL_HEIGHT, 0xD8D8D8);
        renderSkinPanel(guiGraphics, mouseX, mouseY);
        renderPartsPanel(guiGraphics, mouseX, mouseY);
        renderPreviewPanel(guiGraphics, partialTick);
        if (!statusMessage.getString().isBlank()) {
            guiGraphics.drawCenteredString(font, trim(statusMessage, width - SIDE_MARGIN * 2), width / 2, height - 38, 0xA0FFA0);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inSkinPanel(mouseX, mouseY)) {
            int index = rowAt(mouseY, skinTop(), skinScroll);
            if (index >= 0 && index < skins.size()) {
                selectSkin(skins.get(index));
                return true;
            }
        }
        if (button == 0 && inPartsPanel(mouseX, mouseY)) {
            int index = rowAt(mouseY, partsTop(), partScroll);
            if (index >= 0 && index < parts.size()) {
                togglePart(parts.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inSkinPanel(mouseX, mouseY)) {
            skinScroll = clampScroll(skinScroll - scrollY * ROW_HEIGHT, skins.size(), skinHeight());
            return true;
        }
        if (inPartsPanel(mouseX, mouseY)) {
            partScroll = clampScroll(partScroll - scrollY * ROW_HEIGHT, parts.size(), partsHeight());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void renderSkinPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderPanel(guiGraphics, leftX(), skinTop(), leftWidth(), skinHeight(), skins.isEmpty() ? EMPTY_SKINS : Component.empty());
        if (skins.isEmpty()) {
            return;
        }
        skinScroll = clampScroll(skinScroll, skins.size(), skinHeight());
        guiGraphics.enableScissor(leftX(), skinTop(), leftX() + leftWidth(), skinTop() + skinHeight());
        int y = skinTop() - (int) skinScroll;
        for (ArmatureSkinSelectionApi.SkinEntry skin : skins) {
            renderSkinRow(guiGraphics, y, skin, mouseX, mouseY);
            y += ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
    }

    private void renderPartsPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderPanel(guiGraphics, leftX(), partsTop(), leftWidth(), partsHeight(), parts.isEmpty() ? EMPTY_PARTS : Component.empty());
        if (parts.isEmpty()) {
            return;
        }
        partScroll = clampScroll(partScroll, parts.size(), partsHeight());
        guiGraphics.enableScissor(leftX(), partsTop(), leftX() + leftWidth(), partsTop() + partsHeight());
        int y = partsTop() - (int) partScroll;
        for (ArmatureSkinSelectionApi.PartEntry part : parts) {
            renderPartRow(guiGraphics, y, part, mouseX, mouseY);
            y += ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
    }

    private void renderPreviewPanel(GuiGraphics guiGraphics, float partialTick) {
        int x = previewX();
        int y = skinTop();
        int previewWidth = previewWidth();
        int previewHeight = contentBottom() - skinTop();
        renderPanel(guiGraphics, x, y, previewWidth, previewHeight, Component.empty());
        boolean rendered = api.renderPreview(guiGraphics, x + 2, y + 2, previewWidth - 4, previewHeight - 4, partialTick);
        if (!rendered) {
            guiGraphics.drawCenteredString(font, NO_PREVIEW, x + previewWidth / 2, y + previewHeight / 2 - 4, 0xA0A0A0);
        }
    }

    private void renderPanel(GuiGraphics guiGraphics, int x, int y, int panelWidth, int panelHeight, Component emptyText) {
        guiGraphics.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, 0xAA000000);
        guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, 0x66000000);
        if (!emptyText.getString().isBlank()) {
            guiGraphics.drawCenteredString(font, emptyText, x + panelWidth / 2, y + panelHeight / 2 - 4, 0xA0A0A0);
        }
    }

    private void renderSkinRow(GuiGraphics guiGraphics, int y, ArmatureSkinSelectionApi.SkinEntry skin, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < skinTop() || y > skinTop() + skinHeight()) {
            return;
        }
        boolean selected = skin.id().equals(selectedSkinId);
        boolean hovered = mouseX >= leftX() && mouseX <= leftX() + leftWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT;
        int background = selected ? 0xAA4F8CFF : hovered ? 0x663A3A3A : 0x22000000;
        guiGraphics.fill(leftX() + 2, y + 2, leftX() + leftWidth() - 2, y + ROW_HEIGHT - 2, background);

        Component rowTitle = selected ? Component.empty().append(SELECTED).append(" ").append(skin.displayName()) : skin.displayName();
        guiGraphics.drawString(font, trim(rowTitle, leftWidth() - 18), leftX() + 8, y + 6, 0xFFFFFF);
        Path path = skin.path();
        if (path != null) {
            guiGraphics.drawString(font, trim(Component.literal(path.toString()).withStyle(ChatFormatting.GRAY), leftWidth() - 18), leftX() + 8, y + 18, 0xA0A0A0);
        }
    }

    private void renderPartRow(GuiGraphics guiGraphics, int y, ArmatureSkinSelectionApi.PartEntry part, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < partsTop() || y > partsTop() + partsHeight()) {
            return;
        }
        boolean hovered = mouseX >= leftX() && mouseX <= leftX() + leftWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT;
        int background = part.hidden() ? 0x66303030 : hovered ? 0x66433F2A : 0x22000000;
        int textColor = part.hidden() ? 0xA8A8A8 : 0xFFFFFF;
        Component state = part.hidden() ? PART_OFF : PART_ON;
        guiGraphics.fill(leftX() + 2, y + 2, leftX() + leftWidth() - 2, y + ROW_HEIGHT - 2, background);
        guiGraphics.drawString(font, trim(Component.empty().append(state).append(" ").append(part.displayName()), leftWidth() - 18), leftX() + 8, y + 11, textColor);
    }

    private void refresh(boolean announce) {
        skins.clear();
        parts.clear();
        skins.addAll(api.listSkins());
        Optional<ArmatureSkinSelectionApi.SkinEntry> selectedSkin = api.selectedSkin();
        selectedSkinId = selectedSkin.map(ArmatureSkinSelectionApi.SkinEntry::id).orElse("");
        selectedSkin.ifPresent(skin -> parts.addAll(api.listParts(skin)));
        skinScroll = clampScroll(skinScroll, skins.size(), skinHeight());
        partScroll = clampScroll(partScroll, parts.size(), partsHeight());
        if (announce) {
            statusMessage = RELOADED;
        }
    }

    private void reloadAll() {
        try {
            api.reloadSkins();
            refresh(true);
        } catch (RuntimeException ex) {
            statusMessage = Component.empty().append(LOAD_FAILED).append(": ").append(ex.getMessage());
        }
    }

    private void openFolder() {
        try {
            api.openSkinsFolder();
        } catch (RuntimeException ex) {
            statusMessage = Component.empty().append(LOAD_FAILED).append(": ").append(ex.getMessage());
        }
    }

    private void selectSkin(ArmatureSkinSelectionApi.SkinEntry skin) {
        try {
            api.selectSkin(skin);
            selectedSkinId = skin.id();
            parts.clear();
            parts.addAll(api.listParts(skin));
            partScroll = 0.0D;
            statusMessage = Component.empty().append(LOADED).append(" ").append(skin.displayName());
        } catch (RuntimeException ex) {
            statusMessage = Component.empty().append(LOAD_FAILED).append(": ").append(ex.getMessage());
        }
    }

    private void togglePart(ArmatureSkinSelectionApi.PartEntry part) {
        Optional<ArmatureSkinSelectionApi.SkinEntry> selected = skins.stream()
                .filter(skin -> skin.id().equals(selectedSkinId))
                .findFirst();
        if (selected.isEmpty()) {
            return;
        }
        try {
            api.toggleMesh(selected.get(), part.key());
            parts.clear();
            parts.addAll(api.listParts(selected.get()));
            statusMessage = Component.empty().append(part.hidden() ? PART_ON : PART_OFF).append(" ").append(part.displayName());
        } catch (RuntimeException ex) {
            statusMessage = Component.empty().append(LOAD_FAILED).append(": ").append(ex.getMessage());
        }
    }

    private Component trim(Component component, int maxWidth) {
        String text = component.getString();
        if (font.width(text) <= maxWidth) {
            return component;
        }
        return Component.literal(font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...");
    }

    private boolean inSkinPanel(double mouseX, double mouseY) {
        return mouseX >= leftX() && mouseX <= leftX() + leftWidth() && mouseY >= skinTop() && mouseY <= skinTop() + skinHeight();
    }

    private boolean inPartsPanel(double mouseX, double mouseY) {
        return mouseX >= leftX() && mouseX <= leftX() + leftWidth() && mouseY >= partsTop() && mouseY <= partsTop() + partsHeight();
    }

    private int rowAt(double mouseY, int top, double scroll) {
        return (int) ((mouseY - top + scroll) / ROW_HEIGHT);
    }

    private double clampScroll(double value, int rowCount, int panelHeight) {
        return Math.max(0.0D, Math.min(Math.max(0, rowCount * ROW_HEIGHT - panelHeight), value));
    }

    private int leftX() {
        return SIDE_MARGIN;
    }

    private int previewX() {
        return leftX() + leftWidth() + GAP;
    }

    private int leftWidth() {
        return Math.max(220, Math.min(360, (width - SIDE_MARGIN * 2 - GAP) * 2 / 5));
    }

    private int previewWidth() {
        return Math.max(180, width - previewX() - SIDE_MARGIN);
    }

    private int skinTop() {
        return TOP;
    }

    private int skinHeight() {
        return Math.max(ROW_HEIGHT * 2, Math.min(ROW_HEIGHT * 3, Math.max(ROW_HEIGHT * 2, contentHeight() / 4)));
    }

    private int partsTop() {
        return skinTop() + skinHeight() + SECTION_LABEL_HEIGHT + 8;
    }

    private int partsHeight() {
        return Math.max(ROW_HEIGHT, contentBottom() - partsTop());
    }

    private int contentHeight() {
        return Math.max(ROW_HEIGHT * 5, contentBottom() - TOP);
    }

    private int contentBottom() {
        return Math.max(TOP + ROW_HEIGHT, height - FOOTER_HEIGHT);
    }
}
