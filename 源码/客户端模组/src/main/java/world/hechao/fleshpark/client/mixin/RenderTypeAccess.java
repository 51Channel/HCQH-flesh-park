package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 渲染类型的名字，TextBatchMixin 用它认出哪些是画字的。 */
@Mixin(RenderType.class)
public interface RenderTypeAccess {
    @Accessor("name")
    String fleshpark$name();
}
