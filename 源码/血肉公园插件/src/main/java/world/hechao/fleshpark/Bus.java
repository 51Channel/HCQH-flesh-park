package world.hechao.fleshpark;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 接驳大巴：公园当年的游客接驳车，回收公司拿来接体验官。车头朝 +x，车门在右前方（+z 一侧）。
 * 人坐右边第一排靠窗，正对着挡风玻璃，路从前面迎过来；挡风玻璃上方一台电视放公园的宣传片。
 * 司机关在隔间里，只有车上的喇叭传出声音。
 * 和缆车一样，所有零件都是根实体的乘客，靠每 tick 挪根实体走。人也是乘客，客户端在车厢里给人挪位置。
 */
final class Bus {
    /** 车厢地板比根实体高多少。低地板大巴，门口没有台阶。 */
    static final double FLOOR = 0.35;
    static final double ROOF = 3.0, HALF = 1.25, FRONT = 5.0, REAR = -5.0;
    /** 侧窗上下沿。窗子没有玻璃：夜里隔着深色玻璃什么也看不见。 */
    private static final double WIN_LOW = 1.05, WIN_HIGH = 2.45;
    /** 车门在右前方的 x 范围。 */
    static final double DOOR_X0 = 3.65, DOOR_X1 = 4.75;
    /** 人坐的位置：右边第一排靠窗。y 是脚底，坐着眼睛在地板上 1.2 米。 */
    static final double[] SEAT = {2.72, FLOOR - 0.42, 0.78};
    /** 招募函夹板：放在旁边靠过道的座位上。 */
    static final double[] CLIP = {2.72, FLOOR + 0.5, 0.42};
    /** 招募函判定框往车头多挪的一点，抵掉车开着时判定框慢半拍。 */
    private static final double CLIP_LEAD = 0.12;
    /** 换班以后人坐的驾驶位：隔间里，方向盘后面。 */
    static final double[] DRIVER = {4.02, FLOOR - 0.42, -0.92};
    /** 驾驶位右手边的运送单。 */
    static final double[] SHEET = {4.58, FLOOR + 0.82, -0.3};
    private static final double[] WHEEL = {4.5, FLOOR + 0.92, -0.92};
    /** 司机隔间的帘子在哪个 z（取负）：隔间只占左前角，坐第一排往前看，挡风玻璃大半是敞着的。 */
    private static final double CAB_Z = 0.6;
    /** 车门的点击框，相对根实体。 */
    private static final double[] DOOR_HIT = {(DOOR_X0 + DOOR_X1) / 2, 0, HALF + 0.08};
    private static final String BODY = "minecraft:white_concrete", TRIM = "minecraft:light_gray_concrete", DARK = "minecraft:black_concrete";
    private final World world;
    private final Park park;
    final BlockDisplay root;
    final Interaction clip, doorHit;
    private final List<Entity> parts = new ArrayList<>();
    /** 折叠门的零件：x、y、z、长、高、厚，收起来贴到哪个 x。 */
    private final List<BlockDisplay> leaves = new ArrayList<>();
    private final List<double[]> leafBoxes = new ArrayList<>();
    private final List<BlockDisplay> cabinLights = new ArrayList<>();
    private final ItemDisplay screen;
    /** 换班那一辆：隔间的帘子拉开，有方向盘和仪表台，人自己坐在里面开。 */
    private final boolean driving;
    private final double hitX, hitY, hitZ;
    private ItemDisplay wheel;
    private TextDisplay tally;
    private BlockDisplay callLamp;

    Bus(Park park, double x, double y, double z) {
        this(park, x, y, z, false);
    }

