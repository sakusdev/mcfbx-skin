package dev.codex.armatureskin.packageformat.license;

import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;
import dev.codex.armatureskin.packageformat.Mc3dSkinLoadContext;

import java.io.IOException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerHandshakeLicenseProvider implements LicenseProvider {
    private static final Map<String, SignedLicenseResponse> PENDING_RESPONSES = new ConcurrentHashMap<>();
    private static final Map<String, LicenseRequest> PENDING_REQUESTS = new ConcurrentHashMap<>();

    public static void acceptServerResponse(SignedLicenseResponse response) {
        if (response != null && response.packageId() != null && response.keyId() != null) {
            PENDING_RESPONSES.put(cacheKey(response.packageId(), response.packageVersion(), response.keyId()), response);
        }
    }

    public static List<LicenseRequest> drainPendingRequests() {
        List<LicenseRequest> requests = new ArrayList<>(PENDING_REQUESTS.values());
        PENDING_REQUESTS.clear();
        return requests;
    }

    @Override
    public Optional<LicenseGrant> resolveContentKey(Mc3dSkinHeader header, Mc3dSkinLoadContext context) throws IOException {
        String key = cacheKey(header.packageId(), header.packageVersion(), header.keyId());
        SignedLicenseResponse response = PENDING_RESPONSES.remove(key);
        if (response == null) {
            PENDING_REQUESTS.putIfAbsent(key, createRequest(header, context));
            return Optional.empty();
        }
        KeyPair deviceKeyPair = new DeviceKeyStore(context.gameDir()).loadOrCreate();
        String devicePublicKeyHash = LicenseCrypto.devicePublicKeyHash(deviceKeyPair.getPublic());
        if (!context.playerUuid().toString().equalsIgnoreCase(response.minecraftUuid())
                || !devicePublicKeyHash.equals(response.devicePublicKeyHash())) {
            return Optional.empty();
        }
        return Optional.of(LicenseCrypto.verifyAndUnwrap(response, header, deviceKeyPair));
    }

    private static LicenseRequest createRequest(Mc3dSkinHeader header, Mc3dSkinLoadContext context) throws IOException {
        KeyPair deviceKeyPair = new DeviceKeyStore(context.gameDir()).loadOrCreate();
        return new LicenseRequest(
                "mc3dskin-license",
                1,
                header.packageId(),
                header.packageVersion(),
                header.keyId(),
                context.playerUuid().toString(),
                context.playerName(),
                "armature_fbx_skin",
                Base64.getEncoder().encodeToString(deviceKeyPair.getPublic().getEncoded()),
                randomNonce(),
                Instant.now().toString()
        );
    }

    private static String randomNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    private static String cacheKey(String packageId, String packageVersion, String keyId) {
        return String.join("\u0000",
                packageId == null ? "" : packageId,
                packageVersion == null ? "" : packageVersion,
                keyId == null ? "" : keyId);
    }
}
