package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * 羊水压舱液的液面：一张会起伏的半透明网格，不用原版水，人不会变成游泳姿势。
 * 液面在格子中间的 y=0.5 处，插件靠缩放定大小。
 */
final class PoolRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType TRANSLUCENT = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_ITEMS);
    private static final Material METAL = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/pipe"));
    private static final int GRID = 24;

    private final MaterialSet materials;
    private final int color;
    private TextureAtlasSprite white;

    private PoolRenderer(MaterialSet materials, int color) {
        this.materials = materials;
        this.color = color;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        // 水不挡手电，也隔着好几堵墙就不用画
        if (!ClientFx.poolsNear || ClientFx.shadowPass()) return;
        if (white == null) white = materials.get(METAL);
        float time = System.currentTimeMillis() % 100000 / 1000f;
        collector.submitCustomGeometry(stack, TRANSLUCENT, (pose, out) -> {
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02()), sz = (float) Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
            float u = white.getU(.5f), v = white.getV(.5f);
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    vertex(out, m, n, (float) i / GRID, (float) j / GRID, sx, sz, time, u, v, light, overlay);
                    vertex(out, m, n, (float) i / GRID, (float) (j + 1) / GRID, sx, sz, time, u, v, light, overlay);
                    vertex(out, m, n, (float) (i + 1) / GRID, (float) (j + 1) / GRID, sx, sz, time, u, v, light, overlay);
                    vertex(out, m, n, (float) (i + 1) / GRID, (float) j / GRID, sx, sz, time, u, v, light, overlay);
                }
            }
        });
    }

    private void vertex(VertexConsumer out, Matrix4f m, Matrix3f n, float x, float z, float sx, float sz, float t, float u, float v, int light, int overlay) {
        float wx = x * sx, wz = z * sz;
        // 粘稠的液体：波长长、动得慢
        float h = .05f * (float) Math.sin(wx * .9f + t * .55f) + .035f * (float) Math.sin(wz * 1.3f - t * .4f) + .018f * (float) Math.sin((wx + wz) * 2.1f + t * .8f);
        float dx = .045f * (float) Math.cos(wx * .9f + t * .55f) + .038f * (float) Math.cos((wx + wz) * 2.1f + t * .8f);
        float dz = .045f * (float) Math.cos(wz * 1.3f - t * .4f) + .038f * (float) Math.cos((wx + wz) * 2.1f + t * .8f);
        float y = .5f + h;
        float a = -dx / Math.max(.001f, 1), b = 1, c = -dz;
        float nx = n.m00() * a + n.m10() * b + n.m20() * c, ny = n.m01() * a + n.m11() * b + n.m21() * c, nz = n.m02() * a + n.m12() * b + n.m22() * c;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        out.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(), m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                ClientFx.shaderOn ? 0xFC000000 | color & 0xFFFFFF : color, u, v, overlay, light, nx / len, ny / len, nz / len);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) output.accept(new Vector3f(i & 1, i >> 1 & 1, i >> 2 & 1));
    }

    record Unbaked(int color) implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("color").forGetter(Unbaked::color)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new PoolRenderer(context.materials(), color);
        }
    }
}
