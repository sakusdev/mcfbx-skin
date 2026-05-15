package dev.codex.armatureskin.packageformat.license;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.codex.armatureskin.ArmatureSkinMod;
import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;
import dev.codex.armatureskin.packageformat.Mc3dSkinLoadContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

public final class HttpLicenseProvider implements LicenseProvider {
    private static final Gson GSON = new GsonBuilder().create();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Optional<LicenseGrant> resolveContentKey(Mc3dSkinHeader header, Mc3dSkinLoadContext context) throws IOException {
        if (header.licenseServerUrl() == null || header.licenseServerUrl().isBlank()) {
            return Optional.empty();
        }

        KeyPair deviceKeyPair = new DeviceKeyStore(context.gameDir()).loadOrCreate();
        String clientNonce = randomNonce();
        LicenseRequest body = new LicenseRequest(
                "mc3dskin-license",
                1,
                header.packageId(),
                header.packageVersion(),
                header.keyId(),
                context.playerUuid().toString(),
                context.playerName(),
                ArmatureSkinMod.MOD_ID,
                Base64.getEncoder().encodeToString(deviceKeyPair.getPublic().getEncoded()),
                clientNonce,
                Instant.now().toString()
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(header.licenseServerUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            SignedLicenseResponse grant = GSON.fromJson(response.body(), SignedLicenseResponse.class);
            if (grant == null || !clientNonce.equals(grant.clientNonce())) {
                return Optional.empty();
            }
            String devicePublicKeyHash = LicenseCrypto.devicePublicKeyHash(deviceKeyPair.getPublic());
            if (!context.playerUuid().toString().equalsIgnoreCase(grant.minecraftUuid())
                    || !devicePublicKeyHash.equals(grant.devicePublicKeyHash())) {
                return Optional.empty();
            }
            return Optional.of(LicenseCrypto.verifyAndUnwrap(grant, header, deviceKeyPair));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while requesting mc3dskin license.", ex);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid mc3dskin license response.", ex);
        }
    }

    private static String randomNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }
}
