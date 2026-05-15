package dev.codex.armatureskin.fbx;

import dev.codex.armatureskin.model.ArmatureModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FbxLoader {
    private static final byte[] BINARY_MAGIC = new byte[]{
            'K', 'a', 'y', 'd', 'a', 'r', 'a', ' ', 'F', 'B', 'X', ' ', 'B', 'i', 'n', 'a', 'r', 'y', ' ', ' ', 0, 0x1A, 0
    };

    public ArmatureModel load(Path path) throws IOException {
        return load(Files.readAllBytes(path), path.toString());
    }

    public ArmatureModel load(byte[] data, String sourceName) throws IOException {
        if (isBinary(data)) {
            return new BinaryFbxLoader().load(data);
        }
        return new AsciiFbxLoader().loadText(new String(data, StandardCharsets.UTF_8), sourceName);
    }

    private static boolean isBinary(byte[] data) {
        if (data.length < BINARY_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < BINARY_MAGIC.length; i++) {
            if (data[i] != BINARY_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
