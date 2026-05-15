package dev.codex.armatureskin.packageformat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.codex.armatureskin.packageformat.license.CachedLicenseStore;
import dev.codex.armatureskin.packageformat.license.CompositeLicenseProvider;
import dev.codex.armatureskin.packageformat.license.HttpLicenseProvider;
import dev.codex.armatureskin.packageformat.license.LicenseGrant;
import dev.codex.armatureskin.packageformat.license.LicenseProvider;
import dev.codex.armatureskin.packageformat.license.ServerHandshakeLicenseProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Mc3dSkinPackageLoader {
    private static final byte[] MAGIC = new byte[]{'M', 'C', '3', 'D', 'S', 'K', '2', 0};
    private static final int GCM_TAG_BITS = 128;
    private static final Gson GSON = new GsonBuilder().create();

    private final LicenseProvider licenseProvider;

    public Mc3dSkinPackageLoader(LicenseProvider licenseProvider) {
        this.licenseProvider = licenseProvider;
    }

    public static Mc3dSkinPackageLoader defaultClientLoader(Mc3dSkinLoadContext context) {
        CachedLicenseStore cache = new CachedLicenseStore(context.gameDir());
        return new Mc3dSkinPackageLoader(new CompositeLicenseProvider(List.of(
                cache,
                new HttpLicenseProvider(),
                new ServerHandshakeLicenseProvider()
        ), cache));
    }

    public Mc3dSkinContent load(Path path, Mc3dSkinLoadContext context) throws IOException {
        byte[] file = Files.readAllBytes(path);
        if (startsWith(file, MAGIC)) {
            FramedPackage framed = readFramed(file);
            byte[] zipPayload = framed.header.encrypted()
                    ? decrypt(framed.header, framed.payload, context)
                    : framed.payload;
            return readZip(zipPayload);
        }
        return readZip(file);
    }

    private byte[] decrypt(Mc3dSkinHeader header, byte[] cipherText, Mc3dSkinLoadContext context) throws IOException {
        LicenseGrant grant = licenseProvider.resolveContentKey(header, context)
                .orElseThrow(() -> new IOException("No license key available for encrypted mc3dskin package: " + header.packageId()));
        byte[] nonce = Base64.getDecoder().decode(header.nonce());
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(grant.contentKey(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException ex) {
            throw new IOException("Failed to decrypt mc3dskin package: " + header.packageId(), ex);
        }
    }

    private static FramedPackage readFramed(byte[] file) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(file).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        int headerLength = buffer.getInt();
        if (headerLength <= 0 || headerLength > buffer.remaining()) {
            throw new IOException("Invalid mc3dskin header length.");
        }
        byte[] headerBytes = new byte[headerLength];
        buffer.get(headerBytes);
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);

        Mc3dSkinHeader header = GSON.fromJson(new String(headerBytes, StandardCharsets.UTF_8), Mc3dSkinHeader.class);
        if (header == null || !"mc3dskin".equals(header.format()) || header.formatVersion() != 1) {
            throw new IOException("Unsupported mc3dskin header.");
        }
        return new FramedPackage(header, payload);
    }

    private static Mc3dSkinContent readZip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = unzip(zipBytes);
        byte[] manifestBytes = entries.get("manifest.json");
        if (manifestBytes == null) {
            throw new IOException("mc3dskin package is missing manifest.json.");
        }
        Mc3dSkinManifest manifest = GSON.fromJson(new String(manifestBytes, StandardCharsets.UTF_8), Mc3dSkinManifest.class);
        if (manifest == null || manifest.model() == null || manifest.model().isBlank()) {
            throw new IOException("mc3dskin manifest is missing model.");
        }

        byte[] modelBytes = entries.get(normalizeEntry(manifest.model()));
        if (modelBytes == null) {
            throw new IOException("mc3dskin package is missing model entry: " + manifest.model());
        }

        List<Mc3dSkinContent.Texture> textures = new ArrayList<>();
        for (String texturePath : manifest.textures()) {
            byte[] bytes = entries.get(normalizeEntry(texturePath));
            if (bytes != null) {
                textures.add(new Mc3dSkinContent.Texture(texturePath, bytes));
            }
        }
        return new Mc3dSkinContent(manifest, modelBytes, textures);
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        long totalBytes = 0L;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeEntry(entry.getName());
                if (name.startsWith("../") || name.contains("/../")) {
                    throw new IOException("Unsafe mc3dskin entry: " + entry.getName());
                }
                byte[] bytes = zip.readAllBytes();
                totalBytes += bytes.length;
                if (entries.size() > 128 || totalBytes > 100L * 1024L * 1024L) {
                    throw new IOException("mc3dskin package exceeds safety limits.");
                }
                entries.put(name, bytes);
            }
        }
        return entries;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeEntry(String name) {
        return name.replace('\\', '/');
    }

    private record FramedPackage(Mc3dSkinHeader header, byte[] payload) {
    }
}
