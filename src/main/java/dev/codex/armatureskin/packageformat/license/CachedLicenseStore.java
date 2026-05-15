package dev.codex.armatureskin.packageformat.license;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;
import dev.codex.armatureskin.packageformat.Mc3dSkinLoadContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

public final class CachedLicenseStore implements LicenseProvider {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path licenseDir;

    public CachedLicenseStore(Path gameDir) {
        this.licenseDir = gameDir.resolve("config").resolve("armature-fbx-skin").resolve("licenses");
    }

    @Override
    public Optional<LicenseGrant> resolveContentKey(Mc3dSkinHeader header, Mc3dSkinLoadContext context) throws IOException {
        Path path = cachePath(header);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CachedGrant cached = GSON.fromJson(reader, CachedGrant.class);
            if (cached == null || cached.contentKey == null || cached.offlineUntil == null) {
                return Optional.empty();
            }
            Instant offlineUntil = Instant.parse(cached.offlineUntil);
            if (Instant.now().isAfter(offlineUntil)) {
                return Optional.empty();
            }
            return Optional.of(new LicenseGrant(Base64.getDecoder().decode(cached.contentKey), offlineUntil));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public void save(Mc3dSkinHeader header, LicenseGrant grant) throws IOException {
        if (grant.offlineUntil() == null) {
            return;
        }
        Files.createDirectories(licenseDir);
        CachedGrant cached = new CachedGrant(
                1,
                header.packageId(),
                header.packageVersion(),
                header.keyId(),
                Base64.getEncoder().encodeToString(grant.contentKey()),
                grant.offlineUntil().toString()
        );
        try (Writer writer = Files.newBufferedWriter(cachePath(header), StandardCharsets.UTF_8)) {
            GSON.toJson(cached, writer);
        }
    }

    private Path cachePath(Mc3dSkinHeader header) throws IOException {
        return licenseDir.resolve(cacheKey(header) + ".json");
    }

    private static String cacheKey(Mc3dSkinHeader header) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(nullToEmpty(header.packageId()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(nullToEmpty(header.packageVersion()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(nullToEmpty(header.keyId()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable.", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedGrant(int cacheVersion, String packageId, String packageVersion, String keyId, String contentKey, String offlineUntil) {
    }
}
