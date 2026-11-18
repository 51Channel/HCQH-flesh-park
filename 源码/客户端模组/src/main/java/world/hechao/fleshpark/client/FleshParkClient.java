package world.hechao.fleshpark.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

/**
 * 原版物品模型只能用方盒，拼不出圆管：方盒转几个角度叠起来，角会戳在外面，截面是星形。
 * 所以肉索、手电和管线走原版的“特殊物品模型”入口，自己出顶点。服务端插件不用管这些，还是摆物品展示实体。
 */
public final class FleshParkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        register("lump", LumpRenderer.Unbaked.MAP_CODEC);
        register("flashlight", FlashlightRenderer.Unbaked.MAP_CODEC);
        register("pipe", PipeRenderer.Unbaked.MAP_CODEC);
        register("figure", FigureRenderer.Unbaked.MAP_CODEC);
        register("hand", HandRenderer.Unbaked.MAP_CODEC);
        register("pool", PoolRenderer.Unbaked.MAP_CODEC);
        register("burrow", BurrowRenderer.Unbaked.MAP_CODEC);
        register("grape", GrapeRenderer.Unbaked.MAP_CODEC);
        register("mesh", MeshRenderer.Unbaked.MAP_CODEC);
        register("tram", TramRenderer.Unbaked.MAP_CODEC);
        register("mist", MistRenderer.Unbaked.MAP_CODEC);
        register("wheel", WheelRenderer.Unbaked.MAP_CODEC);
        register("valve", ValveRenderer.Unbaked.MAP_CODEC);
        register("face", FaceRenderer.Unbaked.MAP_CODEC);
        register("shot", ShotRenderer.Unbaked.MAP_CODEC);
        register("road", Road.Unbaked.MAP_CODEC);
        register("steel", SteelRenderer.Unbaked.MAP_CODEC);
        MeshStore.loadInBackground();
        SteelRenderer.loadInBackground();
        Phone.register();
        DirectorScreen.register();

        PayloadTypeRegistry.playS2C().register(FxPayload.TYPE, FxPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> ClientFx.handle(payload.text()));
        ClientTickEvents.END_CLIENT_TICK.register(ClientFx::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Cam::tick);
        // 手上拿着纸的时候，右键是把纸收起来，不去点世界里的东西
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!Overlay.diaryOpen()) return;
            boolean pressed = false;
            while (client.options.keyUse.consumeClick()) pressed = true;
            // 按住不放也只算一下：收起来以后不会接着去点刚才那张纸
            client.options.keyUse.setDown(false);
            if (pressed) Overlay.closeDiary();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientFx::reset));

        // 这是一镜到底的实况，画面里不要血条饱食度这些。手电是真拿在手上的，快捷栏也不用
        for (Identifier hud : new Identifier[]{VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
                VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.STATUS_EFFECTS}) {
            HudElementRegistry.replaceElement(hud, original -> outsidePark(original));
        }
        // 入场片和收尾的时候不要准星
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
            if (!Overlay.cinematic()) original.render(graphics, delta);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fleshpark", "overlay"), Overlay::render);
    }

    private static HudElement outsidePark(HudElement original) {
        return (graphics, delta) -> {
            if (!ParkFog.applies(Minecraft.getInstance().level)) original.render(graphics, delta);
        };
    }

    private static void register(String name, com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked> codec) {
        SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath("fleshpark", name), codec);
    }
}
