package dev.codex.armatureskin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.codex.armatureskin.config.ArmatureSkinConfig;
import dev.codex.armatureskin.fbx.FbxLoader;
import dev.codex.armatureskin.model.ArmatureModel;
import dev.codex.armatureskin.packageformat.Mc3dSkinContent;
import dev.codex.armatureskin.packageformat.Mc3dSkinPackage;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Mod(value = ArmatureSkinMod.MOD_ID, dist = Dist.CLIENT)
public final class ArmatureSkinMod {
    public static final String MOD_ID = "armature_fbx_skin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ArmatureSkinRenderer RENDERER = new ArmatureSkinRenderer();
    private static ArmatureSkinConfig config = ArmatureSkinConfig.defaults();
    private static ArmatureSkinManager skinManager;
    private static ResourceLocation loadedTexture;
    private static ResourceLocation loadedFallbackTexture;
    private static ArmatureModel loadedModel;
    private static final Map<String, ResourceLocation> loadedMaterialTextures = new HashMap<>();
    private static final Map<String, ResourceLocation> loadedMeshTextures = new HashMap<>();
    private static final String MESH_ENTRY_PREFIX = "mesh:";
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
        loadedModel = null;

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
            releaseLoadedTexture(client);
            ArmatureSkin selectedSkin = skinManager.resolveSelectedSkin().orElse(null);
            ArmatureModel model;
            ResourceLocation texture;
            Map<String, ResourceLocation> materialTextures;
            Map<String, ResourceLocation> meshTextures;
            if (selectedSkin != null && selectedSkin.packageSkin()) {
                Mc3dSkinContent content = Mc3dSkinPackage.read(fbxPath);
                model = new FbxLoader().load(content.modelBytes(), content.manifest().model());
                texture = loadPackagedTexture(client, content);
                materialTextures = loadPackagedMaterialTextures(client, model, content);
                meshTextures = loadPackagedAssignedMeshTextures(client, model, content);
            } else {
                model = new FbxLoader().load(fbxPath);
                texture = loadSelectedTexture(client);
                materialTextures = loadMaterialTextures(client, model);
                meshTextures = loadAssignedMeshTextures(client, model);
            }
            if (texture == null) {
                texture = loadFallbackTexture(client);
            }
            loadedModel = model;
            RENDERER.setModel(model, config, texture, materialTextures, meshTextures);
            LOGGER.info("Loaded armature FBX skin from {} with texture {}, {} material texture(s), and {} mesh texture override(s)", fbxPath, texture == null ? "none" : texture, materialTextures.size(), meshTextures.size());
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
                List<TextureEntry> textureEntries = new java.util.ArrayList<>(skinManager.availableTextures().stream()
                        .map(ArmatureSkinMod::toTextureEntry)
                        .toList());
                ArmatureModel model = loadedModel;
                if (model != null && selected.id().equals(config.selectedSkinId())) {
                    for (ArmatureModel.Mesh mesh : model.meshes()) {
                        boolean hidden = config.isMeshDisabled(mesh.key());
                        String assignedTexture = config.meshTextureId(mesh.key());
                        String assignment = assignedTexture.isBlank() ? "" : " -> " + textureDisplayName(assignedTexture);
                        Component label = Component.literal((hidden ? "[OFF] " : "[ON] ") + "Part: " + mesh.displayName() + assignment);
                        textureEntries.add(new TextureEntry(MESH_ENTRY_PREFIX + mesh.key(), label, null));
                    }
                }
                return textureEntries;
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
            public void assignTextureToMesh(SkinEntry skin, String meshKey, TextureEntry texture) {
                config = config.withMeshTexture(meshKey, texture.id());
                try {
                    config.save(client.gameDirectory.toPath());
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to save mesh texture assignment", ex);
                }
                reloadModel(client, true);
            }

