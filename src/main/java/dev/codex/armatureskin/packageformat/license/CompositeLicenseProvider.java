package dev.codex.armatureskin.packageformat.license;

import dev.codex.armatureskin.packageformat.Mc3dSkinHeader;
import dev.codex.armatureskin.packageformat.Mc3dSkinLoadContext;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class CompositeLicenseProvider implements LicenseProvider {
    private final List<LicenseProvider> providers;
    private final CachedLicenseStore cache;

    public CompositeLicenseProvider(List<LicenseProvider> providers, CachedLicenseStore cache) {
        this.providers = List.copyOf(providers);
        this.cache = cache;
    }

    @Override
    public Optional<LicenseGrant> resolveContentKey(Mc3dSkinHeader header, Mc3dSkinLoadContext context) throws IOException {
        for (LicenseProvider provider : providers) {
            Optional<LicenseGrant> grant = provider.resolveContentKey(header, context);
            if (grant.isPresent()) {
                if (!(provider instanceof CachedLicenseStore)) {
                    cache.save(header, grant.get());
                }
                return grant;
            }
        }
        return Optional.empty();
    }
}
