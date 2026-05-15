package dev.codex.armatureskin.packageformat.license;

import java.time.Instant;

public record LicenseGrant(byte[] contentKey, Instant offlineUntil) {
    public LicenseGrant {
        contentKey = contentKey.clone();
    }

    @Override
    public byte[] contentKey() {
        return contentKey.clone();
    }
}
