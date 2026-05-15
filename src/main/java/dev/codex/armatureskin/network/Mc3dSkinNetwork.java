package dev.codex.armatureskin.network;

import com.google.gson.Gson;
import dev.codex.armatureskin.ArmatureSkinMod;
import dev.codex.armatureskin.packageformat.license.LicenseRequest;
import dev.codex.armatureskin.packageformat.license.ServerHandshakeLicenseProvider;
import dev.codex.armatureskin.packageformat.license.SignedLicenseResponse;
import dev.codex.armatureskin.server.MinecraftServerLicenseAuthority;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Mc3dSkinNetwork {
    private static final Gson GSON = new Gson();
    private static final int MAX_JSON_LENGTH = 64 * 1024;

    private Mc3dSkinNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ArmatureSkinMod.MOD_ID).versioned("1").optional();
        registrar.playToServer(LicenseRequestPayload.TYPE, LicenseRequestPayload.STREAM_CODEC, Mc3dSkinNetwork::handleLicenseRequest);
        registrar.playToClient(LicenseResponsePayload.TYPE, LicenseResponsePayload.STREAM_CODEC, Mc3dSkinNetwork::handleLicenseResponse);
    }

    public static void sendLicenseRequestToServer(LicenseRequest request) {
        try {
            PacketDistributor.sendToServer(new LicenseRequestPayload(GSON.toJson(request)));
        } catch (RuntimeException ex) {
            ArmatureSkinMod.LOGGER.debug("Unable to send mc3dskin license request to server.", ex);
        }
    }

    private static void handleLicenseRequest(LicenseRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                LicenseRequest request = GSON.fromJson(payload.json(), LicenseRequest.class);
                MinecraftServerLicenseAuthority.createResponse(request, player).ifPresent(response ->
                        PacketDistributor.sendToPlayer(player, new LicenseResponsePayload(GSON.toJson(response))));
            } catch (RuntimeException ex) {
                ArmatureSkinMod.LOGGER.warn("Failed to handle mc3dskin license request.", ex);
            }
        });
    }

    private static void handleLicenseResponse(LicenseResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                SignedLicenseResponse response = GSON.fromJson(payload.json(), SignedLicenseResponse.class);
                ServerHandshakeLicenseProvider.acceptServerResponse(response);
            } catch (RuntimeException ex) {
                ArmatureSkinMod.LOGGER.warn("Failed to handle mc3dskin license response.", ex);
            }
        });
    }

    public record LicenseRequestPayload(String json) implements CustomPacketPayload {
        public static final Type<LicenseRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ArmatureSkinMod.MOD_ID, "license_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LicenseRequestPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.json(), MAX_JSON_LENGTH),
                buffer -> new LicenseRequestPayload(buffer.readUtf(MAX_JSON_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LicenseResponsePayload(String json) implements CustomPacketPayload {
        public static final Type<LicenseResponsePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ArmatureSkinMod.MOD_ID, "license_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LicenseResponsePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.json(), MAX_JSON_LENGTH),
                buffer -> new LicenseResponsePayload(buffer.readUtf(MAX_JSON_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
