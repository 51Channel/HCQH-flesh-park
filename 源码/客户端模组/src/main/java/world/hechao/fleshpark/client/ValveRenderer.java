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

/** 麻醉科镇静剂总阀的手轮：一圈红漆铁环、四根辐条、一个轮毂，轴是 x，圆心在 (0.5, 0.5, 0.5)。漆掉了好几块。 */
final class ValveRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final int RIM = 0xFFA32A20, HUB = 0xFF4A4C50;
    private static final Lathe.Band[] BANDS = {
            new Lathe.Band(0.46f, 0.33f, 0.46f, 0.42f, RIM, false),
            new Lathe.Band(0.46f, 0.42f, 0.54f, 0.42f, RIM, false),
            new Lathe.Band(0.54f, 0.42f, 0.54f, 0.33f, RIM, false),
            new Lathe.Band(0.54f, 0.33f, 0.46f, 0.33f, RIM, false),
            new Lathe.Band(0.44f, 0f, 0.44f, 0.1f, HUB, false),
            new Lathe.Band(0.44f, 0.1f, 0.56f, 0.1f, HUB, false),
            new Lathe.Band(0.56f, 0.1f, 0.56f, 0f, HUB, false),
    };

    private final MaterialSet materials;
    private TextureAtlasSprite metal;

    private ValveRenderer(MaterialSet materials) {
        this.materials = materials;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (metal == null) metal = materials.get(new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flashlight")));
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            for (Lathe.Band band : BANDS) Lathe.band(pose, out, band, metal, 28, true, light, overlay);
            // 四根辐条：十字穿过轮毂
            Road.bar(pose, out, metal, 0.48f, 0.47f, 0.13f, 0.52f, 0.53f, 0.87f, RIM, light, overlay);
            Road.bar(pose, out, metal, 0.48f, 0.13f, 0.47f, 0.52f, 0.87f, 0.53f, RIM, light, overlay);
        });
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
            return new ValveRenderer(context.materials());
        }
    }
}
