package dev.codex.armatureskin.packageformat.license;

import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public final class LicenseCryptoSanity {
    private LicenseCryptoSanity() {
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator ed25519 = KeyPairGenerator.getInstance("Ed25519");
        KeyPair provider = ed25519.generateKeyPair();

        KeyPairGenerator x25519 = KeyPairGenerator.getInstance("X25519");
        x25519.initialize(new NamedParameterSpec("X25519"));
        KeyPair device = x25519.generateKeyPair();

        Mc3dSkinHeader header = new Mc3dSkinHeader(
                "mc3dskin",
                1,
                "test.avatar",
                "1.0.0",
                "Test Avatar",
                "Sakutarooo",
                "aes_gcm_zip",
                "content-key-v1",
                "unused-package-nonce",
                "",
                Base64.getEncoder().encodeToString(provider.getPublic().getEncoded()),
                ""
        );

        byte[] contentKey = new byte[32];
        new SecureRandom().nextBytes(contentKey);
        WrappedContentKey wrapped = LicenseCrypto.wrapContentKeyForClient(contentKey, device.getPublic(), header, "mc3dskin-license:test.avatar");

        SignedLicenseResponse unsigned = new SignedLicenseResponse(
                "mc3dskin-license",
                1,
                "granted",
                "test.avatar",
                "1.0.0",
                "license-test",
                Instant.now().toString(),
                Instant.now().plusSeconds(3600).toString(),
                Instant.now().plusSeconds(600).toString(),
                "content-key-v1",
                "00000000-0000-0000-0000-000000000000",
                LicenseCrypto.devicePublicKeyHash(device.getPublic()),
                "test-server",
                "client-nonce",
                "server-nonce",
                "provider-test",
                "Ed25519",
                List.of(wrapped),
                ""
        );
        String signature = LicenseCrypto.signResponse(unsigned, provider.getPrivate());
        SignedLicenseResponse signed = new SignedLicenseResponse(
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
        );

        LicenseGrant grant = LicenseCrypto.verifyAndUnwrap(signed, header, device);
        require(java.util.Arrays.equals(contentKey, grant.contentKey()), "unwrapped content key should match original");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
