package dev.codex.armatureskin.packageformat.license;

public record WrappedContentKey(
        String keyId,
        String algorithm,
        String ephemeralPublicKey,
        String nonce,
        String aad,
        String ciphertext
) {
}
