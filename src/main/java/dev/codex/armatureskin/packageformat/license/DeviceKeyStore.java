package dev.codex.armatureskin.packageformat.license;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class DeviceKeyStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    public DeviceKeyStore(Path gameDir) {
        this.path = gameDir.resolve("config").resolve("armature-fbx-skin").resolve("device-x25519-key.json");
    }

    public KeyPair loadOrCreate() throws IOException {
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                StoredKey stored = GSON.fromJson(reader, StoredKey.class);
                if (stored != null && stored.publicKey != null && stored.privateKey != null) {
                    return decode(stored);
                }
            } catch (RuntimeException | GeneralSecurityException ignored) {
                // Regenerate below if the local key file is corrupt.
            }
        }

        KeyPair keyPair = generate();
        save(keyPair);
        return keyPair;
    }

    private void save(KeyPair keyPair) throws IOException {
        Files.createDirectories(path.getParent());
        StoredKey stored = new StoredKey(
                1,
                "X25519",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
        );
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(stored, writer);
        }
    }

    private static KeyPair generate() throws IOException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            generator.initialize(new NamedParameterSpec("X25519"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IOException("Failed to create mc3dskin X25519 device key.", ex);
        }
    }

    private static KeyPair decode(StoredKey stored) throws GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("X25519");
        PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(stored.publicKey)));
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(stored.privateKey)));
        return new KeyPair(publicKey, privateKey);
    }

    private record StoredKey(int version, String algorithm, String publicKey, String privateKey) {
    }
}
