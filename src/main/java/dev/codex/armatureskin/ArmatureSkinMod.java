package dev.codex.armatureskin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.codex.armatureskin.config.ArmatureSkinConfig;
import dev.codex.armatureskin.fbx.FbxLoader;
import dev.codex.armatureskin.model.ArmatureModel;
import dev.codex.armatureskin.render.ArmatureSkinRenderer;
import dev.codex.armatureskin.screen.ArmatureSkinSelectionApi;
import dev.codex.armatureskin.screen.ArmatureSkinSelectorScreen;
import dev.codex.armatureskin.skin.ArmatureSkin;
import dev.codex.armatureskin.skin.ArmatureSkinManager;
import dev.codex.armatureskin.skin.ArmatureSkinTexture;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Mod(value = ArmatureSkinMod.MOD_ID, dist = Dist.CLIENT)
public final class ArmatureSkinMod {
    public static final String MOD_ID = "armature_fbx_skin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ArmatureSkinRenderer RENDERER = new ArmatureSkinRenderer();
    private static ArmatureSkinConfig config = ArmatureSkinConfig.defaults();
    private static ArmatureSkinManager skinManager;
    private static ResourceLocation loadedTexture;
    private boolean initialReloadDone;

    private final KeyMapping reloadKey = new KeyMapping(
            "key.armature_fbx_skin.reload",
            GLFW.GLFW_KEY_R,
            "category.armature_fbx_skin"
    );
    private final KeyMapping selectorKey = new KeyMapping(
            "key.armature_fbx_skin.selector",
            GLFW.GLFW_KEY_K,
            "category.armature_fbx_skin"
    );

