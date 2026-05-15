package dev.codex.armatureskin.packageformat;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.UUID;

public record Mc3dSkinLoadContext(Path gameDir, UUID playerUuid, String playerName) {
    public static Mc3dSkinLoadContext fromMinecraft(Minecraft client) {
        UUID uuid = client.getUser() == null || client.getUser().getProfileId() == null
                ? new UUID(0L, 0L)
                : client.getUser().getProfileId();
        String name = client.getUser() == null ? "unknown" : client.getUser().getName();
        return new Mc3dSkinLoadContext(client.gameDirectory.toPath(), uuid, name);
    }
}
