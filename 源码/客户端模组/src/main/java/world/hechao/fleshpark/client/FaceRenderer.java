package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
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
 * 中控室监控墙上的画面：评分机的前置摄像头拍到的你。就是你自己皮肤上那张脸，连帽子那一层，方方正正贴在屏幕上。
 * 用的是本机玩家的皮肤，服务器开不开正版验证都一样。模型里是 z = 0.5 上的一块正方形，朝 +z，跟着实体的朝向转。
 */
final class FaceRenderer implements NoDataSpecialModelRenderer {
    private FaceRenderer() {
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Identifier skin = player.getSkin().body().texturePath();
        // 物品展示实体自带 180 度旋转：转回来，脸才朝着实体的朝向
        stack.pushPose();
        stack.translate(0.5f, 0, 0.5f);
        stack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        stack.translate(-0.5f, 0, -0.5f);
        collector.submitCustomGeometry(stack, RenderTypes.entitySolid(skin), (pose, out) -> quad(pose, out, 0.5f, 8, 8, light, overlay));
        collector.submitCustomGeometry(stack, RenderTypes.entityTranslucent(skin), (pose, out) -> quad(pose, out, 0.51f, 40, 8, light, overlay));
        stack.popPose();
    }

    /** 皮肤上从 (u0, v0) 起的 8×8 一块（脸或者帽子），贴在 z 这一层的正方形上。从外面看逆时针。 */
    private static void quad(PoseStack.Pose pose, VertexConsumer out, float z, int u0, int v0, int light, int overlay) {
        float[][] corners = {{0, 0, u0, v0 + 8}, {1, 0, u0 + 8, v0 + 8}, {1, 1, u0 + 8, v0}, {0, 1, u0, v0}};
        Matrix4f m = pose.pose();
        Vector3f n = new Vector3f(0, 0, 1).mul(pose.normal()).normalize();
        for (float[] c : corners) {
            Vector3f v = new Vector3f(c[0], c[1], z).mulPosition(m);
            out.addVertex(v.x, v.y, v.z, 0xFFFFFFFF, c[2] / 64f, c[3] / 64f, overlay, light, n.x, n.y, n.z);
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 0));
        output.accept(new Vector3f(1, 1, 1));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new FaceRenderer();
        }
    }
}
