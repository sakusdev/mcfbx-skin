package dev.sakusdev.armatureskin.skin;

import dev.sakusdev.armatureskin.config.ArmatureSkinConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArmatureSkinManagerTest {
    @TempDir
    Path gameDir;

    @Test
    void discoversFbxVrmGlbAndGltfModelFiles() throws IOException {
        Path skinDir = gameDir.resolve(ArmatureSkinManager.SKIN_DIRECTORY);
        Files.createDirectories(skinDir.resolve("nested"));
        Files.writeString(skinDir.resolve("avatar.fbx"), "");
        Files.writeString(skinDir.resolve("avatar.vrm"), "");
        Files.writeString(skinDir.resolve("avatar.glb"), "");
        Files.writeString(skinDir.resolve("nested").resolve("avatar.gltf"), "");
        Files.writeString(skinDir.resolve("ignore.txt"), "");

        ArmatureSkinManager manager = ArmatureSkinManager.discover(gameDir, ArmatureSkinConfig.defaults());

        Set<String> ids = manager.availableSkins().stream()
                .map(ArmatureSkin::id)
                .collect(Collectors.toSet());
        assertEquals(Set.of("avatar", "avatar.vrm", "avatar.glb", "nested/avatar.gltf"), ids);
        assertEquals(4, manager.availableSkins().size());
    }
}
