package dev.codex.armatureskin.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.codex.armatureskin.ArmatureSkinMod;
import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;
import dev.codex.armatureskin.packageformat.license.LicenseCrypto;
import dev.codex.armatureskin.packageformat.license.LicenseRequest;
import dev.codex.armatureskin.packageformat.license.SignedLicenseResponse;
import dev.codex.armatureskin.packageformat.license.WrappedContentKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MinecraftServerLicenseAuthority {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("armature-fbx-skin")
            .resolve("server-licenses.json");
    private static final SecureRandom RANDOM = new SecureRandom();

    private MinecraftServerLicenseAuthority() {
    }

    public static Optional<SignedLicenseResponse> createResponse(LicenseRequest request, ServerPlayer player) {
        if (request == null || player == null) {
            return Optional.empty();
        }
        if (!player.getUUID().toString().equalsIgnoreCase(request.minecraftUuid())) {
            return Optional.empty();
        }
        try {
            ServerLicenseConfig config = loadOrCreateConfig();
            PackageGrant grant = findGrant(config, request, player).orElse(null);
            if (grant == null) {
                ArmatureSkinMod.LOGGER.debug("No mc3dskin server grant for {} {}.", request.packageId(), request.packageVersion());
                return Optional.empty();
            }
            if (config.providerPrivateKey() == null || config.providerPrivateKey().isBlank()) {
                ArmatureSkinMod.LOGGER.warn("mc3dskin server grant exists, but providerPrivateKey is not configured.");
                return Optional.empty();
            }

            PublicKey clientPublicKey = KeyFactory.getInstance("X25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(request.devicePublicKey())));
            PrivateKey providerPrivateKey = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(config.providerPrivateKey())));
            byte[] contentKey = Base64.getDecoder().decode(grant.contentKey());
            Mc3dSkinHeader header = new Mc3dSkinHeader(
                    "mc3dskin",
                    1,
                    request.packageId(),
                    request.packageVersion(),
                    "",
                    "",
                    "aes_gcm_zip",
                    request.keyId(),
                    "",
                    "",
                    config.providerPublicKey(),
                    ""
            );
            WrappedContentKey wrapped = LicenseCrypto.wrapContentKeyForClient(
                    contentKey,
                    clientPublicKey,
                    header,
                    "mc3dskin-license:" + request.packageId() + ":" + request.packageVersion() + ":" + player.getUUID()
            );
            String devicePublicKeyHash = LicenseCrypto.devicePublicKeyHash(request.devicePublicKey());
            Instant now = Instant.now();
            Instant offlineUntil = now.plusSeconds(Math.max(60L, grant.offlineSeconds()));
            SignedLicenseResponse unsigned = new SignedLicenseResponse(
                    "mc3dskin-license",
                    1,
                    "granted",
                    request.packageId(),
                    request.packageVersion(),
                    UUID.randomUUID().toString(),
                    now.toString(),
                    offlineUntil.toString(),
                    offlineUntil.toString(),
                    request.keyId(),
                    player.getUUID().toString(),
                    devicePublicKeyHash,
                    config.serverId(),
                    request.clientNonce(),
                    randomNonce(),
                    config.providerKeyId(),
                    "Ed25519",
                    List.of(wrapped),
                    ""
            );
            String signature = LicenseCrypto.signResponse(unsigned, providerPrivateKey);
            return Optional.of(new SignedLicenseResponse(
                    unsigned.protocol(),
                    unsigned.protocolVersion(),
                    unsigned.status(),
                    unsigned.packageId(),
                    unsigned.packageVersion(),
                    unsigned.licenseId(),
                    unsigned.issuedAt(),
                    unsigned.expiresAt(),
                    unsigned.offlineUntil(),
                    unsigned.keyId(),
                    unsigned.minecraftUuid(),
                    unsigned.devicePublicKeyHash(),
                    unsigned.serverId(),
                    unsigned.clientNonce(),
                    unsigned.serverNonce(),
                    unsigned.providerKeyId(),
                    unsigned.signatureAlgorithm(),
                    unsigned.wrappedKeys(),
                    signature
            ));
        } catch (Exception ex) {
            ArmatureSkinMod.LOGGER.warn("Failed to create mc3dskin signed license response.", ex);
            return Optional.empty();
        }
    }

    private static ServerLicenseConfig loadOrCreateConfig() throws IOException {
        if (Files.notExists(CONFIG_PATH)) {
            Files.createDirectories(CONFIG_PATH.getParent());
            ServerLicenseConfig template = new ServerLicenseConfig(
                    1,
                    "replace-with-server-id",
                    "replace-with-provider-key-id",
                    "",
                    "",
                    List.of(new PackageGrant(
                            "example.package",
                            "1.0.0",
                            "content-key-1",
                            "base64-32-byte-content-key",
                            86_400L,
                            List.of("*")
                    ))
            );
            Files.writeString(CONFIG_PATH, GSON.toJson(template), StandardCharsets.UTF_8);
        }
        return GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), ServerLicenseConfig.class);
    }

    private static Optional<PackageGrant> findGrant(ServerLicenseConfig config, LicenseRequest request, ServerPlayer player) {
        if (config == null || config.grants() == null) {
            return Optional.empty();
        }
        return config.grants().stream()
                .filter(grant -> equals(request.packageId(), grant.packageId()))
                .filter(grant -> grant.packageVersion() == null || grant.packageVersion().isBlank() || equals(request.packageVersion(), grant.packageVersion()))
                .filter(grant -> equals(request.keyId(), grant.keyId()))
                .filter(grant -> isPlayerAllowed(grant, player))
                .findFirst();
    }

    private static boolean isPlayerAllowed(PackageGrant grant, ServerPlayer player) {
        if (grant.allowedPlayers() == null || grant.allowedPlayers().isEmpty()) {
            return false;
        }
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        return grant.allowedPlayers().stream().anyMatch(entry ->
                "*".equals(entry)
                        || equals(entry, uuid)
                        || equals(entry, name)
        );
    }

    private static String randomNonce() {
        byte[] nonce = new byte[24];
        RANDOM.nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    private static boolean equals(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private record ServerLicenseConfig(
            int version,
            String serverId,
            String providerKeyId,
            String providerPublicKey,
            String providerPrivateKey,
            List<PackageGrant> grants
    ) {
    }

    private record PackageGrant(
            String packageId,
            String packageVersion,
            String keyId,
            String contentKey,
            long offlineSeconds,
            List<String> allowedPlayers
    ) {
    }
}