            @Override
            public void toggleMesh(SkinEntry skin, String meshKey) {
                config = config.withToggledMesh(meshKey);
                try {
                    config.save(client.gameDirectory.toPath());
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to save mesh visibility", ex);
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

            @Override
            public Path packageSelectedSkin(SkinEntry skin) {
                refreshSkins(client);
                ArmatureSkin selected = skinManager.findById(skin.id())
                        .orElseThrow(() -> new IllegalStateException("Selected FBX skin is no longer available."));
                ArmatureSkinTexture preferred = skinManager.resolveSelectedTexture(selected).orElse(null);
                try {
                    Path output = Mc3dSkinPackage.writePlain(client.gameDirectory.toPath(), selected, selected.availableTextures(), preferred);
                    config = config.withSelectedSkin(new ArmatureSkin(stripSkinExtension(output.getFileName().toString()), stripSkinExtension(output.getFileName().toString()), output), client.gameDirectory.toPath());
                    config.save(client.gameDirectory.toPath());
                    reloadModel(client, true);
                    return output;
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to package mc3dskin", ex);
                }
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

    private static ResourceLocation loadPackagedTexture(Minecraft client, Mc3dSkinContent content) throws IOException {
        if (client.getTextureManager() == null) {
            return null;
        }
        Mc3dSkinContent.Texture texture = content.preferredTexture().orElse(null);
        if (texture == null) {
            return null;
        }
        loadedTexture = loadTexture(client, texture.bytes(), "dynamic/armature_skin");
        return loadedTexture;
    }

    private static Map<String, ResourceLocation> loadMaterialTextures(Minecraft client, ArmatureModel model) throws IOException {
        if (client.getTextureManager() == null) {
            return Map.of();
        }
        Map<String, ArmatureSkinTexture> textureByBaseName = new HashMap<>();
        for (ArmatureSkinTexture texture : skinManager.availableTextures()) {
            textureByBaseName.put(normalizeName(baseName(texture.path().getFileName().toString())), texture);
        }

        Map<String, ResourceLocation> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String materialKey = ArmatureSkinRenderer.normalizeMaterialName(mesh.materialName());
            if (materialKey.isBlank() || textures.containsKey(materialKey)) {
                continue;
            }
            ArmatureSkinTexture matchingTexture = textureByBaseName.get(normalizeName(mesh.materialName()));
            if (matchingTexture == null || matchingTexture.path() == null || !Files.isRegularFile(matchingTexture.path())) {
                continue;
            }
            ResourceLocation location = loadTexture(client, matchingTexture.path(), "dynamic/material_" + index++);
            loadedMaterialTextures.put(materialKey, location);
            textures.put(materialKey, location);
        }
        return textures;
    }

    private static Map<String, ResourceLocation> loadAssignedMeshTextures(Minecraft client, ArmatureModel model) throws IOException {
        if (client.getTextureManager() == null || config.meshTextureAssignments().isEmpty()) {
            return Map.of();
        }

        Map<String, ResourceLocation> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String textureId = config.meshTextureId(mesh.key());
            if (textureId.isBlank()) {
                continue;
            }
            ArmatureSkinTexture assigned = skinManager.findTextureById(textureId).orElse(null);
            if (assigned == null || assigned.path() == null || !Files.isRegularFile(assigned.path())) {
                continue;
            }
            ResourceLocation location = loadTexture(client, assigned.path(), "dynamic/mesh_" + index++);
            loadedMeshTextures.put(mesh.key(), location);
            textures.put(mesh.key(), location);
        }
        return textures;
    }

    private static Map<String, ResourceLocation> loadPackagedMaterialTextures(Minecraft client, ArmatureModel model, Mc3dSkinContent content) throws IOException {
        if (client.getTextureManager() == null) {
            return Map.of();
        }
        Map<String, Mc3dSkinContent.Texture> textureByBaseName = new HashMap<>();
        for (Mc3dSkinContent.Texture texture : content.textures()) {
            textureByBaseName.put(normalizeName(baseName(Path.of(texture.path()).getFileName().toString())), texture);
        }

        Map<String, ResourceLocation> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String materialKey = ArmatureSkinRenderer.normalizeMaterialName(mesh.materialName());
            if (materialKey.isBlank() || textures.containsKey(materialKey)) {
                continue;
            }
            Mc3dSkinContent.Texture matchingTexture = textureByBaseName.get(normalizeName(mesh.materialName()));
            if (matchingTexture == null) {
                continue;
            }
            ResourceLocation location = loadTexture(client, matchingTexture.bytes(), "dynamic/material_" + index++);
            loadedMaterialTextures.put(materialKey, location);
            textures.put(materialKey, location);
        }
        return textures;
    }

