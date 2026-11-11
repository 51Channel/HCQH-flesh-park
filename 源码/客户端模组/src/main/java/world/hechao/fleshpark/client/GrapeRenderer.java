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

/** 腺泡的洞口：鼓起来的一颗肉球，正中一个黑的圆洞，朝 +z。做成圆的，不做成缝。 */
final class GrapeRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final Material FLESH = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flesh_lump"));
    private static final int C = 0xFFFFFFFF, EDGE = 0xFFB8A8A0, HOLE = 0xFF000000;
    private static final Lathe.Band[] BANDS = {
            new Lathe.Band(0f, .5f, .22f, .48f, C),
            new Lathe.Band(.22f, .48f, .36f, .42f, C),
            new Lathe.Band(.36f, .42f, .46f, .33f, C),
            new Lathe.Band(.46f, .33f, .5f, .25f, C),
            new Lathe.Band(.5f, .25f, .46f, .19f, EDGE),
            new Lathe.Band(.46f, .19f, .2f, .17f, EDGE),
            new Lathe.Band(.2f, .17f, .2f, 0f, HOLE),
    };

    private final MaterialSet materials;
    private TextureAtlasSprite flesh;

    private GrapeRenderer(MaterialSet materials) {
        this.materials = materials;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (flesh == null) flesh = materials.get(FLESH);
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            for (Lathe.Band band : BANDS) Lathe.band(pose, out, band, flesh, 24, false, light, overlay);
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
            return new GrapeRenderer(context.materials());
        }
    }
}
