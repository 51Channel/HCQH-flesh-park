package world.hechao.fleshpark.core;

import world.hechao.fleshpark.core.Parts.Beam;
import world.hechao.fleshpark.core.Parts.Box;
import world.hechao.fleshpark.core.Parts.Flat;
import world.hechao.fleshpark.core.Parts.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 整座公园的规划：哪里放什么方块、摆哪些展示实体。纯数据，离线自检和插件用同一份。
 * 下界环境（没有天空、有浓雾）的 y 只有 0 到 255，所以比网页预览压扁了一半。
 *
 * 结构从上到下：地面车站 → 岩石地面上的肉口 → 食道 → 巨大空腔 → 嵌进肉里的游客中心（3 号码头、大厅、全景窗）。
 */
public final class ParkPlan {
    public static final int VERSION = 9;
    /** 新车站仪容镜占的方块：x 范围，镜面在 z = NEW_STATION_DZ + 9。 */
    public static final int MIRROR_X0 = -10, MIRROR_X1 = -8;

    public static final int FLOOR_STATION = 231, FLOOR_HALL = 30;
    public static final double CRATER_X = 42, CRATER_R = 24, FUNNEL_BOTTOM_R = 8.5;
    public static final int THROAT_TOP = 212, THROAT_BOTTOM = 148, SQUEEZE_Y = 176;
    /** 空腔：一个压扁的椭球。 */
    public static final double CAV_X = 64, CAV_Y = 90, CAV_RX = 85, CAV_RY = 70, CAV_RZ = 72;
    public static final int HX0 = 110, HX1 = 146, HZ0 = -8, HZ1 = 12, HALL_TOP = 41;
    /** 大厅是个半圆，圆心在全景窗正中，二层环廊围着中间挑空的中庭。 */
    public static final double HCX = 128, HCZ = 12, HR = 18, HVOID = 11;
    public static final int DECK = FLOOR_HALL + 5;
    public static final double DRUM_X = 134, DRUM_R = 28;
    /** 快餐店后厨的门洞。楼梯最上一级的 x。 */
    public static final int KITCHEN_X = 134, KITCHEN_Z = -3, STAIR_TOP_X = 142;
    /** 洞口里站人的位置（脚底）。隔着全景窗 x=130 到 134 那一格能看见。 */
    public static final double FIGURE_X = 132, FIGURE_Z = 15.3;
    /** 六台满意度评价机：x、y、z、朝向。 */
    public static final double[][] KIOSKS = {
            {-8.5, FLOOR_STATION, 7.75, 180},
            {107.5, FLOOR_HALL, 2.25, 0},
            {128, FLOOR_HALL + 1.02, -1.1, 0},
            {124.5, FLOOR_HALL, 9.55, 180},
            {Lower.HOLE[0] - 4.6, Lower.POOL3_FLOOR + 0.7, Lower.HOLE[2] + 0.7, 0},
            {299.6, 176, -55.9, 0}};

    /** 重开以后的车站放在这个 z 偏移上。 */
    public static final int NEW_STATION_DZ = 300;
    /** 三部一样的电梯：森林里的（Forest.CAB_*）、3 号码头的、重开车站的。门都朝 +x。 */
    public static final int DOCK_CAB_X = 90, DOCK_CAB_Z = 3, NEW_CAB_X = -19, NEW_CAB_Z = NEW_STATION_DZ + 4;

    public static final double[] SPAWN = {-12.5, FLOOR_STATION, 5.5};
    /** 导演栏跳到入园大厅站的地方。 */
    public static final double[] ENTRY = {-30.5, FLOOR_STATION, 5.5};
    /** 接驳大巴：在公路边的旧接驳站接人，开到落客区。都是车的根实体（车身正中）的 x，BUS_Z 是车道中线。 */
    public static final double BUS_Z = 3.5, BUS_PICKUP_X = -549, BUS_FROM_X = BUS_PICKUP_X - 90, BUS_STOP_X = -50;
    /** 接驳站的水泥站台、站牌立柱、开局人站的地方（x、z、朝向）。 */
    public static final int PICKUP_X0 = -553, PICKUP_X1 = -537;
    public static final double[] PICKUP_POLE = {-541.6, 5.7}, PICKUP_SPAWN = {-545.5, 7.4, 150};
    /** 换班：结算窗口（人站的位置）、员工通道门洞的 z、走到哪算进了通道。 */
    public static final double[] SETTLE_AT = {14.2, NEW_STATION_DZ + 6.35};
    public static final int STAFF_Z0 = NEW_STATION_DZ + 3, STAFF_Z1 = NEW_STATION_DZ + 4, STAFF_ENTER_X = 18;
    /** 公路起点的车队调度站：水泥场的范围、小屋门口人出来的地方。车就停在 BUS_FROM_X。 */
    public static final int DEPOT_X0 = -652, DEPOT_X1 = -622;
    public static final double[] DEPOT_SPAWN = {-639, 14.6};
    /** 落客区通入园大厅的门，卷帘门在这儿落下来。落客区敞开的那头有道拦车杆。 */
    public static final int ENTRY_DOOR_X = -34, ENTRY_DOOR_Z0 = 3, ENTRY_DOOR_Z1 = 7, BAY_MOUTH_X = -59;
    /** 入园闸机：x 这一列，z 这几格。顶灯从人这边一盏盏亮到闸机。 */
    public static final int ENTRY_GATE_X = -19, ENTRY_GATE_Z0 = 4, ENTRY_GATE_Z1 = 6;
    public static final int[] ENTRY_LAMPS = {-30, -25, -21, -18};
    public static final double[] DOCK_OFF = {99.3, FLOOR_HALL, 3.5};

    public final Route route = new Route();
    public final List<Parts.Rope> ropes = new ArrayList<>();
    public final List<Box> boxes = new ArrayList<>();
    public final List<Beam> beams = new ArrayList<>();
    public final List<Text> texts = new ArrayList<>();
    public final List<Flat> flats = new ArrayList<>();
    public final List<Parts.Pipe> pipes = new ArrayList<>();
    public final List<Parts.Poster> posters = new ArrayList<>();
    public final List<Parts.Panel> panels = new ArrayList<>();
    public final List<Parts.Lamp> lamps = new ArrayList<>();
    /** 跟着灯带开关的光方块：ring、x、y、z、亮度。 */
    public final List<int[]> lampLights = new ArrayList<>();
    public final List<Parts.Pool> pools = new ArrayList<>();
    public final Lower lower;
    public final Forest forest;
    /** 第四关深井、第五关井底的真游客中心。 */
    public final Well well;
    public final Chamber wellChamber;
    /** 每道钢环对应的路线距离：车过环时响一串。 */
    public final List<Double> hoopDistances = new ArrayList<>();

    private final Map<Long, B> blocks = new HashMap<>(900_000);
    private final List<long[]> staticLights = new ArrayList<>();
    private final List<Integer> hoopYs = new ArrayList<>();
    final Random rnd = new Random(813);
    private int ropeId;

    /** 缆车段的超大肉腔。 */
    public final Chamber chamber;
    /** 森林的肉腔。 */
    public Chamber forestChamber;
    /** 罩住地面站的肉穹顶。 */
    public final Chamber dome;

    public ParkPlan() {
        chamber = Chamber.ride(route);
        dome = Chamber.surface(route);
        for (int y = THROAT_TOP - 4; y >= THROAT_BOTTOM + 2; y -= 12) {
            if (Math.abs(y - SQUEEZE_Y) >= 5) hoopYs.add(y);
        }
        ground();
        funnel();
        throat();
        hoops();
        drum();
        fleshBehindGlass();
        clearInteriors();
        hall();
        dock();
        station();
        cable();
        cablePylons();
        drumFacade();
        anchors();
        floodlights();
        mouthRopes();
        throatRopes();
        swallowRopes();
        windowRopes();
        hallRopes();
        stationPosters();
        reopenedStation();
        stationRopes();
        lower = new Lower(this);
        lower.build();
        forest = new Forest(this);
        forest.build();
        Forest.cabAt(this, DOCK_CAB_X, FLOOR_HALL, DOCK_CAB_Z);
        Forest.cabPanel(this, DOCK_CAB_X, FLOOR_HALL, DOCK_CAB_Z, "下游客中心", 0xffb040);
        Forest.cabAt(this, NEW_CAB_X, FLOOR_STATION, NEW_CAB_Z);
        Forest.cabPanel(this, NEW_CAB_X, FLOOR_STATION, NEW_CAB_Z, "地面车站", 0xffb040);
        carve();
        // 真游客中心照着第一关那座整栋复制，所以要等大厅、花园层都铺完、缆车的路也掏好
        well = new Well(this);
        well.build();
        wellChamber = Chamber.well();
        declipRopes();
        lateRopes.forEach(Runnable::run);
        placeStaticLights();
        ropeColliders();
        // 爬出腺泡的口子：护林站平台的围栏在这里要断开
        clear(Lower.BURROW_X1 + 1, Lower.BURROW_Y, Lower.BURROW_Z);
        clear(Lower.BURROW_X1 + 1, Lower.BURROW_Y + 1, Lower.BURROW_Z);
    }

    // ---------- 查询 ----------

    public int blockCount() {
        return blocks.size();
    }

    public B block(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    /**
     * 真正铺到世界里的方块。肉已经是网格了，方块只在人碰得到的地方留一层看不见的墙，其他的肉不用铺。
     */
    /** 地图内容的指纹：方块有任何变化就不同，旧世界要删掉重铺。和顺序无关。 */
    public long fingerprint() {
        long[] sum = {VERSION};
        forEachPaved((x, y, z, b) -> {
            long h = key(x, y, z) * 0x9E3779B97F4A7C15L + b.ordinal();
            h ^= h >>> 29;
            sum[0] += h * 0xBF58476D1CE4E5B9L;
        });
        return sum[0];
    }

    public void forEachPaved(BlockVisitor visitor) {
        forEachBlock((x, y, z, b) -> {
            if (!b.isFlesh()) visitor.visit(x, y, z, b);
            else if (y < FLOOR_HALL && x >= 116 || x >= 260) visitor.visit(x, y, z, B.BARRIER);
        });
    }

    public interface BlockVisitor {
        void visit(int x, int y, int z, B block);
    }

    public void forEachBlock(BlockVisitor visitor) {
        for (Map.Entry<Long, B> entry : blocks.entrySet()) {
            long k = entry.getKey();
            visitor.visit((int) ((k >>> 40) & 0xffff) - 8192, (int) ((k >>> 20) & 0xfffff) - 1024, (int) (k & 0xfffff) - 8192, entry.getValue());
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 8192)) << 40 | ((long) (y + 1024)) << 20 | (long) (z + 8192);
    }

    void set(int x, int y, int z, B b) {
        blocks.put(key(x, y, z), b);
    }

