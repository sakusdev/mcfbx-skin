package dev.codex.armatureskin.packageformat;

public record Mc3dSkinHeader(
        String format,
        int formatVersion,
        String packageId,
        String packageVersion,
        String displayName,
        String author,
        String contentMode,
        String keyId,
        String nonce,
        String licenseServerUrl,
        String licenseProviderPublicKey,
        String signature
) {
    public boolean encrypted() {
        return "aes_gcm_zip".equalsIgnoreCase(contentMode);
    }
}