    Bus(Park park, double x, double y, double z, boolean driving) {
        this.park = park;
        this.driving = driving;
        hitX = driving ? SHEET[0] : CLIP[0] + CLIP_LEAD;
        hitY = driving ? SHEET[1] - 0.3 : CLIP[1] - 0.25;
        hitZ = driving ? SHEET[2] : CLIP[2];
        world = park.world;
        root = world.spawn(new Location(world, x, y, z), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(2);
        });
        double len = FRONT - REAR, w = 2 * HALF;
        // 地板、车顶、顶上的空调
        box("minecraft:gray_concrete", REAR, FLOOR - 0.06, -HALF, len, 0.06, w, -1);
        box(BODY, REAR, ROOF, -HALF, len, 0.1, w, -1);
        box(TRIM, -2.2, ROOF + 0.1, -0.8, 2.6, 0.28, 1.6, -1);
        // 两边车身，右边前头留出车门
        side(-HALF, REAR, FRONT - 0.15);
        side(HALF - 0.05, REAR, DOOR_X0);
        box(BODY, DOOR_X0, WIN_HIGH, HALF - 0.05, DOOR_X1 - DOOR_X0, ROOF - WIN_HIGH, 0.05, -1);
        box(DARK, DOOR_X1, 0.25, HALF - 0.05, FRONT - DOOR_X1, ROOF - 0.25, 0.05, -1);
        box(DARK, FRONT - 0.15, 0.25, -HALF, 0.15, ROOF - 0.25, 0.05, -1);
        for (double px : new double[]{-4.3, -2.5, -0.7, 1.1, 2.9}) {
            box(DARK, px, WIN_LOW, -HALF, 0.1, WIN_HIGH - WIN_LOW, 0.05, -1);
            box(DARK, px, WIN_LOW, HALF - 0.05, 0.1, WIN_HIGH - WIN_LOW, 0.05, -1);
        }
        // 车头：挡风玻璃是空的，司机那半边贴了深色膜，外面看不进去
        box(BODY, FRONT - 0.06, 0.25, -HALF, 0.06, 0.7, w, -1);
        box(BODY, FRONT - 0.06, WIN_HIGH, -HALF, 0.06, ROOF + 0.1 - WIN_HIGH, w, -1);
        box(DARK, FRONT - 0.07, 0.93, -HALF, 0.07, 0.04, w, -1);
        box(DARK, FRONT - 0.07, WIN_HIGH - 0.02, -HALF, 0.07, 0.04, w, -1);
        if (!driving) box("minecraft:black_stained_glass", FRONT - 0.05, 0.95, -HALF, 0.02, WIN_HIGH - 0.95, HALF - CAB_Z, -1);
        box(DARK, FRONT, 0.22, -HALF - 0.02, 0.1, 0.22, w + 0.04, -1);
        box("minecraft:ochre_froglight", FRONT, 0.5, -1.12, 0.03, 0.16, 0.36, 15);
        box("minecraft:ochre_froglight", FRONT, 0.5, 0.76, 0.03, 0.16, 0.36, 15);
        box(DARK, FRONT, 2.55, -0.95, 0.02, 0.32, 1.9, -1);
        text("息壤国家公园  ·  游客接驳", FRONT + 0.03, 2.6, 0, 90f, 0xffa040, 0.32f, 0, 15);
        for (double side : new double[]{-HALF - 0.32, HALF + 0.18}) {
            box(DARK, FRONT + 0.05, 1.7, side, 0.06, 0.4, 0.14, -1);
            box(DARK, FRONT - 0.05, 2.2, side < 0 ? -HALF - 0.3 : HALF - 0.02, 0.1, 0.04, 0.32, -1);
        }
        // 挡风玻璃左下角，回收公司从里面贴了一张纸。贴在司机隔间旁边，不挡坐第一排的人往前看
        box("minecraft:white_concrete", FRONT - 0.1, 0.98, -CAB_Z + 0.04, 0.01, 0.3, 0.38, -1);
        text("体验官接驳\n二叠纪盆地回收", FRONT - 0.085, 1.03, -CAB_Z + 0.23, 90f, 0x24211f, 0.12f, 0, -1);
        // 车尾：尾灯、后窗、保险杠
        box(BODY, REAR, 0.25, -HALF, 0.06, ROOF - 0.25, w, -1);
        box("minecraft:red_concrete", REAR - 0.03, 0.55, -1.15, 0.03, 0.3, 0.25, 15);
        box("minecraft:red_concrete", REAR - 0.03, 0.55, 0.9, 0.03, 0.3, 0.25, 15);
        box(DARK, REAR - 0.04, 1.4, -0.9, 0.04, 0.9, 1.8, -1);
        box(DARK, REAR - 0.08, 0.22, -HALF, 0.08, 0.22, w, -1);
        // 轮子和轮拱
        for (double wx : new double[]{3.1, -3.3}) {
            box("minecraft:polished_blackstone", wx - 0.48, 0, -HALF - 0.03, 0.96, 0.76, 0.14, -1);
            box("minecraft:polished_blackstone", wx - 0.48, 0, HALF - 0.11, 0.96, 0.76, 0.14, -1);
        }
        // 车身上公园的字
        text("息壤国家公园  游客接驳专线", -1.6, 0.84, HALF + 0.01, 0f, 0x1f6a6a, 0.4f, 0, -1);
        text("息壤国家公园  游客接驳专线", -1.6, 0.84, -HALF - 0.01, 180f, 0x1f6a6a, 0.4f, 0, -1);
        // 车里：两条顶灯、行李架、扶手杆
        for (double lz : new double[]{-0.62, 0.5}) cabinLights.add(box("minecraft:ochre_froglight", -4.6, ROOF - 0.04, lz, 8.0, 0.03, 0.12, 9));
        box(TRIM, -4.7, 2.52, -HALF + 0.05, 7.3, 0.04, 0.38, -1);
        box(TRIM, -4.7, 2.52, HALF - 0.43, 7.3, 0.04, 0.38, -1);
        box("minecraft:yellow_concrete", -4.4, 2.2, -0.3, 7.6, 0.04, 0.04, -1);
        box("minecraft:yellow_concrete", -4.4, 2.2, 0.26, 7.6, 0.04, 0.04, -1);
        for (double[] pole : new double[][]{{3.38, -0.29}, {1.15, -0.29}, {1.15, 0.25}, {-1.5, -0.29}, {-1.5, 0.25}, {-3.9, 0.25}}) {
            box("minecraft:yellow_concrete", pole[0], FLOOR, pole[1], 0.045, 2.2 - FLOOR, 0.045, -1);
        }
        // 广告卡：贴在行李架边上
        text("和全家一起  下到地底！", 0.6, 2.47, HALF - 0.44, 180f, 0x24211f, 0.18f, 0xfff4e6c4, -1);
        text("您的评价  让息壤更好", -1.2, 2.47, -HALF + 0.44, 0f, 0xf4e6c4, 0.18f, 0xff1f6a6a, -1);
        // 座位：两边各八排，最后一排通长
        for (double sx = 2.5; sx > -4.0; sx -= 0.88) {
            seats(sx, -1.15, -0.3);
            seats(sx, 0.3, 1.15);
        }
        seats(-4.66, -1.15, 1.15);
        // 第一排前面一块矮挡板，再往前就是车门。挡板要矮，坐着往前看是挡风玻璃和路
        box(TRIM, 3.45, FLOOR + 0.05, 0.3, 0.04, 0.6, 0.9, -1);
        box("minecraft:yellow_concrete", DOOR_X0, FLOOR, HALF - 0.17, DOOR_X1 - DOOR_X0, 0.01, 0.12, -1);
        // 司机隔间：背后一堵墙，旁边拉着深色的帘子，只留一个喇叭。墙上贴着运送单，司机用马克笔记的数，37 个
        double cab = -HALF, mid = (-HALF - CAB_Z) / 2;
        box(TRIM, 3.55, FLOOR, cab, 0.06, ROOF - FLOOR, HALF - CAB_Z, -1);
        // 帘子：平时拉着；换班那辆拉开，堆在隔间后墙边上
        if (driving) box("minecraft:gray_wool", 3.55, FLOOR, -CAB_Z - 0.09, 0.2, ROOF - FLOOR, 0.09, -1);
        else box("minecraft:gray_wool", 3.55, FLOOR, -CAB_Z - 0.05, FRONT - 3.62, ROOF - FLOOR, 0.05, -1);
        text("请勿与驾驶员交谈", 3.54, 2.2, mid, -90f, 0x24211f, 0.15f, 0, -1);
        box("minecraft:white_concrete", 3.535, 1.2, mid - 0.27, 0.01, 0.5, 0.54, -1);
        text("运送单  No. 0011\n乘员：体验官 #0011\n目的地：息壤地面站\n回程：", 3.53, 1.24, mid, -90f, 0x24211f, 0.17f, 0, -1);
        text(driving ? "正正正正正正正下" : "正正正正正正正丅", 3.53, 1.85, mid, -90f, 0x1a1a1a, 0.22f, 0, -1);
        box(DARK, 3.53, 2.56, cab + 0.05, 0.02, 0.2, HALF - CAB_Z - 0.1, -1);
        text("下一站  息壤地面站", 3.515, 2.6, mid, -90f, 0xff4030, 0.15f, 0, 15);
        box(DARK, 2.9, ROOF - 0.06, 0.25, 0.25, 0.04, 0.25, -1);
        // 电视：挡风玻璃上方，正对着第一排
        box(DARK, 4.8, 2.46, 0.08, 0.12, 0.52, 0.74, -1);
        screen = world.spawn(root.getLocation(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setShadowRadius(0);
            d.setViewRange(1f);
            d.setBrightness(new Display.Brightness(15, 0));
            d.setTransformationMatrix(new Matrix4f().translate(4.785f, 2.72f, 0.45f).rotateY((float) (Math.PI / 2)).scale(0.66f, 0.48f, 1f));
        });
        root.addPassenger(screen);
        parts.add(screen);
        // 忘在座位上的东西：一顶小孩的帽子，地上一张旧门票
        box("minecraft:red_wool", 0.85, FLOOR + 0.48, -0.95, 0.3, 0.06, 0.3, -1);
        box("minecraft:red_wool", 0.92, FLOOR + 0.54, -0.88, 0.16, 0.1, 0.16, -1);
        box("minecraft:white_concrete", 1.4, FLOOR + 0.005, 0.05, 0.13, 0.004, 0.07, -1);
        if (driving) driverCab();
        // 折叠门：两扇，各一块白板加一块深色窗
        leaf(DOOR_X0, DOOR_X0);
        leaf(DOOR_X0 + (DOOR_X1 - DOOR_X0) / 2, DOOR_X1 - 0.08);
        // 招募函夹板。换班那辆的判定框放在驾驶位右手边的运送单上
        box("minecraft:oak_planks", CLIP[0] - 0.14, CLIP[1] - 0.09, CLIP[2] - 0.19, 0.28, 0.02, 0.38, -1);
        box("minecraft:white_concrete", CLIP[0] - 0.11, CLIP[1] - 0.07, CLIP[2] - 0.16, 0.22, 0.004, 0.32, -1);
        // 判定框比夹板大一圈，还往车头挪一点：车开着的时候它每 tick 单独跟上来，会落在夹板后面半格
        clip = world.spawn(root.getLocation().add(hitX, hitY, hitZ), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(driving ? 0.8f : 0.95f);
            i.setInteractionHeight(driving ? 0.7f : 0.5f);
            i.setResponsive(true);
        });
        doorHit = world.spawn(root.getLocation().add(DOOR_HIT[0], DOOR_HIT[1], DOOR_HIT[2]), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.15f);
            i.setInteractionHeight(2.4f);
            i.setResponsive(true);
        });
    }

    /** 换班那辆的驾驶位：仪表台、方向盘、司机座、运送单、呼叫灯，遮阳板上别着上一任的工牌。 */
    private void driverCab() {
        double mid = (-HALF - CAB_Z) / 2, w = HALF - CAB_Z;
        // 仪表台：挡风玻璃下面一条，右边接发动机盖，运送单就立在上面
        box("minecraft:gray_concrete", 4.62, FLOOR, -HALF + 0.02, FRONT - 4.72, 0.95 - FLOOR + 0.02, 2 * HALF - 0.5, -1);
        box(DARK, 4.6, 0.96, -HALF + 0.03, FRONT - 4.72, 0.03, w, -1);
        for (double gz : new double[]{-1.1, -0.92, -0.74}) box("minecraft:lime_concrete", 4.615, 0.82, gz - 0.04, 0.005, 0.07, 0.08, 9);
        callLamp = box("minecraft:red_concrete", 4.615, 0.82, -0.62, 0.005, 0.07, 0.08, 2);
        // 司机座：靠背贴着隔间后墙
        box(DARK, 3.62, FLOOR, -HALF + 0.08, 0.1, 1.25, w - 0.14, -1);
        box("minecraft:gray_wool", 3.72, FLOOR + 0.3, -HALF + 0.08, 0.5, 0.12, w - 0.14, -1);
        // 遮阳板，上面别着上一任的工牌
        box(DARK, 4.45, WIN_HIGH - 0.06, -HALF + 0.05, 0.5, 0.03, w - 0.1, -1);
        box("minecraft:white_concrete", 4.5, WIN_HIGH - 0.26, mid - 0.09, 0.005, 0.2, 0.18, -1);
        box("minecraft:red_concrete", 4.5, WIN_HIGH - 0.11, mid - 0.09, 0.006, 0.05, 0.18, -1);
        // 运送单：立在发动机盖上，朝着人
        box(DARK, SHEET[0] + 0.02, SHEET[1] - 0.32, SHEET[2] - 0.27, 0.04, 0.62, 0.54, -1);
        box("minecraft:white_concrete", SHEET[0], SHEET[1] - 0.3, SHEET[2] - 0.25, 0.02, 0.58, 0.5, 11);
        text("运送单  No. 0039\n乘员：体验官 #0039\n目的地：息壤地面站\n回程：", SHEET[0] - 0.012, SHEET[1] - 0.26, SHEET[2], -90f, 0x24211f, 0.14f, 0, 13);
        tally = textDisplay("正正正正正正正下", SHEET[0] - 0.012, SHEET[1] + 0.1, SHEET[2], -90f, 0x1a1a1a, 0.17f, 0, 13);
        // 方向盘
        wheel = world.spawn(root.getLocation(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(park.modelStack("wheel"));
            d.setShadowRadius(0);
            d.setViewRange(1f);
            d.setBrightness(new Display.Brightness(6, 0));
        });
        root.addPassenger(wheel);
        parts.add(wheel);
        steer(0);
    }

    /** 方向盘转一点。 */
    void steer(double angle) {
        if (wheel == null) return;
        wheel.setInterpolationDelay(0);
        wheel.setInterpolationDuration(6);
        wheel.setTransformationMatrix(new Matrix4f().translate((float) WHEEL[0], (float) WHEEL[1], (float) WHEEL[2]).rotateZ(-0.42f).rotateX((float) angle).scale(0.52f));
    }

    /** 运送单上再划一笔。 */
    void tally(String strokes) {
        if (tally != null) tally.text(Component.text(strokes).color(TextColor.color(0x1a1a1a)));
    }

    /** 接驳站有人按了按钮，仪表台上的灯亮起来。 */
    void callLamp(boolean on) {
        if (callLamp != null) callLamp.setBrightness(new Display.Brightness(on ? 15 : 2, 0));
    }

    private BlockDisplay box(String block, double x, double y, double z, double sx, double sy, double sz, int glow) {
        BlockDisplay display = world.spawn(root.getLocation(), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(Bukkit.createBlockData(block));
            d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, (float) z), new AxisAngle4f(), new Vector3f((float) sx, (float) sy, (float) sz), new AxisAngle4f()));
            d.setViewRange(3f);
            d.setShadowRadius(0);
            if (glow >= 0) d.setBrightness(new Display.Brightness(glow, 0));
        });
        root.addPassenger(display);
        parts.add(display);
        return display;
    }

    /** 一面车身：窗台以下、窗子以上，外面一条青一条橙，里面一层内衬。窗带留空。 */
    private void side(double z, double x0, double x1) {
        double len = x1 - x0, out = z < 0 ? -0.01 : 0.05, in = z < 0 ? 0.05 : -0.02;
        box(BODY, x0, 0.25, z, len, WIN_LOW - 0.25, 0.05, -1);
        box(BODY, x0, WIN_HIGH, z, len, ROOF - WIN_HIGH, 0.05, -1);
        box(DARK, x0, WIN_LOW - 0.03, z, len, 0.03, 0.06, -1);
        box(DARK, x0, WIN_HIGH, z, len, 0.03, 0.06, -1);
        box("minecraft:cyan_concrete", x0, 0.5, z + out, len, 0.18, 0.01, -1);
        box("minecraft:orange_concrete", x0, 0.73, z + out, len, 0.06, 0.01, -1);
        box(TRIM, x0, FLOOR, z + in, len, WIN_LOW - FLOOR, 0.02, -1);
    }

    /** 两个座位并排：座架、坐垫、靠背、靠背上的扶手。 */
    private void seats(double x, double z0, double z1) {
        double w = z1 - z0;
        box(TRIM, x + 0.06, FLOOR, z0 + 0.06, 0.34, 0.36, w - 0.12, -1);
        box("minecraft:cyan_wool", x, FLOOR + 0.36, z0, 0.48, 0.12, w, -1);
        box("minecraft:cyan_wool", x - 0.08, FLOOR + 0.42, z0, 0.09, 0.62, w, -1);
        box(TRIM, x - 0.1, FLOOR + 1.04, z0, 0.12, 0.05, w, -1);
    }

    /** 一扇折叠门：从 x 起，宽半个门洞，下半截是板，上半截只有框（玻璃不做，坐着能透过门看到外面）。打开时收成一条贴到 fold。 */
    private void leaf(double x, double fold) {
        double width = (DOOR_X1 - DOOR_X0) / 2, z = HALF - 0.04;
        double[][] pieces = {{x, 0.25, z, width, WIN_LOW - 0.25, 0.04}, {x, WIN_LOW, z, 0.05, WIN_HIGH - WIN_LOW, 0.04}, {x + width - 0.05, WIN_LOW, z, 0.05, WIN_HIGH - WIN_LOW, 0.04}, {x, WIN_LOW - 0.03, z, width, 0.04, 0.05}};
        String[] blocks = {BODY, DARK, DARK, DARK};
        for (int i = 0; i < pieces.length; i++) {
            double[] b = pieces[i];
            leaves.add(box(blocks[i], b[0], b[1], b[2], b[3], b[4], b[5], -1));
            leafBoxes.add(new double[]{b[0], b[1], b[2], b[3], b[4], b[5], fold});
        }
    }

    private void text(String text, double x, double y, double z, float yaw, int color, float scale, int background, int glow) {
        textDisplay(text, x, y, z, yaw, color, scale, background, glow);
    }

    private TextDisplay textDisplay(String text, double x, double y, double z, float yaw, int color, float scale, int background, int glow) {
        TextDisplay display = world.spawn(root.getLocation(), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text(text).color(TextColor.color(color)));
            d.setBackgroundColor(Color.fromARGB(background));
            d.setDefaultBackground(false);
            d.setShadowed(false);
            if (glow >= 0) d.setBrightness(new Display.Brightness(glow, 0));
            AxisAngle4f rotate = new AxisAngle4f((float) Math.toRadians(yaw), 0, 1, 0);
            d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, (float) z), rotate, new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        root.addPassenger(display);
        parts.add(display);
        return display;
    }

    void moveTo(double x, double y, double z) {
        root.teleport(new Location(world, x, y, z), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        clip.teleport(new Location(world, x + hitX, y + hitY, z + hitZ));
        doorHit.teleport(new Location(world, x + DOOR_HIT[0], y + DOOR_HIT[1], z + DOOR_HIT[2]));
    }

    /** 折叠门：两扇往两边收起来。 */
    void door(boolean open) {
        for (int i = 0; i < leaves.size(); i++) {
            double[] b = leafBoxes.get(i);
            BlockDisplay part = leaves.get(i);
            part.setInterpolationDelay(0);
            part.setInterpolationDuration(12);
            Vector3f at = new Vector3f((float) (open ? b[6] : b[0]), (float) b[1], (float) (open ? b[2] + 0.06 : b[2]));
            part.setTransformation(new Transformation(at, new AxisAngle4f(), new Vector3f((float) (open ? 0.08 : b[3]), (float) b[4], (float) b[5]), new AxisAngle4f()));
        }
    }

    /** 车里的顶灯：开着门、停着的时候亮一点。 */
    void cabinLight(int level) {
        for (BlockDisplay light : cabinLights) light.setBrightness(new Display.Brightness(level, 0));
    }

    /**
     * 结尾那辆的电视：大一倍多，往过道那边挪，坐第二排看得清画面。右边不过 z 0.83，第一排靠窗那人的后脑勺挡不着。
     * 左边到 -0.57，司机隔间的帘子在 -0.6。后面换一块大一圈的黑壳子。
     */
    void bigScreen() {
        box(DARK, 4.8, 1.9, -0.6, 0.12, 1.1, 1.46, -1);
        screen.setTransformationMatrix(new Matrix4f().translate(4.785f, 2.455f, 0.13f).rotateY((float) (Math.PI / 2)).scale(1.4f, 1.05f, 1f));
    }

    /** 电视换一张画面，null 就是关机。 */
    void screen(String model) {
        screen.setItemStack(model == null ? new ItemStack(Material.AIR) : park.modelStack(model));
    }

    boolean hasRider(Player player) {
        return root.getPassengers().contains(player);
    }

    void remove() {
        clip.remove();
        doorHit.remove();
        parts.forEach(Entity::remove);
        parts.clear();
        root.remove();
    }
}
