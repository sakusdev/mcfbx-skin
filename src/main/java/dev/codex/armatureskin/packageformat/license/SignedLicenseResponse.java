package dev.codex.armatureskin.packageformat.license;

import java.util.List;

public record SignedLicenseResponse(
        String protocol,
        int protocolVersion,
        String status,
        String packageId,
        String packageVersion,
        String licenseId,
        String issuedAt,
        String expiresAt,
        String offlineUntil,
        String keyId,
        String minecraftUuid,
        String devicePublicKeyHash,
        String serverId,
        String clientNonce,
        String serverNonce,
        String providerKeyId,
        String signatureAlgorithm,
        List<WrappedContentKey> wrappedKeys,
        String signature
) {
    public List<WrappedContentKey> wrappedKeys() {
        return wrappedKeys == null ? List.of() : List.copyOf(wrappedKeys);
    }
}