    private static Map<String, ResourceLocation> loadPackagedAssignedMeshTextures(Minecraft client, ArmatureModel model, Mc3dSkinContent content) throws IOException {
        if (client.getTextureManager() == null || config.meshTextureAssignments().isEmpty()) {
            return Map.of();
        }

        Map<String, Mc3dSkinContent.Texture> texturesById = new HashMap<>();
        for (Mc3dSkinContent.Texture texture : content.textures()) {
            texturesById.put("package:" + texture.path(), texture);
            texturesById.put(stripTextureExtension(texture.path()), texture);
            texturesById.put(normalizeName(baseName(Path.of(texture.path()).getFileName().toString())), texture);
        }

        Map<String, ResourceLocation> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String textureId = config.meshTextureId(mesh.key());
            Mc3dSkinContent.Texture assigned = texturesById.get(textureId);
            if (assigned == null) {
                continue;
            }
            ResourceLocation location = loadTexture(client, assigned.bytes(), "dynamic/mesh_" + index++);
            loadedMeshTextures.put(mesh.key(), location);
            textures.put(mesh.key(), location);
        }
        return textures;
    }

    private static ResourceLocation loadTexture(Minecraft client, Path path, String id) throws IOException {
        return loadTexture(client, Files.readAllBytes(path), id);
    }

    private static ResourceLocation loadTexture(Minecraft client, byte[] bytes, String id) throws IOException {
        NativeImage image = null;
        try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
            image = NativeImage.read(input);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            image = null;
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
            client.getTextureManager().register(location, dynamicTexture);
            return location;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static ResourceLocation loadFallbackTexture(Minecraft client) {
        if (client.getTextureManager() == null) {
            return null;
        }
        NativeImage image = null;
        try {
            image = new NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, 0xFFFFFFFF);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            image = null;
            loadedFallbackTexture = ResourceLocation.fromNamespaceAndPath(MOD_ID, "dynamic/fallback_white");
            client.getTextureManager().register(loadedFallbackTexture, dynamicTexture);
            return loadedFallbackTexture;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static void releaseLoadedTexture(Minecraft client) {
        if (client.getTextureManager() == null) {
            loadedTexture = null;
            loadedFallbackTexture = null;
            loadedMaterialTextures.clear();
            loadedMeshTextures.clear();
            return;
        }
        if (loadedTexture != null) {
            client.getTextureManager().release(loadedTexture);
            loadedTexture = null;
        }
        if (loadedFallbackTexture != null) {
            client.getTextureManager().release(loadedFallbackTexture);
            loadedFallbackTexture = null;
        }
        for (ResourceLocation texture : loadedMaterialTextures.values()) {
            client.getTextureManager().release(texture);
        }
        loadedMaterialTextures.clear();
        for (ResourceLocation texture : loadedMeshTextures.values()) {
            client.getTextureManager().release(texture);
        }
        loadedMeshTextures.clear();
    }

    private static String textureDisplayName(String textureId) {
        return skinManager.findTextureById(textureId)
                .map(ArmatureSkinTexture::displayName)
                .orElse(textureId);
    }

    private static String baseName(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart < 0 ? fileName : fileName.substring(0, extensionStart);
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private static String stripSkinExtension(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mc3dskin")) {
            return value.substring(0, value.length() - ".mc3dskin".length());
        }
        if (lower.endsWith(".fbx")) {
            return value.substring(0, value.length() - ".fbx".length());
        }
        return value;
    }

    private static String stripTextureExtension(String value) {
        String fileName = Path.of(value).getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart < 0 ? fileName : fileName.substring(0, extensionStart);
    }
}
