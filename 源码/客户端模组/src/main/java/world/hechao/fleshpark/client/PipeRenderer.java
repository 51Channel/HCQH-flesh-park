package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** 一段圆管线，沿 X，两头各一圈法兰。参考图里黄蓝大管线绕着走的那种。 */
final class PipeRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final Material METAL = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/pipe"));
    private static final int SIDES = 18;

    private final MaterialSet materials;
    private final Lathe.Band[] bands;
    private TextureAtlasSprite metal;

    private PipeRenderer(MaterialSet materials, int color) {
        this.materials = materials;
        int collar = 0xFF000000 | (color >> 16 & 255) * 7 / 10 << 16 | (color >> 8 & 255) * 7 / 10 << 8 | (color & 255) * 7 / 10;
        bands = new Lathe.Band[]{
                new Lathe.Band(0, 0.56f, 0.03f, 0.56f, collar),
                new Lathe.Band(0.03f, 0.56f, 0.03f, 0.5f, collar),
                new Lathe.Band(0.03f, 0.5f, 0.97f, 0.5f, color),
                new Lathe.Band(0.97f, 0.5f, 0.97f, 0.56f, collar),
                new Lathe.Band(0.97f, 0.56f, 1, 0.56f, collar),
        };
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        // 管子的影子只有手电贴近才看得出来
        org.joml.Matrix4f at = stack.last().pose();
        if (ClientFx.shadowPass() && at.m30() * at.m30() + at.m31() * at.m31() + at.m32() * at.m32() > 144) return;
        if (metal == null) metal = materials.get(METAL);
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            for (Lathe.Band band : bands) Lathe.band(pose, out, band, metal, SIDES, true, light, overlay);
        });
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
            return new PipeRenderer(context.materials(), color);
        }
    }
}
