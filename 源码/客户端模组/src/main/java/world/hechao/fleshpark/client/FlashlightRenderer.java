package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** 手电：车床车出来的圆筒。灯头朝 -Z，占满 0 到 1 的格子。 */
final class FlashlightRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final int SIDES = 32;

    // 铝合金筒身，灯头一圈亮的镀铬，只有握把是黑胶。以前整支都是深灰，拿在手里在光影下就是一团黑
    private static final int BEZEL = 0xFFB3B6BB, HEAD = 0xFF6A6F76, NECK = 0xFF595D63, BODY = 0xFF7D8289, GRIP = 0xFF2E2F33, RING = 0xFF9EA1A7, CAP = 0xFF55595F;
    private static final Lathe.Band[] BANDS = {
            new Lathe.Band(0.04375f, 0f, 0.04375f, 0.11125f, 0xFFFFFFFF, true),
            new Lathe.Band(0.04375f, 0.11125f, 0.01875f, 0.12625f, BEZEL, false),
            new Lathe.Band(0.01875f, 0.12625f, 0.03125f, 0.13125f, BEZEL, false),
            new Lathe.Band(0.03125f, 0.13125f, 0.125f, 0.13f, HEAD, false),
            new Lathe.Band(0.125f, 0.13f, 0.23125f, 0.075f, HEAD, false),
            new Lathe.Band(0.23125f, 0.075f, 0.28125f, 0.07125f, NECK, false),
            new Lathe.Band(0.28125f, 0.07125f, 0.3f, 0.0775f, RING, false),
            new Lathe.Band(0.3f, 0.0775f, 0.325f, 0.0775f, RING, false),
            new Lathe.Band(0.325f, 0.0775f, 0.3375f, 0.071875f, BODY, false),
            new Lathe.Band(0.3375f, 0.071875f, 0.45f, 0.071875f, BODY, false),
            new Lathe.Band(0.45f, 0.071875f, 0.45937f, 0.079375f, GRIP, false),
            new Lathe.Band(0.45937f, 0.079375f, 0.725f, 0.079375f, GRIP, false),
            new Lathe.Band(0.725f, 0.079375f, 0.73438f, 0.071875f, GRIP, false),
            new Lathe.Band(0.73438f, 0.071875f, 0.89375f, 0.071875f, BODY, false),
            new Lathe.Band(0.89375f, 0.071875f, 0.93125f, 0.059375f, NECK, false),
            new Lathe.Band(0.93125f, 0.059375f, 0.93125f, 0f, CAP, false),
    };

    private final MaterialSet materials;
    /** 烘模型的时候图集还没好，第一次画的时候才取。 */
    private TextureAtlasSprite metal, lens;

    private FlashlightRenderer(MaterialSet materials) {
        this.materials = materials;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (Arm.hiddenOnSelf(context)) return;
        if (metal == null) {
            metal = materials.get(new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flashlight")));
            lens = materials.get(new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flashlight_lens")));
        }
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            for (Lathe.Band band : BANDS) Lathe.band(pose, out, band, band.lens() ? lens : metal, SIDES, false, light, overlay);
        });
        // 第一人称：一只方块手攥着握把，小臂往右下角伸出画面
        boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (leftHand || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            float side = leftHand ? -1 : 1;
            Vector3f toFist = new Vector3f(-0.75f * side, 0.42f, -0.8f).normalize();
            Vector3f fist = new Vector3f(0.5f, 0.5f, GRIP_MID).add(new Vector3f(toFist).mul(2 * ARM_PX));
            Arm.submit(stack, collector, leftHand, fist, toFist, new Vector3f(0, 1, 0), ARM_PX, light, overlay);
        }
    }

    /** 握把中间（沿轴）和手臂一个皮肤像素在格子里的长度。 */
    private static final float GRIP_MID = 0.6f, ARM_PX = 0.075f;

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float lo = 5.8f / 16, hi = 10.2f / 16;
        for (int i = 0; i < 8; i++) output.accept(new Vector3f((i & 1) == 0 ? lo : hi, (i & 2) == 0 ? lo : hi, (i & 4) == 0 ? 0 : 1));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new FlashlightRenderer(context.materials());
        }
    }
}
