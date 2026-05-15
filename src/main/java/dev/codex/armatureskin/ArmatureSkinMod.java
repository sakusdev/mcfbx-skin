package dev.codex.armatureskin;

import dev.codex.armatureskin.client.ArmatureSkinClient;
import dev.codex.armatureskin.network.Mc3dSkinNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ArmatureSkinMod.MOD_ID)
public final class ArmatureSkinMod {
    public static final String MOD_ID = "armature_fbx_skin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ArmatureSkinMod(IEventBus modBus) {
        modBus.addListener(Mc3dSkinNetwork::registerPayloads);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ArmatureSkinClient.init(modBus);
        }
    }
}
