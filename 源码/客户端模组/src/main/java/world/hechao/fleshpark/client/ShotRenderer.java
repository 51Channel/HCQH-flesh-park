package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * 车上电视里的一张截图（Snaps 里第几张）。和海报一样是 z = 0.5 上的一块正方形，正反两面都贴，从哪面看都是正的。
 * 那一张没截到就放雪花。
 */
final class ShotRenderer implements NoDataSpecialModelRenderer {
    private static final Identifier STATIC = Identifier.fromNamespaceAndPath("fleshpark", "textures/item/tv_static.png");
    private final int index;

    private ShotRenderer(int index) {
        this.index = index;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        Identifier shot = Snaps.get(index);
        Identifier texture = shot != null ? shot : STATIC;
        collector.submitCustomGeometry(stack, RenderTypes.entitySolid(texture), (pose, out) -> {
            quad(pose, out, 0.505f, 1, light, overlay);
            quad(pose, out, 0.495f, -1, light, overlay);
        });
    }

    /** side 是 1 朝 +z，-1 朝 -z；背面左右镜像，从背后看字也是正的。 */
    private static void quad(PoseStack.Pose pose, VertexConsumer out, float z, int side, int light, int overlay) {
        float[][] corners = side > 0 ? new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}} : new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        Matrix4f m = pose.pose();
        Vector3f n = new Vector3f(0, 0, side).mul(pose.normal()).normalize();
        for (float[] c : corners) {
            Vector3f v = new Vector3f(c[0], c[1], z).mulPosition(m);
            float u = side > 0 ? c[0] : 1 - c[0];
            out.addVertex(v.x, v.y, v.z, 0xFFFFFFFF, u, 1 - c[1], overlay, light, n.x, n.y, n.z);
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 0.49f));
        output.accept(new Vector3f(1, 1, 0.51f));
    }

    record Unbaked(int index) implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.INT.fieldOf("index").forGetter(Unbaked::index)).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new ShotRenderer(index);
        }
    }
}
