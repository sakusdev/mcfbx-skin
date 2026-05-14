package dev.codex.armatureskin.fbx;

import dev.codex.armatureskin.model.ArmatureModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FbxLoader {
    private static final byte[] BINARY_MAGIC = new byte[]{
            'K', 'a', 'y', 'd', 'a', 'r', 'a', ' ', 'F', 'B', 'X', ' ', 'B', 'i', 'n', 'a', 'r', 'y', ' ', ' ', 0, 0x1A, 0
    };

    public ArmatureModel load(Path path) throws IOException {
        byte[] prefix = Files.readAllBytes(path);
        if (isBinary(prefix)) {
            return new BinaryFbxLoader().load(prefix);
        }
        return new AsciiFbxLoader().load(path);
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
