package dev.codex.armatureskin.fbx;

import dev.codex.armatureskin.model.ArmatureModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class FbxLoader {
    private static final byte[] BINARY_MAGIC = new byte[]{
            'K', 'a', 'y', 'd', 'a', 'r', 'a', ' ', 'F', 'B', 'X', ' ', 'B', 'i', 'n', 'a', 'r', 'y', ' ', ' ', 0, 0x1A, 0
    };

    public ArmatureModel load(Path path) throws IOException {
        byte[] prefix = Files.readAllBytes(path);
        return load(prefix, path.getFileName().toString());
    }

    public ArmatureModel load(byte[] data, String sourceName) throws IOException {
        try {
            return new AssimpFbxLoader().load(data, sourceName);
        } catch (IOException | RuntimeException | LinkageError assimpFailure) {
            try {
                return loadWithBuiltInParser(data, sourceName);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(assimpFailure);
                throw fallbackFailure;
            }
        }
    }

    private ArmatureModel loadWithBuiltInParser(byte[] data, String sourceName) throws IOException {
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
