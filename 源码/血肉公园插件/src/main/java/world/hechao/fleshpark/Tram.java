package world.hechao.fleshpark;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import world.hechao.fleshpark.core.Parts;
import world.hechao.fleshpark.core.Route;
import world.hechao.fleshpark.core.TramModel;

import java.util.ArrayList;
import java.util.List;

/** 缆车：所有零件骑在一个根实体上，挪根实体整辆车一起走。玩家也骑在根实体上，正好贴着前窗。 */
final class Tram {
    private final World world;
    private final Route route;
    private final List<Entity> parts = new ArrayList<>();
    BlockDisplay root;
    private BlockDisplay lamp;
    Interaction button;
    private final java.util.function.Consumer<Interaction> onButton;
    /** 车厢里的纸：纸跟着根实体骑着走，点它用的交互实体不能骑，每次挪车时一起挪。 */
    private final List<Entity> noteParts = new ArrayList<>();
    private final List<Interaction> noteHits = new ArrayList<>();
    private final java.util.Map<BlockDisplay, Integer> glowing = new java.util.HashMap<>();
    private final List<double[]> noteOffsets = new ArrayList<>();

    Tram(World world, Route route, java.util.function.Consumer<Interaction> onButton) {
        this.world = world;
        this.route = route;
        this.onButton = onButton;
    }

