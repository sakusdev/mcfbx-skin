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
    private static final Component SKINS = Component.translatable("screen.armature_fbx_skin.selector.skins");
    private static final Component TEXTURES = Component.translatable("screen.armature_fbx_skin.selector.textures");
    private static final Component EMPTY_SKINS = Component.translatable("screen.armature_fbx_skin.selector.empty");
    private static final Component EMPTY_TEXTURES = Component.translatable("screen.armature_fbx_skin.selector.empty_textures");
    private static final Component SELECTED = Component.translatable("screen.armature_fbx_skin.selector.selected");
    private static final Component RELOAD = Component.translatable("screen.armature_fbx_skin.selector.reload");
    private static final Component OPEN_FOLDER = Component.translatable("screen.armature_fbx_skin.selector.open_folder");
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component LOADED = Component.translatable("screen.armature_fbx_skin.selector.loaded");
    private static final Component TEXTURE_LOADED = Component.translatable("screen.armature_fbx_skin.selector.texture_loaded");
    private static final Component RELOADED = Component.translatable("screen.armature_fbx_skin.selector.reloaded");
    private static final Component LOAD_FAILED = Component.translatable("screen.armature_fbx_skin.selector.load_failed");

    private static final int TOP = 50;
    private static final int ROW_HEIGHT = 34;
    private static final int SIDE_MARGIN = 24;
    private static final int GAP = 12;
    private static final int FOOTER_HEIGHT = 58;

    private final Screen parent;
    private final ArmatureSkinSelectionApi api;
    private final List<ArmatureSkinSelectionApi.SkinEntry> skins = new ArrayList<>();
    private final List<ArmatureSkinSelectionApi.TextureEntry> textures = new ArrayList<>();

    private String selectedSkinId = "";
    private String selectedTextureId = "";
    private double skinScroll;
    private double textureScroll;
    private Component statusMessage = Component.empty();

    public ArmatureSkinSelectorScreen(Screen parent, ArmatureSkinSelectionApi api) {
        super(TITLE);
        this.parent = parent;
        this.api = api;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(RELOAD, button -> reloadAll())
                .bounds(width / 2 - 154, height - 28, 98, 20)
                .build());
        addRenderableWidget(Button.builder(OPEN_FOLDER, button -> openFolder())
                .bounds(width / 2 - 49, height - 28, 98, 20)
                .build());
        addRenderableWidget(Button.builder(BACK, button -> onClose())
                .bounds(width / 2 + 56, height - 28, 98, 20)
                .build());
        refresh(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xD0101010);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        guiGraphics.drawString(font, SKINS, leftX(), TOP - 16, 0xD8D8D8);
        guiGraphics.drawString(font, TEXTURES, rightX(), TOP - 16, 0xD8D8D8);
        renderSkinPanel(guiGraphics, mouseX, mouseY);
        renderTexturePanel(guiGraphics, mouseX, mouseY);
        if (!statusMessage.getString().isBlank()) {
            guiGraphics.drawCenteredString(font, trim(statusMessage, width - SIDE_MARGIN * 2), width / 2, height - 45, 0xA0FFA0);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inSkinPanel(mouseX, mouseY)) {
            int index = rowAt(mouseY, skinScroll);
            if (index >= 0 && index < skins.size()) {
                selectSkin(skins.get(index));
                return true;
            }
        }
        if (button == 0 && inTexturePanel(mouseX, mouseY)) {
            int index = rowAt(mouseY, textureScroll);
            if (index >= 0 && index < textures.size()) {
                selectTexture(textures.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inSkinPanel(mouseX, mouseY)) {
            skinScroll = clampScroll(skinScroll - scrollY * ROW_HEIGHT, skins.size());
            return true;
        }
        if (inTexturePanel(mouseX, mouseY)) {
            textureScroll = clampScroll(textureScroll - scrollY * ROW_HEIGHT, textures.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void renderSkinPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderPanel(guiGraphics, leftX(), panelWidth(), skins.isEmpty() ? EMPTY_SKINS : Component.empty());
        if (skins.isEmpty()) {
            return;
        }
        skinScroll = clampScroll(skinScroll, skins.size());
        guiGraphics.enableScissor(leftX(), TOP, leftX() + panelWidth(), bottom());
        int y = TOP - (int) skinScroll;
        for (ArmatureSkinSelectionApi.SkinEntry skin : skins) {
            renderRow(guiGraphics, leftX(), panelWidth(), y, skin.displayName(), skin.path(), skin.id().equals(selectedSkinId), mouseX, mouseY);
            y += ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
    }

    private void renderTexturePanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderPanel(guiGraphics, rightX(), panelWidth(), textures.isEmpty() ? EMPTY_TEXTURES : Component.empty());
        if (textures.isEmpty()) {
            return;
        }
        textureScroll = clampScroll(textureScroll, textures.size());
        guiGraphics.enableScissor(rightX(), TOP, rightX() + panelWidth(), bottom());
        int y = TOP - (int) textureScroll;
        for (ArmatureSkinSelectionApi.TextureEntry texture : textures) {
            renderRow(guiGraphics, rightX(), panelWidth(), y, texture.displayName(), texture.path(), texture.id().equals(selectedTextureId), mouseX, mouseY);
            y += ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
    }

    private void renderPanel(GuiGraphics guiGraphics, int x, int panelWidth, Component emptyText) {
        guiGraphics.fill(x - 1, TOP - 1, x + panelWidth + 1, bottom() + 1, 0xAA000000);
        guiGraphics.fill(x, TOP, x + panelWidth, bottom(), 0x66000000);
        if (!emptyText.getString().isBlank()) {
            guiGraphics.drawCenteredString(font, emptyText, x + panelWidth / 2, TOP + (bottom() - TOP) / 2 - 4, 0xA0A0A0);
        }
    }

    private void renderRow(GuiGraphics guiGraphics, int x, int panelWidth, int y, Component name, Path path, boolean selected, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < TOP || y > bottom()) {
            return;
        }
        boolean hovered = mouseX >= x && mouseX <= x + panelWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;
        int background = selected ? 0xAA4F8CFF : hovered ? 0x663A3A3A : 0x22000000;
        guiGraphics.fill(x + 2, y + 2, x + panelWidth - 2, y + ROW_HEIGHT - 2, background);

        Component rowTitle = selected ? Component.empty().append(SELECTED).append(" ").append(name) : name;
        guiGraphics.drawString(font, trim(rowTitle, panelWidth - 20), x + 8, y + 7, 0xFFFFFF);
        if (path != null) {
            guiGraphics.drawString(font, trim(Component.literal(path.toString()).withStyle(ChatFormatting.GRAY), panelWidth - 20), x + 8, y + 19, 0xA0A0A0);
        }
    }

    private void refresh(boolean announce) {
        skins.clear();
        textures.clear();
        skins.addAll(api.listSkins());
        Optional<ArmatureSkinSelectionApi.SkinEntry> selectedSkin = api.selectedSkin();
        selectedSkinId = selectedSkin.map(ArmatureSkinSelectionApi.SkinEntry::id).orElse("");
        selectedSkin.ifPresent(skin -> {
            textures.addAll(api.listTextures(skin));
            selectedTextureId = api.selectedTexture(skin).map(ArmatureSkinSelectionApi.TextureEntry::id).orElse("");
        });
        skinScroll = clampScroll(skinScroll, skins.size());
        textureScroll = clampScroll(textureScroll, textures.size());
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
            Optional<ArmatureSkinSelectionApi.SkinEntry> selected = skins.stream()
                    .filter(skin -> skin.id().equals(selectedSkinId))
                    .findFirst();
            if (selected.isPresent()) {
                api.openTexturesFolder(selected.get());
            } else {
                api.openSkinsFolder();
            }
        } catch (RuntimeException ex) {
            statusMessage = Component.empty().append(LOAD_FAILED).append(": ").append(ex.getMessage());
        }
    }

    private void selectSkin(ArmatureSkinSelectionApi.SkinEntry skin) {
        try {
            api.selectSkin(skin);
            selectedSkinId = skin.id();
            textures.clear();
            textures.addAll(api.listTextures(skin));
            selectedTextureId = api.selectedTexture(skin).map(ArmatureSkinSelectionApi.TextureEntry::id).orElse("");
            statusMessage = Component.empty().append(LOADED).append(" ").append(skin.displayName());
        } catch (RuntimeException ex) {
            statusMessage = Component.empty().append(LOAD_FAILED).append(": ").append(ex.getMessage());
        }
    }

    private void selectTexture(ArmatureSkinSelectionApi.TextureEntry texture) {
        Optional<ArmatureSkinSelectionApi.SkinEntry> selected = skins.stream()
                .filter(skin -> skin.id().equals(selectedSkinId))
                .findFirst();
        if (selected.isEmpty()) {
            return;
        }
        try {
            api.selectTexture(selected.get(), texture);
            selectedTextureId = texture.id();
            statusMessage = Component.empty().append(TEXTURE_LOADED).append(" ").append(texture.displayName());
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
        return mouseX >= leftX() && mouseX <= leftX() + panelWidth() && mouseY >= TOP && mouseY <= bottom();
    }

    private boolean inTexturePanel(double mouseX, double mouseY) {
        return mouseX >= rightX() && mouseX <= rightX() + panelWidth() && mouseY >= TOP && mouseY <= bottom();
    }

    private int rowAt(double mouseY, double scroll) {
        return (int) ((mouseY - TOP + scroll) / ROW_HEIGHT);
    }

    private double clampScroll(double value, int rowCount) {
        return Math.max(0.0D, Math.min(Math.max(0, rowCount * ROW_HEIGHT - (bottom() - TOP)), value));
    }

    private int leftX() {
        return SIDE_MARGIN;
    }

    private int rightX() {
        return SIDE_MARGIN + panelWidth() + GAP;
    }

    private int panelWidth() {
        return Math.max(120, (width - SIDE_MARGIN * 2 - GAP) / 2);
    }

    private int bottom() {
        return Math.max(TOP + ROW_HEIGHT, height - FOOTER_HEIGHT);
    }
}