    public ArmatureSkinMod(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRenderPlayer);
    }

    public static ArmatureSkinRenderer renderer() {
        return RENDERER;
    }

    public static ArmatureSkinConfig config() {
        return config;
    }

    public static ArmatureSkinManager skinManager() {
        return skinManager;
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(reloadKey);
        event.register(selectorKey);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!initialReloadDone) {
            initialReloadDone = true;
            reloadModel(client, false);
        }
        while (reloadKey.consumeClick()) {
            reloadModel(client, true);
        }
        while (selectorKey.consumeClick()) {
            openSelector(client);
        }
    }

    private void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer player
                && RENDERER.renderPlayer(player, 0.0F, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight())) {
            event.setCanceled(true);
        }
    }

    private static void reloadModel(Minecraft client, boolean announce) {
        config = ArmatureSkinConfig.loadOrCreate(client.gameDirectory.toPath());
        skinManager = ArmatureSkinManager.discover(client.gameDirectory.toPath(), config);
        RENDERER.clear();

        if (!config.enabled()) {
            LOGGER.info("Armature FBX skin is disabled.");
            return;
        }

        Path fbxPath = skinManager.resolveSelectedPath().orElse(null);
        if (fbxPath == null || !Files.isRegularFile(fbxPath)) {
            LOGGER.warn("Armature FBX skin file was not found: {}", fbxPath);
            if (announce && client.player != null) {
                client.player.sendSystemMessage(Component.literal("No FBX skin found in " + client.gameDirectory.toPath().resolve(ArmatureSkinManager.SKIN_DIRECTORY) + ". Press K to open selector."));
            }
            return;
        }

        try {
            ArmatureModel model = new FbxLoader().load(fbxPath);
            ResourceLocation texture = loadSelectedTexture(client);
            RENDERER.setModel(model, config, texture);
            LOGGER.info("Loaded armature FBX skin from {} with texture {}", fbxPath, texture == null ? "player skin" : texture);
            if (announce && client.player != null) {
                client.player.sendSystemMessage(Component.literal("Reloaded armature FBX skin."));
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to load armature FBX skin from {}", fbxPath, ex);
            if (announce && client.player != null) {
                client.player.sendSystemMessage(Component.literal("Failed to load armature FBX skin: " + ex.getMessage()));
            }
        }
    }

    private static void openSelector(Minecraft client) {
        refreshSkins(client);
        client.setScreen(new ArmatureSkinSelectorScreen(client.screen, selectionApi(client)));
    }

    private static void refreshSkins(Minecraft client) {
        config = ArmatureSkinConfig.loadOrCreate(client.gameDirectory.toPath());
        skinManager = ArmatureSkinManager.discover(client.gameDirectory.toPath(), config);
    }

    private static ArmatureSkinSelectionApi selectionApi(Minecraft client) {
        return new ArmatureSkinSelectionApi() {
            @Override
            public List<SkinEntry> listSkins() {
                refreshSkins(client);
                return skinManager.availableSkins().stream()
                        .map(ArmatureSkinMod::toEntry)
                        .toList();
            }

            @Override
            public Optional<SkinEntry> selectedSkin() {
                refreshSkins(client);
                return skinManager.resolveSelectedSkin().map(ArmatureSkinMod::toEntry);
            }

            @Override
            public void selectSkin(SkinEntry skin) {
                ArmatureSkin selected = skinManager.findById(skin.id())
                        .orElseGet(() -> new ArmatureSkin(skin.id(), skin.displayName().getString(), skin.path()));
                config = config.withSelectedSkin(selected, client.gameDirectory.toPath());
                try {
                    config.save(client.gameDirectory.toPath());
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to save skin selection", ex);
                }
                reloadModel(client, true);
            }

            @Override
            public void reloadSkins() {
                reloadModel(client, true);
            }

            @Override
            public void openSkinsFolder() {
                Path folder = client.gameDirectory.toPath().resolve(ArmatureSkinManager.SKIN_DIRECTORY);
                try {
                    Files.createDirectories(folder);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to create FBX skin folder", ex);
                }
                Util.getPlatform().openFile(folder.toFile());
            }

            @Override
            public List<TextureEntry> listTextures(SkinEntry skin) {
                refreshSkins(client);
                ArmatureSkin selected = skinManager.findById(skin.id())
                        .orElseGet(() -> new ArmatureSkin(skin.id(), skin.displayName().getString(), skin.path()));
                return skinManager.availableTextures().stream()
                        .map(ArmatureSkinMod::toTextureEntry)
                        .toList();
            }

            @Override
            public Optional<TextureEntry> selectedTexture(SkinEntry skin) {
                refreshSkins(client);
                ArmatureSkin selected = skinManager.findById(skin.id())
                        .orElseGet(() -> new ArmatureSkin(skin.id(), skin.displayName().getString(), skin.path()));
                return skinManager.resolveSelectedTexture(selected).map(ArmatureSkinMod::toTextureEntry);
            }

            @Override
            public void selectTexture(SkinEntry skin, TextureEntry texture) {
                ArmatureSkinTexture selected = skinManager.findTextureById(texture.id())
                        .orElseGet(() -> new ArmatureSkinTexture(texture.id(), texture.displayName().getString(), texture.path()));
                config = config.withSelectedTexture(selected, client.gameDirectory.toPath());
                try {
                    config.save(client.gameDirectory.toPath());
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to save texture selection", ex);
                }
                reloadModel(client, true);
            }

            @Override
            public void reloadTextures(SkinEntry skin) {
                reloadModel(client, true);
            }

            @Override
            public void openTexturesFolder(SkinEntry skin) {
                Path folder = skin.path() == null || skin.path().getParent() == null
                        ? client.gameDirectory.toPath().resolve(ArmatureSkinManager.SKIN_DIRECTORY)
                        : skin.path().getParent();
                Util.getPlatform().openFile(folder.toFile());
            }
        };
    }

    private static ArmatureSkinSelectionApi.SkinEntry toEntry(ArmatureSkin skin) {
        return new ArmatureSkinSelectionApi.SkinEntry(skin.id(), Component.literal(skin.displayName()), skin.path());
    }

    private static ArmatureSkinSelectionApi.TextureEntry toTextureEntry(ArmatureSkinTexture texture) {
        return new ArmatureSkinSelectionApi.TextureEntry(texture.id(), Component.literal(texture.displayName()), texture.path());
    }

    private static ResourceLocation loadSelectedTexture(Minecraft client) throws IOException {
        releaseLoadedTexture(client);
        if (client.getTextureManager() == null) {
            return null;
        }
        Path texturePath = skinManager.resolveSelectedTexturePath().orElse(null);
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return null;
        }

        NativeImage image = null;
        try (InputStream input = Files.newInputStream(texturePath)) {
            image = NativeImage.read(input);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            image = null;
            loadedTexture = ResourceLocation.fromNamespaceAndPath(MOD_ID, "dynamic/armature_skin");
            client.getTextureManager().register(loadedTexture, dynamicTexture);
            return loadedTexture;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static void releaseLoadedTexture(Minecraft client) {
        if (loadedTexture != null) {
            client.getTextureManager().release(loadedTexture);
            loadedTexture = null;
        }
    }
}
