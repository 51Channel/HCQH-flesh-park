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

/**
 * 结局：把手电收起来，看见自己的手已经和它们一样了，灰白、手指太长，手心里捏着自己的工牌“体验官 #0011”。
 * 模型里指尖朝 +y，小臂朝 +z 伸向镜头，手心朝镜头。
 */
final class HandRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final Material SKIN = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/grey_skin"));
    private static final Material BADGE = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/poster_badge_self"));
    private static final int SKIN_TINT = 0xFFB4B0AA, CORD = 0xFFD74735;

    private final MaterialSet materials;
    private Shape hand, cord, card;
    private TextureAtlasSprite skin, badge;

    private HandRenderer(MaterialSet materials) {
        this.materials = materials;
    }

    private void build() {
        Sculpt s = new Sculpt();
        // 小臂：瘦，腕骨顶出来
        s.capsule(0, -0.42, 0.95, 0.085, 0, -0.17, 0.12, 0.062, 0.05);
        s.ellipsoid(0.045, -0.15, 0.1, 0.03, 0.03, 0.03, 0.03);
        // 手掌：薄
        s.ellipsoid(0, 0.0, 0.07, 0.11, 0.14, 0.04, 0.05);
        // 四根太长的手指，微微往镜头这边弯，像要把工牌递给你看
        double[] spread = {-0.07, -0.024, 0.024, 0.07};
        double[] length = {0.4, 0.5, 0.48, 0.38};
        for (int i = 0; i < 4; i++) {
            double x = spread[i], top = 0.12 + length[i];
            double mx = x * 1.25, my = 0.12 + length[i] * 0.55;
            s.capsule(x, 0.1, 0.07, 0.024, mx, my, 0.09, 0.019, 0.02);
            s.ellipsoid(mx, my, 0.09, 0.021, 0.021, 0.021, 0.01);
            s.capsule(mx, my, 0.09, 0.018, x * 1.4, top, 0.15, 0.012, 0.012);
        }
        // 拇指压在工牌上
        s.capsule(0.1, -0.05, 0.09, 0.026, 0.13, 0.08, 0.15, 0.02, 0.02);
        s.capsule(0.13, 0.08, 0.15, 0.02, 0.07, 0.15, 0.19, 0.014, 0.015);
        Shape h = new Shape();
        s.into(h, -0.2, 0.22, -0.46, 0.7, -0.05, 1.0, 0.012, 0.5, 0.5, 0.5);
        hand = h;
        // 工牌：在手心前面，被拇指按着；绳子从工牌往下垂，绕在手腕上
        Shape c = new Shape(), k = new Shape();
        double w = 0.075, ht = 0.11, cx = 0.52, cy = 0.55, cz = 0.5 + 0.125;
        k.surface((u, v, out) -> {
            out[0] = cx + (u - 0.5) * 2 * w;
            out[1] = cy + (0.5 - v) * 2 * ht;
            out[2] = cz;
        }, 1, 1, cx, cy, cz - 1);
        c.capsule(cx - 0.03, cy + ht - 0.004, cz - 0.006, 0.006, cx - 0.075, 0.3, 0.53, 0.006, 6, 4);
        c.capsule(cx + 0.03, cy + ht - 0.004, cz - 0.006, 0.006, cx + 0.075, 0.3, 0.53, 0.006, 6, 4);
        cord = c;
        card = k;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (hand == null) build();
        if (skin == null) {
            skin = materials.get(SKIN);
            badge = materials.get(BADGE);
        }
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            hand.emit(pose, out, skin, SKIN_TINT, light, overlay);
            cord.emit(pose, out, skin, CORD, light, overlay);
            card.emit(pose, out, badge, 0xFFFFFFFF, light, overlay);
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) output.accept(new Vector3f(i & 1, i >> 1 & 1, i >> 2 & 1));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new HandRenderer(context.materials());
        }
    }
}
