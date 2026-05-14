package dev.codex.armatureskin;

import dev.codex.armatureskin.config.ArmatureSkinConfig;
import dev.codex.armatureskin.fbx.AsciiFbxLoader;
import dev.codex.armatureskin.model.ArmatureModel;
import dev.codex.armatureskin.render.ArmatureSkinRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(value = ArmatureSkinMod.MOD_ID, dist = Dist.CLIENT)
public final class ArmatureSkinMod {
    public static final String MOD_ID = "armature_fbx_skin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ArmatureSkinRenderer RENDERER = new ArmatureSkinRenderer();
    private static ArmatureSkinConfig config = ArmatureSkinConfig.defaults();

    private final KeyMapping reloadKey = new KeyMapping(
            "key.armature_fbx_skin.reload",
            GLFW.GLFW_KEY_R,
            "category.armature_fbx_skin"
    );

    public ArmatureSkinMod(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        reloadModel(Minecraft.getInstance(), false);
    }

    public static ArmatureSkinRenderer renderer() {
        return RENDERER;
    }

    public static ArmatureSkinConfig config() {
        return config;
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(reloadKey);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        while (reloadKey.consumeClick()) {
            reloadModel(client, true);
        }
    }

    private static void reloadModel(Minecraft client, boolean announce) {
        config = ArmatureSkinConfig.loadOrCreate(client.gameDirectory.toPath());
        RENDERER.clear();

        if (!config.enabled()) {
            LOGGER.info("Armature FBX skin is disabled.");
            return;
        }

        Path fbxPath = config.resolveFbxPath(client.gameDirectory.toPath());
        if (fbxPath == null || !Files.isRegularFile(fbxPath)) {
            LOGGER.warn("Armature FBX skin file was not found: {}", fbxPath);
            if (announce && client.player != null) {
                client.player.sendSystemMessage(Component.literal("Armature FBX skin file not found: " + fbxPath));
            }
            return;
        }

        try {
            ArmatureModel model = new AsciiFbxLoader().load(fbxPath);
            RENDERER.setModel(model, config);
            LOGGER.info("Loaded armature FBX skin from {}", fbxPath);
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
}
