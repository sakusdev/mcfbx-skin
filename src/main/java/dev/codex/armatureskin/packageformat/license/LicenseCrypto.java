package dev.codex.armatureskin.packageformat.license;

import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.KeyPairGenerator;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

public final class LicenseCrypto {
    private static final int GCM_TAG_BITS = 128;

    private LicenseCrypto() {
    }

    public static LicenseGrant verifyAndUnwrap(SignedLicenseResponse response, Mc3dSkinHeader header, KeyPair deviceKeyPair) throws IOException {
        if (response == null || !"granted".equalsIgnoreCase(response.status())) {
            throw new IOException("mc3dskin license was not granted.");
        }
        if (!safeEquals(header.packageId(), response.packageId()) || !safeEquals(header.packageVersion(), response.packageVersion())) {
            throw new IOException("mc3dskin license response does not match package.");
        }
        verifySignature(response, header);
        WrappedContentKey wrapped = response.wrappedKeys().stream()
                .filter(key -> safeEquals(header.keyId(), key.keyId()))
                .findFirst()
                .orElseThrow(() -> new IOException("mc3dskin license response has no matching wrapped key."));
        byte[] contentKey = unwrapContentKey(wrapped, header, deviceKeyPair.getPrivate());
        Instant offlineUntil = response.offlineUntil() == null || response.offlineUntil().isBlank()
                ? Instant.now()
                : Instant.parse(response.offlineUntil());
        return new LicenseGrant(contentKey, offlineUntil);
    }

    public static void verifySignature(SignedLicenseResponse response, Mc3dSkinHeader header) throws IOException {
        if (!"Ed25519".equalsIgnoreCase(response.signatureAlgorithm())) {
            throw new IOException("Unsupported mc3dskin license signature algorithm: " + response.signatureAlgorithm());
        }
        if (header.licenseProviderPublicKey() == null || header.licenseProviderPublicKey().isBlank()) {
            throw new IOException("mc3dskin package does not declare a license provider public key.");
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(header.licenseProviderPublicKey())));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(canonicalResponseBytes(response));
            if (!signature.verify(Base64.getDecoder().decode(response.signature()))) {
                throw new IOException("Invalid mc3dskin license response signature.");
            }
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IOException("Failed to verify mc3dskin license signature.", ex);
        }
    }

    public static byte[] canonicalResponseBytes(SignedLicenseResponse response) {
        StringBuilder builder = new StringBuilder();
        append(builder, response.protocol());
        append(builder, response.protocolVersion());
        append(builder, response.status());
        append(builder, response.packageId());
        append(builder, response.packageVersion());
        append(builder, response.licenseId());
        append(builder, response.issuedAt());
        append(builder, response.expiresAt());
        append(builder, response.offlineUntil());
        append(builder, response.keyId());
        append(builder, response.minecraftUuid());
        append(builder, response.devicePublicKeyHash());
        append(builder, response.serverId());
        append(builder, response.clientNonce());
        append(builder, response.serverNonce());
        append(builder, response.providerKeyId());
        append(builder, response.signatureAlgorithm());
        for (WrappedContentKey key : response.wrappedKeys()) {
            append(builder, key.keyId());
            append(builder, key.algorithm());
            append(builder, key.ephemeralPublicKey());
            append(builder, key.nonce());
            append(builder, key.aad());
            append(builder, key.ciphertext());
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] unwrapContentKey(WrappedContentKey wrapped, Mc3dSkinHeader header, PrivateKey privateKey) throws IOException {
        if (!"X25519-HKDF-SHA256+A256GCM".equalsIgnoreCase(wrapped.algorithm())) {
            throw new IOException("Unsupported mc3dskin wrapped key algorithm: " + wrapped.algorithm());
        }
        try {
            PublicKey ephemeralPublicKey = KeyFactory.getInstance("X25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(wrapped.ephemeralPublicKey())));
            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(privateKey);
            agreement.doPhase(ephemeralPublicKey, true);
            byte[] sharedSecret = agreement.generateSecret();
            byte[] kek = hkdfSha256(sharedSecret, null, keyInfo(header, wrapped), 32);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(wrapped.nonce())));
            if (wrapped.aad() != null && !wrapped.aad().isBlank()) {
                cipher.updateAAD(wrapped.aad().getBytes(StandardCharsets.UTF_8));
            }
            return cipher.doFinal(Base64.getDecoder().decode(wrapped.ciphertext()));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IOException("Failed to unwrap mc3dskin content key.", ex);
        }
    }

    public static WrappedContentKey wrapContentKeyForClient(byte[] contentKey, PublicKey clientPublicKey, Mc3dSkinHeader header, String aad) throws IOException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            generator.initialize(new NamedParameterSpec("X25519"));
            java.security.KeyPair ephemeral = generator.generateKeyPair();

            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(clientPublicKey, true);
            byte[] sharedSecret = agreement.generateSecret();

            WrappedContentKey metadata = new WrappedContentKey(
                    header.keyId(),
                    "X25519-HKDF-SHA256+A256GCM",
                    Base64.getEncoder().encodeToString(ephemeral.getPublic().getEncoded()),
                    "",
                    aad,
                    ""
            );
            byte[] kek = hkdfSha256(sharedSecret, null, keyInfo(header, metadata), 32);
            byte[] nonce = new byte[12];
            new java.security.SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            if (aad != null && !aad.isBlank()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] ciphertext = cipher.doFinal(contentKey);
            return new WrappedContentKey(
                    header.keyId(),
                    "X25519-HKDF-SHA256+A256GCM",
                    Base64.getEncoder().encodeToString(ephemeral.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(nonce),
                    aad,
                    Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (GeneralSecurityException ex) {
            throw new IOException("Failed to wrap mc3dskin content key.", ex);
        }
    }

    public static String signResponse(SignedLicenseResponse response, PrivateKey providerPrivateKey) throws IOException {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(providerPrivateKey);
            signature.update(canonicalResponseBytes(response));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException ex) {
            throw new IOException("Failed to sign mc3dskin license response.", ex);
        }
    }

    public static String devicePublicKeyHash(PublicKey publicKey) throws IOException {
        return devicePublicKeyHash(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }

    public static String devicePublicKeyHash(String base64PublicKey) throws IOException {
        try {
            byte[] encoded = Base64.getDecoder().decode(base64PublicKey);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IOException("Failed to hash mc3dskin device public key.", ex);
        }
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) throws GeneralSecurityException, IOException {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] actualSalt = salt == null || salt.length == 0 ? new byte[mac.getMacLength()] : salt;
        mac.init(new SecretKeySpec(actualSalt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] previous = new byte[0];
        int counter = 1;
        while (output.size() < length) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter++);
            previous = mac.doFinal();
            output.write(previous);
        }
        return output.toByteArray().length == length ? output.toByteArray() : java.util.Arrays.copyOf(output.toByteArray(), length);
    }

    private static byte[] keyInfo(Mc3dSkinHeader header, WrappedContentKey wrapped) {
        return ("mc3dskin-license-wrap-v1\n" + nullToEmpty(header.packageId()) + "\n" + nullToEmpty(header.packageVersion()) + "\n" + nullToEmpty(wrapped.keyId()))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder builder, Object value) {
        builder.append(value == null ? "" : value).append('\n');
    }

    private static boolean safeEquals(String a, String b) {
        return nullToEmpty(a).equals(nullToEmpty(b));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
