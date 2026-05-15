package dev.codex.armatureskin.packageformat.license;

public record LicenseRequest(
        String protocol,
        int protocolVersion,
        String packageId,
        String packageVersion,
        String keyId,
        String minecraftUuid,
        String minecraftName,
        String modId,
        String devicePublicKey,
        String clientNonce,
        String requestedAt
) {
}
