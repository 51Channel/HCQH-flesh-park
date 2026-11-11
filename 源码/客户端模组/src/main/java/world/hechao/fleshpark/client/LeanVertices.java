package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 肉和人形的顶点不让光影再加料。
 * 开着光影时，Iris 给每个实体顶点多算三样东西：切线、贴图中点、实体编号。每四个顶点要回头把四个都改一遍，
 * 实测渲染线程一大半的时间耗在这上面（肉一帧五六万个面，阴影图里再来一遍）。我们的光影包这三样一个都不读：
 * 肉的法线和种类是自己塞在贴图坐标和顶点色里的。
 * 所以这几种渲染类型单独开缓冲，开缓冲的时候告诉 Iris 别扩展（它给界面用的就是这个开关），画的时候告诉它这一批是原版格式。
 * 原版格式和 Iris 的格式前六项一样，后面三项不给，着色器读到的是零。
 */
public final class LeanVertices {
    /** 测帧数时可以关掉（fx lean off），比一比。 */
    static boolean on = true;
    /** 资源重载时在别的线程上加，渲染线程上读：每次加都换一份新的。 */
    private static volatile Set<RenderType> types = Set.of();
    private static ThreadLocal<Boolean> skip;
    private static VarHandle renderingLevel, extended;
    private static boolean tried;

    private LeanVertices() {
    }

    static synchronized void add(RenderType type) {
        Set<RenderType> next = Collections.newSetFromMap(new IdentityHashMap<>());
        next.addAll(types);
        next.add(type);
        types = next;
    }

    /** 光影正在画世界，这一批是肉或人形。 */
    public static boolean wants(RenderType type) {
        if (!on || !types.contains(type) || !hooked()) return false;
        return (boolean) renderingLevel.get();
    }

    /** 开一个不扩展的缓冲。渲染类型报的格式在画世界时已经被 Iris 换成扩展的了，先关掉开关再问它原版的。 */
    public static BufferBuilder builder(ByteBufferBuilder bytes, RenderType type) {
        boolean was = plain();
        com.mojang.blaze3d.vertex.VertexFormat format = type.format();
        restore(was);
        skip.set(Boolean.TRUE);
        try {
            return new BufferBuilder(bytes, type.mode(), format);
        } finally {
            skip.set(Boolean.FALSE);
        }
    }

    /** 画这一批之前：管线按原版格式绑顶点。返回原来的值，画完放回去。 */
    public static boolean plain() {
        boolean was = (boolean) extended.get();
        extended.set(false);
        return was;
    }

    public static void restore(boolean was) {
        extended.set(was);
    }

    @SuppressWarnings("unchecked")
    private static boolean hooked() {
        if (tried) return skip != null;
        tried = true;
        try {
            Class<?> state = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            var lookup = MethodHandles.publicLookup();
            renderingLevel = lookup.findStaticVarHandle(state, "isRenderingLevel", boolean.class);
            extended = lookup.findStaticVarHandle(state, "renderWithExtendedVertexFormat", boolean.class);
            skip = (ThreadLocal<Boolean>) state.getField("skipExtension").get(null);
            org.slf4j.LoggerFactory.getLogger("fleshpark").info("肉和人形走原版顶点格式");
        } catch (ReflectiveOperationException | LinkageError | ClassCastException missing) {
            skip = null;
            org.slf4j.LoggerFactory.getLogger("fleshpark").info("没有 Iris 或版本不对，顶点格式不动：" + missing);
        }
        return skip != null;
    }
}