    void clear(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, B b) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(x, y, z, b);
    }

    void clearBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) clear(x, y, z);
    }

    /** 一格是不是落在某个范围里。 */
    interface Cell {
        boolean test(int x, int y, int z);
    }

    /** 三层 fbm 大约是均值 0.5、标准差 0.13 的钟形：要占 share 这么多，阈值取多少。 */
    private static double shareCut(double share) {
        if (share <= 0.01) return -1;
        if (share >= 0.99) return 2;
        return 0.5 + 0.13 * Math.log(share / (1 - share)) / 1.7;
    }

    private static boolean tile(B b) {
        return b == B.TILE_A || b == B.TILE_B || b == B.WALL_STRIP;
    }

    /**
     * 屋子被它长进来了：墙上的瓷砖一片片换成肉，越往上越多（是从顶上压下来的），墙根裂开几道缝，肉从缝里顶出来；
     * 顶棚成片是肉；地上靠墙一圈有肉漫进来。只换瓷砖、顶棚、水磨石，门洞、窗、镜子不动。
     * 屋子沿 +x 越往里越多：west 是 x0 那头的量，east 是 x1 那头的，0 一点没有，1 墙上几乎全是肉。
     * shell 不为空时，换成肉的格子外面再补两层肉（肉要有厚度，曲面才算得出来），shell 圈住的格子不补；外面本来就包着肉的屋子传空。
     * keep 圈住的格子一律不动。返回换掉的格子。
     */
    List<int[]> overgrow(int x0, int z0, int x1, int z1, int floor, int ceil, double west, double east, Cell shell, Cell keep) {
        List<int[]> changed = new ArrayList<>();
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            double amount = west + (east - west) * Math.max(0, Math.min(1, (x - x0) / (double) Math.max(1, x1 - x0)));
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean wx = x == x0 - 1 || x == x1 + 1, wz = z == z0 - 1 || z == z1 + 1;
                for (int y = floor - 1; y <= ceil; y++) {
                    if (keep != null && keep.test(x, y, z)) continue;
                    B b = block(x, y, z);
                    boolean meat;
                    if (y == ceil) {
                        if (b != B.HALL_CEIL && !tile(b)) continue;
                        meat = Noise.fbm(x * 0.17, 11.3, z * 0.17, 3) < shareCut(amount * 1.1);
                    } else if (y == floor - 1) {
                        if (b != B.TERRAZZO && b != B.TERRAZZO_DARK && !tile(b)) continue;
                        // 只在墙根那一圈：窄过 5 格的屋子（走廊、床位）那个方向不铺，不然一路都是肉包；门洞跟前也不铺
                        boolean wideX = x1 - x0 >= 4, wideZ = z1 - z0 >= 4;
                        boolean ring = (wideX && (x <= x0 && block(x0 - 1, floor, z) != null || x >= x1 && block(x1 + 1, floor, z) != null))
                                || (wideZ && (z <= z0 && block(x, floor, z0 - 1) != null || z >= z1 && block(x, floor, z1 + 1) != null));
                        meat = ring && Noise.fbm(x * 0.31, 5.7, z * 0.31, 3) < shareCut(amount * 0.45);
                    } else {
                        if (!(wx || wz) || !tile(b)) continue;
                        double h = (y - floor) / (double) Math.max(1, ceil - 1 - floor);
                        double along = wx ? z : x, side = wx ? x * 0.7 : z * 0.7;
                        boolean patch = Noise.fbm(along * 0.19 + side, y * 0.27, side * 1.3, 3) < shareCut(amount * (0.35 + 0.75 * h));
                        // 瓷砖裂开的缝：竖着的一道一道，下面也有
                        boolean crack = Noise.fbm(along * 0.95 + side, y * 0.09, side + 3.1, 2) > shareCut(1 - amount * 0.16);
                        meat = patch || crack;
                    }
                    if (!meat) continue;
                    set(x, y, z, flesh(x, y, z));
                    changed.add(new int[]{x, y, z});
                    if (shell == null) continue;
                    int dx = x == x0 - 1 ? -1 : x == x1 + 1 ? 1 : 0, dz = z == z0 - 1 ? -1 : z == z1 + 1 ? 1 : 0;
                    int dy = y == ceil ? 1 : y == floor - 1 ? -1 : 0;
                    if (dy != 0) {
                        dx = 0;
                        dz = 0;
                    }
                    for (int k = 1; k <= 2; k++) {
                        int sx = x + dx * k, sy = y + dy * k, sz = z + dz * k;
                        if (block(sx, sy, sz) == null && !shell.test(sx, sy, sz)) set(sx, sy, sz, flesh(sx, sy, sz));
                    }
                }
            }
        }
        return changed;
    }

    /** 员工用的小房间：瓷砖墙、水磨石地、平顶。墙在 x0-1、x1+1、z0-1、z1+1，里面清空。 */
    void office(int x0, int z0, int x1, int z1, int floor, int ceil) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
                set(x, floor - 1, z, ((x + z) & 1) == 0 ? B.TERRAZZO : B.TERRAZZO_DARK);
                set(x, ceil, z, B.HALL_CEIL);
                for (int y = floor; y < ceil; y++) {
                    if (edge) set(x, y, z, y - floor <= 1 ? B.WALL_STRIP : y - floor == 2 ? B.TILE_B : B.TILE_A);
                    else clear(x, y, z);
                }
            }
        }
    }

    void light(int x, int y, int z, int level) {
        staticLights.add(new long[]{x, y, z, level});
    }

    /** 肉的种类：大块是暗肉和红肉，偶尔一条白筋。 */
    static B flesh(int x, int y, int z) {
        if (Noise.fbm(x * 0.05, y * 0.32, z * 0.05, 2) > 0.73) return B.FLESH_PALE;
        return Noise.fbm(x * 0.09 + 40, y * 0.09, z * 0.09, 2) < 0.5 ? B.FLESH_DARK : B.FLESH_MID;
    }

    /** 肉口的漏斗面高度。 */
    public static double funnelHeight(double r) {
        return 229 - 17 * Noise.smooth(0, 1, (CRATER_R - r) / (CRATER_R - FUNNEL_BOTTOM_R));
    }

    /** 食道中心线：车厢中心走的线。 */
    private double throatX(int y) {
        double[] p = new double[3];
        route.at(route.distAtY(y), p);
        return p[0] + Route.CABIN_CENTER_X;
    }

    /** 食道内壁半径：七道纵向皱襞挤成星形，钢环处勒紧。这是原作入口“像食道”的来源。 */
    private double throatRadius(double phi, int y) {
        double r = 8.4 + 1.7 * Math.cos(7 * phi + y * 0.035);
        r += (Noise.fbm(Math.cos(phi) * 2 + 9, y * 0.12, Math.sin(phi) * 2, 2) - 0.5) * 2.4;
        double pinch = 0;
        for (int hoop : hoopYs) pinch = Math.max(pinch, Math.exp(-(y - hoop) * (y - hoop) / 5.0));
        r = Noise.mix(r, 8.2, pinch);
        // 出口像喇叭一样张开
        return r + Noise.smooth(THROAT_BOTTOM + 8, THROAT_BOTTOM - 3, y) * 9;
    }

    // ---------- 地面和肉口 ----------

    private void ground() {
        for (int x = -44; x <= 100; x++) {
            for (int z = -56; z <= 56; z++) {
                double r = Math.hypot(x + 0.5 - CRATER_X, z + 0.5);
                if (r < CRATER_R - 0.5) continue;
                double n = Noise.fbm(x * 0.12, 3, z * 0.12, 3);
                B rock = n < 0.42 ? B.ROCK_B : n < 0.6 ? B.ROCK_A : B.ROCK_C;
                set(x, 229, z, rock);
                set(x, 228, z, B.ROCK_B);
                if (x > 17 && Noise.fbm(x * 0.2 + 7, 0, z * 0.2, 2) > 0.66) set(x, 230, z, rock);
            }
        }
    }

    private void funnel() {
        int reach = (int) CRATER_R + 1;
        for (int x = (int) CRATER_X - reach; x <= CRATER_X + reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double r = Math.hypot(x + 0.5 - CRATER_X, z + 0.5);
                if (r >= CRATER_R || r < FUNNEL_BOTTOM_R - 1) continue;
                double n = Noise.fbm(x * 0.18, 0, z * 0.18, 3);
                int top = (int) Math.floor(funnelHeight(r) + (n - 0.5) * 2.4);
                for (int y = top - 3; y <= top; y++) set(x, y, z, flesh(x, y, z));
            }
        }
    }

    private void throat() {
        for (int y = THROAT_TOP + 1; y >= THROAT_BOTTOM - 5; y--) {
            double cx = throatX(y);
            int reach = 23;
            for (int x = (int) cx - reach; x <= cx + reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5, rr = Math.hypot(dx, dz);
                    if (rr < 5 || rr > 22) continue;
                    double ri = throatRadius(Math.atan2(dz, dx), y);
                    if (rr >= ri && rr < ri + 3.2) set(x, y, z, flesh(x, y, z));
                }
            }
        }
    }

    private void hoops() {
        for (int y : hoopYs) {
            double cx = throatX(y);
            for (int x = (int) cx - 11; x <= cx + 11; x++) {
                for (int z = -11; z <= 11; z++) {
                    double rr = Math.hypot(x + 0.5 - cx, z + 0.5);
                    if (rr >= 8.0 && rr < 9.2) set(x, y, z, B.STEEL);
                }
            }
            hoopDistances.add(route.distAtY(y));
            // 吊住缆绳的两根横梁
            double cable = route.distAtY(y);
            double[] p = new double[3];
            route.at(cable, p);
            double cy = y + 0.5;
            double hx = p[0] + Route.CABIN_CENTER_X;
            for (int sign = -1; sign <= 1; sign += 2) {
                beams.add(new Beam("minecraft:polished_deepslate", hx, cy + Route.CABLE_Y - 1, sign * 8.6, hx, cy + Route.CABLE_Y - 1, sign * Route.CABLE_SIDE, 0.22, -1));
            }
        }
        // 每三道环上还有一盏没坏的灯
        for (int i = 0; i < hoopYs.size(); i += 3) {
            int y = hoopYs.get(i);
            double cx = throatX(y);
            boxes.add(new Box("minecraft:ochre_froglight", cx - 0.2, y + 0.9, 7.55, 0.4, 0.14, 0.14, 11));
            boxes.add(new Box("minecraft:ochre_froglight", cx - 0.2, y + 0.9, -7.69, 0.4, 0.14, 0.14, 11));
            light((int) Math.floor(cx), y + 1, 6, 7);
            light((int) Math.floor(cx), y + 1, -7, 7);
        }
    }

    // ---------- 空腔 ----------

    // ---------- 游客中心 ----------

    /** 圆盘外壳：大半个埋在肉里，只有朝着空腔的一面露出来，几扇窗还亮着应急灯。 */
    private void drum() {
        int reach = (int) DRUM_R + 2;
        for (int x = (int) DRUM_X - reach; x <= DRUM_X + reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double r = Math.hypot(x + 0.5 - DRUM_X, z + 0.5);
                if (r < DRUM_R) {
                    set(x, 46, z, B.HULL_DARK);
                    set(x, 26, z, B.HULL);
                } else if (r < DRUM_R + 1.2) {
                    for (int y = 26; y <= 46; y++) set(x, y, z, y >= 32 && y <= 35 ? B.HULL_GLASS : y % 7 == 0 ? B.HULL_DARK : B.HULL);
                }
            }
        }
        // 外壳里面原来是空的，肉不要长进来
        for (double a : new double[]{2.45, 2.72, 3.56, 3.83}) {
            double wx = DRUM_X + Math.cos(a) * (DRUM_R - 0.3), wz = Math.sin(a) * (DRUM_R - 0.3);
            boxes.add(new Box("minecraft:red_concrete", wx - 0.5, 32.6, wz - 0.5, 1.0, 1.8, 1.0, 9));
            light((int) Math.floor(DRUM_X + Math.cos(a) * (DRUM_R + 2.5)), 34, (int) Math.floor(Math.sin(a) * (DRUM_R + 2.5)), 7);
        }
    }

    /** 全景窗外：肉把玻璃整面堵死，只留一个洞。 */
    private void fleshBehindGlass() {
        for (int x = HX0 - 4; x <= HX1 + 4; x++) {
            for (int y = FLOOR_HALL - 3; y <= HALL_TOP + 4; y++) {
                double surface = 13 + 2.4 * Noise.fbm(x * 0.2, y * 0.25, 5, 3);
                double hx = (x + 0.5 - FIGURE_X) / 2.6, hy = (y + 0.5 - (FLOOR_HALL + 1.9)) / 2.9;
                double hole = 1 - (hx * hx + hy * hy);
                if (hole > 0) surface = Noise.mix(surface, 18.5, Math.min(1, hole * 2.4));
                for (int z = (int) Math.ceil(surface); z <= 24; z++) set(x, y, z, flesh(x, y, z));
            }
        }
    }

    private void clearInteriors() {
        clearBox(HX0, FLOOR_HALL, HZ0, HX1, HALL_TOP - 1, HZ1 - 1);
        clearBox(92, FLOOR_HALL - 1, 1, HX0 - 1, FLOOR_HALL + 6, 8);
        // 门和全景窗之间要是空的，大厅以外的肉不进外壳
        for (int x = (int) DRUM_X - 28; x <= DRUM_X + 28; x++) {
            for (int z = -28; z <= 12; z++) {
                if (x >= HX0 && x <= HX1 && z >= HZ0 && z <= HZ1) continue;
                if (Math.hypot(x + 0.5 - DRUM_X, z + 0.5) >= DRUM_R - 0.5) continue;
                for (int y = 27; y <= 45; y++) if (block(x, y, z) != null && block(x, y, z).isFlesh()) clear(x, y, z);
            }
        }
    }

    /** 大厅里这一格离圆心多远。 */
    private static double hd(int x, int z) {
        return Math.hypot(x + 0.5 - HCX, z + 0.5 - HCZ);
    }

    /** 半圆上的点。deg：0 朝 +x，90 朝大厅最里面，180 朝码头那边。 */
    private static double[] arc(double deg, double r) {
        double a = Math.toRadians(deg);
        return new double[]{HCX + Math.cos(a) * r, HCZ - Math.sin(a) * r};
    }

    /**
     * 游客中心的美食零售环廊：现代商业景区的中庭，两层，中间挑空。
     * 一层是店铺，二层环廊围着中庭，顶上钢梁和黄蓝管线。公园封了以后灯只亮一半，肉从碎窗爬进来。
     */
    private void hall() {
        int y0 = FLOOR_HALL;
        for (int x = HX0 - 2; x <= HX1 + 2; x++) {
            for (int z = HZ0 - 1; z <= HZ1; z++) {
                double d = hd(x, z);
                if (d >= HR + 1.6) continue;
                if (z == HZ1) {
                    if (x < HX0 || x > HX1) for (int y = y0; y < HALL_TOP; y++) set(x, y, z, B.STEEL);
                    continue;
                }
                set(x, y0 - 1, z, hallFloor(x, z, d));
                // 顶上破了一个洞，肉从那里压下来
                double hole = Math.hypot(x + 0.5 - 115.5, z + 0.5 - 9);
                if (hole < 2.2 + 1.6 * Noise.fbm(x * 0.5, z * 0.5, 7, 2)) {
                    for (int y = HALL_TOP + 1; y <= HALL_TOP + 3; y++) set(x, y, z, flesh(x, y, z));
                } else {
                    set(x, HALL_TOP, z, B.HALL_CEIL);
                }
                if (d >= HR) {
                    for (int y = y0; y < HALL_TOP; y++) set(x, y, z, hallWall(x, y, z));
                } else if (d >= HVOID + 0.7) {
                    set(x, DECK, z, B.HALL_CEIL);
                }
            }
        }
        hallWindow();
        hallEntrance();
        hallBalcony();
        hallCeiling();
        giftShop(116, 124, 1);
        ribShack(132, 140, 1);
        infoDesk();
        hallFurniture();
        hallWallPanels();
        kitchenDoor();
        hallStairs();
    }

    /** 弧形的墙面：方块只能拼出台阶，一圈平滑的板贴在里面，看着才是圆的。 */
    private void hallWallPanels() {
        int y0 = FLOOR_HALL;
        double step = 10, r = HR - 1.1;
        for (double deg = 0; deg < 180; deg += step) {
            double[] a = arc(deg, r), b = arc(deg + step, r);
            boolean door = deg + step / 2 > 143 && deg + step / 2 < 165;
            // 相邻两段半径错开一点，接缝处的面不共面就不闪
            double in = Math.floorMod((int) (deg / step), 2) * 0.012;
            double[] c = arc(deg, r - in), e = arc(deg + step, r - in);
            double[] mid = arc(deg + step / 2, r);
            boolean inShop = mid[1] < 2 && (mid[0] > 115 && mid[0] < 125 || mid[0] > 131 && mid[0] < 141);
            if (inShop) {
                // 店里是店自己的墙
            } else if (!door) {
                panels.add(new Parts.Panel("minecraft:cyan_terracotta", c[0], y0 + 0.9, c[1], e[0], y0 + 0.9, e[1], 1.79, 0.5, -1));
                panels.add(new Parts.Panel("minecraft:orange_terracotta", c[0], y0 + 1.95, c[1], e[0], y0 + 1.95, e[1], 0.29, 0.5, -1));
                panels.add(new Parts.Panel("minecraft:white_terracotta", c[0], y0 + 3.05, c[1], e[0], y0 + 3.05, e[1], 1.89, 0.5, -1));
            } else {
                panels.add(new Parts.Panel("minecraft:white_terracotta", c[0], y0 + 4.5, c[1], e[0], y0 + 4.5, e[1], 0.99, 0.5, -1));
            }
            panels.add(new Parts.Panel("minecraft:cyan_terracotta", c[0], DECK + 1.6, c[1], e[0], DECK + 1.6, e[1], 1.39, 0.5, -1));
            panels.add(new Parts.Panel("minecraft:orange_terracotta", c[0], DECK + 2.42, c[1], e[0], DECK + 2.42, e[1], 0.23, 0.5, -1));
            panels.add(new Parts.Panel("minecraft:white_terracotta", c[0], DECK + 4.2, c[1], e[0], DECK + 4.2, e[1], 3.39, 0.5, -1));
        }
        // 贴了板以后人不能走进板里
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z < HZ1; z++) {
                double d = hd(x, z);
                if (d < HR - 1.35 || d >= HR) continue;
                if (z >= 2 && z <= 6 && x < 115) continue;
                for (int y = y0; y <= y0 + 2; y++) if (block(x, y, z) == null) set(x, y, z, B.BARRIER);
            }
        }
    }

    /** 快餐店的后墙和 STAFF ONLY 门。门后没有后厨：肉紧贴着门框，压出灶台和货架的形状。 */
    private void kitchenDoor() {
        int y0 = FLOOR_HALL;
        for (int x = 133; x <= 139; x++) for (int y = y0; y <= y0 + 4; y++) set(x, y, KITCHEN_Z, y - y0 <= 1 ? B.WALL_STRIP : B.TILE_A);
        for (int y = y0; y <= y0 + 1; y++) set(KITCHEN_X, y, KITCHEN_Z, B.BARRIER);
        for (int x = 132; x <= 136; x++) {
            for (int y = y0 - 1; y <= y0 + 4; y++) {
                for (int z = KITCHEN_Z - 3; z < KITCHEN_Z; z++) {
                    if (hd(x, z) < HR) set(x, y, z, flesh(x, y, z));
                }
            }
        }
        // 门洞正对着的这块肉：灶台的台面压出一道沟，货架压出一格格的凹坑
        clear(KITCHEN_X, y0, KITCHEN_Z - 1);
        clear(KITCHEN_X, y0 + 1, KITCHEN_Z - 1);
        rope(List.of(new double[]{KITCHEN_X + 0.5, y0 + 2.2, KITCHEN_Z - 0.7}, new double[]{KITCHEN_X + 0.2, y0 + 1.2, KITCHEN_Z - 0.6}, new double[]{KITCHEN_X + 0.8, y0 + 0.2, KITCHEN_Z - 0.5}), 0.5, 0, 0.35, false);
        rope(List.of(new double[]{KITCHEN_X - 0.2, y0 + 1.6, KITCHEN_Z - 0.75}, new double[]{KITCHEN_X + 1.2, y0 + 1.0, KITCHEN_Z - 0.75}), 0.42, 1, 0.3, false);
        boxes.add(new Box("minecraft:waxed_oxidized_copper", KITCHEN_X - 0.06, y0, KITCHEN_Z + 0.94, 1.12, 2.14, 0.08, -1));
        texts.add(new Text("员工专用", KITCHEN_X + 0.5, y0 + 2.28, KITCHEN_Z + 1.04, 0f, 0xf2e8cc, 0.5f, 0xff24211f, 8));
    }

    /** 下到休闲花园层的楼梯。大厅地上敞开一个楼梯井，三面围着跟窗前一样的铁栏杆，走道上一眼就看得见台阶往下走。
     *  东头是入口，平时拉一道警戒线（剧情里生成，停电以后松开掉到地上）。栏杆上面还有一格看不见的墙，翻不过去：
     *  以前这里敞着没有栏杆，从窗前往东一走就从西头踩空掉下去，掉下去也找不到回大厅的路。 */
    private void hallStairs() {
        int y0 = FLOOR_HALL;
        for (int z = 5; z <= 7; z++) {
            for (int x = STAIR_TOP_X; x >= STAIR_TOP_X - 9; x--) {
                int y = y0 - 1 - (STAIR_TOP_X - x);
                clear(x, y0 - 1, z);
                clearBox(x, y + 1, z, x, y0 + 2, z);
                set(x, y, z, B.STAIR_E);
            }
        }
        // 楼板夹层里的两侧墙
        for (int x = STAIR_TOP_X; x >= STAIR_TOP_X - 9; x--) {
            int y = y0 - 1 - (STAIR_TOP_X - x);
            for (int z : new int[]{4, 8}) {
                if (y < y0 - 1) for (int yy = Math.max(y, Lower.GARDEN_CEIL + 1); yy < y0 - 1; yy++) set(x, yy, z, B.TILE_A);
            }
        }
        for (int y = Lower.GARDEN_CEIL + 1; y < y0 - 1; y++) for (int z = 4; z <= 8; z++) set(STAIR_TOP_X + 2, y, z, B.WALL_A);
        fill(STAIR_TOP_X + 1, y0 - 1, 5, STAIR_TOP_X + 1, y0 - 1, 8, B.TERRAZZO_DARK);
        // 大厅这一层：南北两边和西头围铁栏杆，顶上一根铜扶手，跟窗前的栏杆一个样子。北边那根钢柱落在栏杆线上，栏杆从它两边接过去
        int west = STAIR_TOP_X - 10;
        for (int x = west; x <= STAIR_TOP_X; x++) {
            for (int z : new int[]{4, 8}) {
                if (block(x, y0, z) == B.STEEL) continue;
                set(x, y0, z, x == west ? (z == 4 ? B.BARS_ES : B.BARS_EN) : B.BARS_X);
                set(x, y0 + 1, z, B.BARRIER);
            }
        }
        for (int z = 5; z <= 7; z++) {
            set(west, y0, z, B.BARS_Z);
            set(west, y0 + 1, z, B.BARRIER);
        }
        for (double z : new double[]{4.42, 8.42}) boxes.add(new Box("minecraft:waxed_copper_block", west + 0.42, y0 + 1, z, STAIR_TOP_X + 1 - west - 0.42, 0.08, 0.16, -1));
        boxes.add(new Box("minecraft:waxed_copper_block", west + 0.42, y0 + 1, 4.42, 0.16, 0.08, 4.16, -1));
        // 入口两边的立柱，警戒线拴在上面。线后面那一格看不见的墙，松线的时候剧情里拆掉
        for (double z : new double[]{4.5, 8.5}) {
            beams.add(new Parts.Beam("minecraft:polished_deepslate", STAIR_TOP_X + 1, y0, z, STAIR_TOP_X + 1, y0 + 1.12, z, 0.09, -1));
            boxes.add(new Box("minecraft:yellow_concrete", STAIR_TOP_X + 0.94, y0 + 1.1, z - 0.06, 0.12, 0.06, 0.12, -1));
        }
        fill(STAIR_TOP_X, y0, 5, STAIR_TOP_X, y0 + 1, 7, B.BARRIER);
        // 两边铜扶手钉在楼梯井的墙上，从大厅地面的边上开始，顺着台阶一直下到花园
        for (double side : new double[]{5.07, 7.93}) {
            beams.add(new Parts.Beam("minecraft:waxed_copper_block", STAIR_TOP_X - 0.45, y0, side, STAIR_TOP_X - 9.5, y0 - 9.05, side, 0.07, -1));
        }
        // 每一级台阶的边上一条亮的防滑条，两侧墙上一路应急灯，往下走看得清楚。大厅地面以上没有墙，那一截不挂灯
        for (int x = STAIR_TOP_X; x >= STAIR_TOP_X - 9; x--) {
            int y = y0 - 1 - (STAIR_TOP_X - x);
            boxes.add(new Box("minecraft:yellow_terracotta", x + 0.46, y + 0.97, 5.05, 0.08, 0.05, 2.9, 11));
            if ((STAIR_TOP_X - x) % 3 == 1) {
                if (y + 2.52 < y0 - 1) {
                    boxes.add(new Box("minecraft:shroomlight", x + 0.2, y + 2.4, 5.02, 0.6, 0.12, 0.05, 15));
                    boxes.add(new Box("minecraft:shroomlight", x + 0.2, y + 2.4, 7.93, 0.6, 0.12, 0.05, 15));
                }
                light(x, y + 2, 6, 9);
            }
        }
        // 栏杆上方从二层环廊吊下来一块牌子，朝着窗前的走道。下半块是本站进度（剧情里写），上半块印着去花园层，开了以后亮成绿的
        for (double x : new double[]{STAIR_TOP_X - 2.3, STAIR_TOP_X - 0.7}) {
            beams.add(new Parts.Beam("minecraft:polished_deepslate", x, y0 + 3.5, 8.47, x, DECK, 8.47, 0.035, -1));
        }
        boxes.add(new Box("minecraft:polished_deepslate", STAIR_TOP_X - 2.6, y0 + 2.15, 8.44, 2.2, 1.4, 0.06, -1));
        texts.add(new Text("↓  休闲花园层", STAIR_TOP_X - 1.5, y0 + 2.95, 8.52, 0f, 0x6f7a72, 0.6f, 0, -1));
        // 下到底，回头能看见往上的路
        texts.add(new Text("↑  游客中心大厅", STAIR_TOP_X - 9.08, Lower.GARDEN_CEIL - 0.55, 6.5, 90f, 0xb8ffc8, 0.55f, 0xff0d5a2a, 15));
    }

    /** 水磨石地面：中庭一圈放射状的拼花，外面素色，两道铜和青的嵌条。 */
    private B hallFloor(int x, int z, double d) {
        if (d >= HVOID - 1 && d < HVOID - 0.2) return B.WALL_STRIP;
        if (d >= 4.6 && d < 5.3) return B.BRASS;
        if (d < HVOID - 1) {
            double deg = Math.toDegrees(Math.atan2(HCZ - z - 0.5, x + 0.5 - HCX));
            boolean wedge = Math.floorMod((int) Math.floor(deg / 15), 2) == 0;
            return wedge ? B.TERRAZZO : B.TERRAZZO_DARK;
        }
        return rnd.nextDouble() < 0.05 ? B.TERRAZZO_DARK : B.TERRAZZO;
    }

    /** 墙：下面青色墙裙和铜线，上面米白，二层有一条深色长窗。每 30 度一根钢壁柱。 */
    private B hallWall(int x, int y, int z) {
        double deg = Math.toDegrees(Math.atan2(HCZ - z - 0.5, x + 0.5 - HCX));
        if (Math.floorMod((int) Math.round(deg), 30) < 2 && hd(x, z) < HR + 1) return B.STEEL;
        int h = y - FLOOR_HALL;
        if (h <= 1) return B.WALL_STRIP;
        if (h == 2) return B.BRASS;
        if (h == 5) return B.STEEL;
        if (h == 6 || h == 7) return B.WALL_STRIP;
        if (h == 8 && hd(x, z) < HR + 1) return B.HULL_GLASS;
        return B.TILE_A;
    }

    /** 全景窗：通高的玻璃，细竖棂，二层楼板那里一道横梁。x=127 到 129 下半截碎了，右上角也碎了一块，肉就从这两处进来。 */
    private void hallWindow() {
        int y0 = FLOOR_HALL;
        for (int x = HX0; x <= HX1; x++) {
            for (int y = y0; y < HALL_TOP; y++) {
                boolean broken = (x >= 127 && x <= 129 && y <= y0 + 2) || (x >= 141 && x <= 143 && y >= y0 + 7 && y <= y0 + 9);
                if (y == y0) {
                    if (!broken) set(x, y, HZ1, B.WALL_STRIP);
                } else if (y == DECK && hd(x, HZ1) >= HVOID) {
                    set(x, y, HZ1, B.STEEL);
                } else if (!broken) {
                    set(x, y, HZ1, B.GLASS_X);
                }
            }
            set(x, y0 - 1, HZ1, B.TERRAZZO);
            set(x, HALL_TOP, HZ1, B.STEEL);
            if ((x - HX0) % 4 == 2) boxes.add(new Box("minecraft:polished_deepslate", x + 0.42, y0 + 1, HZ1 + 0.36, 0.16, HALL_TOP - y0 - 1, 0.3, -1));
        }
        texts.add(new Text("请勿触摸\n观景玻璃", 121.9, FLOOR_HALL + 2.0, HZ1 - 0.08, 180f, 0x2c2a24, 0.8f, 0xe0d8d0b8, 6));
    }

    /** 从 3 号码头进来的门洞：一段短走廊穿过外墙。 */
    private void hallEntrance() {
        int y0 = FLOOR_HALL;
        for (int z = -12; z <= 11; z++) {
            for (int y = y0; y <= y0 + 11; y++) {
                if (hd(HX0, z) < HR) continue;
                set(HX0, y, z, y - y0 <= 1 ? B.WALL_STRIP : y - y0 == 2 ? B.BRASS : B.TILE_A);
            }
        }
        for (int x = HX0; x <= 113; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = y0; y <= y0 + 3; y++) clear(x, y, z);
                set(x, y0 - 1, z, B.TERRAZZO);
                set(x, y0 + 4, z, B.STEEL);
            }
            for (int y = y0; y <= y0 + 3; y++) {
                set(x, y, 1, B.TILE_A);
                set(x, y, 7, B.TILE_A);
            }
        }
    }

    /** 二层环廊的边：栏杆、灯带、钢柱。人上不去，只从下面看。 */
    private void hallBalcony() {
        int y0 = FLOOR_HALL;
        double step = 12;
        for (double deg = 2; deg < 178; deg += step) {
            double[] a = arc(deg, HVOID + 0.3), b = arc(deg + step, HVOID + 0.3);
            beams.add(new Parts.Beam("minecraft:waxed_copper_block", a[0], DECK + 2.1, a[1], b[0], DECK + 2.1, b[1], 0.09, -1));
            beams.add(new Parts.Beam("minecraft:polished_deepslate", a[0], DECK + 1.5, a[1], b[0], DECK + 1.5, b[1], 0.05, -1));
            beams.add(new Parts.Beam("minecraft:polished_deepslate", a[0], DECK + 1, a[1], a[0], DECK + 2.1, a[1], 0.07, -1));
            // 楼板边的灯带，三段灭一段
            boolean alive = Math.floorMod((int) (deg / step), 3) != 2;
            double[] c = arc(deg, HVOID - 0.02), e = arc(deg + step, HVOID - 0.02);
            lamps.add(new Parts.Lamp(alive ? 2 : 3, "minecraft:ochre_froglight", c[0], DECK - 0.2, c[1], e[0], DECK - 0.2, e[1], 0.12, 13, 1));
            // 楼板的边：一圈平滑的板盖住方块的锯齿，下面再托一层
            // 相邻两块板错开一点高度，重叠的地方才不会共面闪烁
            double wobble = Math.floorMod((int) (deg / step), 2) * 0.012;
            double[] f = arc(deg, HVOID + 0.9), g = arc(deg + step, HVOID + 0.9);
            panels.add(new Parts.Panel("minecraft:smooth_quartz", f[0], DECK + 0.5 + wobble, f[1], g[0], DECK + 0.5 + wobble, g[1], 1.2, 1.82, -1));
            double[] i = arc(deg, HVOID + 1.6), j = arc(deg + step, HVOID + 1.6);
            panels.add(new Parts.Panel("minecraft:smooth_quartz", i[0], DECK - 0.12 - wobble, i[1], j[0], DECK - 0.12 - wobble, j[1], 0.16, 3.3, -1));
        }
        // 钢柱，避开从门口到窗前的路
        for (double deg : new double[]{20, 52, 128, 160}) {
            double[] c = arc(deg, HVOID - 0.9);
            int x = (int) Math.floor(c[0]), z = (int) Math.floor(c[1]);
            fill(x, y0, z, x, HALL_TOP - 1, z, B.STEEL);
            boxes.add(new Box("minecraft:waxed_copper_block", x - 0.08, y0 + 1.1, z - 0.08, 1.16, 0.12, 1.16, -1));
        }
        // 环廊下面的筒灯：一半是灭的。
        for (double deg = 10; deg < 175; deg += 20) {
            double[] c = arc(deg, 14.6);
            int x = (int) Math.floor(c[0]), z = (int) Math.floor(c[1]);
            boolean alive = Math.floorMod((int) (deg / 20), 2) == 0;
            lamps.add(new Parts.Lamp(alive ? 1 : 3, "minecraft:ochre_froglight", x + 0.2, DECK - 0.25, z + 0.5, x + 0.8, DECK - 0.25, z + 0.5, 0.5, 14, 1));
            lampLights.add(new int[]{alive ? 1 : 3, x, DECK - 1, z, 9});
        }
        // 楼板下贴墙走的灰管
        pipeArc(2, 17.2, DECK - 0.5, 0.2, 6, 174);
        // 二层的招牌
        double[] m = arc(90, HR - 0.45);
        texts.add(new Text("博物馆与展览层", m[0], DECK + 2.4, m[1], 0f, 0xf0c060, 1.9f, 0xff1b2a2a, 12));
        // 两条挂幅
        for (double deg : new double[]{62, 118}) {
            double[] c = arc(deg, HVOID - 0.5);
            boxes.add(new Box("minecraft:cyan_wool", c[0] - 1.1, DECK + 0.9, c[1], 2.2, 3.9, 0.06, -1));
            texts.add(new Text("安诺丹\n公园家族", c[0], DECK + 2.0, c[1] + 0.08, 0f, 0xf2e8cc, 1.5f, 0, -1));
        }
    }

    /** 顶：放射的钢梁，一圈灯带，绕着走的黄蓝管线。 */
    private void hallCeiling() {
        for (double deg = 8; deg < 178; deg += 20) {
            double[] a = arc(deg, HR - 0.3), b = arc(deg, 1.2);
            beams.add(new Parts.Beam("minecraft:polished_deepslate", a[0], HALL_TOP - 0.4, a[1], b[0], HALL_TOP - 0.4, b[1], 0.42, -1));
        }
        double step = 14;
        for (double deg = 4; deg < 176; deg += step) {
            double[] a = arc(deg, 8.5), b = arc(deg + step, 8.5);
            boolean alive = Math.floorMod((int) (deg / step), 4) != 3;
            lamps.add(new Parts.Lamp(alive ? 0 : 3, "minecraft:ochre_froglight", a[0], HALL_TOP - 0.85, a[1], b[0], HALL_TOP - 0.85, b[1], 0.16, 14, 1));
        }
        for (double deg = 15; deg < 175; deg += 30) {
            double[] c = arc(deg, 8.5);
            lampLights.add(new int[]{0, (int) Math.floor(c[0]), HALL_TOP - 2, (int) Math.floor(c[1]), 9});
        }
        pipeArc(0, 17.2, HALL_TOP - 1.6, 0.34, 4, 176);
        pipeArc(1, 16.5, HALL_TOP - 1.1, 0.28, 4, 176);
        // 一根蓝管横穿中庭，接到窗框上，一根黄管从顶上下到礼品店后面
        pipes.add(new Parts.Pipe(1, 112, HALL_TOP - 2.6, 11.2, 144, HALL_TOP - 2.6, 11.2, 0.22));
        pipes.add(new Parts.Pipe(0, 112.2, HALL_TOP - 1.6, 4.5, 112.2, DECK + 0.5, 4.5, 0.2));
    }

    private void pipeArc(int kind, double r, double y, double rad, double from, double to) {
        double step = 11;
        for (double deg = from; deg < to; deg += step) {
            double[] a = arc(deg, r), b = arc(Math.min(to, deg + step), r);
            pipes.add(new Parts.Pipe(kind, a[0], y, a[1], b[0], y, b[1], rad));
        }
    }

    /** 店面的壳：两侧墙、木招牌、门两边的玻璃。 */
    private void shopShell(int x0, int x1, int front, B band) {
        int y0 = FLOOR_HALL;
        for (int z = front; z >= HZ0; z--) {
            for (int x : new int[]{x0, x1}) {
                if (hd(x, z) >= HR) continue;
                for (int y = y0; y < DECK; y++) set(x, y, z, y - y0 <= 1 ? B.WALL_STRIP : B.TILE_A);
            }
        }
        for (int x = x0; x <= x1; x++) {
            set(x, y0 + 3, front, band);
            set(x, y0 + 4, front, band);
        }
        for (int y = y0; y <= y0 + 2; y++) {
            set(x0 + 1, y, front, B.SHOP_GLASS);
            set(x1 - 1, y, front, B.SHOP_GLASS);
        }
    }

    /** 礼品店：木招牌、挂满 T 恤的墙、明信片架、一堆肉做的毛绒玩具。 */
    private void giftShop(int x0, int x1, int front) {
        int y0 = FLOOR_HALL;
        double mid = (x0 + x1 + 1) / 2.0;
        shopShell(x0, x1, front, B.WOOD);
        texts.add(new Text("礼品与纪念品\n公园官方商品", mid, y0 + 3.3, front + 1.04, 0f, 0x2a1d10, 1.25f, 0xffc9a86a, 12));
        // 柜台和货架
        fill(x0 + 1, y0, front - 2, x0 + 3, y0, front - 2, B.WOOD_DARK);
        fill(x1 - 3, y0, front - 2, x1 - 1, y0, front - 2, B.WOOD_DARK);
        // T 恤：一排彩色的小块挂在背板上
        int[][] shirts = {{0xb04030, 0}, {0x2c5a80, 1}, {0x3a3a3a, 2}, {0xd8c8a0, 3}, {0x7a2c2c, 4}};
        String[] wool = {"minecraft:red_wool", "minecraft:blue_wool", "minecraft:gray_wool", "minecraft:white_wool", "minecraft:brown_wool"};
        for (int i = 0; i < 5; i++) {
            double sx = x0 + 1.4 + i * 1.3;
            boxes.add(new Box(wool[i], sx, y0 + 1.5, front - 3.5, 0.9, 1.0, 0.06, -1));
            boxes.add(new Box("minecraft:polished_deepslate", sx - 0.1, y0 + 2.5, front - 3.5, 1.1, 0.04, 0.04, -1));
            if (i % 2 == 0) texts.add(new Text("我从息壤\n活着回来了", sx + 0.45, y0 + 1.95, front - 3.42, 0f, 0xf2e8cc, 0.3f, 0, -1));
        }
        // 明信片架：门口的一根立杆加几张彩色小卡片
        for (int i = 0; i < 6; i++) {
            String card = i % 3 == 0 ? "minecraft:orange_concrete" : i % 3 == 1 ? "minecraft:cyan_concrete" : "minecraft:yellow_concrete";
            boxes.add(new Box(card, x0 + 2.2, y0 + 0.7 + i * 0.26, front + 1.2, 0.2, 0.22, 0.02, -1));
        }
        boxes.add(new Box("minecraft:iron_block", x0 + 2.28, y0, front + 1.17, 0.04, 2.3, 0.04, -1));
        // 毛绒玩具：柜台上一小堆圆滚滚的红块
        for (int i = 0; i < 4; i++) {
            boxes.add(new Box("minecraft:red_wool", x0 + 1.2 + i * 0.5, y0 + 1, front - 1.9 + (i % 2) * 0.15, 0.36, 0.3, 0.36, -1));
        }
        boxes.add(new Box("minecraft:ochre_froglight", mid - 1.5, y0 + 4.9, front - 2.5, 3, 0.05, 0.3, 13));
        light((int) mid, y0 + 3, front - 2, 7);
        light((int) mid, y0 + 2, front + 2, 5);
    }

    /** 快餐：红白条纹雨篷，发亮的菜单，门口几张桌子。 */
    private void ribShack(int x0, int x1, int front) {
        int y0 = FLOOR_HALL;
        double mid = (x0 + x1 + 1) / 2.0;
        shopShell(x0, x1, front, B.WALL_A);
        texts.add(new Text("肋排小屋", mid, y0 + 3.2, front + 1.04, 0f, 0xffc040, 2.8f, 0xff5a1414, 15));
        for (int x = x0; x <= x1; x++) {
            boxes.add(new Box((x & 1) == 0 ? "minecraft:red_wool" : "minecraft:white_wool", x, y0 + 2.62, front + 2, 1, 0.1, 0.8, -1));
        }
        // 柜台左边留一个口，人才走得到后厨的门
        fill(x0 + 3, y0, front - 1, x1 - 1, y0, front - 1, B.COUNTER);
        texts.add(new Text("肋排  $6.95\n汉堡  $3.50\n汽水  $1.25", mid, y0 + 2.2, front - 3.45, 0f, 0xfff0c0, 0.75f, 0xff1a120c, 13));
        boxes.add(new Box("minecraft:polished_deepslate", mid - 1.6, y0 + 1, front - 3.6, 3.2, 2, 0.1, -1));
        boxes.add(new Box("minecraft:ochre_froglight", mid - 1.5, y0 + 4.9, front - 2, 3, 0.05, 0.3, 13));
        light((int) mid, y0 + 3, front - 2, 7);
        // 桌子：一张翻了
        for (double[] t : new double[][]{{x0 + 1.5, front + 2.2}, {x0 + 5.5, front + 2.2}, {x0 + 9, front + 2.8}}) {
            int tx = (int) Math.floor(t[0]), tz = (int) Math.floor(t[1]);
            set(tx, y0, tz, B.TABLE);
            boxes.add(new Box("minecraft:polished_deepslate", tx + 0.44, y0, tz + 0.44, 0.12, 0.5, 0.12, -1));
            for (double[] o : new double[][]{{-0.75, 0.2}, {1.35, 0.5}}) {
                boxes.add(new Box("minecraft:polished_deepslate", tx + o[0] + 0.14, y0, tz + o[1] + 0.14, 0.06, 0.42, 0.06, -1));
                boxes.add(new Box("minecraft:red_concrete", tx + o[0], y0 + 0.42, tz + o[1], 0.34, 0.07, 0.34, -1));
            }
        }
        set(x1 + 3, y0, front + 1, B.BIN);
    }

    /** 服务台和背后的公园地图。 */
    private void infoDesk() {
        int y0 = FLOOR_HALL;
        fill(125, y0, -2, 131, y0, -2, B.TILE_A);
        for (int x = 125; x <= 131; x++) boxes.add(new Box("minecraft:waxed_copper_block", x, y0 + 1, -2, 1, 0.08, 1, -1));
        boxes.add(new Box("minecraft:waxed_copper_block", 125.5, y0 + 2.75, -5.9, 5, 0.06, 0.06, -1));
        texts.add(new Text("游客服务", 128, y0 + 3.0, -5.9, 0f, 0xf2e8cc, 1.1f, 0xff1b3a3a, 12));
        // 钟停在 23:05：楼有电，钟不走。它照着井底那只停了的钟长的
        texts.add(new Text("23:05", 132.9, y0 + 3.0, -5.9, 0f, 0xe8dcc0, 0.8f, 0xff0c0a08, 10));
        texts.add(new Text("息壤\n国家公园\n\n您在这里：第一层", 128, y0 + 1.1, -5.9, 0f, 0x2a2418, 0.7f, 0xffd8c9a0, 8));
        light(128, y0 + 3, -3, 7);
    }

    /** 观景的长椅、栏杆、望远镜、指示牌、枯死的花槽，地上掉的东西。 */
    private void hallFurniture() {
        int y0 = FLOOR_HALL;
        // 窗前栏杆，人只能站到这里
        for (int x = 112; x <= 144; x++) {
            // 肉从碎窗进来，把这几格栏杆顶掉了。人还是过不去
            if (x >= 127 && x <= 129) {
                fill(x, y0, 10, x, y0 + 3, 10, B.BARRIER);
                continue;
            }
            set(x, y0, 10, B.BARS_X);
            boxes.add(new Box("minecraft:waxed_copper_block", x, y0 + 1, 10.42, 1, 0.08, 0.16, -1));
        }
        fill(111, y0, 10, 111, y0 + 3, 11, B.BARRIER);
        fill(145, y0, 10, 145, y0 + 3, 11, B.BARRIER);
        // 长椅朝着窗
        fill(113, y0, 9, 120, y0, 9, B.BENCH);
        // 投币望远镜
        for (double x : new double[]{121.6, 133.6, 137.5}) {
            boxes.add(new Box("minecraft:polished_deepslate", x - 0.06, y0, 9.4, 0.12, 1.2, 0.12, -1));
            boxes.add(new Box("minecraft:waxed_oxidized_copper", x - 0.18, y0 + 1.2, 9.25, 0.36, 0.22, 0.42, -1));
        }
        // 黄色指示牌，红色警告
        texts.add(new Text("观景区", 122, y0 + 1.75, 10.34, 180f, 0x2a2418, 0.75f, 0xffd8b030, 9));
        texts.add(new Text("警告\n请勿倚靠玻璃", 134.5, y0 + 1.6, 10.34, 180f, 0xf4ecd8, 0.65f, 0xffa02818, 9));
        // 导览立牌
        boxes.add(new Box("minecraft:polished_deepslate", 117.1, y0, 3.3, 0.8, 2.4, 0.12, -1));
        texts.add(new Text("← 三号缆车码头\n↑ 观景区\n→ 肋排小屋", 117.5, y0 + 1.3, 3.45, 0f, 0xf2e8cc, 0.5f, 0, 11));
        // 花槽：枯死的灌木
        for (double deg : new double[]{128}) {
            double[] c = arc(deg, HVOID + 0.4);
            int x = (int) Math.floor(c[0]), z = (int) Math.floor(c[1]) + 1;
            fill(x - 2, y0, z, x + 2, y0, z, B.WOOD_DARK);
            for (int k = -2; k <= 2; k += 2) set(x + k, y0 + 1, z, B.DEAD_PLANT);
        }
        set(114, y0, 7, B.BIN);
        // 掉在地上的吊顶碎片和宣传册，只在顶上破洞的那一片
        for (int i = 0; i < 16; i++) {
            double x = 113 + rnd.nextDouble() * 9, z = 5 + rnd.nextDouble() * 4, a = rnd.nextDouble() * 6.28, tilt = rnd.nextDouble() * 0.12;
            panels.add(new Parts.Panel("minecraft:smooth_quartz", x, y0 + 0.05 + tilt, z, x + Math.cos(a) * 0.9, y0 + 0.05, z + Math.sin(a) * 0.9, 0.05, 0.7, -1));
        }
        for (int i = 0; i < 22; i++) {
            flats.add(new Flat("minecraft:paper", 116 + rnd.nextDouble() * 26, y0 + 0.02, 1.5 + rnd.nextDouble() * 8, rnd.nextDouble() * 6.28, 0.42f));
        }
        texts.add(new Text("安诺丹大家庭祝您：\n下行愉快！", 128, DECK + 4.6, HCZ - HR + 0.55, 0f, 0x4a4438, 1.5f, 0, -1));
        // 应急灯
        boxes.add(new Box("minecraft:red_concrete", 144.2, DECK - 1.2, 5.5, 0.4, 0.18, 0.4, 13));
        light(143, DECK - 2, 6, 5);
        texts.add(new Text("出口", HX0 + 3.97, FLOOR_HALL + 3.5, 8.1, 90f, 0xb8ffc8, 1.2f, 0xff0d5a2a, 13));
    }

    /** 肉爬进大厅：从右上角的碎窗越过环廊垂下来，一根缠着钢柱，一根搭在二层栏杆上。 */
    private void hallRopes() {
        int y0 = FLOOR_HALL;
        rope(List.of(new double[]{142, y0 + 8.5, 14}, new double[]{141.2, y0 + 8, 10}, new double[]{139.5, y0 + 6.2, 7.5}, new double[]{138.2, y0 + 5.6, 6.1},
                new double[]{137.6, y0 + 3.6, 5.6}, new double[]{137.2, y0 + 1, 5.2}, new double[]{136, y0 + 0.35, 4.2}), 0.62, 0, 0.5, false);
        rope(List.of(new double[]{143, y0 + 9, 14}, new double[]{142, y0 + 7.4, 9}, new double[]{141.5, y0 + 5.9, 5.6}, new double[]{142, y0 + 5.8, 2.2}), 0.42, 1, 0.5, false);
        double[] c = arc(52, HVOID + 0.4);
        List<double[]> around = new ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double f = i / 16.0, a = f * Math.PI * 5;
            around.add(new double[]{Math.floor(c[0]) + 0.5 + Math.cos(a) * 0.72, y0 + 0.3 + f * 8.5, Math.floor(c[1]) + 0.5 + Math.sin(a) * 0.72});
        }
        rope(around, 0.2, 1, 0.3, false);
        // 从顶上破口垂下来的一束，落在中庭左边
        rope(List.of(new double[]{115, HALL_TOP + 1, 9}, new double[]{116, HALL_TOP - 2, 8.6}, new double[]{118, DECK + 1.3, 7.8}, new double[]{118.6, DECK - 1, 7.4}, new double[]{118.7, y0 + 1.6, 7.3}), 0.3, 0, 0.42, false);
    }


    private void dock() {
        int y0 = FLOOR_HALL;
        fill(95, y0 - 1, 2, HX0 - 1, y0 - 1, 6, B.PLATFORM);
        for (int x = 95; x < HX0; x++) set(x, y0 - 1, 2, B.EDGE);
        fill(95, y0 + 4, 3, HX0 - 1, y0 + 4, 6, B.WALL_A);
        for (int x = 96; x < HX0; x += 4) boxes.add(new Box("minecraft:polished_deepslate", x + 0.38, y0, 6.38, 0.24, 4, 0.24, -1));
        // 栏杆和看不见的墙，掉不下去
        for (int x = 95; x < HX0; x++) {
            if (block(x, y0, 6) == null) set(x, y0, 6, B.BARS_X);
            fill(x, y0 + 1, 7, x, y0 + 3, 7, B.BARRIER);
            fill(x, y0, 7, x, y0, 7, B.BARRIER);
            fill(x, y0, 1, x, y0 + 3, 1, B.BARRIER);
        }
        fill(94, y0, 1, 94, y0 + 3, 7, B.BARRIER);
        fill(HX0 - 1, y0, 1, HX0 - 1, y0 + 3, 1, B.BARRIER);
        // 码头牌子挂在迎面的外墙上，缆车进站时正对着
        texts.add(new Text("三号缆车码头\n下层游客中心", HX0 - 0.03, y0 + 3.2, -2.0, 90f, 0xe9e1cd, 1.7f, 0xff30443f, 10));
        texts.add(new Text("餐饮购物\n大厅", HX0 - 0.03, y0 + 4.5, 4.0, 90f, 0x2c2a24, 1.1f, 0xffd8c9a0, 11));
        texts.add(new Text("小心脚下", HX0 - 0.03, y0 + 1.9, 0.6, 90f, 0x2c2a24, 0.6f, 0xffd8b030, 8));
        // 雨棚下的管线和灯
        pipes.add(new Parts.Pipe(0, 95, y0 + 3.7, 3.2, HX0, y0 + 3.7, 3.2, 0.16));
        pipes.add(new Parts.Pipe(1, 95, y0 + 3.7, 3.7, HX0, y0 + 3.7, 3.7, 0.12));
        for (int x : new int[]{99, 106}) {
            boxes.add(new Box("minecraft:ochre_froglight", x, y0 + 3.86, 4.6, 0.9, 0.08, 0.3, x == 106 ? 12 : 3));
        }
        light(106, y0 + 3, 5, 7);
        light(99, y0 + 3, 5, 3);
        // 纪念照领取处：下车一转身就是。缆车上拍照点拍的那张，已经摆出来了
        double px = 107.6, pz = 5.75;
        for (double lx : new double[]{px - 0.75, px + 0.75}) beams.add(new Beam("minecraft:polished_deepslate", lx, y0, pz, lx, y0 + 2.3, pz, 0.06, -1));
        boxes.add(new Box("minecraft:polished_deepslate", px - 0.85, y0 + 0.95, pz - 0.02, 1.7, 1.35, 0.08, -1));
        posters.add(new Parts.Poster("ride_photo", px, y0 + 1.62, pz - 0.05, 0f, 1.32, 0.99, 11));
        texts.add(new Text("缆车纪念照  ·  每张 $5\n请凭票根领取", px, y0 + 2.36, pz - 0.05, 180f, 0xf2e8cc, 0.42f, 0xff1b3a3a, 10));
        boxes.add(new Box("minecraft:ochre_froglight", px - 0.6, y0 + 2.28, pz - 0.2, 1.2, 0.05, 0.12, 9));
    }

    // ---------- 地面车站 ----------

    private void station() {
        int y0 = FLOOR_STATION, top = y0 + 9;
        for (int x = -16; x <= 16; x++) {
            for (int z = -9; z <= 9; z++) {
                set(x, y0 - 2, z, B.TRACKBED);
                if (z >= 3) set(x, y0 - 1, z, z == 3 ? B.EDGE : B.PLATFORM);
                else if (z <= -3) set(x, y0 - 1, z, B.PLATFORM);
                set(x, top, z, B.ROOF);
                if (Math.abs(x) == 16 || Math.abs(z) == 9) {
                    for (int y = y0 - 1; y < top; y++) {
                        double n = Noise.fbm(x * 0.3 + z * 0.3, y * 0.4, 8, 2);
                        set(x, y, z, y == y0 + 2 ? B.WALL_STRIP : n > 0.6 ? B.WALL_C : n > 0.42 ? B.WALL_B : B.WALL_A);
                    }
                }
            }
        }
        // 屋架和柱子
        for (int x = -12; x <= 12; x += 6) {
            fill(x, top - 1, -9, x, top - 1, 9, B.STEEL);
            fill(x, y0, 8, x, top - 1, 8, B.WALL_A);
            fill(x, y0, -8, x, top - 1, -8, B.WALL_A);
        }
        // 出站门洞
        clearBox(16, y0 - 1, -6, 16, y0 + 6, 6);
        // 门外的肉已经漫到门框上
        for (int y = y0 - 2; y <= y0 + 8; y++) {
            for (int z = -9; z <= 9; z++) {
                for (int x = 17; x <= 21; x++) {
                    boolean edge = Math.abs(z) > 6 || y > y0 + 6;
                    if (edge && Noise.fbm(x * 0.25, y * 0.25, z * 0.25, 2) > 0.35 + (x - 17) * 0.03) set(x, y, z, flesh(x, y, z));
                }
            }
        }
        // 缆车停靠时的车厢空气墙：门洞在 x=-5、-4，地板在 y0-1
        fill(-7, y0 - 1, -2, -1, y0 - 1, 2, B.BARRIER);
        fill(-8, y0, -3, -8, y0 + 3, 2, B.BARRIER);
        fill(0, y0, -3, 0, y0 + 3, 2, B.BARRIER);
        fill(-8, y0, -3, 0, y0 + 3, -3, B.BARRIER);
        fill(-8, y0 + 3, -3, 0, y0 + 3, 2, B.BARRIER);
        // 站台边的空气墙：整条站台不让下轨道，只留车门口
        for (int x = -15; x <= 15; x++) {
            if (x == -5 || x == -4 || (x >= -8 && x <= 0)) continue;
            fill(x, y0, 2, x, y0 + 3, 2, B.BARRIER);
        }
        for (int x = -8; x <= 0; x++) {
            if (x == -5 || x == -4) {
                set(x, y0 - 1, 2, B.EDGE);
                continue;
            }
            fill(x, y0, 2, x, y0 + 3, 2, B.BARRIER);
        }
        fill(15, y0, 3, 15, y0 + 3, 8, B.BARRIER);
        // 长椅
        fill(-15, y0, 7, -12, y0, 7, B.BENCH);
        fill(6, y0, 7, 10, y0, 7, B.BENCH);
        // 灯：出口的绿牌，门外一盏快灭的
        texts.add(new Text("出口", -15.0 + 0.03, y0 + 3.3, 5.5, -90f, 0xb8ffc8, 1.6f, 0xff0d5a2a, 12));
        light(-15, y0 + 3, 5, 3);
        boxes.add(new Box("minecraft:ochre_froglight", 14.6, y0 + 7.2, -4.4, 0.8, 0.12, 0.2, 6));
        light(14, y0 + 6, -4, 5);
        // 牌子。公园已经封了：封园告示、搜救队喷的标记
        texts.add(new Text("安诺丹缆车系统\n下行至下层游客中心", -2, y0 + 5.4, 8.97 - 1, 180f, 0xd8d0ba, 2.2f, 0xff2c3a3a, -1));
        texts.add(new Text("通告\n\n本设施已关闭\n重开日期另行通知\n2007 年 7 月 5 日", -12, y0 + 1.6, 7.97, 180f, 0x2a2a28, 0.9f, 0xffd8d2bc, -1));
        texts.add(new Text("\\ 07.7.12 /\n搜救队\nX\n无生还\n/ 禁止进入 \\", 8, y0 + 2, 7.97, 180f, 0xd9681e, 1.4f, 0, -1));
        // 断掉的警戒带
        beams.add(new Beam("minecraft:yellow_concrete", 15, y0 + 1.1, 3.2, 12.5, y0 + 0.2, 4.6, 0.04, -1));
        beams.add(new Beam("minecraft:yellow_concrete", 15, y0 + 1.1, 8.0, 12.2, y0 + 0.1, 6.9, 0.04, -1));
    }

    /** 站务室：站台南墙开一扇门，里面是调度的桌子、文件柜、墙上的通知。门开在出站口那根肉索够不着的西边。 */
    private void stationOffice() {
        int y0 = FLOOR_STATION;
        office(2, 10, 7, 15, y0, y0 + 4);
        clearBox(4, y0, 9, 5, y0 + 2, 9);
        boxes.add(new Box("minecraft:polished_deepslate", 3.9, y0, 8.9, 0.12, 3.0, 0.2, -1));
        boxes.add(new Box("minecraft:polished_deepslate", 5.98, y0, 8.9, 0.12, 3.0, 0.2, -1));
        boxes.add(new Box("minecraft:polished_deepslate", 3.9, y0 + 2.92, 8.9, 2.2, 0.2, 0.2, -1));
        texts.add(new Text("站务室  ·  员工专用", 5.0, y0 + 3.25, 8.88, 180f, 0xf2e8cc, 0.6f, 0xff1b3a3a, 9));
        // 调度台：桌子、对讲机、一摞票据
        boxes.add(new Box("minecraft:dark_oak_planks", 3.4, y0, 12.9, 2.6, 0.93, 1.0, -1));
        boxes.add(new Box("minecraft:polished_deepslate", 5.3, y0 + 0.93, 13.1, 0.22, 0.34, 0.12, -1));
        boxes.add(new Box("minecraft:red_concrete", 5.36, y0 + 1.2, 13.06, 0.05, 0.05, 0.05, 12));
        boxes.add(new Box("minecraft:white_concrete", 3.7, y0 + 0.93, 13.2, 0.4, 0.12, 0.3, -1));
        boxes.add(new Box("minecraft:oak_planks", 4.0, y0, 14.3, 0.5, 0.5, 0.5, -1));
        // 文件柜，墙上的发车表和满意度终端的通知
        boxes.add(new Box("minecraft:waxed_weathered_copper", 6.05, y0, 10.4, 0.9, 0.93, 1.2, -1));
        boxes.add(new Box("minecraft:waxed_weathered_copper", 6.05, y0, 12.0, 0.9, 1.9, 0.9, -1));
        posters.add(new Parts.Poster("poster_rating", 2.06, y0 + 1.9, 12.5, 90f, 1.2, 1.6, -1));
        texts.add(new Text("今日发车\n811  13:40\n812  14:05\n813  14:52\n814  ——", 7.94, y0 + 2.1, 13.8, 90f, 0x24211f, 0.45f, 0xffe6dcc8, 7));
        light(4, y0 + 3, 12, 6);
    }

    /**
     * 入园大厅：站台西墙外面。身后是落下来的卷帘门，回收公司摆了张桌子放装备，
     * 售票窗、排队栏杆（要真的绕着走）、闸机，过了闸机才是站台。顶灯、闸机臂、计数屏在剧情里生成。
     */
    private void entryHall() {
        int y0 = FLOOR_STATION;
        office(-33, 0, -18, 11, y0, y0 + 6);
        clearBox(-17, y0, 4, -16, y0 + 2, 6);
        for (double z : new double[]{3.8, 6.98}) boxes.add(new Box("minecraft:polished_deepslate", -17.05, y0, z, 0.12, 3.0, 0.22, -1));
        boxes.add(new Box("minecraft:polished_deepslate", -17.05, y0 + 2.9, 3.8, 0.12, 0.22, 3.4, -1));
        // 卷帘门在剧情里生成：人走进来才落下
        clearBox(ENTRY_DOOR_X, y0, ENTRY_DOOR_Z0, ENTRY_DOOR_X, y0 + 3, ENTRY_DOOR_Z1);
        texts.add(new Text("单向通道\n请勿折返", -32.9, y0 + 4.4, 5.5, -90f, 0xf2e8cc, 0.8f, 0xff1b3a3a, -1));
        // 回收公司的桌子和工作灯：开局只有这一盏亮着
        fill(-28, y0, 4, -27, y0, 6, B.BARRIER);
        boxes.add(new Box("minecraft:white_concrete", -27.9, y0 + 0.86, 4.15, 1.8, 0.06, 2.7, -1));
        for (double x : new double[]{-27.7, -26.3}) boxes.add(new Box("minecraft:iron_block", x, y0, 4.4, 0.05, 0.86, 2.2, -1));
        texts.add(new Text("二叠纪盆地回收\n现场物资  请勿挪用", -27.93, y0 + 0.5, 5.5, 90f, 0x24211f, 0.4f, 0xffe6dcc8, -1));
        for (double[] leg : new double[][]{{-27.5, 2.6}, {-26.5, 2.6}, {-27, 3.5}}) beams.add(new Beam("minecraft:iron_block", leg[0], y0, leg[1], -27, y0 + 1.1, 3, 0.03, -1));
        beams.add(new Beam("minecraft:iron_block", -27, y0 + 1.1, 3, -27, y0 + 2.1, 3, 0.03, -1));
        boxes.add(new Box("minecraft:ochre_froglight", -27.25, y0 + 2.1, 2.95, 0.5, 0.32, 0.12, 15));
        boxes.add(new Box("minecraft:yellow_concrete", -33, y0 + 0.01, 2.96, 6, 0.02, 0.05, -1));
        light(-28, y0 + 2, 4, 12);
        // 售票窗：两个，第二个的帘子拉到一半
        fill(-32, y0, 0, -30, y0, 0, B.WOOD_DARK);
        fill(-28, y0, 0, -26, y0, 0, B.WOOD_DARK);
        boxes.add(new Box("minecraft:light_blue_stained_glass", -32, y0 + 1, 0.35, 3, 1.5, 0.04, -1));
        boxes.add(new Box("minecraft:light_blue_stained_glass", -28, y0 + 1, 0.35, 3, 1.5, 0.04, -1));
        boxes.add(new Box("minecraft:polished_deepslate", -28, y0 + 1.7, 0.4, 3, 0.8, 0.05, -1));
        posters.add(new Parts.Poster("poster_ticket", -30.5, y0 + 1.75, 0.08, 180f, 1.2, 0.9, -1));
        texts.add(new Text("售票  1", -30.5, y0 + 2.85, 0.05, 0f, 0xf2e8cc, 0.7f, 0xff1b3a3a, -1));
        texts.add(new Text("售票  2", -26.5, y0 + 2.85, 0.05, 0f, 0xf2e8cc, 0.7f, 0xff1b3a3a, -1));
        // 排队栏杆：真要绕着走。一个开口在 z 大的那头，一个在 z 小的那头
        rail(-25, 0, 8, y0);
        rail(-22, 3, 11, y0);
        texts.add(new Text("由此排队\n从此处起约 120 分钟", -25.5, y0 + 2.5, 11.93, 180f, 0x24211f, 0.55f, 0xffe6dcc8, -1));
        posters.add(new Parts.Poster("poster_park", -30.5, y0 + 2.1, 11.93, 0f, 1.5, 2.25, -1));
        posters.add(new Parts.Poster("poster_july4", -23.5, y0 + 2.1, 11.93, 0f, 1.5, 2.1, -1));
        posters.add(new Parts.Poster("poster_survived", -20.5, y0 + 2.1, 11.93, 0f, 1.5, 2.25, -1));
        posters.add(new Parts.Poster("poster_heartthrob", -23.5, y0 + 2.1, 0.07, 180f, 1.5, 2.25, -1));
        posters.add(new Parts.Poster("poster_rating", -20.5, y0 + 2.1, 0.07, 180f, 1.3, 1.7, -1));
        // 验票机：检票员的便条放在上面
        set(-21, y0, 0, B.STEEL);
        // 闸机一排：两边是隔断，中间三个闸口
        for (int z = 0; z <= 11; z++) {
            if (z >= ENTRY_GATE_Z0 && z <= ENTRY_GATE_Z1) continue;
            set(ENTRY_GATE_X, y0, z, B.STEEL);
            fill(ENTRY_GATE_X, y0 + 1, z, ENTRY_GATE_X, y0 + 2, z, B.BARRIER);
        }
        for (int z = ENTRY_GATE_Z0; z <= ENTRY_GATE_Z1 + 1; z++) boxes.add(new Box("minecraft:polished_deepslate", ENTRY_GATE_X - 0.1, y0, z - 0.07, 1.2, 0.95, 0.14, -1));
        boxes.add(new Box("minecraft:polished_deepslate", ENTRY_GATE_X - 0.12, y0 + 2.9, 3.4, 0.1, 0.75, 4.2, -1));
        texts.add(new Text("检  票  口", ENTRY_GATE_X - 0.15, y0 + 3.1, 5.5, 90f, 0xf2e8cc, 0.9f, 0, -1));
    }

    /**
     * 开局那条夜路：接送车从这头开过来，窗外是路灯、公园的旧广告牌、回收公司后来立的牌子。
     * 这一带在穹顶外面，是车站西边的荒地。
     */
    private void nightRoad() {
        int y0 = FLOOR_STATION, x0 = -660, x1 = BAY_MOUTH_X;
        java.util.Random rnd = new java.util.Random(2007);
        for (int x = x0; x <= x1; x++) {
            for (int z = -34; z <= 42; z++) {
                boolean lane = z >= -1 && z <= 8;
                B ground = lane ? (z == 6 && Math.floorMod(x, 7) < 3 ? B.EDGE : B.TRACKBED) : Noise.fbm(x * 0.09, 0, z * 0.09, 2) > 0.5 ? B.DESERT_B : B.DESERT;
                set(x, y0 - 1, z, ground);
            }
        }
        // 荒地上的枯草和石头
        for (int i = 0; i < 420; i++) {
            int x = x0 + rnd.nextInt(x1 - x0), z = rnd.nextBoolean() ? 10 + rnd.nextInt(32) : -34 + rnd.nextInt(32);
            set(x, y0, z, rnd.nextDouble() < 0.72 ? B.BUSH : B.ROCK_A);
        }
        // 路灯：公园断电十九年了，这些灯还亮着
        int k = 0;
        for (int x = -616; x <= -70; x += 38) {
            double z = k++ % 2 == 0 ? 10.5 : -3.5, arm = z > 3 ? -1.4 : 1.4;
            beams.add(new Beam("minecraft:gray_concrete", x, y0, z, x, y0 + 6.5, z, 0.09, -1));
            beams.add(new Beam("minecraft:gray_concrete", x, y0 + 6.5, z, x, y0 + 6.5, z + arm, 0.06, -1));
            boxes.add(new Box("minecraft:ochre_froglight", x - 0.3, y0 + 6.32, z + arm - 0.15, 0.6, 0.1, 0.3, 15));
            light(x, y0 + 5, (int) Math.floor(z + arm), 13);
        }
        // 广告牌：从开朗的公园广告，慢慢变成封路的牌子。都立在车的右手边，窗子正对着
        billboardText(-505, "息壤国家公园\n前方 12 公里", 0xf2e8cc, 0xff1b5a5a, 6.5f);
        billboardPoster(-450, "poster_park", 4.4, 6.6);
        billboardText(-395, "和全家一起\n下到地底！", 0x24211f, 0xffe6b84a, 6.5f);
        billboardPoster(-340, "poster_survived", 4.4, 6.6);
        billboardText(-285, "安诺丹家族祝您\n一路平安", 0xf2e8cc, 0xff2c3a3a, 6f);
        billboardText(-230, "封园公告\n本路段已封闭\n2007 年 7 月 5 日", 0x24211f, 0xffd8d2bc, 4.2f);
        texts.add(new Text("\\ 07.7.12 /\n搜救队\nX\n无生还", -218, y0 + 0.1, 11.9, 180f, 0xd9681e, 1.6f, 0, 9));
        billboardText(-170, "二叠纪盆地回收与超级生物体收容公司\n封闭作业区  持证车辆通行", 0x24211f, 0xfff4f0e6, 3.1f);
        boxes.add(new Box("minecraft:gray_concrete", -221, y0, 12.1, 6, 2.2, 0.5, -1));
        roadMemorial(-212);
        // 路肩的反光桩：一根一根往后闪，坐在车里才知道车在跑。接驳站那一段没有
        for (int x = -644; x <= -66; x += 16) {
            if (x >= PICKUP_X0 - 1 && x <= PICKUP_X1 + 1) continue;
            boxes.add(new Box("minecraft:white_concrete", x, y0, 8.7, 0.12, 0.85, 0.12, -1));
            boxes.add(new Box("minecraft:orange_concrete", x - 0.01, y0 + 0.62, 8.68, 0.14, 0.14, 0.14, 13));
        }
        // 路边扔着的车
        for (double[] car : new double[][]{{-470, 15}, {-300, -11}, {-250, 16}}) {
            boxes.add(new Box("minecraft:brown_terracotta", car[0], y0, car[1], 4.3, 0.95, 1.9, -1));
            boxes.add(new Box("minecraft:gray_terracotta", car[0] + 0.8, y0 + 0.95, car[1] + 0.05, 2.3, 0.6, 1.8, -1));
        }
        // 回收公司的围栏和检查站
        for (int x = -165; x <= x1; x++) {
            for (int y = y0; y <= y0 + 2; y++) {
                set(x, y, 13, B.BARS_X);
                set(x, y, -6, B.BARS_X);
            }
        }
        for (int x = -112; x <= -109; x++) {
            for (int z = 10; z <= 12; z++) {
                for (int y = y0; y <= y0 + 2; y++) set(x, y, z, B.WALL_B);
                set(x, y0 + 3, z, B.ROOF);
            }
        }
        boxes.add(new Box("minecraft:light_blue_stained_glass", -111.6, y0 + 1.1, 9.97, 2.2, 1.0, 0.04, 8));
        beams.add(new Beam("minecraft:yellow_concrete", -113, y0 + 1.1, 9.4, -113, y0 + 6.2, 8.6, 0.07, -1));
        texts.add(new Text("检查站", -110.5, y0 + 2.5, 9.97, 180f, 0xf2e8cc, 1f, 0xff1b3a3a, 8));
    }

    /** 公路边的旧接驳站：开局人站在这儿等车。水泥站台、雨棚、长椅、站牌，呼叫按钮在剧情里生成。 */
    private void pickupStop() {
        int y0 = FLOOR_STATION, x0 = PICKUP_X0, x1 = PICKUP_X1;
        for (int x = x0; x <= x1; x++) for (int z = 5; z <= 9; z++) set(x, y0 - 1, z, z == 5 ? B.EDGE : B.PLATFORM);
        // 空气墙：只能在站台上等车，走不到路上和荒地里去
        fill(x0 - 1, y0, 4, x1 + 1, y0 + 1, 4, B.BARRIER);
        fill(x0 - 1, y0, 5, x0 - 1, y0 + 1, 10, B.BARRIER);
        fill(x1 + 1, y0, 5, x1 + 1, y0 + 1, 10, B.BARRIER);
        fill(x0, y0, 10, x1, y0 + 1, 10, B.BARRIER);
        // 雨棚：两根柱子、顶、背板，背板上一张褪色的公园海报
        double sx0 = -549.5, sx1 = -541.5;
        for (double px : new double[]{sx0, sx1}) beams.add(new Beam("minecraft:gray_concrete", px, y0, 9.4, px, y0 + 2.7, 9.4, 0.1, -1));
        boxes.add(new Box("minecraft:light_gray_concrete", sx0 - 0.3, y0 + 2.7, 7.6, sx1 - sx0 + 0.6, 0.1, 2.1, -1));
        boxes.add(new Box("minecraft:cyan_terracotta", sx0 - 0.3, y0 + 2.58, 7.56, sx1 - sx0 + 0.6, 0.14, 0.04, -1));
        boxes.add(new Box("minecraft:gray_concrete", sx0, y0 + 0.4, 9.45, sx1 - sx0, 2.1, 0.06, -1));
        fill(-548, y0, 8, -545, y0, 8, B.BENCH);
        // 雨棚下面一根灯管，站台和站牌照得见
        boxes.add(new Box("minecraft:ochre_froglight", sx0 + 0.6, y0 + 2.62, 8.5, sx1 - sx0 - 1.2, 0.06, 0.14, 12));
        light((int) Math.floor((sx0 + sx1) / 2), y0 + 2, 8, 12);
        light((int) Math.floor(PICKUP_POLE[0]), y0 + 2, 6, 9);
        posters.add(new Parts.Poster("poster_park", -543.2, y0 + 1.5, 9.4, 0f, 1.0, 1.5, 5));
        texts.add(new Text("息壤国家公园  游客接驳站", (sx0 + sx1) / 2, y0 + 2.61, 7.53, 180f, 0xf2e8cc, 0.55f, 0, -1));
        // 站牌：站名、时刻表、回收公司贴的纸。呼叫按钮在剧情里生成
        double px = PICKUP_POLE[0], pz = PICKUP_POLE[1];
        beams.add(new Beam("minecraft:gray_concrete", px, y0, pz, px, y0 + 2.9, pz, 0.08, -1));
        boxes.add(new Box("minecraft:cyan_terracotta", px - 0.45, y0 + 2.2, pz + 0.04, 0.9, 0.62, 0.05, -1));
        texts.add(new Text("接驳站\n地面站方向", px, y0 + 2.3, pz + 0.1, 0f, 0xf2e8cc, 0.5f, 0, -1));
        boxes.add(new Box("minecraft:white_concrete", px - 0.32, y0 + 1.25, pz + 0.04, 0.64, 0.82, 0.03, -1));
        texts.add(new Text("游客接驳专线\n地面站  ⇄  公路口\n\n末班  2007.07.04  22:30\n本线路已停运", px, y0 + 1.42, pz + 0.08, 0f, 0x24211f, 0.22f, 0, -1));
        boxes.add(new Box("minecraft:light_gray_concrete", px - 0.22, y0 + 0.72, pz + 0.04, 0.44, 0.42, 0.03, -1));
        texts.add(new Text("体验官接驳\n请按铃\n二叠纪盆地回收", px, y0 + 0.78, pz + 0.08, 0f, 0x24211f, 0.2f, 0, -1));
    }

    /**
     * 路边的一片小坟：圆头的石碑，矮矮的一排排，立在离路远一点的荒地里，只有几根蜡烛照着，隐隐约约。
     * 车经过的时候收音机正好念到遇难人数。
     */
    private void roadMemorial(double x) {
        int y0 = FLOOR_STATION;
        java.util.Random r = new java.util.Random(704);
        for (int i = 0; i < 14; i++) {
            double cx = x + (i % 7) * 1.25 + r.nextDouble() * 0.4, cz = 16.2 + (i / 7) * 1.6 + r.nextDouble() * 0.5;
            double w = 0.42 + r.nextDouble() * 0.12, h = 0.45 + r.nextDouble() * 0.25, d = 0.12;
            String stone = r.nextBoolean() ? "minecraft:andesite" : "minecraft:tuff";
            // 碑身，上面一层层收窄，远看是圆头
            boxes.add(new Box(stone, cx - w / 2, y0, cz, w, h, d, -1));
            boxes.add(new Box(stone, cx - w * 0.42, y0 + h, cz, w * 0.84, 0.06, d, -1));
            boxes.add(new Box(stone, cx - w * 0.32, y0 + h + 0.06, cz, w * 0.64, 0.05, d, -1));
            boxes.add(new Box(stone, cx - w * 0.18, y0 + h + 0.11, cz, w * 0.36, 0.04, d, -1));
            if (i % 3 == 0) boxes.add(new Box("minecraft:shroomlight", cx - 0.03, y0, cz - 0.18, 0.05, 0.1, 0.05, 7));
            if (i % 4 == 1) boxes.add(new Box("minecraft:red_wool", cx - 0.1, y0, cz - 0.24, 0.2, 0.05, 0.14, -1));
        }
        light((int) Math.floor(x + 4), y0, 16, 4);
    }

    /** 车队调度站：公路起点的一块水泥场和一间值班小屋。员工通道走出来就在这儿，接驳车停在门口打着火。 */
    private void depot() {
        int y0 = FLOOR_STATION, x0 = DEPOT_X0, x1 = DEPOT_X1;
        for (int x = x0; x <= x1; x++) {
            for (int z = 1; z <= 19; z++) {
                if (z >= 9) set(x, y0 - 1, z, B.PLATFORM);
                for (int y = y0; y <= y0 + 4; y++) if (block(x, y, z) != null) clear(x, y, z);
            }
        }
        // 空气墙：场子外面是荒地和路，走不出去
        fill(x0 - 1, y0, 0, x1 + 1, y0 + 1, 0, B.BARRIER);
        fill(x0 - 1, y0, 20, x1 + 1, y0 + 1, 20, B.BARRIER);
        fill(x0 - 1, y0, 0, x0 - 1, y0 + 1, 20, B.BARRIER);
        fill(x1 + 1, y0, 0, x1 + 1, y0 + 1, 20, B.BARRIER);
        // 值班小屋：朝着路开一扇门，窗子亮着
        int hx0 = -646, hx1 = -633, hz0 = 13, hz1 = 19;
        for (int x = hx0; x <= hx1; x++) {
            for (int z = hz0; z <= hz1; z++) {
                set(x, y0 + 4, z, B.ROOF);
                boolean edge = x == hx0 || x == hx1 || z == hz0 || z == hz1;
                for (int y = y0; y <= y0 + 3; y++) if (edge) set(x, y, z, y == y0 ? B.WALL_A : B.WALL_B);
            }
        }
        for (int y = y0; y <= y0 + 2; y++) for (int x = -640; x <= -639; x++) clear(x, y, hz0);
        for (int x = -637; x <= -635; x++) for (int y = y0 + 1; y <= y0 + 2; y++) set(x, y, hz0, B.SHOP_GLASS);
        for (int x = -644; x <= -642; x++) for (int y = y0 + 1; y <= y0 + 2; y++) set(x, y, hz0, B.SHOP_GLASS);
        light(-639, y0 + 3, 16, 12);
        light(-643, y0 + 3, 16, 10);
        light(-636, y0 + 3, 16, 10);
        // 屋里：一张桌子、一块工牌板、一只钟
        boxes.add(new Box("minecraft:dark_oak_planks", -645.5, y0, 17, 4, 0.9, 1.6, -1));
        boxes.add(new Box("minecraft:gray_concrete", -637.5, y0 + 1.1, 18.85, 4, 2, 0.1, -1));
        texts.add(new Text("工牌板", -635.5, y0 + 2.75, 18.8, 180f, 0xf2e8cc, 0.5f, 0, 10));
        for (int i = 0; i < 38; i++) {
            double bx = -637.25 + (i % 13) * 0.29, by = y0 + 2.42 - (i / 13) * 0.42;
            boxes.add(new Box(i == 37 ? "minecraft:red_concrete" : "minecraft:white_concrete", bx, by, 18.8, 0.17, 0.26, 0.02, 8));
        }
        // 门头、门灯
        texts.add(new Text("息壤国家公园  接驳车队", -639, y0 + 3.05, 12.9, 180f, 0xf2e8cc, 0.62f, 0xff1b5a5a, 12));
        boxes.add(new Box("minecraft:ochre_froglight", -640.2, y0 + 2.95, 12.72, 1.4, 0.08, 0.2, 15));
        light(-640, y0 + 2, 11, 13);
        // 场子上一根灯杆，一台油泵，几只油桶，一摞轮胎
        beams.add(new Beam("minecraft:gray_concrete", -628, y0, 11, -628, y0 + 6.5, 11, 0.09, -1));
        boxes.add(new Box("minecraft:ochre_froglight", -628.3, y0 + 6.32, 9.4, 0.6, 0.1, 1.6, 15));
        light(-628, y0 + 5, 9, 13);
        boxes.add(new Box("minecraft:red_concrete", -625.4, y0, 13, 0.8, 1.7, 0.6, -1));
        boxes.add(new Box("minecraft:white_concrete", -625.3, y0 + 1.25, 12.97, 0.6, 0.3, 0.02, 9));
        boxes.add(new Box("minecraft:gray_concrete", -625.8, y0, 12.8, 1.6, 0.14, 1.0, -1));
        for (double[] drum : new double[][]{{-631, 15.4}, {-630.1, 15.7}, {-630.6, 14.7}}) boxes.add(new Box("minecraft:blue_terracotta", drum[0], y0, drum[1], 0.72, 1.05, 0.72, -1));
        for (int i = 0; i < 4; i++) boxes.add(new Box("minecraft:black_concrete", -649, y0 + i * 0.26, 15, 1.0, 0.25, 1.0, -1));
    }

    private void billboardFrame(double x, double w, double h) {
        int y0 = FLOOR_STATION;
        double base = y0 + 0.9;
        for (double px : new double[]{x - w / 2 + 0.5, x + w / 2 - 0.5}) beams.add(new Beam("minecraft:gray_concrete", px, y0, 12.8, px, base + 0.5, 12.8, 0.12, -1));
        boxes.add(new Box("minecraft:gray_concrete", x - w / 2, base, 12.7, w, h, 0.2, -1));
        light((int) Math.floor(x), (int) base + 2, 10, 11);
    }

    private void billboardPoster(double x, String model, double w, double h) {
        billboardFrame(x, w + 0.4, h + 0.4);
        posters.add(new Parts.Poster(model, x, FLOOR_STATION + 1.1 + h / 2, 12.6, 0f, w, h, 9));
    }

    private void billboardText(double x, String text, int color, int background, float scale) {
        billboardFrame(x, 13, 5.5);
        texts.add(new Text(text, x, FLOOR_STATION + 1.6, 12.6, 180f, color, scale, background, 9));
    }

    /** 落客区：有顶的车道，接送车停在这儿。一头敞着对着夜路，一头是入园大厅的门。 */
    private void dropOffBay() {
        int y0 = FLOOR_STATION, ceil = y0 + 6;
        for (int x = BAY_MOUTH_X; x < ENTRY_DOOR_X; x++) {
            for (int z = -1; z <= 12; z++) {
                set(x, y0 - 1, z, z == 5 ? B.EDGE : z > 5 ? B.PLATFORM : B.TRACKBED);
                set(x, ceil, z, B.ROOF);
                if (z == -1 || z == 12) {
                    for (int y = y0; y < ceil; y++) set(x, y, z, y - y0 <= 1 ? B.WALL_A : Noise.fbm(x * 0.3, y * 0.4, z * 0.3, 2) > 0.5 ? B.WALL_C : B.WALL_B);
                }
            }
        }
        // 空气墙：人只能进大厅，追不了车。看见的是拦车杆
        fill(BAY_MOUTH_X, y0, 0, BAY_MOUTH_X, y0 + 5, 11, B.BARRIER);
        for (int x : new int[]{-50, -43, -37}) {
            boxes.add(new Box("minecraft:ochre_froglight", x - 0.6, y0 + 5.85, 7, 1.2, 0.1, 0.3, 12));
            light(x, y0 + 4, 7, x == -50 ? 13 : 10);
        }
        texts.add(new Text("落  客  区", -48, y0 + 3.7, 11.93, 180f, 0xf2e8cc, 1.6f, 0xff1b3a3a, 8));
        texts.add(new Text("←  体验官入口", -39, y0 + 2.6, 11.93, 180f, 0x24211f, 0.9f, 0xffe6dcc8, -1));
        texts.add(new Text("请勿在此停留", -52, y0 + 2.6, 11.93, 180f, 0xd9681e, 0.8f, 0, -1));
        texts.add(new Text("入  园", ENTRY_DOOR_X - 0.03, y0 + 4.4, 5.5, 90f, 0xf2e8cc, 1.2f, 0xff1b3a3a, -1));
        for (double z : new double[]{2.8, 8.0}) boxes.add(new Box("minecraft:polished_deepslate", ENTRY_DOOR_X - 0.12, y0, z, 0.12, 4.1, 0.2, -1));
        boxes.add(new Box("minecraft:polished_deepslate", ENTRY_DOOR_X - 0.12, y0 + 4.0, 2.8, 0.12, 0.2, 5.4, -1));
        posters.add(new Parts.Poster("poster_closed", -47.5, y0 + 2.0, 0.07, 180f, 1.5, 1.88, -1));
        for (double[] cone : new double[][]{{-42.5, 5.6}, {-41.2, 5.7}, {-39.9, 5.5}}) {
            boxes.add(new Box("minecraft:orange_concrete", cone[0], y0, cone[1], 0.3, 0.5, 0.3, -1));
            boxes.add(new Box("minecraft:white_concrete", cone[0] + 0.05, y0 + 0.5, cone[1] + 0.05, 0.2, 0.14, 0.2, -1));
        }
    }

    /** 一道排队栏杆：挡人的是空气墙，看见的是立柱和红带子。 */
    private void rail(int x, int z0, int z1, int y0) {
        fill(x, y0, z0, x, y0 + 1, z1, B.BARRIER);
        for (int z = z0; z <= z1 + 1; z += 2) beams.add(new Beam("minecraft:polished_deepslate", x + 0.5, y0, z, x + 0.5, y0 + 1, z, 0.04, -1));
        boxes.add(new Box("minecraft:red_concrete", x + 0.47, y0 + 0.86, z0, 0.06, 0.07, z1 + 1 - z0, -1));
    }

    private void stationPosters() {
        int y0 = FLOOR_STATION;
        stationOffice();
        entryHall();
        dropOffBay();
        nightRoad();
        pickupStop();
        depot();
        posters.add(new Parts.Poster("poster_closed", -14.3, y0 + 2.0, 8.93, 0f, 1.5, 1.88, -1));
        posters.add(new Parts.Poster("poster_park", -9.3, y0 + 2.1, 8.93, 0f, 1.5, 2.25, -1));
        posters.add(new Parts.Poster("poster_survived", -3.5, y0 + 2.1, 8.93, 0f, 1.5, 2.25, -1));
        posters.add(new Parts.Poster("poster_heartthrob", 3, y0 + 2.1, 8.93, 0f, 1.5, 2.25, -1));
        posters.add(new Parts.Poster("poster_map", 9, y0 + 2.2, 8.93, 0f, 2.4, 1.8, -1));
        // 售票机
        boxes.add(new Box("minecraft:waxed_weathered_copper", -1.7, y0, 8.1, 1.1, 2.0, 0.85, -1));
        posters.add(new Parts.Poster("poster_ticket", -1.15, y0 + 1.45, 8.07, 0f, 0.8, 0.6, 7));
        // 长椅上的可乐罐
        boxes.add(new Box("minecraft:red_concrete", -13.2, y0 + 0.5, 7.3, 0.08, 0.14, 0.08, -1));
        // 检票口的闸门框
        for (double z : new double[]{3.1, 8.0}) boxes.add(new Box("minecraft:polished_deepslate", -10.6, y0, z, 0.16, 4.2, 0.16, -1));
        boxes.add(new Box("minecraft:polished_deepslate", -10.6, y0 + 4.05, 3.1, 0.16, 0.2, 5.06, -1));
    }

    /** 结局的车站：同一个车站的一份干净的拷贝，放在 z+300。人从电梯里出来才看得到。 */
    private void reopenedStation() {
        int y0 = FLOOR_STATION, top = y0 + 9, dz = NEW_STATION_DZ;
        for (int x = -16; x <= 16; x++) {
            for (int y = y0 - 2; y <= top; y++) {
                for (int z = -9; z <= 9; z++) {
                    B b = block(x, y, z);
                    if (b == null || b.isFlesh() || b == B.DEBRIS || b == B.DEBRIS_DARK) continue;
                    set(x, y, z + dz, b == B.WALL_C ? B.WALL_B : b == B.WALL_A ? B.WALL_B : b);
                }
            }
        }
        // 出站门洞封死，横幅挂在上面
        for (int y = y0 - 1; y < top; y++) for (int z = -9; z <= 9; z++) set(16, y, z + dz, B.WALL_B);
        // 墙上开一个电梯门
        for (int y = y0; y <= y0 + 2; y++) for (int z = 4; z <= 6; z++) clear(-16, y, z + dz);
        // 灯全亮
        for (int x = -12; x <= 12; x += 6) {
            for (int z : new int[]{-5, 5}) {
                light(x, y0 + 6, z + dz, 9);
                boxes.add(new Box("minecraft:ochre_froglight", x - 1, top - 0.14, z + dz - 0.3, 2, 0.1, 0.6, 15));
            }
        }
        for (int x = -15; x <= 15; x += 5) light(x, y0 + 2, 6 + dz, 9);
        // 横幅：一条挂在站台上方正对电梯，一条挂在尽头的墙上
        posters.add(new Parts.Poster("poster_reopen", -6, y0 + 4.5, dz + 5.5, -90f, 5.2, 1.3, 10));
        posters.add(new Parts.Poster("poster_reopen", 15.9, y0 + 4.6, dz, -90f, 14, 3.5, 10));
        posters.add(new Parts.Poster("poster_new", -3.5, y0 + 2.1, dz + 8.93, 0f, 1.5, 2.25, 13));
        posters.add(new Parts.Poster("poster_new", 3, y0 + 2.1, dz + 8.93, 0f, 1.5, 2.25, 13));
        posters.add(new Parts.Poster("poster_park", 9, y0 + 2.1, dz + 8.93, 0f, 1.5, 2.25, 13));
        posters.add(new Parts.Poster("poster_survived", -14.3, y0 + 2.1, dz + 8.93, 0f, 1.5, 2.25, 13));
        // 红绳围栏排好了队
        for (int x = -10; x <= 9; x += 3) {
            boxes.add(new Box("minecraft:waxed_copper_block", x, y0, dz + 4.6, 0.1, 1.0, 0.1, -1));
            boxes.add(new Box("minecraft:waxed_copper_block", x, y0, dz + 7.0, 0.1, 1.0, 0.1, -1));
            if (x < 9) {
                beams.add(new Parts.Beam("minecraft:red_wool", x + 0.05, y0 + 0.92, dz + 4.65, x + 3.05, y0 + 0.92, dz + 4.65, 0.05, -1));
                beams.add(new Parts.Beam("minecraft:red_wool", x + 0.05, y0 + 0.92, dz + 7.05, x + 3.05, y0 + 0.92, dz + 7.05, 0.05, -1));
            }
        }
        // 结算窗口：排队的红绳一直通到这里，大横幅正下方。窗子一开始是黑的，灯在剧情里亮
        for (int z = 5; z <= 7; z++) set(15, y0, z + dz, B.BARRIER);
        boxes.add(new Box("minecraft:waxed_copper_block", 15.05, y0, dz + 5, 0.92, 1.06, 2.9, -1));
        boxes.add(new Box("minecraft:black_concrete", 15.9, y0 + 1.06, dz + 5, 0.06, 1.6, 2.9, -1));
        boxes.add(new Box("minecraft:waxed_copper_block", 15.82, y0 + 2.62, dz + 4.95, 0.14, 0.12, 3, -1));
        texts.add(new Text("体验官结算处", 15.75, y0 + 2.2, dz + 6.45, 90f, 0xfff4dc, 0.6f, 0xff1b2a2a, 12));
        // 员工通道：结算窗口旁边的一扇门，后面一截黑走廊。门在剧情里打开
        for (int y = y0; y <= y0 + 3; y++) for (int z = STAFF_Z0; z <= STAFF_Z1; z++) clear(15, y, z);
        for (int y = y0; y <= y0 + 2; y++) for (int z = STAFF_Z0; z <= STAFF_Z1; z++) set(16, y, z, B.BARRIER);
        for (int x = 17; x <= 25; x++) {
            for (int z = STAFF_Z0 - 1; z <= STAFF_Z1 + 1; z++) {
                set(x, y0 - 1, z, B.WALL_A);
                set(x, y0 + 3, z, B.WALL_A);
                boolean edge = z < STAFF_Z0 || z > STAFF_Z1 || x == 25;
                for (int y = y0; y <= y0 + 2; y++) if (edge) set(x, y, z, B.WALL_A);
            }
        }
        boxes.add(new Box("minecraft:polished_deepslate", 15.84, y0, STAFF_Z0 - 0.14, 0.14, 2.86, 0.14, -1));
        boxes.add(new Box("minecraft:polished_deepslate", 15.84, y0, STAFF_Z1 + 1, 0.14, 2.86, 0.14, -1));
        texts.add(new Text("员工通道", 15.8, y0 + 1.72, STAFF_Z0 - 0.62, 90f, 0xf4f0e6, 0.5f, 0xff2c3a3a, 10));
        // 仪容镜：出电梯右手边的墙上，两根柱子中间。镜子其实是一扇深色玻璃，后面是一间照着这边做的小屋，“倒影”站在里面
        for (int x = MIRROR_X0; x <= MIRROR_X1; x++) {
            for (int y = y0; y <= y0 + 2; y++) set(x, y, dz + 9, B.MIRROR);
        }
        for (int x = MIRROR_X0 - 4; x <= MIRROR_X1 + 4; x++) {
            set(x, y0 - 1, dz + 13, B.WALL_B);
            for (int z = dz + 10; z <= dz + 14; z++) {
                set(x, y0 - 1, z, B.PLATFORM);
                set(x, y0 + 4, z, B.WALL_B);
                boolean edge = x == MIRROR_X0 - 4 || x == MIRROR_X1 + 4 || z == dz + 14;
                for (int y = y0; y <= y0 + 3; y++) {
                    if (edge) set(x, y, z, B.WALL_B);
                    else if (block(x, y, z) != null) clear(x, y, z);
                }
            }
        }
        light(MIRROR_X0 + 1, y0 + 3, dz + 11, 7);
        boxes.add(new Box("minecraft:waxed_copper_block", MIRROR_X0 - 0.12, y0 - 0.05, dz + 8.86, MIRROR_X1 - MIRROR_X0 + 1.24, 0.12, 0.16, -1));
        boxes.add(new Box("minecraft:waxed_copper_block", MIRROR_X0 - 0.12, y0 + 2.98, dz + 8.86, MIRROR_X1 - MIRROR_X0 + 1.24, 0.12, 0.16, -1));
        boxes.add(new Box("minecraft:waxed_copper_block", MIRROR_X0 - 0.12, y0, dz + 8.86, 0.12, 3.0, 0.16, -1));
        boxes.add(new Box("minecraft:waxed_copper_block", MIRROR_X1 + 1, y0, dz + 8.86, 0.12, 3.0, 0.16, -1));
        texts.add(new Text("请整理您的仪容", (MIRROR_X0 + MIRROR_X1 + 1) / 2.0, y0 + 3.2, dz + 8.9, 180f, 0xfff4dc, 1.0f, 0xff1b2a2a, 12));
        // 海报的卷边下面露出一点肉
        rope(List.of(new double[]{-8.75, y0 + 1.12, dz + 8.84}, new double[]{-8.5, y0 + 1.0, dz + 8.82}), 0.11, 0, 0.1, false);
        rope(List.of(new double[]{3.7, y0 + 3.1, dz + 8.84}, new double[]{3.95, y0 + 3.2, dz + 8.82}), 0.09, 0, 0.1, false);
    }

    private void cable() {
        double[] a = new double[3], b = new double[3];
        for (double d = 0; d < route.length; d += 5) {
            route.at(d, a);
            route.at(Math.min(route.length, d + 5), b);
            double ox = Route.CABIN_CENTER_X, oy = Route.CABLE_Y + 0.05;
            for (double side : new double[]{-Route.CABLE_SIDE, Route.CABLE_SIDE}) {
                beams.add(new Beam("minecraft:black_concrete", a[0] + ox, a[1] + oy, side, b[0] + ox, b[1] + oy, side, 0.07, -1));
            }
        }
    }

    /** 空腔里的吊架：两边各一根斜撑拉到肉壁上，夹着缆绳。红的指示灯，隔一个架子一盏工作灯，把近处的肉照出来。 */
    private void cablePylons() {
        double[] a = new double[3];
        double from = route.distAtY(THROAT_BOTTOM - 6);
        int i = 0;
        for (double d = from; d < route.length - 12; d += 13, i++) {
            route.at(d, a);
            double x = a[0] + Route.CABIN_CENTER_X, y = a[1] + Route.CABLE_Y + 0.05;
            for (int sign = -1; sign <= 1; sign += 2) {
                double z = sign * Route.CABLE_SIDE;
                double[] w = chamber.hit(x, y + 0.2, z, -0.2, 0.8, sign * 0.9, 60);
                beams.add(new Beam("minecraft:polished_deepslate", x, y + 0.2, z, w[0], w[1], w[2], 0.2, -1));
                boxes.add(new Box("minecraft:polished_deepslate", x - 0.4, y + 0.08, z - 0.16, 0.8, 0.26, 0.32, -1));
                boxes.add(new Box("minecraft:red_concrete", x - 0.08, y + 0.34, z - 0.08, 0.16, 0.16, 0.16, 15));
            }
            int side = i % 2 == 0 ? 1 : -1;
            double z = side * Route.CABLE_SIDE;
            beams.add(new Beam("minecraft:polished_deepslate", x, y + 0.24, z, x, y + 0.24, z + side * 1.6, 0.08, -1));
            boxes.add(new Box("minecraft:shroomlight", x - 0.45, y + 0.08, z + side * 1.6 - 0.2, 0.9, 0.1, 0.4, 15));
            light((int) Math.floor(x), (int) Math.floor(y - 1), (int) Math.floor(z + side * 3.5), 15);
            light((int) Math.floor(x), (int) Math.floor(y - 7), (int) Math.floor(-z - side * 3), 15);
        }
    }

    /** 游客中心朝着空腔的一面：深色外壳、一排还亮着的窗、屋檐下的灯、大招牌。老远就看得见，这才是这一段的目的地。 */
    private void drumFacade() {
        double r = DRUM_R + 1.28;
        Random lit = new Random(41);
        for (double a = 2.15; a < 4.15; a += 0.125) {
            if (lit.nextDouble() < 0.72) {
                panels.add(new Parts.Panel("minecraft:shroomlight", DRUM_X + Math.cos(a) * r, 33.7, Math.sin(a) * r, DRUM_X + Math.cos(a + 0.1) * r, 33.7, Math.sin(a + 0.1) * r, 3.2, 0.05, 15));
            }
        }
        for (double a = 2.05; a < 4.25; a += 0.085) {
            if (lit.nextDouble() < 0.7) {
                panels.add(new Parts.Panel("minecraft:shroomlight", DRUM_X + Math.cos(a) * (r + 0.1), 46.6, Math.sin(a) * (r + 0.1), DRUM_X + Math.cos(a + 0.07) * (r + 0.1), 46.6, Math.sin(a + 0.07) * (r + 0.1), 0.22, 0.22, 15));
            }
        }
        // 屋檐往下打的灯，一盏一盏的光斑落在外壳上
        for (double a = 2.2; a < 4.1; a += 0.16) {
            light((int) Math.floor(DRUM_X + Math.cos(a) * (DRUM_R + 2.2)), 45, (int) Math.floor(Math.sin(a) * (DRUM_R + 2.2)), 9);
        }
        double face = DRUM_X - DRUM_R - 1.25;
        boxes.add(new Box("minecraft:polished_deepslate", face - 0.2, 36.3, -10.5, 0.2, 7.4, 21, -1));
        // 这块大字用英文：用户看过中文版，觉得土
        texts.add(new Text("MYSTERY FLESH PIT", face - 0.26, 40.0, 0, 90f, 0xfff0cc, 7f, 0, 15));
        texts.add(new Text("NATIONAL PARK  ·  LOWER VISITOR CENTER", face - 0.26, 37.2, 0, 90f, 0xffd890, 3.2f, 0, 15));
    }

    /** 坑边的探照灯塔：公园关门后只剩几盏还亮着，把穹顶照出一块一块。 */
    private void floodlights() {
        double[][] towers = {{8, -26}, {70, -30}, {74, 24}, {14, 28}, {48, -40}};
        for (int i = 0; i < towers.length; i++) {
            double x = towers[i][0], z = towers[i][1];
            beams.add(new Beam("minecraft:gray_concrete", x, 229, z, x, 247, z, 0.35, -1));
            boolean lit = i != 2;
            boxes.add(new Box(lit ? "minecraft:shroomlight" : "minecraft:gray_concrete", x - 1.2, 247, z - 0.6, 2.4, 1.0, 1.2, lit ? 15 : -1));
            if (!lit) continue;
            light((int) Math.floor(x), 246, (int) Math.floor(z), 15);
            // 灯往坑里照：沿着照的方向放几格光
            double dx = CRATER_X - x, dz = -z, len = Math.hypot(dx, dz);
            for (double t = 6; t < len - 12; t += 7) {
                int lx = (int) Math.floor(x + dx / len * t), lz = (int) Math.floor(z + dz / len * t);
                int ly = (int) Math.floor(funnelHeight(Math.hypot(lx + 0.5 - CRATER_X, lz + 0.5)) + 3);
                if (!route.carHit(lx + 0.5, ly + 0.5, lz + 0.5, 1)) light(lx, ly, lz, 11);
            }
        }
    }

    /** 撑开肉口的黄色锚臂：原作的 dilation anchors。 */
    private void anchors() {
        for (double a : new double[]{0.3, 1.35, 2.2, -2.2, -1.35, -0.3}) {
            double ca = Math.cos(a), sa = Math.sin(a);
            beams.add(new Beam("minecraft:yellow_concrete", CRATER_X + ca * 30, 231.5, sa * 30, CRATER_X + ca * 11.5, funnelHeight(11.5) + 2.2, sa * 11.5, 0.6, -1));
            beams.add(new Beam("minecraft:polished_deepslate", CRATER_X + ca * 31, 230, sa * 31, CRATER_X + ca * 31, 232.4, sa * 31, 1.4, -1));
        }
    }

    // ---------- 肉索 ----------

    /**
     * 沿折线铺一根肉索。一节一节首尾搭着，粗细不匀，两头收细，蠕动的鼓包顺着 s 爬。
     * 碰到车厢扫过的空间的节直接不放：肉索在那儿像被车磨断了，比穿进车里强。
     */
    /** 不用给路和摆设让道的肉索（花槽里的肉草：短，本来就长在槽里）：等别的肉索都让完再加，不然被当成挡路的截掉。 */
    private final List<Runnable> lateRopes = new ArrayList<>();

    void lateRope(List<double[]> path, double radius, int kind, double minStep) {
        lateRopes.add(() -> rope(path, radius, kind, minStep, false));
    }

    void rope(List<double[]> path, double radius, int kind, double minStep, boolean window) {
        double total = 0;
        double[] lens = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1), b = path.get(i);
            total += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
            lens[i] = total;
        }
        if (total <= 0) return;
        double phase = rnd.nextDouble() * 100;
        double step = Math.max(minStep * 0.8, Math.max(0.15, radius * 0.9));
        List<double[]> run = new ArrayList<>();
        int seg = 0;
        for (double s = 0; ; s = Math.min(total, s + step)) {
            while (seg < path.size() - 2 && lens[seg + 1] < s) seg++;
            double[] a = path.get(seg), b = path.get(seg + 1);
            double span = Math.max(1e-6, lens[seg + 1] - lens[seg]), k = (s - lens[seg]) / span;
            double x = a[0] + (b[0] - a[0]) * k, y = a[1] + (b[1] - a[1]) * k, z = a[2] + (b[2] - a[2]) * k;
            double u = s / total, taper = Math.pow(Noise.smooth(0, 0.07, u) * Noise.smooth(1, 0.74, u), 0.8);
            double rad = radius * (0.86 + Noise.value(s * 0.3 + phase, ropeId, 0) * 0.28) * (0.28 + 0.72 * taper);
            // 碰到缆车扫过的空间就断开，两头收成圆头
            if (route.carHit(x, y, z, rad * 1.3 + 0.1)) {
                addRope(run, kind, window);
                run = new ArrayList<>();
            } else {
                run.add(new double[]{x, y, z, rad});
            }
            if (s >= total) break;
        }
        addRope(run, kind, window);
    }

    private void addRope(List<double[]> run, int kind, boolean window) {
        if (run.size() < 2) return;
        ropes.add(new Parts.Rope(ropeId++, run.toArray(new double[0][]), kind, window));
    }

    /** 把 from 之后加进来的肉索里碰到 hit 的部分挖掉。森林的栈道用。 */
    void splitRopes(int from, java.util.function.Predicate<double[]> hit) {
        List<Parts.Rope> kept = new ArrayList<>(ropes.subList(0, from));
        for (Parts.Rope rope : ropes.subList(from, ropes.size())) {
            List<double[]> run = new ArrayList<>();
            for (double[] q : rope.pts()) {
                if (hit.test(q)) {
                    if (run.size() >= 2) kept.add(new Parts.Rope(ropeId++, run.toArray(new double[0][]), rope.kind(), rope.window()));
                    run = new ArrayList<>();
                } else {
                    run.add(q);
                }
            }
            if (run.size() >= 2) kept.add(new Parts.Rope(ropeId++, run.toArray(new double[0][]), rope.kind(), rope.window()));
        }
        ropes.clear();
        ropes.addAll(kept);
    }

    private void mouthRopes() {
        for (int i = 0; i < 34; i++) {
            double a = i / 34.0 * Math.PI * 2 + rnd.nextDouble() * 0.1, wob = rnd.nextDouble() * 6;
            double radius = 0.95 + rnd.nextDouble() * 0.85;
            List<double[]> path = new ArrayList<>();
            double start = 25 + rnd.nextDouble() * 6;
            for (double r = start; r >= 9; r -= 1.2) {
                double aa = a + Math.sin(r * 0.4 + wob) * 0.05;
                double h = r > CRATER_R ? 230 : funnelHeight(r);
                path.add(new double[]{CRATER_X + Math.cos(aa) * r, h + radius * 0.7, Math.sin(aa) * r});
            }
            rope(path, radius, i % 5 == 0 ? 1 : 0, 0.9, false);
        }
        // 括约肌：一圈圈箍在漏斗上
        for (double r : new double[]{12.5, 16, 20}) {
            List<double[]> path = new ArrayList<>();
            for (int q = 0; q <= 90; q++) {
                double a = q / 90.0 * Math.PI * 2, rr = r + Math.sin(a * 5 + r) * 0.8;
                path.add(new double[]{CRATER_X + Math.cos(a) * rr, funnelHeight(rr) + 1.3, Math.sin(a) * rr});
            }
            rope(path, 1.25 + (r - 12.5) * 0.05, 0, 1.4, false);
        }
    }

    private void throatRopes() {
        for (int i = 0; i < 18; i++) {
            double phi0 = i / 18.0 * Math.PI * 2 + rnd.nextDouble() * 0.2, tw = (rnd.nextDouble() - 0.5) * 0.05;
            double radius = 0.9 + rnd.nextDouble() * 0.8;
            List<double[]> path = new ArrayList<>();
            for (int y = THROAT_TOP - 1; y >= THROAT_BOTTOM - 4; y--) {
                double phi = phi0 + tw * (THROAT_TOP - y) + Math.sin(y * 0.07 + i) * 0.06;
                double r = throatRadius(phi, y) - radius * 0.55;
                path.add(new double[]{throatX(y) + Math.cos(phi) * r, y + 0.5, Math.sin(phi) * r});
            }
            rope(path, radius, i % 6 == 2 ? 2 : i % 3 == 0 ? 1 : 0, 0.9, false);
        }
    }

    /** 缆绳旁边垂下来的细肉须：心跳时发亮，人才知道这里有多大。 */
    private static double sq(double v) {
        return v * v;
    }

    /** 把游客中心吞掉一半的粗肉索：从下面漫上来，爬过外壳。 */
    private void swallowRopes() {
        for (int i = 0; i < 16; i++) {
            double side = i % 2 == 0 ? 1 : -1;
            double a = Math.PI + side * (0.35 + rnd.nextDouble() * 1.1);
            double radius = 1.7 + rnd.nextDouble() * 2.2, y1 = 34 + rnd.nextDouble() * 12;
            List<double[]> path = new ArrayList<>();
            for (int q = 0; q <= 14; q++) {
                double f = q / 14.0;
                double r = Noise.mix(58, DRUM_R + radius * 0.6, Noise.smooth(0, 0.75, f));
                double aa = a + Math.sin(f * 3 + i) * 0.07;
                path.add(new double[]{DRUM_X + Math.cos(aa) * r, Noise.mix(12, y1, Noise.smooth(0.15, 1, f)), Math.sin(aa) * r});
            }
            rope(path, radius, i % 3 == 0 ? 1 : 0, 1.6, false);
        }
        // 爬上码头雨棚的两根
        rope(List.of(new double[]{113, FLOOR_HALL + 9, 9}, new double[]{107, FLOOR_HALL + 5.3, 7.2}, new double[]{101, FLOOR_HALL + 5.2, 6.2}, new double[]{96, FLOOR_HALL + 5.1, 5}), 0.55, 0, 0.7, false);
        rope(List.of(new double[]{118, FLOOR_HALL - 6, 3}, new double[]{108, FLOOR_HALL - 1.4, 2.8}, new double[]{100, FLOOR_HALL - 0.8, 2.4}), 0.7, 1, 0.7, false);
    }

    /** 贴在全景窗外的肉索，被玻璃压平。中心要留在 z>=12.9，贴着玻璃但不进屋。 */
    private void windowRopes() {
        for (double x = HX0 - 1; x < HX1 + 2; x += 1.6 + rnd.nextDouble() * 1.1) {
            if (Math.abs(x - FIGURE_X) < 2.7) continue;
            double radius = 0.75 + rnd.nextDouble() * 0.75, base = x;
            List<double[]> path = new ArrayList<>();
            for (int q = 0; q <= 8; q++) {
                double f = q / 8.0;
                path.add(new double[]{base + Math.sin(f * 5 + base) * 0.8, FLOOR_HALL - 1 + f * (HALL_TOP - FLOOR_HALL + 2), 13.15 + radius * 0.25 + Math.sin(f * 3 + base) * 0.2});
            }
            rope(path, radius, rnd.nextDouble() < 0.25 ? 1 : 0, 0.8, true);
        }
        // 横着绕过去的，洞口上下各拦一根，看着像撕开的洞，不是一道竖缝
        for (double[] c : new double[][]{{FLOOR_HALL + 5.3, 0.7}, {FLOOR_HALL + 4.5, 0.55}, {FLOOR_HALL - 0.3, 0.5}, {FLOOR_HALL + 3.3, 0.5}}) {
            List<double[]> path = new ArrayList<>();
            for (int q = 0; q <= 12; q++) {
                double f = q / 12.0, x = Noise.mix(HX0, HX1, f);
                double yy = c[0] + Math.sin(f * 7 + c[0]) * 0.7;
                if (yy < FLOOR_HALL + 4.1 && yy > FLOOR_HALL + 0.3 && Math.abs(x - FIGURE_X) < 3) continue;
                path.add(new double[]{x, yy, 13.6});
            }
            rope(path, c[1], 0, 0.8, true);
        }
        // 从碎掉的那格玻璃漫进屋里的肉索，贴着栏杆往两边摊开，人走到窗前的路要留着
        rope(List.of(new double[]{128.4, FLOOR_HALL + 0.5, 13.6}, new double[]{128.2, FLOOR_HALL + 0.4, 11}, new double[]{127.2, FLOOR_HALL + 0.36, 9.6}, new double[]{124.6, FLOOR_HALL + 0.34, 9.3}, new double[]{121.5, FLOOR_HALL + 0.3, 8.9}), 0.34, 0, 0.5, false);
        rope(List.of(new double[]{128.9, FLOOR_HALL + 0.8, 13.4}, new double[]{129, FLOOR_HALL + 0.4, 10.6}, new double[]{130.6, FLOOR_HALL + 0.3, 9.7}, new double[]{133.6, FLOOR_HALL + 0.26, 9.5}), 0.26, 0, 0.45, false);
        rope(List.of(new double[]{127.6, FLOOR_HALL + 1.4, 13.4}, new double[]{127.7, FLOOR_HALL + 0.4, 11.6}, new double[]{126.6, FLOOR_HALL + 0.26, 9.9}), 0.2, 1, 0.42, false);
    }

    /** 爬进车站的肉：顺着门框和轨道沟长进来。 */
    private void stationRopes() {
        double y0 = FLOOR_STATION;
        double[][][] paths = {
                {{23, 229.5, -6}, {17.4, y0 + 0.2, -6.7}, {15.4, y0 + 3, -6.5}, {15.4, y0 + 6.5, -6.2}, {9, y0 + 7.8, -4}, {3, y0 + 7.8, -3}},
                {{22, 229.8, 7}, {17, y0 + 0.4, 6.8}, {12, y0 + 0.5, 7.3}, {5.5, y0 + 0.5, 7.8}, {5.4, y0 + 4, 7.7}},
                {{21, 228.8, -2}, {12, y0 - 1.1, -1.6}, {3, y0 - 1.2, -1.1}, {-5, y0 - 1.2, -1.4}},
                {{21, 228.8, 2.4}, {13, y0 - 1.1, 1.6}, {6, y0 - 0.9, -3.5}, {2, y0 + 0.2, -5.6}},
                {{21, 231, 4}, {16.6, y0 + 3, 6.4}, {16.1, y0 + 6.5, 5}, {16, y0 + 7.7, 1}},
                // 入园大厅：肉已经从墙角长进来了
                {{-18.4, y0 + 5.6, 11.4}, {-18.9, y0 + 3.4, 11.3}, {-20.4, y0 + 1.5, 11.4}, {-21.4, y0 + 0.25, 11.4}},
                // 落客区：肉从大厅门边的墙角爬出来，这是人下车以后第一眼看到的肉
                {{-35.6, y0 + 5.6, 11.4}, {-36.6, y0 + 3.2, 11.4}, {-38.4, y0 + 1.3, 11.4}, {-40.4, y0 + 0.25, 11.4}}
        };
        double[] radius = {0.55, 0.42, 0.5, 0.38, 0.32, 0.26, 0.24};
        for (int i = 0; i < paths.length; i++) {
            List<double[]> path = smooth(paths[i]);
            rope(path, radius[i], 0, 0.55, false);
            List<double[]> second = new ArrayList<>();
            for (double[] q : path) second.add(new double[]{q[0], q[1] + 0.1, q[2] + 0.3 * Math.sin(q[0])});
            rope(second, radius[i] * 0.55, 1, 0.4, false);
        }
    }

    /** 折线插几个点，拐弯不要太硬。 */
    private static List<double[]> smooth(double[][] points) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < points.length - 1; i++) {
            double[] a = points[i], b = points[i + 1];
            double[] before = i > 0 ? points[i - 1] : a, after = i < points.length - 2 ? points[i + 2] : b;
            for (int k = 0; k < 8; k++) {
                double t = k / 8.0, t2 = t * t, t3 = t2 * t;
                double[] q = new double[3];
                for (int c = 0; c < 3; c++) {
                    q[c] = 0.5 * (2 * a[c] + (b[c] - before[c]) * t + (2 * before[c] - 5 * a[c] + 4 * b[c] - after[c]) * t2 + (3 * a[c] - before[c] - 3 * b[c] + after[c]) * t3);
                }
                out.add(q);
            }
        }
        out.add(points[points.length - 1].clone());
        return out;
    }

    // ---------- 收尾 ----------

    /** 把车厢扫过的空间挖空。空气墙留着（只在停靠时起作用，不挡展示实体）。站台和码头的地面本来就在车厢下面。 */
    private void carve() {
        route.forEachSample(0.5, (x, y, z) -> {
            int x0 = (int) Math.floor(x + Route.CABIN_X0 - 0.2), x1 = (int) Math.floor(x + Route.CABIN_X1 + 0.2);
            int y0 = (int) Math.floor(y - Route.FLOOR_DROP), y1 = (int) Math.floor(y + Route.CABLE_Y + 0.2);
            int z0 = (int) Math.floor(z - Route.CABIN_HALF_Z - 0.2), z1 = (int) Math.floor(z + Route.CABIN_HALF_Z + 0.2);
            for (int bx = x0; bx <= x1; bx++) {
                for (int by = y0; by <= y1; by++) {
                    for (int bz = z0; bz <= z1; bz++) {
                        B b = block(bx, by, bz);
                        if (b != null && b != B.BARRIER) clear(bx, by, bz);
                    }
                }
            }
        });
    }

    /** 实心的东西：方块（除了肉和人能穿过的）、方块展示。肉索不能穿过它们。 */
    private boolean solidForRope(int x, int y, int z) {
        B b = block(x, y, z);
        // 栈道的板也算：肉不能从板子里穿出来
        return b != null && !b.isFlesh() && b != B.BARRIER && b != B.DEBRIS && b != B.DEBRIS_DARK;
    }

    /** 人要走的路：脚踩的高度 + 平面上的折线。肉索得让开，碰撞也不能堵在上面。 */
    private List<double[]> corridor() {
        List<double[]> c = new ArrayList<>();
        path(c, FLOOR_STATION, -12.5, 5.5, -8.5, 6.2, -4.5, 5.5, -4.5, 0);
        path(c, FLOOR_HALL, 99.3, 3.5, 108, 4.5, 112, 4.5, 116, 5.4, 123, 5.2, 125.5, 3.2, 128, 1.4, 134.5, 2.6, 134.5, -1.2);
        path(c, FLOOR_HALL, 134.5, 2.6, 128.5, 5, 124.5, 7.6, 127, 8.4, 131, 9.4, 143.5, 9.4, 143.5, 7);
        path(c, FLOOR_HALL, 112, 4.5, 124, 7, 130, 8, 132, 9.5);
        path(c, Lower.GARDEN_FLOOR, 140, 6, 131, 6, 131, 2, 152, 1.5, 180, 1.5);
        path(c, Lower.POOL1_FLOOR, 181, 1.5, 191, 1.5);
        path(c, Lower.POOL2_FLOOR, 193, 1.5, 203, 1.5);
        path(c, Lower.POOL3_FLOOR, 205, 1.5, 209.5, 1.5, 209.5, -13, 213, -14.6, 214, -14);
        path(c, Lower.POOL3_FLOOR, 207, 1.5, 212, -3, 214, -14);
        // 爬出腺泡，掉到站台上，走到气压计
        path(c, Lower.BURROW_Y, 270.5, -65.5, 292, -65.5, 295, -65.5);
        path(c, 176, 294, -65.5, 296, -64, 299.5, -60, 299.5, -53.8, 300, -58, 300, -56);
        // 护林员车里
        path(c, (int) Forest.VEHICLE[1], 323, 30, 322, 30.5, 316, 30.5, 314, 30.5);
        path(c, (int) Forest.VEHICLE[1], 322, 30.5, 321, 35.5, 310, 34, 305, 24);
        for (double[] q : forest.walkLine) c.add(new double[]{q[0], q[1], q[2]});
        for (double[] q : forest.exitLine) c.add(new double[]{q[0], q[1], q[2]});
        c.addAll(well.walk);
        return c;
    }

    private static void path(List<double[]> out, int floor, double... xz) {
        for (int i = 0; i + 3 < xz.length; i += 2) {
            double len = Math.hypot(xz[i + 2] - xz[i], xz[i + 3] - xz[i + 1]);
            for (double d = 0; d <= len; d += 0.4) {
                double k = d / Math.max(len, 1e-6);
                out.add(new double[]{xz[i] + (xz[i + 2] - xz[i]) * k, floor, xz[i + 1] + (xz[i + 3] - xz[i + 1]) * k});
            }
        }
    }

    /** 肉索碰到建筑、挡了路就贴着绕过去，实在挤不过去就在那里断开，端头收成圆的。 */
    private void declipRopes() {
        Map<Long, List<Box>> buckets = new HashMap<>();
        List<Box> solids = new ArrayList<>(boxes);
        for (double[] k : KIOSKS) solids.add(new Box("minecraft:black_concrete", k[0] - 0.62, k[1], k[2] - 0.62, 1.24, 1.7, 1.24, -1));
        for (Box box : solids) {
            if (box.sx() * box.sy() * box.sz() <= 0.02) continue;
            for (int x = (int) Math.floor(box.x()) >> 3; x <= (int) Math.floor(box.x() + box.sx()) >> 3; x++) {
                for (int y = (int) Math.floor(box.y()) >> 3; y <= (int) Math.floor(box.y() + box.sy()) >> 3; y++) {
                    for (int z = (int) Math.floor(box.z()) >> 3; z <= (int) Math.floor(box.z() + box.sz()) >> 3; z++) {
                        buckets.computeIfAbsent(key(x, y, z), k -> new ArrayList<>()).add(box);
                    }
                }
            }
        }
        Map<Long, List<double[]>> walks = new HashMap<>();
        for (double[] q : corridor()) walks.computeIfAbsent(key((int) Math.floor(q[0]) >> 3, (int) Math.floor(q[1]) >> 3, (int) Math.floor(q[2]) >> 3), k -> new ArrayList<>()).add(q);
        for (Parts.Beam b : beams) if (b.thick() >= 0.05) thin(walks, b.ax(), b.ay(), b.az(), b.bx(), b.by(), b.bz(), b.thick());
        for (Parts.Pipe b : pipes) thin(walks, b.ax(), b.ay(), b.az(), b.bx(), b.by(), b.bz(), b.rad());
        for (Parts.Lamp b : lamps) thin(walks, b.ax(), b.ay(), b.az(), b.bx(), b.by(), b.bz(), b.thick());
        List<Parts.Rope> result = new ArrayList<>();
        for (Parts.Rope rope : ropes) {
            double[][] pts = rope.pts();
            double[][] start = new double[pts.length][];
            for (int i = 0; i < pts.length; i++) start[i] = pts[i].clone();
            for (int pass = 0; pass < 6; pass++) {
                for (double[] q : pts) pushOut(q, buckets, walks);
                // 推完抹平一点，别一节一节地拐
                if (pass < 5) {
                    double[][] old = new double[pts.length][];
                    for (int i = 0; i < pts.length; i++) old[i] = pts[i].clone();
                    for (int i = 1; i < pts.length - 1; i++) {
                        for (int c = 0; c < 3; c++) pts[i][c] = old[i][c] * 0.6 + (old[i - 1][c] + old[i + 1][c]) * 0.2;
                    }
                }
            }
            List<double[]> run = new ArrayList<>();
            int firstKept = -1;
            for (int i = 0; i < pts.length; i++) {
                double[] q = pts[i];
                double moved = Math.sqrt((q[0] - start[i][0]) * (q[0] - start[i][0]) + (q[1] - start[i][1]) * (q[1] - start[i][1]) + (q[2] - start[i][2]) * (q[2] - start[i][2]));
                // 轻轻挪开可以，要掰很远就是真撞上了：断开，两头收圆
                boolean through = i > 0 && crosses(pts[i - 1], q, buckets);
                if (through || moved > Math.max(1.2, q[3] * 1.2) || depth(q, buckets, walks) > q[3] * 0.3 || route.carHit(q[0], q[1], q[2], q[3] * 1.2 + 0.1)) {
                    keepPiece(run, firstKept == 0, false, rope, result);
                    run = new ArrayList<>();
                    firstKept = -1;
                } else {
                    if (run.isEmpty()) firstKept = i;
                    run.add(q);
                }
            }
            keepPiece(run, firstKept == 0, true, rope, result);
        }
        ropes.clear();
        ropes.addAll(result);
    }

    /** 细长的东西按一串小球算。 */
    private static void thin(Map<Long, List<double[]>> out, double ax, double ay, double az, double bx, double by, double bz, double r) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int n = (int) Math.max(1, Math.ceil(len / 0.4));
        for (int i = 0; i <= n; i++) {
            double x = ax + (bx - ax) * i / n, y = ay + (by - ay) * i / n, z = az + (bz - az) * i / n;
            out.computeIfAbsent(key((int) Math.floor(x) >> 3, (int) Math.floor(y) >> 3, (int) Math.floor(z) >> 3), k -> new ArrayList<>()).add(new double[]{x, y, z, r + 0.05});
        }
    }

    /**
     * 断开之后的一段。两头都没连着东西的，就是飘在半空的一截肉，不要。
     * 被断开的那头收成细尖，看起来是伸到这里为止。
     */
    private void keepPiece(List<double[]> run, boolean fromStart, boolean toEnd, Parts.Rope rope, List<Parts.Rope> out) {
        if (run.size() < 2 || (!fromStart && !toEnd)) return;
        double length = 0;
        for (int i = 1; i < run.size(); i++) {
            double[] a = run.get(i - 1), b = run.get(i);
            length += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
        }
        if (length < 1.5) return;
        double reach = Math.min(length * 0.5, 2.2), walked = 0;
        if (!toEnd) {
            for (int i = run.size() - 1; i > 0 && walked < reach; i--) {
                double[] a = run.get(i), b = run.get(i - 1);
                a[3] *= 0.3 + 0.7 * walked / reach;
                walked += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
            }
        }
        walked = 0;
        if (!fromStart) {
            for (int i = 0; i < run.size() - 1 && walked < reach; i++) {
                double[] a = run.get(i), b = run.get(i + 1);
                a[3] *= 0.3 + 0.7 * walked / reach;
                walked += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
            }
        }
        out.add(new Parts.Rope(ropeId++, run.toArray(new double[0][]), rope.kind(), rope.window()));
    }

    /** 相邻两个采样点之间有没有穿过薄板：车壳、玻璃这些比一步还薄。 */
    private boolean crosses(double[] a, double[] b, Map<Long, List<Box>> buckets) {
        int cx = (int) Math.floor(b[0]) >> 3, cy = (int) Math.floor(b[1]) >> 3, cz = (int) Math.floor(b[2]) >> 3;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    List<Box> list = buckets.get(key(x, y, z));
                    if (list == null) continue;
                    for (Box box : list) {
                        double near = 0, far = 1;
                        double[] from = {box.x(), box.y(), box.z()}, to = {box.x() + box.sx(), box.y() + box.sy(), box.z() + box.sz()};
                        for (int k = 0; k < 3 && near <= far; k++) {
                            double d = b[k] - a[k];
                            if (Math.abs(d) < 1e-9) {
                                if (a[k] < from[k] || a[k] > to[k]) far = -1;
                            } else {
                                double t0 = (from[k] - a[k]) / d, t1 = (to[k] - a[k]) / d;
                                near = Math.max(near, Math.min(t0, t1));
                                far = Math.min(far, Math.max(t0, t1));
                            }
                        }
                        if (near <= far) return true;
                    }
                }
            }
        }
        return false;
    }

    /** 球和周围东西的最深穿透量。 */
    private double depth(double[] q, Map<Long, List<Box>> buckets, Map<Long, List<double[]>> walks) {
        double[] worst = {0};
        visitSolids(q, buckets, walks, (dx, dy, dz, len, pad) -> worst[0] = Math.max(worst[0], q[3] + pad - len));
        return worst[0];
    }

    private void pushOut(double[] q, Map<Long, List<Box>> buckets, Map<Long, List<double[]>> walks) {
        double[] move = new double[3];
        visitSolids(q, buckets, walks, (dx, dy, dz, len, pad) -> {
            double gap = q[3] + pad - len;
            if (gap <= 0) return;
            double nx = len < 1e-4 ? 0 : dx / len, ny = len < 1e-4 ? 1 : dy / len, nz = len < 1e-4 ? 0 : dz / len;
            if (Math.abs(nx * gap) > Math.abs(move[0])) move[0] = nx * gap;
            if (Math.abs(ny * gap) > Math.abs(move[1])) move[1] = ny * gap;
            if (Math.abs(nz * gap) > Math.abs(move[2])) move[2] = nz * gap;
        });
        q[0] += move[0];
        q[1] += move[1];
        q[2] += move[2];
    }

    private interface SolidVisitor {
        void visit(double dx, double dy, double dz, double len, double pad);
    }

    private void visitSolids(double[] q, Map<Long, List<Box>> buckets, Map<Long, List<double[]>> walks, SolidVisitor visitor) {
        int reach = (int) Math.ceil(q[3]);
        int cx = (int) Math.floor(q[0]), cy = (int) Math.floor(q[1]), cz = (int) Math.floor(q[2]);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int y = cy - reach; y <= cy + reach; y++) {
                for (int z = cz - reach; z <= cz + reach; z++) {
                    if (solidForRope(x, y, z)) aabb(q, x, y, z, x + 1, y + 1, z + 1, visitor);
                }
            }
        }
        for (int x = (cx - reach) >> 3; x <= (cx + reach) >> 3; x++) {
            for (int y = (cy - reach) >> 3; y <= (cy + reach) >> 3; y++) {
                for (int z = (cz - reach) >> 3; z <= (cz + reach) >> 3; z++) {
                    List<Box> list = buckets.get(key(x, y, z));
                    if (list == null) continue;
                    for (Box b : list) aabb(q, b.x(), b.y(), b.z(), b.x() + b.sx(), b.y() + b.sy(), b.z() + b.sz(), visitor);
                }
            }
        }
        // 人走的路是一根竖着的柱子：脚底到头顶
        int far = reach + 3;
        for (int x = (cx - far) >> 3; x <= (cx + far) >> 3; x++) {
            for (int y = (cy - far) >> 3; y <= (cy + far) >> 3; y++) {
                for (int z = (cz - far) >> 3; z <= (cz + far) >> 3; z++) {
                    List<double[]> list = walks.get(key(x, y, z));
                    if (list == null) continue;
                    for (double[] w : list) {
                        if (w.length == 4) {
                            double sx = q[0] - w[0], sy = q[1] - w[1], sz = q[2] - w[2];
                            visitor.visit(sx, sy, sz, Math.sqrt(sx * sx + sy * sy + sz * sz), w[3]);
                            continue;
                        }
                        double ny = Math.max(w[1] - 0.3, Math.min(w[1] + 2.1, q[1]));
                        double dx = q[0] - w[0], dy = q[1] - ny, dz = q[2] - w[2];
                        visitor.visit(dx, dy, dz, Math.sqrt(dx * dx + dy * dy + dz * dz), 0.75);
                    }
                }
            }
        }
    }

    private static void aabb(double[] q, double x0, double y0, double z0, double x1, double y1, double z1, SolidVisitor visitor) {
        double nx = Math.max(x0, Math.min(x1, q[0])), ny = Math.max(y0, Math.min(y1, q[1])), nz = Math.max(z0, Math.min(z1, q[2]));
        double dx = q[0] - nx, dy = q[1] - ny, dz = q[2] - nz;
        visitor.visit(dx, dy, dz, Math.sqrt(dx * dx + dy * dy + dz * dz), 0);
    }

    /** 人走得到的地方，肉索要挡人：不然一走就走进肉里，镜头看到肉的里面。太细的不用挡。 */
    private void ropeColliders() {
        java.util.Set<Long> keep = new java.util.HashSet<>();
        for (double[] w : corridor()) {
            for (double ox = -0.4; ox <= 0.4; ox += 0.4) {
                for (double oz = -0.4; oz <= 0.4; oz += 0.4) {
                    for (int y = (int) Math.floor(w[1]) - 1; y <= (int) Math.floor(w[1]) + 2; y++) {
                        keep.add(key((int) Math.floor(w[0] + ox), y, (int) Math.floor(w[2] + oz)));
                    }
                }
            }
        }
        for (Parts.Rope rope : ropes) {
            for (double[] q : rope.pts()) {
                if (q[3] < 0.22) continue;
                boolean walkable = q[1] >= 225 && q[0] < 20 || q[0] >= 95 && q[1] < 60 || q[0] >= 260;
                if (!walkable) continue;
                int reach = (int) Math.ceil(q[3]);
                for (int x = (int) Math.floor(q[0]) - reach; x <= q[0] + reach; x++) {
                    for (int y = (int) Math.floor(q[1]) - reach; y <= q[1] + reach; y++) {
                        for (int z = (int) Math.floor(q[2]) - reach; z <= q[2] + reach; z++) {
                            double dx = x + 0.5 - q[0], dy = y + 0.5 - q[1], dz = z + 0.5 - q[2];
                            if (dx * dx + dy * dy + dz * dz < q[3] * q[3] + 0.1 && block(x, y, z) == null && !keep.contains(key(x, y, z))) set(x, y, z, B.BARRIER);
                        }
                    }
                }
            }
        }
    }

    private void placeStaticLights() {
        for (long[] l : staticLights) {
            int x = (int) l[0], y = (int) l[1], z = (int) l[2];
            if (block(x, y, z) == null) set(x, y, z, l[3] >= 15 ? B.LIGHT_15 : l[3] >= 9 ? B.LIGHT_9 : l[3] >= 7 ? B.LIGHT_7 : l[3] >= 5 ? B.LIGHT_5 : B.LIGHT_3);
        }
    }
}
