package dev.codex.armatureskin.packageformat.license;

import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;
import dev.codex.armatureskin.packageformat.Mc3dSkinLoadContext;

import java.io.IOException;
import java.util.Optional;

public interface LicenseProvider {
    Optional<LicenseGrant> resolveContentKey(Mc3dSkinHeader header, Mc3dSkinLoadContext context) throws IOException;
}
