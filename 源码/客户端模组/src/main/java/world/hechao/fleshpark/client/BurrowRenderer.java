package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** 腺泡里面：一段内壁朝里的肉管，沿 X，人在管子里爬。 */
final class BurrowRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath("fleshpark", "textures/item/flesh_lump_dark.png"));

    static {
        LeanVertices.add(SOLID);
    }
    private static final int SIDES = 22, RINGS = 12;

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            Matrix4f m = pose.pose();
            float len = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
            for (int r = 0; r < RINGS; r++) {
                for (int s = 0; s < SIDES; s++) {
                    vertex(pose, out, r, s, len, light, overlay);
                    vertex(pose, out, r, s + 1, len, light, overlay);
                    vertex(pose, out, r + 1, s + 1, len, light, overlay);
                    vertex(pose, out, r + 1, s, len, light, overlay);
                }
            }
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, int ring, int side, float len, int light, int overlay) {
        float t = (float) ring / RINGS;
        double a = Math.PI * 2 * (side % SIDES) / SIDES;
        double bump = 1 + .1 * Math.sin(a * 3 + t * 9) + .06 * Math.sin(a * 7 - t * 15);
        float y = (float) (.5 + .5 * bump * Math.cos(a)), z = (float) (.5 + .5 * bump * Math.sin(a));
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // 法线朝管子里
        float a0 = (float) -Math.cos(a), a1 = (float) -Math.sin(a);
        float nx = n.m10() * a0 + n.m20() * a1, ny = n.m11() * a0 + n.m21() * a1, nz = n.m12() * a0 + n.m22() * a1, l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        out.addVertex(m.m00() * t + m.m10() * y + m.m20() * z + m.m30(), m.m01() * t + m.m11() * y + m.m21() * z + m.m31(), m.m02() * t + m.m12() * y + m.m22() * z + m.m32(),
                Shape.smoothColor(nx / l, ny / l, nz / l, 0xFFFFFFFF), t * len / 1.4f, (float) side / SIDES * 2, overlay, light, nx / l, ny / l, nz / l);
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
            return new BurrowRenderer();
        }
    }
}
