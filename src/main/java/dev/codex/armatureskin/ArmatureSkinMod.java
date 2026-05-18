package dev.codex.armatureskin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.codex.armatureskin.config.ArmatureSkinConfig;
import dev.codex.armatureskin.fbx.FbxLoader;
import dev.codex.armatureskin.model.ArmatureModel;
import dev.codex.armatureskin.packageformat.Mc3dSkinContent;
import dev.codex.armatureskin.packageformat.Mc3dSkinPackage;
import dev.codex.armatureskin.render.ArmatureSkinRenderer;
import dev.codex.armatureskin.render.SkinRenderTexture;
import dev.codex.armatureskin.screen.ArmatureSkinSelectionApi;
import dev.codex.armatureskin.screen.ArmatureSkinSelectorScreen;
import dev.codex.armatureskin.skin.ArmatureSkin;
import dev.codex.armatureskin.skin.ArmatureSkinManager;
import dev.codex.armatureskin.skin.ArmatureSkinTexture;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
    private static SkinRenderTexture loadedTexture;
    private static SkinRenderTexture loadedFallbackTexture;
    private static ArmatureModel loadedModel;
    private static final Map<String, SkinRenderTexture> loadedMaterialTextures = new HashMap<>();
    private static final Map<String, SkinRenderTexture> loadedMeshTextures = new HashMap<>();
    private static final String MESH_ENTRY_PREFIX = "mesh:";
    private static final boolean DEBUG_CAPTURE = Boolean.getBoolean("armature_fbx_skin.debugCapture");
    private static final int DEBUG_CAPTURE_DELAY = Integer.getInteger("armature_fbx_skin.debugCaptureDelay", 60);
    private static final boolean DEBUG_CAPTURE_QUIT = Boolean.getBoolean("armature_fbx_skin.debugCaptureQuit");
    private static final String DEBUG_WORLD = System.getProperty("armature_fbx_skin.debugWorld", "DebugWorld");
    private boolean initialReloadDone;
    private boolean debugWorldOpenRequested;
    private int debugCaptureStage;
    private int debugCaptureTicks;

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
        tickDebugCapture(client);
    }

    private void tickDebugCapture(Minecraft client) {
        if (DEBUG_CAPTURE && client.player == null && client.level == null && !debugWorldOpenRequested && !DEBUG_WORLD.isBlank()) {
            debugWorldOpenRequested = true;
            try {
                if (client.getLevelSource().levelExists(DEBUG_WORLD)) {
                    LOGGER.info("Debug capture opening singleplayer world '{}'", DEBUG_WORLD);
                    client.createWorldOpenFlows().openWorld(DEBUG_WORLD, () -> client.setScreen(null));
                } else {
                    LOGGER.warn("Debug capture world '{}' was not found.", DEBUG_WORLD);
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to open debug capture world '{}'", DEBUG_WORLD, ex);
            }
        }
        if (!DEBUG_CAPTURE || debugCaptureStage >= 6 || client.player == null || client.level == null || loadedModel == null) {
            return;
        }
        if (debugCaptureStage == 0) {
            client.setScreen(null);
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            debugCaptureTicks = Math.max(10, DEBUG_CAPTURE_DELAY);
            debugCaptureStage = 1;
            LOGGER.info("Debug capture armed: third-person front in {} ticks", debugCaptureTicks);
            return;
        }
        if (debugCaptureStage == 1 && --debugCaptureTicks <= 0) {
            grabDebugScreenshot(client, "armature-fbx-debug-front.png");
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            debugCaptureTicks = 40;
            debugCaptureStage = 2;
            return;
        }
        if (debugCaptureStage == 2 && --debugCaptureTicks <= 0) {
            grabDebugScreenshot(client, "armature-fbx-debug-back.png");
            debugCaptureStage = 3;
            debugCaptureTicks = 20;
            return;
        }
        if (debugCaptureStage == 3 && --debugCaptureTicks <= 0) {
            openSelector(client);
            debugCaptureTicks = 40;
            debugCaptureStage = 4;
            return;
        }
        if (debugCaptureStage == 4 && --debugCaptureTicks <= 0) {
            grabDebugScreenshot(client, "armature-fbx-debug-selector.png");
            debugCaptureStage = 5;
            if (DEBUG_CAPTURE_QUIT) {
                client.stop();
            }
            return;
        }
        if (debugCaptureStage == 5) {
            debugCaptureStage = 6;
        }
    }

    private static void grabDebugScreenshot(Minecraft client, String fileName) {
        Screenshot.grab(client.gameDirectory, fileName, client.getMainRenderTarget(), message -> LOGGER.info("Saved debug screenshot {}: {}", fileName, message.getString()));
    }

    private void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
            if (RENDERER.renderPlayer(player, bodyYaw, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight())) {
                event.setCanceled(true);
            }
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
            SkinRenderTexture texture;
            Map<String, SkinRenderTexture> materialTextures;
            Map<String, SkinRenderTexture> meshTextures;
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
            LOGGER.info("Loaded armature FBX skin from {} with texture {}, {} material texture(s), and {} mesh texture override(s). bones={}, meshes={}, yawOffset={}, animation={}@{}",
                    fbxPath,
                    texture == null ? "none" : texture.location(),
                    materialTextures.size(),
                    meshTextures.size(),
                    model.bones().size(),
                    model.meshes().size(),
                    config.modelYawOffsetDegrees(),
                    config.animationEnabled(),
                    config.animationStrength());
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
                    Map<String, ArmatureSkinTexture> textureByBaseName = new HashMap<>();
                    for (ArmatureSkinTexture texture : skinManager.availableTextures()) {
                        textureByBaseName.put(normalizeName(baseName(texture.path().getFileName().toString())), texture);
                    }
                    for (ArmatureModel.Mesh mesh : model.meshes()) {
                        boolean hidden = config.isMeshDisabled(mesh.key());
                        String assignedTexture = config.meshTextureId(mesh.key());
                        String resolvedTexture = assignedTexture.isBlank()
                                ? Optional.ofNullable(bestTextureKey(mesh, textureByBaseName.keySet()))
                                .map(textureByBaseName::get)
                                .map(ArmatureSkinTexture::displayName)
                                .orElse("")
                                : textureDisplayName(assignedTexture);
                        String assignment = resolvedTexture.isBlank() ? "" : " -> " + resolvedTexture;
                        String hint = mesh.textureHint().isBlank() ? "" : " [" + mesh.textureHint() + "]";
                        Component label = Component.literal((hidden ? "[OFF] " : "[ON] ") + "Part: " + mesh.displayName() + hint + assignment);
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

    private static SkinRenderTexture loadSelectedTexture(Minecraft client) throws IOException {
        if (client.getTextureManager() == null) {
            return null;
        }
        Path texturePath = skinManager.resolveSelectedTexturePath().orElse(null);
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return null;
        }
        loadedTexture = loadTexture(client, texturePath, "dynamic/armature_skin");
        return loadedTexture;
    }

    private static SkinRenderTexture loadPackagedTexture(Minecraft client, Mc3dSkinContent content) throws IOException {
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

    private static Map<String, SkinRenderTexture> loadMaterialTextures(Minecraft client, ArmatureModel model) throws IOException {
        if (client.getTextureManager() == null) {
            return Map.of();
        }
        Map<String, ArmatureSkinTexture> textureByBaseName = new HashMap<>();
        for (ArmatureSkinTexture texture : skinManager.availableTextures()) {
            textureByBaseName.put(normalizeName(baseName(texture.path().getFileName().toString())), texture);
        }

        Map<String, SkinRenderTexture> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String materialKey = ArmatureSkinRenderer.normalizeMaterialName(mesh.materialName());
            String textureHintKey = ArmatureSkinRenderer.normalizeMaterialName(mesh.textureHint());
            if ((materialKey.isBlank() && textureHintKey.isBlank()) || textures.containsKey(materialKey) || textures.containsKey(textureHintKey)) {
                continue;
            }
            String textureKey = bestTextureKey(mesh, textureByBaseName.keySet());
            ArmatureSkinTexture matchingTexture = textureKey == null ? null : textureByBaseName.get(textureKey);
            if (matchingTexture == null || matchingTexture.path() == null || !Files.isRegularFile(matchingTexture.path())) {
                continue;
            }
            SkinRenderTexture location = loadTexture(client, matchingTexture.path(), "dynamic/material_" + index++);
            putTextureAlias(loadedMaterialTextures, materialKey, location);
            putTextureAlias(loadedMaterialTextures, textureHintKey, location);
            putTextureAlias(textures, materialKey, location);
            putTextureAlias(textures, textureHintKey, location);
            LOGGER.info("Matched FBX material '{}' hint '{}' to texture '{}'", mesh.materialName(), mesh.textureHint(), matchingTexture.path().getFileName());
        }
        return textures;
    }

    private static Map<String, SkinRenderTexture> loadAssignedMeshTextures(Minecraft client, ArmatureModel model) throws IOException {
        if (client.getTextureManager() == null || config.meshTextureAssignments().isEmpty()) {
            return Map.of();
        }

        Map<String, SkinRenderTexture> textures = new HashMap<>();
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
            SkinRenderTexture location = loadTexture(client, assigned.path(), "dynamic/mesh_" + index++);
            loadedMeshTextures.put(mesh.key(), location);
            textures.put(mesh.key(), location);
        }
        return textures;
    }

    private static Map<String, SkinRenderTexture> loadPackagedMaterialTextures(Minecraft client, ArmatureModel model, Mc3dSkinContent content) throws IOException {
        if (client.getTextureManager() == null) {
            return Map.of();
        }
        Map<String, Mc3dSkinContent.Texture> textureByBaseName = new HashMap<>();
        for (Mc3dSkinContent.Texture texture : content.textures()) {
            textureByBaseName.put(normalizeName(baseName(Path.of(texture.path()).getFileName().toString())), texture);
        }

        Map<String, SkinRenderTexture> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String materialKey = ArmatureSkinRenderer.normalizeMaterialName(mesh.materialName());
            String textureHintKey = ArmatureSkinRenderer.normalizeMaterialName(mesh.textureHint());
            if ((materialKey.isBlank() && textureHintKey.isBlank()) || textures.containsKey(materialKey) || textures.containsKey(textureHintKey)) {
                continue;
            }
            String textureKey = bestTextureKey(mesh, textureByBaseName.keySet());
            Mc3dSkinContent.Texture matchingTexture = textureKey == null ? null : textureByBaseName.get(textureKey);
            if (matchingTexture == null) {
                continue;
            }
            SkinRenderTexture location = loadTexture(client, matchingTexture.bytes(), "dynamic/material_" + index++);
            putTextureAlias(loadedMaterialTextures, materialKey, location);
            putTextureAlias(loadedMaterialTextures, textureHintKey, location);
            putTextureAlias(textures, materialKey, location);
            putTextureAlias(textures, textureHintKey, location);
        }
        return textures;
    }

    private static Map<String, SkinRenderTexture> loadPackagedAssignedMeshTextures(Minecraft client, ArmatureModel model, Mc3dSkinContent content) throws IOException {
        if (client.getTextureManager() == null || config.meshTextureAssignments().isEmpty()) {
            return Map.of();
        }

        Map<String, Mc3dSkinContent.Texture> texturesById = new HashMap<>();
        for (Mc3dSkinContent.Texture texture : content.textures()) {
            texturesById.put("package:" + texture.path(), texture);
            texturesById.put(stripTextureExtension(texture.path()), texture);
            texturesById.put(normalizeName(baseName(Path.of(texture.path()).getFileName().toString())), texture);
        }

        Map<String, SkinRenderTexture> textures = new HashMap<>();
        int index = 0;
        for (ArmatureModel.Mesh mesh : model.meshes()) {
            String textureId = config.meshTextureId(mesh.key());
            Mc3dSkinContent.Texture assigned = texturesById.get(textureId);
            if (assigned == null) {
                continue;
            }
            SkinRenderTexture location = loadTexture(client, assigned.bytes(), "dynamic/mesh_" + index++);
            loadedMeshTextures.put(mesh.key(), location);
            textures.put(mesh.key(), location);
        }
        return textures;
    }

    private static SkinRenderTexture loadTexture(Minecraft client, Path path, String id) throws IOException {
        return loadTexture(client, Files.readAllBytes(path), id);
    }

    private static SkinRenderTexture loadTexture(Minecraft client, byte[] bytes, String id) throws IOException {
        NativeImage image = null;
        try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
            image = NativeImage.read(input);
            SkinRenderTexture.AlphaMode alphaMode = alphaMode(image);
            if (config.forceOpaqueSkin() && alphaMode != SkinRenderTexture.AlphaMode.OPAQUE) {
                alphaMode = SkinRenderTexture.AlphaMode.CUTOUT;
            }
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            dynamicTexture.setFilter(true, false);
            image = null;
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
            client.getTextureManager().register(location, dynamicTexture);
            return new SkinRenderTexture(location, alphaMode);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static SkinRenderTexture loadFallbackTexture(Minecraft client) {
        if (client.getTextureManager() == null) {
            return null;
        }
        NativeImage image = null;
        try {
            image = new NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, 0xFFFFFFFF);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            image = null;
            loadedFallbackTexture = new SkinRenderTexture(ResourceLocation.fromNamespaceAndPath(MOD_ID, "dynamic/fallback_white"), SkinRenderTexture.AlphaMode.OPAQUE);
            client.getTextureManager().register(loadedFallbackTexture.location(), dynamicTexture);
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
            client.getTextureManager().release(loadedTexture.location());
            loadedTexture = null;
        }
        if (loadedFallbackTexture != null) {
            client.getTextureManager().release(loadedFallbackTexture.location());
            loadedFallbackTexture = null;
        }
        for (ResourceLocation texture : loadedMaterialTextures.values().stream().map(SkinRenderTexture::location).collect(java.util.stream.Collectors.toSet())) {
            client.getTextureManager().release(texture);
        }
        loadedMaterialTextures.clear();
        for (ResourceLocation texture : loadedMeshTextures.values().stream().map(SkinRenderTexture::location).collect(java.util.stream.Collectors.toSet())) {
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

    private static String compactName(String value) {
        return normalizeName(value).replaceAll("[^a-z0-9]+", "");
    }

    private static void putTextureAlias(Map<String, SkinRenderTexture> textures, String key, SkinRenderTexture texture) {
        if (key != null && !key.isBlank() && texture != null) {
            textures.put(key, texture);
        }
    }

    private static SkinRenderTexture.AlphaMode alphaMode(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long total = (long) width * height;
        long transparent = 0L;
        long partial = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                if (alpha < 255) {
                    transparent++;
                }
                if (alpha > 0 && alpha < 240) {
                    partial++;
                }
            }
        }
        if (transparent == 0L) {
            return SkinRenderTexture.AlphaMode.OPAQUE;
        }

        return partial > Math.max(16L, total / 4L)
                ? SkinRenderTexture.AlphaMode.TRANSLUCENT
                : SkinRenderTexture.AlphaMode.CUTOUT;
    }

    private static String bestTextureKey(ArmatureModel.Mesh mesh, java.util.Set<String> textureKeys) {
        List<String> candidates = List.of(mesh.textureHint(), mesh.materialName(), mesh.name());
        for (String candidate : candidates) {
            String exact = normalizeName(candidate);
            if (!exact.isBlank() && textureKeys.contains(exact)) {
                return exact;
            }
        }
        for (String candidate : candidates) {
            String match = bestTextureKey(candidate, textureKeys);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static String bestTextureKey(String value, java.util.Set<String> textureKeys) {
        String key = normalizeName(value);
        if (key.isBlank()) {
            return null;
        }
        String compactMaterial = compactName(key);
        return textureKeys.stream()
                .filter(textureKey -> {
                    String compactTexture = compactName(textureKey);
                    return textureKey.endsWith("_" + key)
                            || textureKey.endsWith("-" + key)
                            || textureKey.endsWith("." + key)
                            || compactTexture.endsWith(compactMaterial)
                            || compactTexture.contains(compactMaterial);
                })
                .min(java.util.Comparator.comparingInt(String::length))
                .orElse(null);
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