    void spawn() {
        remove();
        double[] p = new double[3];
        route.at(0, p);
        Location at = new Location(world, p[0], p[1], p[2]);
        root = world.spawn(at, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(2);
        });
        parts.add(shell(world, root.getLocation()));
        root.addPassenger(parts.get(parts.size() - 1));
        for (Parts.Box box : TramModel.parts()) mount(box);
        mount(TramModel.button());
        lamp = mount(TramModel.lampTube());
        mountText("安诺丹缆车系统", Route.CABIN_CENTER_X - 1.2, 0.02, Route.CABIN_HALF_Z + 0.01, false, 1.0f);
        mountText("安诺丹缆车系统", Route.CABIN_CENTER_X, 0.02, -Route.CABIN_HALF_Z - 0.01, true, 1.0f);
        mountText("813", Route.CABIN_X0 + 0.6, 0.02, -Route.CABIN_HALF_Z - 0.01, true, 1.6f);
        spawnButton();
    }

    /** 一辆停着不走的同型号车（第四关井里挂在断缆上那辆）：没有按钮，车号自己定。 */
    void spawnAt(double x, double y, double z, String number) {
        remove();
        root = world.spawn(new Location(world, x, y, z), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(2);
        });
        parts.add(shell(world, root.getLocation()));
        root.addPassenger(parts.get(parts.size() - 1));
        for (Parts.Box box : TramModel.parts()) mount(box);
        lamp = mount(TramModel.lampTube());
        mountText("安诺丹缆车系统", Route.CABIN_CENTER_X - 1.2, 0.02, Route.CABIN_HALF_Z + 0.01, false, 1.0f);
        mountText("安诺丹缆车系统", Route.CABIN_CENTER_X, 0.02, -Route.CABIN_HALF_Z - 0.01, true, 1.0f);
        mountText(number, Route.CABIN_X0 + 0.6, 0.02, -Route.CABIN_HALF_Z - 0.01, true, 1.6f);
        mountText(number, Route.CABIN_X0 + 0.6, 0.02, Route.CABIN_HALF_Z + 0.01, false, 1.6f);
    }

    private static org.bukkit.entity.ItemDisplay shell(World world, Location at) {
        org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new org.bukkit.NamespacedKey("fleshpark", "tram_body"));
        stack.setItemMeta(meta);
        return world.spawn(at, org.bukkit.entity.ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(stack);
            d.setShadowRadius(0);
            d.setViewRange(3f);
        });
    }

    private BlockDisplay mount(Parts.Box box) {
        BlockDisplay display = world.spawn(root.getLocation(), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(Bukkit.createBlockData(box.block()));
            d.setTransformation(new Transformation(new Vector3f((float) box.x(), (float) box.y(), (float) box.z()), new AxisAngle4f(), new Vector3f((float) box.sx(), (float) box.sy(), (float) box.sz()), new AxisAngle4f()));
            d.setViewRange(3f);
            d.setShadowRadius(0);
            if (box.glow() >= 0) d.setBrightness(new Display.Brightness(box.glow(), 0));
        });
        if (box.glow() >= 0) glowing.put(display, box.glow());
        root.addPassenger(display);
        parts.add(display);
        return display;
    }

    private void mountText(String text, double x, double y, double z, boolean flip, float scale) {
        TextDisplay display = world.spawn(root.getLocation(), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text(text).color(TextColor.color(0x8a8f7c)));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setDefaultBackground(false);
            d.setShadowed(false);
            d.setViewRange(2f);
            AxisAngle4f rotate = flip ? new AxisAngle4f((float) Math.PI, 0, 1, 0) : new AxisAngle4f();
            d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, (float) z), rotate, new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        root.addPassenger(display);
        parts.add(display);
    }

    /** 红按钮：只有停在车站时才能按。能点的范围是整个操作箱，不用瞄准那个小按钮。 */
    void spawnButton() {
        removeButton();
        double[] p = new double[3];
        route.at(0, p);
        button = world.spawn(new Location(world, p[0] + Route.CABIN_X1 - 0.3, p[1] - Route.FLOOR_DROP, p[2] - Route.CABIN_HALF_Z + 0.65), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.1f);
            i.setInteractionHeight(1.3f);
            i.setResponsive(true);
        });
        onButton.accept(button);
    }

    void removeButton() {
        if (button != null) button.remove();
        button = null;
    }

    void moveTo(double x, double y, double z) {
        root.teleport(new Location(world, x, y, z), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        for (int i = 0; i < noteHits.size(); i++) {
            double[] o = noteOffsets.get(i);
            noteHits.get(i).teleport(new Location(world, x + o[0], y + o[1] - 0.12, z + o[2]));
        }
    }

    /** 车厢里放一张纸。返回点它用的交互实体。 */
    Interaction mountNote(Notes.Note n) {
        org.bukkit.entity.ItemDisplay paper = world.spawn(root.getLocation(), org.bukkit.entity.ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER));
            d.setShadowRadius(0);
            d.setBrightness(new Display.Brightness(11, 0));
            d.setTransformationMatrix(new org.joml.Matrix4f().translate((float) n.x(), (float) n.y(), (float) n.z())
                    .rotateY((float) Math.toRadians(n.yaw())).rotateX((float) (Math.PI / 2)).scale(0.42f));
        });
        root.addPassenger(paper);
        noteParts.add(paper);
        Location at = root.getLocation().add(n.x(), n.y() - 0.12, n.z());
        Interaction hit = world.spawn(at, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(0.6f);
            i.setInteractionHeight(0.3f);
            i.setResponsive(true);
        });
        noteHits.add(hit);
        noteOffsets.add(new double[]{n.x(), n.y(), n.z()});
        return hit;
    }

    /** 车上发光的零件都灭掉或者都亮起来。开局这辆车是断电的。 */
    /** 车上一个方块小物件（磁带、录音机）：跟着车走。坐标相对根实体，返回那个展示实体，捡走时删掉。 */
    org.bukkit.entity.BlockDisplay mountBox(String block, double x, double y, double z, double sx, double sy, double sz, int light) {
        org.bukkit.entity.BlockDisplay box = world.spawn(root.getLocation(), org.bukkit.entity.BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(org.bukkit.Bukkit.createBlockData(block));
            d.setShadowRadius(0);
            if (light >= 0) d.setBrightness(new Display.Brightness(light, 0));
            d.setTransformationMatrix(new org.joml.Matrix4f().translate((float) x, (float) y, (float) z).scale((float) sx, (float) sy, (float) sz));
        });
        root.addPassenger(box);
        noteParts.add(box);
        return box;
    }

    /** 车上一个能点的地方：点击框跟着车走。 */
    Interaction mountHit(double x, double y, double z, float width, float height) {
        Interaction hit = world.spawn(root.getLocation().add(x, y, z), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
        });
        noteHits.add(hit);
        // moveTo 会减掉 0.12（纸的点击框比纸低一点），这里先加回来
        noteOffsets.add(new double[]{x, y + 0.12, z});
        return hit;
    }

    void power(boolean on) {
        for (var part : glowing.entrySet()) part.getKey().setBrightness(on ? new Display.Brightness(part.getValue(), 0) : new Display.Brightness(0, 0));
        lamp.setBrightness(new Display.Brightness(on ? 11 : 0, 0));
    }

    void clearNotes() {
        noteParts.forEach(Entity::remove);
        noteHits.forEach(Entity::remove);
        noteParts.clear();
        noteHits.clear();
        noteOffsets.clear();
    }

    /** 结局的车站里停着的那一辆：不会动，亮着灯。 */
    static void spawnStatic(World world, Route route, int dz, List<Entity> out) {
        double[] p = new double[3];
        route.at(0, p);
        Location at = new Location(world, p[0], p[1], p[2] + dz);
        out.add(shell(world, at));
        List<Parts.Box> boxes = new ArrayList<>(TramModel.parts());
        boxes.add(TramModel.lampTube());
        for (Parts.Box box : boxes) {
            out.add(world.spawn(at, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData(box.block()));
                d.setTransformation(new Transformation(new Vector3f((float) box.x(), (float) box.y(), (float) box.z()), new AxisAngle4f(), new Vector3f((float) box.sx(), (float) box.sy(), (float) box.sz()), new AxisAngle4f()));
                d.setShadowRadius(0);
                d.setBrightness(new Display.Brightness(box.block().contains("froglight") ? 15 : 11, 0));
            }));
        }
    }

    /** 灯管亮度：0 就是灭了。 */
    void lamp(int level) {
        lamp.setBrightness(new Display.Brightness(level, 0));
    }

    void remove() {
        removeButton();
        clearNotes();
        glowing.clear();
        parts.forEach(Entity::remove);
        parts.clear();
        if (root != null) root.remove();
    }
}
