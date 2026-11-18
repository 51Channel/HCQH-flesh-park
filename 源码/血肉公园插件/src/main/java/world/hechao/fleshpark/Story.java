package world.hechao.fleshpark;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import world.hechao.fleshpark.core.B;
import world.hechao.fleshpark.core.Forest;
import world.hechao.fleshpark.core.Well;
import world.hechao.fleshpark.core.Lower;
import world.hechao.fleshpark.core.ParkPlan;
import world.hechao.fleshpark.core.Parts;
import world.hechao.fleshpark.core.TramModel;
import world.hechao.fleshpark.core.RideProfile;
import world.hechao.fleshpark.core.Route;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 这一期的全部剧情：招募函、六台评价机、全景窗和人形、停电、温泉、爬腺泡、森林、电台、呼气追逐、电梯、结局。
 * 吓点的时机带随机，录的人也不知道它们什么时候来。
 */
final class Story {
    private static final String[] STOPS = {"您对本站满意吗？", "您对本次乘坐满意吗？", "您对游客中心满意吗？", "您对这扇窗满意吗？", "您对这个温泉满意吗？", "您对这片森林满意吗？", "您对本次体验满意吗？"};

    private final Park park;
    private final World world;
    private final ParkPlan plan;
    private final Fx fx;
    private final Random rnd = new Random();
    private final List<Task> tasks = new ArrayList<>();
    private final List<Entity> scene = new ArrayList<>();
    private final List<Kiosk> kiosks = new ArrayList<>();
    /** 每一站第一次按的分、按了几次。 */
    private final int[] asked = new int[7], presses = new int[7];
    private final java.util.Set<String> noticed = new java.util.HashSet<>();
    /** 六项分数，0 是没打。 */
    private final int[] scores = new int[6];

    int flashCut;
    boolean flashWeak;

    private Player player;
    private int age;
    private String zone = "";
    private String ambient = "", music = "";
    private boolean introDone, boardSaid;
    /** 开场的进度：拿了桌上的东西、闸机开了、过了闸机、走上站台。 */
    private boolean kitTaken, gateOpened, counted, onPlatform, letterRead, shutterDown, boomDown, allowBusDismount;
    private Bus bus;
    /** 接驳大巴走到哪一步：0 等人按铃，1 开过来，2 停在站台等人上车，3 上车，4 走夜路，5 停在落客区等人下车，6 人下了车，7 倒出去开走。 */
    private int busT, busStage, busStopT;
    private double busX, busSpeed;
    private BlockDisplay callLight;
    private BlockDisplay boom, shutterMain;
    private final List<BlockDisplay> shutterSlats = new ArrayList<>();
    private int boardAt;
    private final List<BlockDisplay> entryLamps = new ArrayList<>();
    private final List<BlockDisplay> gateArms = new ArrayList<>();
    private final List<Entity> kit = new ArrayList<>();
    private TextDisplay entryCounter;
    /** 名牌在纸上面多高。 */
    private static final double TAG_ABOVE = 0.42;

    // 全景窗
    /** 观景卷帘：一条条横板，从下往上卷。 */
    private static final int SLATS = 8;
    private static final double SLAT = 0.6, BLIND_BOTTOM = 0.95, BLIND_X0 = 117.5, BLIND_X1 = 146.5;
    private final List<BlockDisplay> slats = new ArrayList<>();
    private TextDisplay blindSign;
    private double blindOpen;
    private double blindTarget;
    private int lookTimer = -1;
    private boolean revealed, windowRated, lightsOut, welcomed;
    private ItemDisplay holeFigure, follower;
    private boolean crawled;
    private BlockDisplay shutter, shutterBar, stationLamp, door;
    /** 闸门落下以后帘子前面留一盏灯。 */
    private boolean shutterLit;
    /** 楼梯口的警戒线：一段黄一段黑，拴在南边的立柱上。 */
    private final List<BlockDisplay> stairTape = new ArrayList<>();

    // 温泉
    private final List<ItemDisplay> row = new ArrayList<>();
    private int poolStage, rowTimer = -1, heartAt;
    /** 森林里最后站稳的地方：万一掉下去就回到这里，不用从头走。 */
    private Location lastBoard;
    private boolean rowTurned, specialSaid, lockerSaid;
    private double rowX = Lower.ROW_X;
    private float wantSpeed = 0.2f;

    // 森林
    private final List<ItemDisplay> burrow = new ArrayList<>();
    private final List<Entity> tetherParts = new ArrayList<>();
    private boolean radioStarted, radioDone, reportRead, exhaled;
    private int holdNotice;
    private int moanAt;
    private final List<ItemDisplay> chasers = new ArrayList<>();
    private final List<ItemDisplay> watchers = new ArrayList<>();
    private double[] chaserS = new double[0];
    private int catches;

    // 电梯和结局
    /** 电梯的门：森林、3 号码头、新车站、麻醉科坠下来的那部、泵房底下的员工电梯、改进部的两部、运营中心那部。 */
    private final BlockDisplay[] doorA = new BlockDisplay[8], doorB = new BlockDisplay[8];
    private boolean inCab, cabGoing, atNewStation, finale, smashed;
    private int finaleAt;
    private Kiosk finalKiosk, anesCabKiosk;
    /** 森林电梯评分机上方的楼层显示：往上走、停住、坠下去，字跟着变。 */
    private TextDisplay cabFloor;

    private record Task(int at, Runnable run) {
    }

    Story(Park park) {
        this.park = park;
        world = park.world;
        plan = park.plan;
        fx = park.fx;
    }

    private void after(int ticks, Runnable run) {
        tasks.add(new Task(age + ticks, run));
    }

    // ---------- 开局 ----------

    void reset() {
        tasks.clear();
        clearScene();
        age = 0;
        zone = ambient = music = "";
        flashCut = 0;
        flashWeak = false;
        introDone = boardSaid = kitTaken = gateOpened = counted = onPlatform = letterRead = shutterDown = boomDown = allowBusDismount = false;
        if (bus != null) bus.remove();
        bus = null;
        busStage = 0;
        shutterSlats.clear();
        boardAt = 0;
        entryLamps.clear();
        gateArms.clear();
        kit.clear();
        entryCounter = null;
        lookTimer = -1;
        blindOpen = blindTarget = 0;
        revealed = windowRated = lightsOut = welcomed = false;
        poolStage = 0;
        lastBoard = null;
        rowTimer = -1;
        heartAt = 0;
        rowTurned = specialSaid = lockerSaid = false;
        rowX = Lower.ROW_X;
        wantSpeed = 0.2f;
        radioStarted = radioDone = reportRead = exhaled = false;
        holdNotice = 0;
        driverBusyUntil = 0;
        if (cineOn && player != null) endCine(player);
        cineOn = false;
        crawled = false;
        follower = null;
        shutterLit = false;
        rideBeats.clear();
        rideCuts.clear();
        catches = 0;
        inCab = cabGoing = atNewStation = finale = smashed = handsGrey = false;
        resetWell();
        java.util.Arrays.fill(snapped, false);
        if (player != null) player.setWalkSpeed(0.2f);
        moanAt = 0;
        java.util.Arrays.fill(scores, 0);
        java.util.Arrays.fill(asked, 0);
        java.util.Arrays.fill(presses, 0);
        noticed.clear();
        read.clear();
        opened.clear();
        gateLabels.clear();
        gateShutters.clear();
        stairsDue = stairsOpen = false;
        lastBlocked = 0;
        for (int ring = 0; ring <= 2; ring++) park.ring(ring, true);
        park.ring(3, false);
        BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        // 楼梯口警戒线后面的墙、森林出路都关上；车站的铁闸拉起来
        for (int y = ParkPlan.FLOOR_HALL; y <= ParkPlan.FLOOR_HALL + 1; y++) {
            for (int z = 5; z <= 7; z++) world.getBlockAt(ParkPlan.STAIR_TOP_X, y, z).setBlockData(barrier, false);
        }
        for (int y = ParkPlan.FLOOR_STATION; y <= ParkPlan.FLOOR_STATION + 3; y++) {
            for (int z = 3; z <= 8; z++) world.getBlockAt(-11, y, z).setType(Material.AIR, false);
        }
        for (int[] c : plan.forest.exitGate) world.getBlockAt(c[0], c[1], c[2]).setBlockData(barrier, false);
        buildScene();
    }

    private void clearScene() {
        for (Kiosk kiosk : kiosks) kiosk.remove();
        kiosks.clear();
        scene.forEach(Entity::remove);
        scene.clear();
        row.clear();
        burrow.clear();
        tetherParts.clear();
        chasers.clear();
        watchers.clear();
        holeFigure = null;
        chaserS = new double[0];
    }

    void shutdown() {
        if (car807 != null) car807.remove();
        clearScene();
    }

    private void buildScene() {
        int station = ParkPlan.FLOOR_STATION, hall = ParkPlan.FLOOR_HALL;
        for (int i = 0; i < ParkPlan.KIOSKS.length; i++) {
            double[] k = ParkPlan.KIOSKS[i];
            kiosk(i, k[0], k[1], k[2], (float) k[3]);
        }
        // 车站回程通道的紧急闸门：两根立柱、顶上卷帘匣、黄边、一盏警示灯。平时卷在匣子里，人从底下走过去
        for (double z : new double[]{2.82, 9.0}) box("minecraft:polished_deepslate", -10.62, station, z, 0.24, 4.0, 0.18, -1);
        box("minecraft:polished_deepslate", -10.78, station + 3.95, 2.82, 0.56, 0.5, 6.36, -1);
        box("minecraft:yellow_concrete", -10.23, station + 3.95, 2.82, 0.02, 0.07, 6.36, -1);
        stationLamp = box("minecraft:orange_concrete", -10.3, station + 4.45, 2.9, 0.16, 0.18, 0.16, -1);
        TextDisplay gateSign = world.spawn(new Location(world, -10.2, station + 4.08, 6.0, -90f, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text("回程通道  ·  紧急闸门").color(TextColor.color(0xf2e8cc)));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.4f, 0.4f, 0.4f), new AxisAngle4f()));
        });
        scene.add(gateSign);
        // 卷帘是浅灰的铁皮，落下来以后在暗处也认得出是一扇门
        shutter = box("minecraft:iron_block", -10.55, station, 3.0, 1, 1, 1, 6);
        shutter.setTransformation(new Transformation(new Vector3f(0, 3.95f, 0), new AxisAngle4f(), new Vector3f(0.06f, 0.02f, 6.0f), new AxisAngle4f()));
        shutterBar = box("minecraft:yellow_concrete", -10.57, station, 3.0, 1, 1, 1, -1);
        shutterBar.setTransformation(new Transformation(new Vector3f(0, 3.9f, 0), new AxisAngle4f(), new Vector3f(0.1f, 0.06f, 6.0f), new AxisAngle4f()));
        // 全景窗下半截的观景卷帘：平时放下，打完分才升起来。底下卡着爬进来的肉，关不严
        for (int i = 0; i < SLATS; i++) {
            BlockDisplay slat = box("minecraft:light_gray_concrete", BLIND_X0, hall + BLIND_BOTTOM + i * SLAT, 11.74, BLIND_X1 - BLIND_X0, SLAT - 0.035, 0.05, -1);
            slat.setTeleportDuration(3);
            slats.add(slat);
        }
        box("minecraft:polished_deepslate", BLIND_X0 - 0.2, hall + BLIND_BOTTOM + SLATS * SLAT, 11.52, BLIND_X1 - BLIND_X0 + 0.4, 0.38, 0.42, -1);
        blindSign = world.spawn(new Location(world, 128, hall + 2.6, 11.68, 180f, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text("观景时间结束\n请为本窗评分后再次开放").color(TextColor.color(0x24211f)));
            d.setBackgroundColor(Color.fromARGB(255, 232, 224, 208));
            d.setLineWidth(400);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f()));
        });
        scene.add(blindSign);
        // 楼梯口的警戒线，两根立柱之间拉直
        stairTape.clear();
        for (int i = 0; i < TAPE_PIECES; i++) {
            int k = i;
            BlockDisplay piece = world.spawn(new Location(world, ParkPlan.STAIR_TOP_X + 1, hall + 0.95, TAPE_Z0), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData((k & 1) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete"));
                d.setTransformationMatrix(tapePiece(0, 0, k * TAPE_PIECE, 0));
                d.setShadowRadius(0);
            });
            scene.add(piece);
            stairTape.add(piece);
        }
        // 快餐店的门
        door = box("minecraft:waxed_oxidized_copper", ParkPlan.KITCHEN_X, hall, ParkPlan.KITCHEN_Z + 0.9, 1, 2.05, 0.06, -1);
        Interaction doorHit = interaction(ParkPlan.KITCHEN_X + 0.5, hall, ParkPlan.KITCHEN_Z + 1.0, 1.1f, 2.1f);
        park.onClick(doorHit, this::openKitchen);
        // 37 个泡在池子里的人形，背对着人
        for (int i = 0; i < Lower.FIGURES; i++) {
            double z = -14.4 + i * 0.8 + rnd.nextDouble() * 0.2;
            ItemDisplay figure = item("fig_bather_" + i + "_bust", Lower.ROW_X + rnd.nextDouble() * 0.4, Lower.POOL3_FLOOR + Lower.SURFACE - 0.58, z, -90f);
            figure.setTeleportDuration(10);
            // 给一个剔除盒子，不在视野里就不画
            figure.setDisplayWidth(1.6f);
            figure.setDisplayHeight(3f);
            row.add(figure);
        }
        // 腺泡的洞口
        Interaction hole = interaction(Lower.HOLE[0], Lower.HOLE[1] - 0.75, Lower.HOLE[2] + 0.2, 1.4f, 1.5f);
        park.onClick(hole, this::crawl);
        // 腺泡里面
        for (int x = Lower.BURROW_X0; x < Lower.BURROW_X1; x += 3) {
            ItemDisplay piece = item("burrow", x + 1.5, Lower.BURROW_Y + 0.42, Lower.BURROW_Z + 0.5, 0f);
            piece.setTransformationMatrix(burrowMatrix(0));
            burrow.add(piece);
        }
        // 系绳
        for (Parts.Beam b : plan.forest.tether) {
            float dx = (float) (b.bx() - b.ax()), dy = (float) (b.by() - b.ay()), dz = (float) (b.bz() - b.az()), len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            BlockDisplay rope = world.spawn(new Location(world, b.ax(), b.ay(), b.az()), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData(b.block()));
                d.setShadowRadius(0);
                d.setViewRange(2f);
                d.setTransformationMatrix(new Matrix4f().rotate(new org.joml.Quaternionf().rotationTo(0, 1, 0, dx / len, dy / len, dz / len)).scale((float) b.thick(), len, (float) b.thick()).translate(-0.5f, 0, -0.5f));
            });
            scene.add(rope);
            tetherParts.add(rope);
        }
        for (Parts.Box lamp : plan.forest.tetherLamps) scene.add(box(lamp.block(), lamp.x(), lamp.y(), lamp.z(), lamp.sx(), lamp.sy(), lamp.sz(), 1));
        // 护林员车：车载电台早就没电了，只剩壳子和一根天线；值班日志在座位上
        double vy = Forest.VEHICLE[1];
        scene.add(box("minecraft:polished_deepslate", 314.1, vy + 1.05, 29.2, 0.36, 0.22, 0.5, -1));
        scene.add(box("minecraft:iron_block", 314.462, vy + 1.08, 29.24, 0.006, 0.16, 0.2, -1));
        scene.add(box("minecraft:iron_block", 314.16, vy + 1.27, 29.64, 0.012, 0.5, 0.012, -1));
        ItemDisplay paper = world.spawn(new Location(world, 315.7, vy + 0.6, 31.1), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemStack(Material.PAPER));
            d.setShadowRadius(0);
            d.setBrightness(new Display.Brightness(9, 0));
            d.setTransformationMatrix(new Matrix4f().rotateX((float) (Math.PI / 2)).scale(0.5f));
        });
        scene.add(paper);
        Interaction report = interaction(315.7, vy + 0.45, 31.1, 0.8f, 0.4f);
        park.onClick(report, this::readReport);
        spawnNotes();
        spawnTape();
        spawnEntry();
        park.ride.departGate = this::canDepart;
        // 三部电梯
        cabDoors(0, Forest.CAB_X, Forest.CAB_Y, Forest.CAB_Z, true);
        cabDoors(1, ParkPlan.DOCK_CAB_X, ParkPlan.FLOOR_HALL, ParkPlan.DOCK_CAB_Z, false);
        cabDoors(2, ParkPlan.NEW_CAB_X, ParkPlan.FLOOR_STATION, ParkPlan.NEW_CAB_Z, false);
        finalKiosk = new Kiosk(park, world, Forest.CAB_X + 0.05, Forest.CAB_Y, Forest.CAB_Z + 1.5, -90f, STOPS[6]);
        finalKiosk.spawn((p, stars) -> leaveInCab(p, stars), this::smash);
        // 最后这台只能打满分
        for (int s = 1; s <= 4; s++) finalKiosk.killButton(s);
        kiosks.add(finalKiosk);
        cabFloor = text("支气管森林", Forest.CAB_X + 0.04, Forest.CAB_Y + Forest.FLOOR_SIGN_Y, Forest.CAB_Z + 1.5, -90f, 0xffb040, 0.5f, 0xff0a0806, 15);
        for (int[] c : new int[][]{{ParkPlan.DOCK_CAB_X, ParkPlan.FLOOR_HALL, ParkPlan.DOCK_CAB_Z}, {ParkPlan.NEW_CAB_X, ParkPlan.FLOOR_STATION, ParkPlan.NEW_CAB_Z}}) {
            Kiosk shell = new Kiosk(park, world, c[0] + 0.05, c[1], c[2] + 1.5, -90f, "感谢您的评价");
            shell.spawn(null, null);
            kiosks.add(shell);
        }
        buildWell();
    }

    // ---------- 纸条和每一站的门 ----------

    /** 每一站的纸都读完，去下一站的门才开。门在哪：x、地面 y、z0、z1。 */
    private static final java.util.Map<String, int[]> DOORS = java.util.Map.of(
            "garden", new int[]{Lower.LOCKER_X0 - 1, Lower.GARDEN_FLOOR, 0, 2},
            "lockers", new int[]{Lower.LOCKER_X1 + 1, Lower.GARDEN_FLOOR, 0, 2});
    /** 每一站的进度牌放在哪：x、y、z、朝向。 */
    private static final java.util.Map<String, double[]> LABELS = java.util.Map.of(
            "station", new double[]{-4.5, ParkPlan.FLOOR_STATION + 2.55, 2.97, 0},
            "hall", new double[]{ParkPlan.STAIR_TOP_X - 1.5, ParkPlan.FLOOR_HALL + 2.35, 8.52, 0},
            "garden", new double[]{Lower.LOCKER_X0 - 1.6, Lower.GARDEN_FLOOR + 1.95, 1.5, 90},
            "lockers", new double[]{Lower.LOCKER_X1 + 0.4, Lower.GARDEN_FLOOR + 1.95, 1.5, 90},
            "pools", new double[]{Lower.HOLE[0], Lower.HOLE[1] + 2.5, Lower.HOLE[2] + 1.4, 0},
            "forest", new double[]{301.5, 178.5, -56.3, 0});
    private final java.util.Map<String, Notes.Note> notes = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> read = new java.util.HashSet<>();
    private final java.util.Set<String> opened = new java.util.HashSet<>();
    private final java.util.Map<String, TextDisplay> gateLabels = new java.util.HashMap<>();
    private final java.util.Map<String, BlockDisplay> gateShutters = new java.util.HashMap<>();
    private boolean stairsDue, stairsOpen;
    private long lastBlocked;

    private void spawnNotes() {
        notes.clear();
        noteParts.clear();
        park.tram.clearNotes();
        for (Notes.Note n : Notes.all(plan)) {
            notes.put(n.id(), n);
            Interaction hit = n.cabin() ? park.tram.mountNote(n) : paper(n);
            park.onClick(hit, p -> readNote(p, n));
        }
        for (String area : Notes.AREAS.keySet()) {
            double[] at = LABELS.get(area);
            TextDisplay label = world.spawn(new Location(world, at[0], at[1], at[2], (float) at[3], 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBackgroundColor(Color.fromARGB(220, 36, 33, 31));
                d.setShadowed(false);
                d.setLineWidth(400);
                d.setViewRange(1.2f);
                d.setBrightness(new Display.Brightness(13, 0));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.4f, 0.4f, 0.4f), new AxisAngle4f()));
            });
            scene.add(label);
            gateLabels.put(area, label);
            updateLabel(area);
        }
        BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        for (var door : DOORS.entrySet()) {
            int[] d = door.getValue();
            for (int y = d[1]; y <= d[1] + 2; y++) for (int z = d[2]; z <= d[3]; z++) world.getBlockAt(d[0], y, z).setBlockData(barrier, false);
            BlockDisplay shutter = box("minecraft:polished_deepslate", d[0] + 0.42, d[1], d[2], 0.16, 3, d[3] - d[2] + 1, -1);
            shutter.setTeleportDuration(30);
            gateShutters.put(door.getKey(), shutter);
        }
    }

    // ---------- 813 号车的行车录音带 ----------

    /** 磁带掉在车门边的地上；录音机装在驾驶台上。坐标相对缆车根实体。 */
    private static final double[] TAPE_AT = {-2.75, -0.31, 1.05}, RECORDER_AT = {0.05, 0.42, -0.75};
    /** 0 躺在地上，1 拿在手里，2 放进去了。 */
    private int tapeState;
    private final List<Entity> tapeParts = new ArrayList<>();

    private void spawnTape() {
        tapeState = 0;
        tapeParts.clear();
        double[] t = TAPE_AT, r = RECORDER_AT;
        // 一盘黑色的盒式磁带，两个白色的带轴
        tapeParts.add(park.tram.mountBox("minecraft:black_concrete", t[0], t[1], t[2], 0.26, 0.035, 0.17, 7));
        tapeParts.add(park.tram.mountBox("minecraft:white_concrete", t[0] + 0.06, t[1] + 0.036, t[2] + 0.06, 0.05, 0.004, 0.05, 7));
        tapeParts.add(park.tram.mountBox("minecraft:white_concrete", t[0] + 0.15, t[1] + 0.036, t[2] + 0.06, 0.05, 0.004, 0.05, 7));
        Interaction tapeHit = park.tram.mountHit(t[0] + 0.13, t[1], t[2] + 0.08, 0.5f, 0.25f);
        park.onClick(tapeHit, this::takeTape);
        // 行车录音机：灰壳子、一道带仓的缝、一颗小红灯
        park.tram.mountBox("minecraft:gray_concrete", r[0], r[1], r[2], 0.3, 0.12, 0.24, 6);
        park.tram.mountBox("minecraft:black_concrete", r[0] - 0.005, r[1] + 0.04, r[2] + 0.04, 0.005, 0.04, 0.16, 6);
        park.tram.mountBox("minecraft:red_concrete", r[0] - 0.005, r[1] + 0.09, r[2] + 0.2, 0.005, 0.015, 0.015, 12);
        Interaction recorderHit = park.tram.mountHit(r[0] + 0.15, r[1] - 0.1, r[2] + 0.12, 0.55f, 0.4f);
        park.onClick(recorderHit, this::playTape);
    }

    private void takeTape(Player p) {
        if (tapeState != 0) return;
        tapeState = 1;
        tapeParts.forEach(Entity::remove);
        dropTag(p, "tape");
        sound(p, "item.armor.equip_leather", 0.7f, 1.4f);
        double[] r = RECORDER_AT;
        fx.send(p, "tag recorder|" + (r[0] + 0.15) + "|" + (r[1] + 0.12 + TAG_ABOVE) + "|" + (r[2] + 0.12) + "|1|行车录音机");
        Bukkit.getLogger().info("磁带 捡起");
    }

    /** 带子上录的是 2007 年 7 月 4 日下午这辆车收到的无线电。旧带子，底下一层咝咝声。 */
    private static final String[][] TAPE = {
            {"调度", "r1"}, {"813 次", "r2"}, {"813 次", "r3"}, {"调度", "r4"}, {"813 次", "r5"}, {"调度", "r6"},
            {"813 次", "r7"}, {"调度", "r8"}, {"813 次", "r9"}, {"调度", "r10"}};
    /** 量出来的每句长度（tick）。 */
    private static final java.util.Map<String, Integer> TAPE_TICKS = java.util.Map.of("r1", 147, "r2", 112, "r3", 136, "r4", 111, "r5", 110,
            "r6", 82, "r7", 98, "r8", 72, "r9", 37, "r10", 113);
    /** 每句之后停多久（tick）。r9 之后是很长一段沙沙声。 */
    private static final java.util.Map<String, Integer> TAPE_GAP = java.util.Map.of("r6", 50, "r9", 110);

    private void playTape(Player p) {
        if (tapeState == 0) {
            fx.send(p, "sub 提示|录音机的带仓是空的。|50");
            return;
        }
        if (tapeState != 1) return;
        tapeState = 2;
        dropTag(p, "recorder");
        Bukkit.getLogger().info("磁带 放进录音机");
        sound(p, "block.iron_trapdoor.close", 0.5f, 1.8f);
        after(8, () -> sound(p, "block.lever.click", 0.6f, 1.4f));
        after(14, () -> fx.play(p, "tapebed", "radio_static", 0.16f, 0.75f, true, 20));
        int at = 40;
        for (String[] line : TAPE) {
            int start = at;
            after(start, () -> {
                fx.subtitle(p, line[0], line[1]);
                fx.play(p, "tramradio", line[1], 0.95f, 1f, false, 0);
            });
            at += TAPE_TICKS.getOrDefault(line[1], 80) + 16 + TAPE_GAP.getOrDefault(line[1], 0);
        }
        // 放完带子自己停：咔一声，咝咝声没了
        after(at + 20, () -> {
            fx.gain(p, "tapebed", 0f, 10);
            sound(p, "block.lever.click", 0.6f, 1.0f);
        });
    }

    /** 一张纸平放着，点一下读。 */
    private Interaction paper(Notes.Note n) {
        ItemDisplay sheet = world.spawn(new Location(world, n.x(), n.y(), n.z()), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemStack(Material.PAPER));
            d.setShadowRadius(0);
            d.setBrightness(new Display.Brightness(11, 0));
            d.setTransformationMatrix(new Matrix4f().rotateY((float) Math.toRadians(n.yaw())).rotateX((float) (Math.PI / 2)).scale(0.42f));
        });
        scene.add(sheet);
        Interaction hit = interaction(n.x(), n.y() - 0.12, n.z(), 0.6f, 0.3f);
        noteParts.put(n.id(), List.of(sheet, hit));
        return hit;
    }

    /** 地上的纸：捡起来读，读完右键收进背包，地上就没了。 */
    private final java.util.Map<String, List<Entity>> noteParts = new java.util.HashMap<>();

    /** 收进背包：一张有名字的纸，放在快捷栏以外的格子里。 */
    private void stash(Player p, String who, String date) {
        var inv = p.getInventory();
        for (int slot = 9; slot < 36; slot++) {
            ItemStack old = inv.getItem(slot);
            if (old != null && old.getType() == Material.PAPER && old.hasItemMeta() && who.equals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(old.getItemMeta().displayName()))) return;
        }
        ItemStack paper = new ItemStack(Material.PAPER);
        var meta = paper.getItemMeta();
        meta.displayName(Component.text(who).color(TextColor.color(0xf2e8cc)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(date).color(TextColor.color(0x9a928a)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        paper.setItemMeta(meta);
        for (int slot = 9; slot < 36; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, paper);
                return;
            }
        }
    }

    /**
     * 名牌由客户端画在界面层：世界里的字会被光影的雾和辉光吃掉。
     * 走到 5 米内才显示；读过就收掉。
     */
    private void sendTags(Player p) {
        double[] root = new double[3];
        plan.route.at(0, root);
        fx.send(p, "taganchor " + root[0] + " " + root[1] + " " + root[2]);
        for (Notes.Note n : notes.values()) {
            if (read.contains(n.id())) continue;
            fx.send(p, "tag " + n.id() + "|" + n.x() + "|" + (n.y() + TAG_ABOVE) + "|" + n.z() + "|" + (n.cabin() ? 1 : 0) + "|" + n.who());
        }
        if (!read.contains(Notes.VEHICLE_LOG)) fx.send(p, "tag " + Notes.VEHICLE_LOG + "|315.7|" + (Forest.VEHICLE[1] + 0.6 + TAG_ABOVE) + "|31.1|0|十四号护林员车 · 值班日志");
        if (!kitTaken) fx.send(p, "tag kit|-27.35|" + (ParkPlan.FLOOR_STATION + 1.45) + "|5.3|0|体验官 #0011 · 手电和手机");
        if (tapeState == 0) fx.send(p, "tag tape|" + (TAPE_AT[0] + 0.13) + "|" + (TAPE_AT[1] + 0.05 + TAG_ABOVE) + "|" + (TAPE_AT[2] + 0.08) + "|1|磁带 · 813");
        if (tapeState == 1) fx.send(p, "tag recorder|" + (RECORDER_AT[0] + 0.15) + "|" + (RECORDER_AT[1] + 0.12 + TAG_ABOVE) + "|" + (RECORDER_AT[2] + 0.12) + "|1|行车录音机");
    }

    private void dropTag(Player p, String id) {
        fx.send(p, "untag " + id);
    }

    private void readNote(Player p, Notes.Note n) {
        fx.send(p, "diary " + n.who() + "|" + n.date() + "|" + String.join("|", noteLines(n)));
        sound(p, "item.book.page_turn", 0.9f, 0.8f);
        dropTag(p, n.id());
        stash(p, n.who(), n.date());
        // 地上那张捡走了
        List<Entity> parts = noteParts.remove(n.id());
        if (parts != null) parts.forEach(Entity::remove);
        if (read.add(n.id())) {
            progress(p, n.area());
            if (n.area().equals("real")) maybeEnd(p);
            org.bukkit.Bukkit.getLogger().info("读到纸条 " + n.id() + "，" + n.area() + " " + done(n.area()) + "/" + total(n.area()));
        }
    }

    /** 纸上写着你打的分的地方，读的时候填上。 */
    private String[] noteLines(Notes.Note n) {
        String[] out = n.lines().clone();
        for (int i = 0; i < out.length; i++) {
            for (int k = 0; k < scores.length; k++) out[i] = out[i].replace(Notes.SCORE + k + "〕", scores[k] == 0 ? "—" : String.valueOf(scores[k]));
            if (player != null) out[i] = out[i].replace(Notes.NAME, player.getName());
        }
        return out;
    }

    private String areaOf(String id) {
        if (id.equals(Notes.VEHICLE_LOG)) return "forest";
        Notes.Note n = notes.get(id);
        return n == null ? "" : n.area();
    }

    private int total(String area) {
        int n = area.equals("forest") ? 1 : 0;
        for (Notes.Note note : notes.values()) if (note.area().equals(area)) n++;
        return n;
    }

    private int done(String area) {
        int n = 0;
        for (String id : read) if (areaOf(id).equals(area)) n++;
        return n;
    }

    boolean areaDone(String area) {
        return opened.contains(area) || done(area) >= total(area);
    }

    private void updateLabel(String area) {
        TextDisplay label = gateLabels.get(area);
        if (label == null) return;
        String name = Notes.AREAS.get(area);
        boolean ok = areaDone(area);
        label.text(Component.text("「" + name + "」\n" + (ok ? "体验项目已完成" : "体验项目  " + done(area) + " / " + total(area)))
                .color(TextColor.color(ok ? 0xb8ffc8 : 0xfffbf5)));
    }

    private void progress(Player p, String area) {
        if (!Notes.AREAS.containsKey(area)) return;
        updateLabel(area);
        if (done(area) < total(area) || !opened.add(area)) return;
        updateLabel(area);
        int n = total(area);
        String next = switch (area) {
            case "station" -> "813 次缆车可以发车。请进入车厢。";
            case "hall" -> "楼梯间开放后，请前往休闲花园层。";
            case "garden" -> "更衣室通道已开放。";
            case "lockers" -> "温泉通道已开放。";
            case "pools" -> "特别体验「未开发腺泡」已开放。";
            default -> "请沿栈道继续游览。";
        };
        fx.parkSms(p, "「" + Notes.AREAS.get(area) + "」体验项目已全部完成（" + n + "/" + n + "）。" + next);
        switch (area) {
            case "hall" -> tryOpenStairs(p);
            case "garden", "lockers" -> openDoor(p, area);
            case "forest" -> maybeExhale(p);
            default -> {
            }
        }
    }

    /** 门还没开：短信提醒还差几张，不要每一下都震。 */
    private void blocked(Player p, String area) {
        long now = System.currentTimeMillis();
        if (now - lastBlocked < 15000) return;
        lastBlocked = now;
        fx.parkSms(p, "前往下一站前，请先完成「" + Notes.AREAS.get(area) + "」全部体验项目（" + done(area) + "/" + total(area) + "）。");
        sound(p, "block.iron_door.close", 0.6f, 0.7f);
    }

    private void openDoor(Player p, String area) {
        int[] d = DOORS.get(area);
        if (d == null) return;
        for (int y = d[1]; y <= d[1] + 2; y++) for (int z = d[2]; z <= d[3]; z++) world.getBlockAt(d[0], y, z).setType(Material.AIR, false);
        BlockDisplay shutter = gateShutters.get(area);
        if (shutter != null) shutter.teleport(shutter.getLocation().add(0, 3, 0));
        sound(p, "block.chain.place", 1f, 0.6f);
        sound(p, "block.piston.extend", 0.6f, 0.5f);
    }

    /** 缆车发车前：车站的纸要读完。 */
    boolean canDepart(Player p) {
        if (areaDone("station")) return true;
        blocked(p, "station");
        return false;
    }

    private void tryOpenStairs(Player p) {
        if (!stairsDue || stairsOpen) return;
        if (!areaDone("hall")) {
            blocked(p, "hall");
            return;
        }
        stairsOpen = true;
        openStairs(p);
    }

    /** 导演栏和测试用：人所在这一站的纸全部算读过。 */
    void unlock(Player p) {
        String area = switch (zone) {
            case "station", "out", "throat", "cavity" -> "station";
            case "dock", "hall" -> "hall";
            case "garden", "tunnel" -> "garden";
            case "lockers" -> "lockers";
            case "pools", "burrow" -> "pools";
            case "forest" -> "forest";
            case "well", "real" -> zone;
            default -> "";
        };
        if (area.isEmpty()) return;
        for (Notes.Note n : notes.values()) if (n.area().equals(area) || area.equals("well") && n.area().equals("rope")) read.add(n.id());
        if (area.equals("forest")) {
            read.add(Notes.VEHICLE_LOG);
            reportRead = radioDone = true;
        }
        progress(p, area);
    }

    boolean atNewStation() {
        return atNewStation;
    }

    private void kiosk(int index, double x, double y, double z, float yaw) {
        Kiosk kiosk = new Kiosk(park, world, x, y, z, yaw, STOPS[index]);
        kiosk.spawn((p, stars) -> rated(index, p, stars), null);
        kiosk.gate = (p, stars) -> gate(index, kiosk, p, stars);
        // 越往下，能选的分数越少
        if (index == 4) kiosk.killButton(1);
        if (index == 5) for (int s = 1; s <= 3; s++) kiosk.killButton(s);
        kiosks.add(kiosk);
    }

    private void cabDoors(int i, int x0, int y, int z0, boolean open) {
        double dx = x0 + 3.02;
        doorA[i] = box("minecraft:iron_block", dx, y, z0 + (open ? -1.4 : 0), 0.1, 3, 1.5, -1);
        doorB[i] = box("minecraft:iron_block", dx, y, z0 + (open ? 2.9 : 1.5), 0.1, 3, 1.5, -1);
        doorA[i].setTeleportDuration(14);
        doorB[i].setTeleportDuration(14);
        BlockData state = Bukkit.createBlockData(open ? Material.AIR : Material.BARRIER);
        for (int yy = y; yy <= y + 2; yy++) for (int z = z0; z <= z0 + 2; z++) world.getBlockAt(x0 + 3, yy, z).setBlockData(state, false);
    }

    private void cabDoor(int i, int x0, int y, int z0, boolean open) {
        double dx = x0 + 3.02;
        doorA[i].teleport(new Location(world, dx, y, z0 + (open ? -1.4 : 0)));
        doorB[i].teleport(new Location(world, dx, y, z0 + (open ? 2.9 : 1.5)));
        BlockData state = Bukkit.createBlockData(open ? Material.AIR : Material.BARRIER);
        for (int yy = y; yy <= y + 2; yy++) for (int z = z0; z <= z0 + 2; z++) world.getBlockAt(x0 + 3, yy, z).setBlockData(state, false);
        if (player != null) player.playSound(player.getLocation(), "block.iron_door.close", SoundCategory.MASTER, 0.9f, open ? 0.8f : 0.6f);
    }

    /**
     * 开场，全程第一人称。先是赫朝的片名，再是一张情境提要：你是谁、来干什么。
     * 亮起来人站在公路边的旧接驳站，按站牌上的铃叫车。公园当年的接驳大巴开过来，开门，人上车坐到第一排。
     * 车走夜路：电视放公园 2007 年的宣传片，放到独立日烟花晚会那张就坏了；收音机里是重开评估的新闻；
     * 司机关在隔间里，只有喇叭里的声音。窗外是旧广告牌、遇难者纪念点、封路的牌子、检查站。
     * 车开进落客区，门开了，人下车，车当着人的面倒出去开走。人走进入园大厅，卷帘门在身后落下。
     * 大厅里只有工作灯照着一张桌子，拿了手电和手机，广播醒过来，顶灯一盏盏亮到闸机，闸机自己开。
     */
    void begin(Player p) {
        player = p;
        fx.send(p, "clear");
        fx.black(p, 1f, 1);
        p.setWalkSpeed(0f);
        roadAir(p);
        busStage = 0;
        busX = ParkPlan.BUS_FROM_X;
        allowBusDismount = true;
        p.leaveVehicle();
        allowBusDismount = false;
        // 先是入场片：旁白讲清楚这是哪、出过什么事、我来干什么。讲完出片名，再落到公路边的接驳站
        cine(p, coldOpen(), () -> {
            p.teleport(new Location(world, ParkPlan.PICKUP_SPAWN[0], ParkPlan.FLOOR_STATION, ParkPlan.PICKUP_SPAWN[1], (float) ParkPlan.PICKUP_SPAWN[2], 4));
            roadAir(p);
            fx.send(p, "title");
            after(85, () -> fx.send(p, "intro"));
            after(85 + BRIEF_TICKS - 20, () -> {
                fx.black(p, 0f, 40);
                p.setWalkSpeed(0.2f);
            });
            after(85 + BRIEF_TICKS + 20, () -> {
                if (busStage == 0) fx.send(p, "tag call|" + ParkPlan.PICKUP_POLE[0] + "|" + (ParkPlan.FLOOR_STATION + 1.55) + "|" + (ParkPlan.PICKUP_POLE[1] + 0.1) + "|0|呼叫接驳车");
            });
        });
    }

    /** 片名之后那张小卡在屏幕上停多久，和客户端 Overlay.BRIEF_SECONDS 一样。 */
    private static final int BRIEF_TICKS = 140;

    // ---------- 入场片和收尾 ----------

    /**
     * 一个镜头：一串机位，每个机位是眼睛在哪 (x,y,z) 和看向哪 (lx,ly,lz)；镜头在这几个机位之间顺着推过去。
     * zone 决定这一镜画哪几块肉、用什么雾；clip 是这一句旁白，ticks 是它多长。
     */
    private record Shot(String zone, double[][] path, String clip, int ticks, String who, String... more) {
        Shot(String zone, double[][] path, String clip, int ticks) {
            this(zone, path, clip, ticks, "旁白");
        }
    }

    private boolean cineOn;

    private static double[] key(double x, double y, double z, double lx, double ly, double lz) {
        return new double[]{x, y, z, lx, ly, lz};
    }

    /** 缆车走的那条线上，第 d 米处车厢中间往上 up 米。 */
    private double[] onRoute(double d, double up) {
        double[] q = new double[3];
        plan.route.at(d, q);
        return new double[]{q[0] + Route.CABIN_CENTER_X, q[1] + up, q[2]};
    }

    private double[] routeKey(double d, double up, double ahead) {
        double[] a = onRoute(d, up), b = onRoute(Math.min(plan.route.length, d + ahead), up - 0.6);
        return key(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    /** 开场的字幕不是谁在说话，是公司档案：冷的字，没有人声。 */
    private static final String ARCHIVE = "档案";

    /**
     * 入场片：没有人讲解，只有二叠纪公司的档案字幕一行行打出来。"我"是谁，上了车看任务书才知道。
     * 夜路和旧广告牌 → 缆车下到喉咙里 → 腔里的游客中心 → 关了十九年的中庭 → 车站的评分机 → 今晚等车的接驳站。
     */
    private List<Shot> coldOpen() {
        int st = ParkPlan.FLOOR_STATION;
        double[] k = ParkPlan.KIOSKS[0];
        double[] pole = ParkPlan.PICKUP_POLE;
        return List.of(
                // 夜路：贴着路面滑过去，先是“前方 12 公里”的路牌，再推到公园的旧海报
                new Shot("station", new double[][]{key(-521, st + 2.0, 2.5, -505, st + 3.8, 12.6), key(-497, st + 2.4, 3.5, -470, st + 4.0, 12.6), key(-472, st + 2.8, 5, -450, st + 4.4, 12.6)},
                        "二叠纪盆地回收与超级生物体收容公司¶档案  ·  息壤国家公园", 130, ARCHIVE),
                new Shot("throat", new double[][]{routeKey(44, 3, 22), routeKey(62, 2.6, 20), routeKey(86, 2.4, 18), routeKey(112, 2.4, 18)},
                        "1976 年开放。园区位于一个活体有机物内部，游客乘缆车进入。", 150, ARCHIVE),
                new Shot("cavity", new double[][]{key(onRoute(132, 5)[0], onRoute(132, 5)[1], onRoute(132, 5)[2], 110, 36, 0),
                        key(onRoute(186, 3.5)[0], onRoute(186, 3.5)[1], onRoute(186, 3.5)[2], 110, 35, 0),
                        key(onRoute(236, 2.5)[0], onRoute(236, 2.5)[1], onRoute(236, 2.5)[2], 110, 33, 0)}, "下游客中心悬挂于中央腔顶部，由六根支撑固定。", 140, ARCHIVE),
                // 中庭挑空里往上升，看顶上破口垂下来的那一束肉
                new Shot("hall", new double[][]{key(127, 31.0, 3.5, 116, 39.5, 9), key(125.5, 32.5, 4.5, 115.8, 40.2, 9), key(124, 34, 5.5, 115.5, 40.5, 9)},
                        "2007 年 7 月 4 日 23:05，下游客中心坠落。未返回：754 人。", 170, ARCHIVE),
                new Shot("station", new double[][]{key(k[0], st + 1.7, 2.8, k[0], st + 1.3, k[2]), key(k[0], st + 1.55, 4.6, k[0], st + 1.3, k[2]), key(k[0], st + 1.45, 5.9, k[0], st + 1.3, k[2])},
                        "2008 年起由本公司收容。封锁半径 3 公里。无人员下行。", 150, ARCHIVE),
                // 从路对面慢慢推到接驳站：雨棚下亮着一根灯管，站牌立在黄线边上。片子一完，人就站在这里
                new Shot("station", new double[][]{key(pole[0] + 3.5, st + 1.75, -0.5, pole[0] - 2.6, st + 1.5, 8), key(pole[0] + 0.6, st + 1.65, 1.9, pole[0] - 3, st + 1.4, 8)},
                        "2025 年起，园区评分终端恢复上传。来源不明。¶外勤评估    体验官 #0011    2026 年 11 月 7 日出发", 220, ARCHIVE));
    }

    private void shotAir(Player p, String zone) {
        fx.send(p, "zone " + (zone.equals("road") ? "station" : zone));
        switch (zone) {
            case "station" -> roadAir(p);
            case "throat" -> fx.fog(p, 3, 40, 1f, "100303", 1f);
            case "cavity" -> fx.fog(p, 7, 120, 1f, "0e0303", 1f);
            case "hall" -> fx.fog(p, 14, 70, 1f, "0e0806", 1f);
            case "pools" -> fx.fog(p, 7, 42, 1f, "140506", 1f);
            case "lockers" -> fx.fog(p, 4, 26, 1f, "0c0807", 1f);
            case "new" -> fx.fog(p, 30, 120, 1f, "4a4238", 1f);
            default -> {
            }
        }
    }

    /**
     * 放一串镜头：人变成旁观者（看不见手，不能动），每个镜头黑一下切进去，镜头沿着机位慢慢推，念一句旁白。
     * 镜头怎么动由客户端每帧插值，服务端只给机位。放完回到冒险模式，接着往下演。
     */
    private void cine(Player p, List<Shot> shots, Runnable done) {
        cineOn = true;
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        p.setFlySpeed(0f);
        fx.send(p, "cine on");
        int at = 10;
        for (Shot shot : shots) {
            int start = at, length = 22 + shot.ticks() + 26;
            after(start, () -> fx.black(p, 1f, 6));
            after(start + 7, () -> {
                if (!cineOn) return;
                StringBuilder path = new StringBuilder();
                float lastYaw = 0;
                for (int i = 0; i < shot.path().length; i++) {
                    double[] c = shot.path()[i];
                    double dx = c[3] - c[0], dy = c[4] - c[1], dz = c[5] - c[2];
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
                    // 朝向不绕远路：和上一个机位差不超过半圈
                    if (i > 0) while (yaw - lastYaw > 180) yaw -= 360;
                    if (i > 0) while (yaw - lastYaw < -180) yaw += 360;
                    lastYaw = yaw;
                    if (i == 0) p.teleport(new Location(world, c[0], c[1] - 1.62, c[2], yaw, pitch));
                    path.append(i == 0 ? "" : ";").append(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f %.2f %.2f", c[0], c[1], c[2], yaw, pitch));
                }
                shotAir(p, shot.zone());
                fx.send(p, "cam " + (length - 7) + " " + path);
            });
            after(start + 12, () -> fx.black(p, 0f, 10));
            after(start + 22, () -> {
                if (!cineOn) return;
                if (shot.who().equals(ARCHIVE)) {
                    // 档案字幕：字直接给客户端打出来，不放配音
                    fx.send(p, "sub " + ARCHIVE + "|" + shot.clip() + "|" + (shot.ticks() + 16));
                    return;
                }
                fx.subtitle(p, shot.who(), shot.clip(), LINE_TICKS.getOrDefault(shot.clip(), shot.ticks()) + 16);
                fx.play(p, "voice", shot.clip(), 1f, 1f, false, 0);
            });
            // 一个镜头里接着念的几句：上一句念完停一下再接
            int next = start + 22 + LINE_TICKS.getOrDefault(shot.clip(), shot.ticks()) + 8;
            for (String clip : shot.more()) {
                after(next, () -> {
                    if (!cineOn) return;
                    fx.subtitle(p, shot.who(), clip, LINE_TICKS.getOrDefault(clip, 120) + 16);
                    fx.play(p, "voice", clip, 1f, 1f, false, 0);
                });
                next += LINE_TICKS.getOrDefault(clip, 120) + 8;
            }
            at += length;
        }
        after(at, () -> fx.black(p, 1f, 8));
        after(at + 10, () -> {
            if (!cineOn) return;
            endCine(p);
            done.run();
        });
    }

    /** 量出来的配音长度（tick）。没有的按镜头长度算。 */
    private static final java.util.Map<String, Integer> LINE_TICKS = java.util.Map.of();

    private void endCine(Player p) {
        cineOn = false;
        fx.send(p, "cam off");
        fx.send(p, "doc off");
        fx.send(p, "cine off");
        p.setFlySpeed(0.1f);
        p.setGameMode(org.bukkit.GameMode.ADVENTURE);
    }

    /** 司机每句话多长（tick）。一句没说完，下一句等他说完再说，不会被进站那句打断。 */
    private static final java.util.Map<String, Integer> DRIVER_TICKS = java.util.Map.ofEntries(
            java.util.Map.entry("drv_board", 111), java.util.Map.entry("drv_wait", 53), java.util.Map.entry("drv_1", 74), java.util.Map.entry("drv_2", 103),
            java.util.Map.entry("drv_tv", 104), java.util.Map.entry("drv_off", 59),
            java.util.Map.entry("drv_5", 90), java.util.Map.entry("drv_6", 109), java.util.Map.entry("drv_7", 65), java.util.Map.entry("drv_8", 82),
            java.util.Map.entry("drv_9", 55));
    private int driverBusyUntil;

    private void driver(Player p, String clip) {
        if (age < driverBusyUntil) {
            after(driverBusyUntil - age + 6, () -> driver(p, clip));
            return;
        }
        driverBusyUntil = age + DRIVER_TICKS.getOrDefault(clip, 100);
        fx.subtitle(p, "司机", clip);
        fx.play(p, "voice", clip, 1f, 1f, false, 0);
    }

    private void readLetter(Player p) {
        letterRead = true;
        if (letterAt < 0 && busStage == 4) letterAt = age;
        fx.send(p, "untag letter");
        stash(p, "外勤任务书", "二叠纪公司");
        fx.send(p, "diary 外勤任务书 · 二叠纪公司|体验官 #0011  " + p.getName() + "（初级研究员）|园区评分终端自去年起恢复上传，原因不明。|请沿原游览路线走完全程，在每一站的终端上如实打分。评分会传回公司，用于评估园区现状。|已回传：前十位体验官，平均 5.0 分。|外勤补贴：每日 800 美元，回到地面后结算。|回程由公司安排。");
        sound(p, "item.book.page_turn", 0.9f, 0.8f);
    }

    /** 公路边是夜里的荒地，雾是蓝黑的，看得远一点。 */
    private void roadAir(Player p) {
        fx.fog(p, 14, 72, 1f, "05070c", 1f);
        amb(p, "wind_cold", 0.05f);
    }

    /** 站牌上的呼叫按钮。 */
    private void spawnCallButton() {
        int y0 = ParkPlan.FLOOR_STATION;
        double x = ParkPlan.PICKUP_POLE[0], z = ParkPlan.PICKUP_POLE[1];
        box("minecraft:light_gray_concrete", x - 0.09, y0 + 1.1, z + 0.04, 0.18, 0.24, 0.05, -1);
        callLight = box("minecraft:red_concrete", x - 0.05, y0 + 1.16, z + 0.08, 0.1, 0.1, 0.03, 13);
        Interaction hit = interaction(x, y0 + 1.0, z + 0.2, 0.5f, 0.45f);
        park.onClick(hit, this::callBus);
    }

    private void callBus(Player p) {
        if (busStage != 0) return;
        busStage = 1;
        busT = 0;
        fx.send(p, "untag call");
        callLight.setBlock(Bukkit.createBlockData("minecraft:lime_concrete"));
        sound(p, "block.note_block.bit", 0.8f, 1.4f);
        after(4, () -> sound(p, "block.note_block.bit", 0.8f, 1.8f));
        after(14, () -> {
            fx.subtitle(p, "站牌", "stop_call");
            fx.play(p, "voice", "stop_call", 0.9f, 1f, false, 0);
        });
        after(60, () -> {
            bus = new Bus(park, ParkPlan.BUS_FROM_X, ParkPlan.FLOOR_STATION, ParkPlan.BUS_Z);
            busX = ParkPlan.BUS_FROM_X;
            busT = 0;
            bus.cabinLight(6);
            park.onClick(bus.doorHit, this::board);
            park.onClick(bus.clip, this::readLetter);
            fx.play(p, "bus", "bus_loop", 0.05f, 1.1f, true, 0);
            fx.gain(p, "bus", 0.6f, 200);
        });
    }

    /** 车开过来：先匀速，最后 6 秒刹在站台前面。 */
    private static final double BUS_COME = 9, BUS_COME_T = 7, BRAKE_T = 6;
    /**
     * 车走夜路分两段。前一段慢慢开（3.5 米/秒）：司机说话、人看招募函、电视放宣传片，时间不定，看人什么时候拿起招募函。
     * 宣传片放完开收音机：车黑一下跳到 RADIO_X（纪念点前面一点），再按 5 米/秒开，新闻念到遇难人数时正好经过纪念点，
     * 司机说完“你是第十一个”刚好开始刹车进落客区。
     */
    private static final double BUS_SLOW = 3.5, BUS_FAST = 5, BUS_ACCEL = 1.1, BUS_DECEL = 0.76, RADIO_X = -290;
    /** 上车以后过多久开车。 */
    private static final int DEPART_DELAY = 40;
    /** 拿起招募函以后过多久开电视；一直不拿，上车后这么久也开。 */
    private static final int PROMO_AFTER_LETTER = 200, PROMO_FALLBACK = 700;
    private boolean promoStarted, radioPhase, brakeSaid;
    private int letterAt = -1;

    private double comeAt(double t) {
        double x0 = ParkPlan.BUS_FROM_X;
        if (t <= BUS_COME_T) return x0 + BUS_COME * t;
        double tau = Math.min(BRAKE_T, t - BUS_COME_T);
        return x0 + BUS_COME * BUS_COME_T + BUS_COME * (tau - tau * tau / (2 * BRAKE_T));
    }


    private void busTick(Player p, Location at) {
        if (bus == null) return;
        busT++;
        int y0 = ParkPlan.FLOOR_STATION;
        if (busStage == 1) {
            double t = busT / 20.0;
            busX = comeAt(t);
            bus.moveTo(busX, y0, ParkPlan.BUS_Z);
            if (t >= BUS_COME_T + BRAKE_T) atPickup(p);
            return;
        }
        if (busStage == 2) {
            if (busT == 400) driver(p, "drv_wait");
            return;
        }
        if (busStage == 4) {
            if (busT < DEPART_DELAY) return;
            if (busT == DEPART_DELAY) {
                sound(p, "block.piston.contract", 0.6f, 0.6f);
                fx.gain(p, "bus", 0.85f, 60);
                fx.pitch(p, "bus", 1.15f, 100);
            }
            if (!promoStarted && (letterAt >= 0 && age >= letterAt + PROMO_AFTER_LETTER || busT >= PROMO_FALLBACK)) startPromo(p);
            double left = ParkPlan.BUS_STOP_X - busX;
            double target = Math.min(radioPhase ? BUS_FAST : BUS_SLOW, radioPhase ? Math.sqrt(2 * BUS_DECEL * Math.max(0, left)) + 0.05 : 99);
            busSpeed += Math.max(-BUS_DECEL / 20, Math.min(BUS_ACCEL / 20, target - busSpeed));
            busX = Math.min(ParkPlan.BUS_STOP_X, busX + busSpeed / 20);
            double t = busT / 20.0, shake = busSpeed > 0.3 ? (0.004 * Math.sin(t * 23) + 0.003 * Math.sin(t * 37)) * Math.min(1, busSpeed / 3) : 0;
            bus.moveTo(busX, y0 + shake, ParkPlan.BUS_Z);
            if (radioPhase && !brakeSaid && left < 17) {
                brakeSaid = true;
                driver(p, "drv_6");
                fx.pitch(p, "bus", 0.95f, 100);
            }
            if (radioPhase && left < 0.03) atBay(p);
            return;
        }
        if (busStage == 5) {
            if (at.getZ() > 5.3) {
                busStage = 6;
                after(14, () -> driver(p, "drv_8"));
                after(110, () -> {
                    bus.door(false);
                    busWalls(false);
                    sound(p, "block.iron_door.close", 1f, 0.6f);
                });
                after(150, () -> {
                    busStage = 7;
                    busSpeed = 0;
                    bus.cabinLight(3);
                    fx.gain(p, "bus", 0.9f, 20);
                    fx.pitch(p, "bus", 1.25f, 40);
                });
            } else if (busT - busStopT == 200) {
                driver(p, "drv_5");
            } else if (busT - busStopT == 360) {
                driver(p, "drv_7");
            }
            return;
        }
        if (busStage == 7) {
            // 倒着开出去，车灯对着人越来越远
            busSpeed = Math.min(14, busSpeed + 0.1);
            busX -= busSpeed / 20;
            bus.moveTo(busX, y0, ParkPlan.BUS_Z);
            if (busT % 16 == 0 && busX > ParkPlan.BAY_MOUTH_X - 8) sound(p, "block.note_block.bit", 0.6f, 1.2f);
            if (!boomDown && busX < ParkPlan.BAY_MOUTH_X - 8) {
                boomDown = true;
                lowerBoom(p);
                fx.gain(p, "bus", 0f, 200);
                amb(p, "wind_cold", 0.2f);
            }
            if (busX < -420) {
                bus.remove();
                bus = null;
                fx.gain(p, "bus", 0f, 1);
            }
        }
    }

    /** 车停在接驳站：放气、开门、车里的灯亮起来，司机叫人上车。 */
    private void atPickup(Player p) {
        busStage = 2;
        busT = 0;
        fx.gain(p, "bus", 0.3f, 30);
        fx.pitch(p, "bus", 0.9f, 30);
        sound(p, "block.fire.extinguish", 0.7f, 0.6f);
        after(12, () -> {
            bus.door(true);
            bus.cabinLight(10);
            sound(p, "block.piston.contract", 0.8f, 0.8f);
        });
        after(40, () -> driver(p, "drv_board"));
        after(50, () -> fx.send(p, "tag door|" + (busX + (Bus.DOOR_X0 + Bus.DOOR_X1) / 2) + "|" + (ParkPlan.FLOOR_STATION + 2.0) + "|" + (ParkPlan.BUS_Z + Bus.HALF) + "|0|上车"));
    }

    /** 上车：黑一下，坐到第一排靠窗。之后车上开始演：电视、收音机、司机。 */
    private void board(Player p) {
        if (busStage != 2) return;
        busStage = 3;
        fx.send(p, "untag door");
        fx.black(p, 1f, 8);
        sound(p, "block.metal.step", 0.6f, 0.9f);
        after(6, () -> sound(p, "block.metal.step", 0.6f, 0.9f));
        after(10, () -> {
            allowBusDismount = true;
            p.leaveVehicle();
            p.teleport(new Location(world, busX + Bus.SEAT[0], ParkPlan.FLOOR_STATION, ParkPlan.BUS_Z + Bus.SEAT[2], -90, 6));
            allowBusDismount = false;
            bus.root.addPassenger(p);
            // 人坐在第一排，脚比地板低一点，眼睛就在坐着的高度；动一下就站起来
            fx.send(p, "cabin " + Bus.SEAT[0] + " " + Bus.SEAT[1] + " " + Bus.SEAT[2] + " -4.4 3.4 -0.3 1.0 " + Bus.FLOOR);
            fx.send(p, "tag letter|" + Bus.CLIP[0] + "|" + (Bus.CLIP[1] + 0.2) + "|" + Bus.CLIP[2] + "|1|任务书");
            bus.door(false);
            bus.cabinLight(7);
            sound(p, "block.wool.place", 0.7f, 0.7f);
            busStage = 4;
            busT = 0;
            ride(p);
        });
        after(20, () -> fx.black(p, 0f, 16));
    }

    /** 车上这一段：时间从上车算起。 */
    private void ride(Player p) {
        promoStarted = radioPhase = brakeSaid = false;
        letterAt = -1;
        busSpeed = 0;
        after(20, () -> driver(p, "drv_1"));
        // 人已经拿起招募函了，司机就不用再提醒
        after(100, () -> {
            if (!letterRead) driver(p, "drv_2");
        });
    }

    /** 电视：人拿起招募函读到一半，电视才亮起来放宣传片。 */
    private void startPromo(Player p) {
        promoStarted = true;
        String[][] promo = {{"8", "tv_1", "promo_1"}, {"68", "tv_2", "promo_2"}, {"213", "tv_3", "promo_3"}, {"298", "tv_4", "promo_4"}, {"433", "tv_5", "promo_5"}, {"513", "tv_6", "promo_6"}};
        sound(p, "block.lever.click", 0.5f, 1.6f);
        for (String[] slide : promo) {
            after(Integer.parseInt(slide[0]), () -> {
                if (bus == null) return;
                bus.screen(slide[1]);
                fx.subtitle(p, "宣传片", slide[2]);
                fx.play(p, "tv", slide[2], 0.85f, 1f, false, 0);
            });
        }
        // 放到烟花晚会，带子坏了
        after(633, () -> {
            if (bus == null) return;
            bus.screen("tv_static");
            fx.play(p, "tv", "radio_static", 0.5f, 1.3f, false, 0);
            fx.send(p, "glitch 8");
        });
        after(660, () -> {
            if (bus == null) return;
            bus.screen("tv_off");
            fx.gain(p, "tv", 0f, 4);
        });
        after(668, () -> driver(p, "drv_tv"));
        after(790, () -> startRadio(p));
    }

    /** 收音机：车黑一下跳到纪念点前面，接着开。新闻念到遇难人数时窗外正好是纪念点。 */
    private void startRadio(Player p) {
        if (bus == null || busStage != 4) return;
        if (busX < RADIO_X) {
            fx.black(p, 1f, 5);
            after(3, () -> fx.send(p, "glitch 5"));
            after(6, () -> {
                busX = RADIO_X;
                radioPhase = true;
            });
            after(10, () -> fx.black(p, 0f, 10));
        } else {
            radioPhase = true;
        }
        after(20, () -> {
            sound(p, "block.lever.click", 0.5f, 1.2f);
            fx.play(p, "busradio", "radio_static", 0.25f, 1f, false, 0);
            fx.gain(p, "busradio", 0f, 30);
        });
        String[][] news = {{"50", "news_1"}, {"230", "news_2"}, {"420", "news_3"}, {"600", "news_4"}};
        for (String[] line : news) {
            after(Integer.parseInt(line[0]), () -> {
                fx.subtitle(p, "电台", line[1]);
                fx.play(p, "busradio", line[1], 0.8f, 1f, false, 0);
            });
        }
        after(800, () -> {
            driver(p, "drv_off");
            sound(p, "block.lever.click", 0.5f, 1.0f);
            fx.gain(p, "busradio", 0f, 4);
        });
    }

    /** 车开进落客区：门开了，人站起来，只能从车门下去。 */
    private void atBay(Player p) {
        busStage = 5;
        busStopT = busT;
        fx.gain(p, "bus", 0.3f, 30);
        sound(p, "block.fire.extinguish", 0.7f, 0.6f);
        sound(p, "block.piston.contract", 0.8f, 0.8f);
        bus.door(true);
        bus.cabinLight(10);
        bus.screen(null);
        busWalls(true);
        allowBusDismount = true;
        p.leaveVehicle();
        allowBusDismount = false;
        fx.send(p, "cabin off");
        Location was = p.getLocation();
        p.teleport(new Location(world, busX + Bus.SEAT[0], ParkPlan.FLOOR_STATION, ParkPlan.BUS_Z + Bus.SEAT[2], was.getYaw(), was.getPitch()));
        fx.send(p, "untag letter");
        sendTags(p);
        after(70, () -> driver(p, "drv_9"));
    }

    /** 车停稳以后车身算墙：人只能从右前方的车门下去。 */
    private void busWalls(boolean on) {
        busWalls(ParkPlan.BUS_STOP_X, on);
    }

    private void busWalls(double busAt, boolean on) {
        BlockData state = Bukkit.createBlockData(on ? Material.BARRIER : Material.AIR);
        int y0 = ParkPlan.FLOOR_STATION, rear = (int) Math.floor(busAt + Bus.REAR) - 1, front = (int) Math.floor(busAt + Bus.FRONT);
        int door0 = (int) Math.floor(busAt + Bus.DOOR_X0), door1 = (int) Math.floor(busAt + Bus.DOOR_X1 - 0.01);
        for (int y = y0; y <= y0 + 2; y++) {
            for (int x = rear; x <= front; x++) {
                world.getBlockAt(x, y, 2).setBlockData(state, false);
                if (x < door0 || x > door1) world.getBlockAt(x, y, 5).setBlockData(state, false);
            }
            for (int z = 3; z <= 4; z++) {
                world.getBlockAt(rear, y, z).setBlockData(state, false);
                world.getBlockAt(front, y, z).setBlockData(state, false);
            }
        }
    }

    private void lowerBoom(Player p) {
        boom.setInterpolationDelay(0);
        boom.setInterpolationDuration(24);
        boom.setTransformationMatrix(new Matrix4f().scale(0.12f, 0.12f, 9f));
        p.playSound(new Location(world, ParkPlan.BAY_MOUTH_X, ParkPlan.FLOOR_STATION + 1, 3), "block.iron_trapdoor.close", SoundCategory.MASTER, 1f, 0.5f);
    }

    /** 卷帘门：平时卷在门楣上，人走进大厅就落下来。 */
    private void spawnShutter() {
        int y0 = ParkPlan.FLOOR_STATION;
        double x = ParkPlan.ENTRY_DOOR_X + 0.92;
        int z0 = ParkPlan.ENTRY_DOOR_Z0;
        float w = ParkPlan.ENTRY_DOOR_Z1 + 1 - z0;
        shutterMain = box("minecraft:polished_deepslate", x, y0, z0, 1, 1, 1, -1);
        shutterMain.setTransformation(new Transformation(new Vector3f(0, 3.7f, 0), new AxisAngle4f(), new Vector3f(0.08f, 0.25f, w), new AxisAngle4f()));
        for (int i = 0; i < 7; i++) {
            BlockDisplay slat = box(i == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete", x + 0.09, y0, z0, 1, 1, 1, -1);
            slat.setTransformation(new Transformation(new Vector3f(0, 3.72f + i * 0.03f, 0), new AxisAngle4f(), new Vector3f(0.02f, i == 0 ? 0.12f : 0.05f, w), new AxisAngle4f()));
            shutterSlats.add(slat);
        }
    }

    private void dropShutter(Player p, boolean loud) {
        if (shutterDown) return;
        shutterDown = true;
        int y0 = ParkPlan.FLOOR_STATION, ticks = loud ? 60 : 1;
        BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        for (int y = y0; y <= y0 + 3; y++) {
            for (int z = ParkPlan.ENTRY_DOOR_Z0; z <= ParkPlan.ENTRY_DOOR_Z1; z++) world.getBlockAt(ParkPlan.ENTRY_DOOR_X, y, z).setBlockData(barrier, false);
        }
        float w = ParkPlan.ENTRY_DOOR_Z1 + 1 - ParkPlan.ENTRY_DOOR_Z0;
        shutterMain.setInterpolationDelay(0);
        shutterMain.setInterpolationDuration(ticks);
        shutterMain.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.08f, 3.95f, w), new AxisAngle4f()));
        for (int i = 0; i < shutterSlats.size(); i++) {
            BlockDisplay slat = shutterSlats.get(i);
            slat.setInterpolationDelay(0);
            slat.setInterpolationDuration(ticks);
            slat.setTransformation(new Transformation(new Vector3f(0, i * 0.6f, 0), new AxisAngle4f(), new Vector3f(0.02f, i == 0 ? 0.12f : 0.05f, w), new AxisAngle4f()));
        }
        if (!loud) return;
        fx.play(p, "shutter", "shutter", 1f, 1f, false, 0);
        // 第 3 秒砸到地上，外面的风声一下子没了
        after(60, () -> {
            fx.send(p, "glitch 5");
            fx.gain(p, "amb", 0.015f, 6);
            fx.gain(p, "bus", 0f, 6);
            fx.fog(p, 9, 64, 1f, "0b0506", 1f);
        });
    }

    boolean busHolds(Player p) {
        if (allowBusDismount) return false;
        return bus != null && bus.hasRider(p);
    }

    /** 807 号车顺着断缆往下滑的时候按蹲键下不了车：车悬在井里，下去就掉进井里了。到底了自己放人下来。 */
    boolean carHolds(Player p) {
        return carRiding && car807 != null && car807.root != null && car807.root.getPassengers().contains(p);
    }

    /** 车和路灯的光：车里的顶灯，往前照的车灯；路灯经过的时候，光从车窗扫进来。 */
    void addLights(java.util.Map<Long, Integer> desired) {
        // 入场片和收尾：镜头带着一盏主光走，像拍片子的灯，不然地底下黑得什么都看不见
        // 收尾：水里那台对讲机的录音灯照着一小圈水面
        if (replayOn && player != null) {
            // 老周的头灯：跟着镜头走，往前照一道
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection();
            desired.merge(Lights.key(eye.getBlockX(), eye.getBlockY(), eye.getBlockZ()), 10, Math::max);
            for (int k : new int[]{2, 4, 7}) {
                Location ahead = eye.clone().add(dir.clone().multiply(k));
                desired.merge(Lights.key(ahead.getBlockX(), ahead.getBlockY(), ahead.getBlockZ()), k == 2 ? 13 : k == 4 ? 12 : 10, Math::max);
            }
            // 另外几个队员的头灯：照在他们前面一点，不打在自己身上（橙衣服一照就发亮）
            for (ItemDisplay d : rescuers) {
                if (!d.isValid()) continue;
                Location at = d.getLocation();
                org.bukkit.util.Vector f = at.getDirection().setY(0).normalize().multiply(1.8);
                desired.merge(Lights.key((int) Math.floor(at.getX() + f.getX()), at.getBlockY(), (int) Math.floor(at.getZ() + f.getZ())), 7, Math::max);
            }
        }
        if (walkieAt != null && walkieState >= 1) desired.merge(Lights.key((int) Math.floor(walkieAt[0]), (int) Math.floor(walkieAt[1]) + 1, (int) Math.floor(walkieAt[2])), 5, Math::max);
        if (cineOn && player != null) {
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection();
            desired.merge(Lights.key(eye.getBlockX(), eye.getBlockY(), eye.getBlockZ()), 12, Math::max);
            for (int k : new int[]{4, 9}) {
                Location ahead = eye.clone().add(dir.clone().multiply(k));
                desired.merge(Lights.key(ahead.getBlockX(), ahead.getBlockY(), ahead.getBlockZ()), k == 4 ? 13 : 11, Math::max);
            }
        }
        if (shutterLit) desired.merge(Lights.key(-10, ParkPlan.FLOOR_STATION + 2, 6), 9, Math::max);
        wellLights(desired);
        if (bus == null) return;
        int y0 = ParkPlan.FLOOR_STATION, x = (int) Math.floor(busX), z = (int) Math.floor(ParkPlan.BUS_Z);
        boolean sweep = (busStage == 4 || busStage == 8) && Math.floorMod(x + 616, 38) < 9;
        int inside = busStage == 2 || busStage == 5 || busStage == 6 ? 10 : busStage >= 3 && busStage <= 4 || busStage == 8 ? 7 : 4;
        desired.merge(Lights.key(x + 2, y0 + 2, z), sweep ? 13 : inside, Math::max);
        desired.merge(Lights.key(x - 2, y0 + 2, z), inside, Math::max);
        if (busStage == 2 || busStage == 5 || busStage == 6) return;
        desired.merge(Lights.key(x + 9, y0 + 1, z), 13, Math::max);
        desired.merge(Lights.key(x + 17, y0 + 1, z), 11, Math::max);
    }

    /** 入园大厅里会变的东西：桌上的装备、顶灯、闸机臂。 */
    private void spawnEntry() {
        int y0 = ParkPlan.FLOOR_STATION;
        for (int x : ParkPlan.ENTRY_LAMPS) {
            world.getBlockAt(x, y0 + 4, 5).setType(Material.AIR, false);
            entryLamps.add(box("minecraft:light_gray_concrete", x - 1.3, y0 + 5.84, 5.2, 2.6, 0.14, 0.6, -1));
        }
        BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        for (int z = ParkPlan.ENTRY_GATE_Z0; z <= ParkPlan.ENTRY_GATE_Z1; z++) {
            for (int y = y0; y <= y0 + 2; y++) world.getBlockAt(ParkPlan.ENTRY_GATE_X, y, z).setBlockData(barrier, false);
            gateArms.add(box("minecraft:iron_block", ParkPlan.ENTRY_GATE_X + 0.5, y0 + 0.85, z + 0.07, 0.05, 0.05, 0.86, -1));
        }
        park.tram.power(false);
        spawnShutter();
        for (int y = y0; y <= y0 + 3; y++) {
            for (int z = ParkPlan.ENTRY_DOOR_Z0; z <= ParkPlan.ENTRY_DOOR_Z1; z++) world.getBlockAt(ParkPlan.ENTRY_DOOR_X, y, z).setType(Material.AIR, false);
        }
        busWalls(false);
        spawnCallButton();
        boom = box("minecraft:yellow_concrete", ParkPlan.BAY_MOUTH_X + 0.4, y0 + 1.05, 0.5, 1, 1, 1, -1);
        boom.setTransformationMatrix(new Matrix4f().rotateX((float) (-Math.PI / 2)).scale(0.12f, 0.12f, 9f));
        layKit(this::takeKit);
    }

    /** 手电和手机摆在桌上，朝着进门的这一头。推迟的结局里再摆一回，给下一个。 */
    private void layKit(java.util.function.Consumer<Player> onClick) {
        int y0 = ParkPlan.FLOOR_STATION;
        kit.forEach(Entity::remove);
        kit.clear();
        ItemDisplay torch = world.spawn(new Location(world, -27.3, y0 + 0.97, 5.0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(park.flashlightStack());
            d.setShadowRadius(0);
            d.setBrightness(new Display.Brightness(13, 0));
            d.setTransformationMatrix(new Matrix4f().rotateY(0.5f).rotateX((float) (Math.PI / 2)).scale(0.7f));
        });
        scene.add(torch);
        kit.add(torch);
        kit.add(box("minecraft:black_concrete", -27.45, y0 + 0.925, 5.65, 0.14, 0.025, 0.26, -1));
        kit.add(box("minecraft:lime_concrete", -27.43, y0 + 0.951, 5.7, 0.1, 0.003, 0.1, 9));
        Interaction hit = interaction(-27.35, y0 + 0.85, 5.3, 1.3f, 0.6f);
        kit.add(hit);
        park.onClick(hit, onClick);
    }

    private void takeKit(Player p) {
        if (kitTaken) return;
        kitTaken = true;
        kit.forEach(Entity::remove);
        kit.clear();
        p.getInventory().setItem(0, park.flashlightStack());
        p.getInventory().setHeldItemSlot(0);
        sound(p, "item.armor.equip_chain", 0.8f, 1.1f);
        park.flashlightOn(p);
        fx.send(p, "phone on");
        dropTag(p, "kit");
        after(30, () -> welcomeSms(p));
        after(110, () -> wakeHall(p));
    }

    /** 广播醒过来，灯一盏盏亮，最后闸机自己开。 */
    private void wakeHall(Player p) {
        fx.pa(p, "pa_open");
        for (int i = 0; i < entryLamps.size(); i++) {
            int at = 50 + i * 28, index = i;
            after(at, () -> entryLamp(p, index, true));
            after(at + 3, () -> entryLamp(p, index, false));
            after(at + 7, () -> entryLamp(p, index, true));
        }
        after(50 + entryLamps.size() * 28 + 20, () -> openEntryGate(p, true));
    }

    private void entryLamp(Player p, int index, boolean on) {
        int y0 = ParkPlan.FLOOR_STATION, x = ParkPlan.ENTRY_LAMPS[index];
        BlockDisplay lamp = entryLamps.get(index);
        lamp.setBlock(Bukkit.createBlockData(on ? "minecraft:ochre_froglight" : "minecraft:light_gray_concrete"));
        lamp.setBrightness(on ? new Display.Brightness(15, 15) : null);
        if (on) world.getBlockAt(x, y0 + 4, 5).setBlockData(Bukkit.createBlockData("minecraft:light[level=13]"), false);
        else world.getBlockAt(x, y0 + 4, 5).setType(Material.AIR, false);
        if (p != null) p.playSound(new Location(world, x, y0 + 5, 5), "block.copper_bulb.turn_on", SoundCategory.MASTER, on ? 1f : 0.5f, 0.7f);
    }

    private void openEntryGate(Player p, boolean loud) {
        if (gateOpened) return;
        gateOpened = true;
        int y0 = ParkPlan.FLOOR_STATION;
        for (int z = ParkPlan.ENTRY_GATE_Z0; z <= ParkPlan.ENTRY_GATE_Z1; z++) {
            for (int y = y0; y <= y0 + 2; y++) world.getBlockAt(ParkPlan.ENTRY_GATE_X, y, z).setType(Material.AIR, false);
        }
        for (BlockDisplay arm : gateArms) {
            arm.setInterpolationDelay(0);
            arm.setInterpolationDuration(8);
            arm.setTransformationMatrix(new Matrix4f().rotateX((float) (Math.PI / 2)).scale(0.05f, 0.05f, 0.8f));
        }
        entryCounter = world.spawn(new Location(world, ParkPlan.ENTRY_GATE_X - 0.15, y0 + 2.45, 5.5, 90f, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBackgroundColor(Color.fromARGB(255, 16, 10, 10));
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        scene.add(entryCounter);
        counter(1241);
        if (loud && p != null) after(50, () -> parkSms(p));
        if (loud && p != null) {
            Location at = new Location(world, ParkPlan.ENTRY_GATE_X, y0 + 1, 5);
            p.playSound(at, "block.iron_trapdoor.open", SoundCategory.MASTER, 1f, 0.6f);
            p.playSound(at, "block.piston.contract", SoundCategory.MASTER, 0.5f, 0.7f);
        }
    }

    private void counter(int n) {
        if (entryCounter != null) entryCounter.text(Component.text("今日入园  " + n).color(TextColor.color(0xff4a32)));
    }

    /** 过闸机计一个数，上站台断电的车闪两下亮起来。 */
    private void openingTick(Player p, Location at) {
        if (at.getY() < 222 || at.getZ() > 250) return;
        if (!shutterDown && busStage >= 5 && at.getX() > ParkPlan.ENTRY_DOOR_X + 1.6) dropShutter(p, true);
        if (gateOpened && !counted && at.getX() > ParkPlan.ENTRY_GATE_X + 1.2) {
            counted = true;
            counter(1242);
            sound(p, "block.note_block.bit", 0.7f, 1.4f);
            after(16, () -> fx.pa(p, "pa_count"));
        }
        if (!onPlatform && at.getX() > -15.3) {
            onPlatform = true;
            introDone = true;
            boardAt = age + 900;
            fx.gain(p, "amb", 0.14f, 60);
            after(24, () -> tramPower(p, true));
            after(28, () -> tramPower(p, false));
            after(34, () -> {
                tramPower(p, true);
                park.ride.parkedLight(13);
            });
        }
    }

    private void tramPower(Player p, boolean on) {
        park.tram.power(on);
        if (p != null && on) p.playSound(new Location(world, -4, ParkPlan.FLOOR_STATION + 2, 0), "block.copper_bulb.turn_on", SoundCategory.MASTER, 1f, 0.8f);
    }

    /** 导演栏和跳段：不演了，把开场该有的结果都给上。 */
    void finishOpening(Player p) {
        if (p != null && cineOn) endCine(p);
        if (onPlatform || p == null) return;
        tasks.clear();
        fx.send(p, "clear");
        fx.send(p, "cabin off");
        fx.black(p, 0f, 1);
        p.setWalkSpeed(0.2f);
        if (bus != null) {
            allowBusDismount = true;
            p.leaveVehicle();
            allowBusDismount = false;
            bus.remove();
            bus = null;
        }
        busWalls(false);
        busStage = 7;
        fx.gain(p, "bus", 0f, 1);
        fx.gain(p, "tv", 0f, 1);
        fx.gain(p, "busradio", 0f, 1);
        fx.send(p, "untag call");
        fx.send(p, "untag door");
        dropShutter(p, false);
        fx.fog(p, 9, 64, 1f, "0b0506", 1f);
        if (!boomDown) {
            boomDown = true;
            boom.setTransformationMatrix(new Matrix4f().scale(0.12f, 0.12f, 9f));
        }
        if (!kitTaken) {
            kitTaken = true;
            kit.forEach(Entity::remove);
            kit.clear();
            p.getInventory().setItem(0, park.flashlightStack());
            p.getInventory().setHeldItemSlot(0);
            park.flashlightOn(p);
        }
        fx.send(p, "phone on");
        sendTags(p);
        for (int i = 0; i < entryLamps.size(); i++) entryLamp(null, i, true);
        openEntryGate(p, false);
        counted = onPlatform = introDone = true;
        counter(1242);
        boardAt = age + 900;
        fx.gain(p, "amb", 0.14f, 20);
        tramPower(null, true);
        park.ride.parkedLight(13);
    }

    /** 测试和重录用：跳过开场，直接站到站台上。 */
    void skipIntro() {
        if (player == null) return;
        boolean first = !onPlatform;
        finishOpening(player);
        player.teleport(new Location(world, ParkPlan.SPAWN[0], ParkPlan.SPAWN[1], ParkPlan.SPAWN[2], -90, 4));
        if (first) {
            after(60, () -> welcomeSms(player));
            after(160, () -> parkSms(player));
        }
    }

    /** 领了套件，公司先发一条：地面上还有信号。 */
    private void welcomeSms(Player p) {
        fx.sms(p, "二叠纪公司", "", "体验官 #0011：外勤评估开始。请沿原游览路线走完全程，每站打分。下去以后手机可能没有信号。");
    }

    /** 闸机自己开了以后，关了十九年的公园给你发第一条短信。从这条起，园区短信都是它发的。 */
    private void parkSms(Player p) {
        parkSmsAt = clock();
        fx.parkSms(p, "欢迎光临息壤国家公园。每一站的体验项目（站内留存的文件、物品）全部查看后，前往下一站的通道才会开放。祝您体验愉快。");
    }

    // ---------- 每 tick ----------

    void tick(Player p) {
        age++;
        walkieTick();
        if (p == null) return;
        player = p;
        for (Task task : new ArrayList<>(tasks)) {
            if (task.at <= age && tasks.remove(task)) task.run.run();
        }
        Location at = p.getLocation();
        // 放入场片和收尾的时候，人是一台摆来摆去的摄影机：不算走到了哪，不触发任何机关；只有镜子照常照
        if (cineOn) {
            zone = zoneOf(at);
            return;
        }
        String now = zoneOf(at);
        if (!now.equals(zone)) {
            zone = now;
            enterZone(p, now);
        }
        rideTick(p);
        busTick(p, at);
        openingTick(p, at);
        if (introDone && !boardSaid && age > boardAt && now.equals("station")) {
            boardSaid = true;
            fx.pa(p, "pa_board");
        }
        windowTick(p);
        blindTick();
        poolTick(p, at);
        burrowTick();
        forestTick(p, at);
        cabTick(p, at);
        wellTick(p, at);
    }

    /** 被肉夹住停车：不能干停着，弹一张临时停车的通告，广播说一句；松开了再告诉一声。 */
    /**
     * 缆车下行这一段：车上自带的导览机（2007 年的磁带）按车开到哪里放，讲公园是什么。跳段也对得上位置。
     * 2007 年 7 月 4 日下午的无线电不会自己响：要捡起地上那盘行车录音带，放进驾驶台的录音机。
     * 距离、是谁、哪一句、只在夹停以后放。
     */
    private static final Object[][] RIDE_BEATS = {
            {4.0, "车载广播", "guide_1", false}, {16.0, "车载广播", "guide_2", false}, {40.0, "车载广播", "guide_3", false},
            {124.0, "车载广播", "guide_4", false}, {175.0, "车载广播", "guide_5", false}, {228.0, "车载广播", "guide_6", false}};
    private final java.util.Set<String> rideBeats = new java.util.HashSet<>();
    /**
     * 剪掉的路段：车走到前一个数，黑一下（像录像带跳了一段），睁眼已经在后一个数。
     * 每一刀都落在下一句话前面一点，整段从四分钟缩到三分钟以内，要紧的话一句不少。
     */
    private static final double[][] RIDE_CUTS = {{29, 44}, {82, 88}, {108, 123}, {143, 172}, {191, 226}};
    private final java.util.Set<Integer> rideCuts = new java.util.HashSet<>();

    private void rideLine(Player p, String who, String clip) {
        fx.subtitle(p, who, clip);
        fx.play(p, who.equals("车载广播") ? "tramguide" : "tramradio", clip, 0.95f, 1f, false, 0);
    }

    private void rideTick(Player p) {
        RideProfile ride = park.ride.profile;
        if (ride == null) return;
        // 跳段直接从半路开始：前面那些不补放
        if (rideBeats.isEmpty() && ride.d > 3) {
            for (Object[] b : RIDE_BEATS) if ((double) b[0] < ride.d - 2) rideBeats.add((String) b[2]);
            for (int i = 0; i < RIDE_CUTS.length; i++) if (RIDE_CUTS[i][0] < ride.d - 2) rideCuts.add(i);
        }
        for (int i = 0; i < RIDE_CUTS.length; i++) {
            if (ride.d < RIDE_CUTS[i][0] || ride.holding() || !rideCuts.add(i)) continue;
            double to = RIDE_CUTS[i][1];
            fx.black(p, 1f, 5);
            after(3, () -> fx.send(p, "glitch 5"));
            after(6, () -> park.ride.cutTo(to));
            after(10, () -> fx.black(p, 0f, 10));
        }
        for (Object[] b : RIDE_BEATS) {
            String clip = (String) b[2];
            if (ride.d < (double) b[0] || rideBeats.contains(clip)) continue;
            if ((boolean) b[3] && (!ride.squeezed || ride.holding())) continue;
            rideBeats.add(clip);
            rideLine(p, (String) b[1], clip);
            if (clip.equals("guide_5")) {
                // 拍照点：闪一下
                after(130, () -> {
                    photoCam(p);
                    fx.send(p, "flash");
                    sound(p, "block.note_block.hat", 1f, 1.8f);
                    sound(p, "entity.firework_rocket.twinkle_far", 0.4f, 1.6f);
                });
            }
        }
        if (ride.holding() && holdNotice == 0) {
            holdNotice = 1;
            fx.send(p, "glitch 10");
            after(10, () -> fx.pa(p, "pa_hold"));
        } else if (!ride.holding() && holdNotice == 1 && ride.squeezed) {
            holdNotice = 2;
            // 广播和无线电已经说了，不再发短信
        }
    }

    private String zoneOf(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (z > 250) return "new";
        if (park.ride.state == Ride.State.RUNNING || park.ride.state == Ride.State.PINNED) {
            double d = park.ride.distance();
            if (park.ride.profile == null) return "cavity";
            return d < park.ride.profile.dMouth - 8 ? "out" : d < park.ride.profile.dThroatEnd ? "throat" : "cavity";
        }
        // 深井（第四关）和井底那座真楼（第五关）
        if (Well.inWell(x, y, z)) return y < Well.REAL_FLOOR + 12 && Math.hypot(x - (ParkPlan.DRUM_X + Well.DX), z) < ParkPlan.DRUM_R + 1 || y < Well.REAL_FLOOR - 1 ? "real" : "well";
        if (x >= Lower.BURROW_X0 - 1 && x < Lower.BURROW_X1 + 1 && y > 150 && z < -40 && Math.abs(z + 65.5) < 1.6) return "burrow";
        if (x >= 260) return "forest";
        if (y < 29 && x >= 181) return "pools";
        if (y < 29 && x >= Lower.LOCKER_X0) return "lockers";
        if (y < 29 && x >= Lower.TUNNEL_X0) return "tunnel";
        if (y < 29) return "garden";
        if (y >= 222 && x < 20) return "station";
        if (x >= 92 && y < 50) return x < ParkPlan.HX0 ? "dock" : "hall";
        return "cavity";
    }

    /** 进每个区域弹一张公告：语气是景区的客服，内容不对劲。一个区域只弹一次。 */
    /**
     * 每到一段来一条短信。发件人是 2007 年那套园区短信系统，现在跑它的是息壤：
     * 写得客客气气，就是有几个字坏了。
     */
    private void zoneNotice(Player p, String z) {
        // 手机上只有园方的系统短信，一站最多一条。2007 年别人的短信（妈妈、调度、未知号码）都去掉了：
        // 玩家是体验官，收到写给“小敏”的短信会以为自己是她
        String[][] texts = switch (z) {
            case "hall" -> new String[][]{{"息壤国家公园", "", "欢迎来到下游客中心。本建筑 2007 年损毁，现已{码:自然}修复，请放心游览。", "60"}};
            case "lockers" -> new String[][]{{"息壤国家公园", "", "温泉开放前请存放全部个人{码:物品}。", "60"}};
            case "burrow" -> new String[][]{{"息壤国家公园", "", "特别体验：未开发腺泡。请匍匐前进，途中请勿回头。途中请勿回头。", "36"}};
            case "forest" -> new String[][]{{"息壤国家公园", "", "支气管森林步道已开放。请沿栈道和系绳行走。", "60"}};
            default -> null;
        };
        if (texts == null || !noticed.add(z)) return;
        for (String[] t : texts) after(Integer.parseInt(t[3]), () -> fx.parkSms(p, t[2]));
    }

    private void enterZone(Player p, String z) {
        if (!z.equals("pools")) fx.send(p, "warm 0 40");
        if (z.equals("pools") && !crawled) fx.send(p, "tag hole|" + Lower.HOLE[0] + "|" + (Lower.HOLE[1] + 1.5) + "|" + (Lower.HOLE[2] + 0.8) + "|0|未开发腺泡 · 入口");
        fx.send(p, "zone " + z);
        fx.send(p, "depth " + (z.equals("out") || z.equals("throat") || z.equals("cavity") ? 1 : 0));
        fx.send(p, "ember " + (z.equals("cavity") ? 1 : z.equals("throat") || z.equals("forest") || z.equals("out") ? 0.6 : z.equals("station") ? 0.4 : z.equals("well") ? 0.5 : z.equals("real") ? 0.2 : 0));
        zoneNotice(p, z);
        switch (z) {
            case "station" -> {
                if (shutterDown) {
                    fx.fog(p, 9, 64, 1f, "0b0506", 1f);
                    amb(p, "wind_cold", 0.14f);
                } else {
                    roadAir(p);
                }
                mus(p, "", 0);
            }
            case "out" -> {
                fx.fog(p, 10, 80, 1f, "0d0405", 1f);
                amb(p, "wind_cold", 0.3f);
            }
            case "throat" -> {
                fx.fog(p, 3, 40, 1f, "100303", 1f);
                amb(p, "drone", 0.22f);
            }
            case "cavity" -> {
                fx.fog(p, 7, 120, 1f, "0e0303", 1f);
                amb(p, "drone", 0.5f);
            }
            case "dock" -> {
                fx.fog(p, 14, 70, 1f, "0e0806", 1f);
                amb(p, "drone", 0.18f);
                if (!lightsOut) mus(p, "mall", 0.14f);
            }
            case "hall" -> {
                fx.fog(p, lightsOut ? 6 : 14, lightsOut ? 34 : 70, 1f, lightsOut ? "080404" : "0e0806", 1f);
                amb(p, "drone", 0.1f);
                if (!lightsOut) mus(p, "mall", 0.42f);
                if (!welcomed && !lightsOut) {
                    welcomed = true;
                    fx.pa(p, "pa_welcome");
                }
            }
            case "garden" -> {
                fx.fog(p, 6, 40, 1f, "0a0707", 1f);
                amb(p, "drips", 0.5f);
                mus(p, "", 0);
            }
            case "tunnel" -> {
                fx.fog(p, 4, 26, 1f, "0c0807", 1f);
                amb(p, "drips", 0.3f);
                mus(p, "spa", 0.12f);
            }
            case "lockers" -> {
                fx.fog(p, 5, 30, 1f, "0c0807", 1f);
                amb(p, "drips", 0.15f);
                mus(p, "spa", 0.25f);
                if (!lockerSaid) {
                    lockerSaid = true;
                    after(30, () -> fx.pa(p, "pa_locker"));
                }
            }
            case "pools" -> {
                fx.fog(p, 7, 42, 1f, "140506", 1f);
                amb(p, "", 0);
            }
            case "burrow" -> {
                fx.fog(p, 1, 9, 1f, "0a0202", 1f);
                amb(p, "drone", 0.3f);
                mus(p, "", 0);
            }
            case "forest" -> {
                if (exhaled) {
                    fx.fog(p, EXHALE_FOG[0], EXHALE_FOG[1], 1f, EXHALE_TINT, 1f);
                } else {
                    fx.fog(p, 6, 70, 1f, "0a0405", 1f);
                    amb(p, "wind_cold", 0.26f);
                }
                mus(p, "", 0);
            }
            case "new" -> {
                fx.fog(p, 30, 120, 1f, "4a4238", 1f);
                amb(p, "", 0);
            }
            case "well", "real" -> wellAir(p, z);
            default -> {
            }
        }
    }

    private void amb(Player p, String clip, float gain) {
        if (clip.equals(ambient)) {
            if (!clip.isEmpty()) fx.gain(p, "amb", gain, 40);
            return;
        }
        ambient = clip;
        if (clip.isEmpty()) fx.gain(p, "amb", 0, 40);
        else fx.play(p, "amb", clip, gain, 1f, true, 40);
    }

    private void mus(Player p, String clip, float gain) {
        if (clip.equals(music)) {
            if (!clip.isEmpty()) fx.gain(p, "music", gain, 40);
            return;
        }
        music = clip;
        if (clip.isEmpty()) fx.gain(p, "music", 0, 40);
        else fx.play(p, "music", clip, gain, 1f, true, 40);
    }

    private void sound(Player p, String key, float volume, float pitch) {
        p.playSound(p.getLocation(), key, SoundCategory.MASTER, volume, pitch);
    }

    // ---------- 打分 ----------

    /** 一星到五星的回应。3 星最冷：沉默两秒，只说已记录。 */
    /**
     * 打分是这个公园在学习。一开始怎么打都行；往后打低了它会反问，再打低，低分的按钮就没了。
     * 你第一次按的分记在 asked 里，最后的报告会当着你的面把它“修正”掉。
     */
    private boolean gate(int index, Kiosk kiosk, Player p, int stars) {
        if (asked[index] == 0) asked[index] = stars;
        int times = ++presses[index];
        if (index < 2) return true;
        int need = index == 2 ? 3 : index == 5 ? 5 : 4;
        if (stars >= need) return true;
        fx.send(p, "glitch 10");
        if (times == 1) {
            kiosk.say("您确定吗？");
            fx.pa(p, "pa_sure");
            return false;
        }
        if (index == 2) return true;
        if (times == 2) {
            kiosk.say("请重新考虑。");
            for (int s = 1; s <= stars; s++) kiosk.killButton(s);
            fx.pa(p, "pa_reconsider");
            return false;
        }
        kiosk.say("我们会改进。");
        for (int s = 1; s <= stars; s++) kiosk.killButton(s);
        fx.pa(p, "pa_improve");
        return false;
    }

    private void rated(int index, Player p, int stars) {
        scores[index] = stars;
        scoreAt[index] = clock();
        fx.send(p, "stamp " + stars + "|" + Math.round(average() * 20));
        sound(p, "block.note_block.chime", 0.7f, 1.2f);
        boolean good = stars >= 4, bad = stars <= 2;
        if (good) fx.pa(p, index == 0 ? "pa_thanks" : "pa_thanks2");
        else if (bad) after(10, () -> fx.pa(p, "pa_improve"));
        else after(40, () -> fx.pa(p, "pa_recorded"));
        switch (index) {
            case 0 -> {
                if (good) park.ride.steadyLamp = true;
                if (bad) after(30, () -> dropShutter(p));
                after(good ? 90 : 70, () -> {
                    boardSaid = true;
                    fx.pa(p, "pa_board");
                });
            }
            case 1 -> {
                if (good) after(40, () -> fx.pa(p, "pa_next_tram"));
                if (bad) after(30, () -> tramBack(p));
            }
            case 2 -> {
                if (good) after(10, () -> park.ring(3, true));
                if (bad) after(24, () -> {
                    park.ring(2, false);
                    sound(p, "block.lever.click", 1f, 0.6f);
                });
            }
            case 3 -> {
                // 先评分，再看窗。打完分卷帘才升起来
                if (!revealed) {
                    windowRated = true;
                    after(20, () -> {
                        fx.pa(p, "pa_window");
                        reveal(p, true);
                    });
                    return;
                }
                if (bad) after(30, () -> pressAgainstGlass(p));
                after(bad ? 110 : 60, () -> lightsOut(p));
            }
            case 4 -> {
                if (good) {
                    fx.send(p, "warm 1.0 60");
                    fx.gain(p, "music", 0.5f, 60);
                }
                if (bad) rowTimer = 1;
            }
            case 5 -> {
                if (good) {
                    after(20, () -> {
                        for (Entity e : scene) {
                            if (e instanceof BlockDisplay d && d.getBlock().getMaterial() == Material.ORANGE_CONCRETE && d.getLocation().getX() > 290) d.setBrightness(new Display.Brightness(13, 0));
                        }
                    });
                }
                if (bad) {
                    after(20, () -> {
                        // 系绳被扯断一段：栈道还在，照着手电沿栈道走
                        int from = tetherParts.size() * 2 / 5, to = tetherParts.size() * 4 / 5;
                        for (int i = from; i < to; i++) tetherParts.get(i).remove();
                        sound(p, "entity.leash_knot.break", 1f, 0.6f);
                    });
                }
            }
            default -> {
            }
        }
    }

    /**
     * 第一站打了差评：回程的紧急闸门放下来。先是警示灯闪、蜂鸣器响，然后卷帘慢慢落下来，砸到地上，
     * 帘子上亮出“回程通道已关闭”，肉从帘子底下一点点挤出来。
     */
    private void dropShutter(Player p) {
        int y = ParkPlan.FLOOR_STATION;
        Location lamp = new Location(world, -10.2, y + 4.5, 2.95);
        for (int i = 0; i < 26; i++) {
            int k = i;
            after(i * 5, () -> {
                if (stationLamp == null || !stationLamp.isValid()) return;
                stationLamp.setBrightness(new Display.Brightness(k % 2 == 0 ? 15 : 2, 0));
                if (k % 2 == 0 && k < 14) world.playSound(lamp, "block.note_block.bass", SoundCategory.MASTER, 0.9f, 0.5f);
            });
        }
        after(56, () -> {
            fx.play(p, "shutter", "shutter", 1f, 1f, false, 0);
            for (BlockDisplay part : new BlockDisplay[]{shutter, shutterBar}) {
                part.setInterpolationDelay(0);
                part.setInterpolationDuration(70);
            }
            shutter.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.06f, 3.95f, 6.0f), new AxisAngle4f()));
            shutterBar.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.1f, 0.06f, 6.0f), new AxisAngle4f()));
        });
        after(126, () -> {
            sound(p, "block.anvil.land", 0.7f, 0.5f);
            fx.send(p, "glitch 6");
            // 警示灯不闪了，换成常亮的红灯，把帘子和底下挤出来的肉照出来
            if (stationLamp != null && stationLamp.isValid()) {
                stationLamp.setBlock(Bukkit.createBlockData("minecraft:red_concrete"));
                stationLamp.setBrightness(new Display.Brightness(15, 0));
            }
            shutterLit = true;
            BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
            for (int yy = y; yy <= y + 3; yy++) for (int z = 3; z <= 8; z++) world.getBlockAt(-11, yy, z).setBlockData(barrier, false);
            TextDisplay closed = world.spawn(new Location(world, -10.47, y + 1.7, 6.0, -90f, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.text(Component.text("回程通道已关闭\n我们会改进").color(TextColor.color(0xf4ecd8)));
                d.setBackgroundColor(Color.fromARGB(255, 160, 40, 24));
                d.setShadowed(false);
                d.setBrightness(new Display.Brightness(12, 0));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
            });
            scene.add(closed);
            // 肉从帘子底下一点点挤出来
            for (int i = 0; i < 7; i++) {
                ItemDisplay lump = item(i % 2 == 0 ? "flesh_lump" : "flesh_lump_dark", -10.4, y + 0.1, 3.4 + i * 0.8, 0f);
                lump.setTransformationMatrix(new Matrix4f().scale(0.01f));
                int k = i;
                after(20 + k * 12, () -> {
                    lump.setInterpolationDelay(0);
                    lump.setInterpolationDuration(80);
                    lump.setTransformationMatrix(new Matrix4f().rotateY((float) (Math.PI / 2)).scale(1.0f, 0.34f, 0.34f));
                    world.playSound(lump.getLocation(), "entity.slime.squish", SoundCategory.MASTER, 0.7f, 0.5f + k * 0.03f);
                });
            }
        });
    }

    private void tramBack(Player p) {
        park.tram.lamp(0);
        sound(p, "block.lever.click", 1f, 0.5f);
        double[] q = new double[3];
        for (int i = 1; i <= 240; i++) {
            int k = i;
            after(i, () -> {
                plan.route.at(plan.route.length - k * 0.12, q);
                park.tram.moveTo(q[0], q[1], q[2]);
            });
        }
    }

    // ---------- 大厅 ----------

    void onDepart() {
        rideBeats.clear();
        rideCuts.clear();
        if (player != null) fx.pa(player, "pa_depart");
    }

    void onArrive() {
    }

    private void openKitchen(Player p) {
        door.setInterpolationDelay(0);
        door.setInterpolationDuration(14);
        door.setTransformationMatrix(new Matrix4f().rotateY((float) (Math.PI / 2 * 1.1)).scale(1, 2.05f, 0.06f));
        sound(p, "block.iron_door.open", 0.8f, 0.7f);
        after(10, () -> {
            sound(p, "block.mud.step", 1f, 0.5f);
            sound(p, "entity.slime.squish", 0.8f, 0.5f);
            if (!lightsOut) fx.gain(p, "music", 0.12f, 12);
            after(160, () -> {
                if (!lightsOut && zone.equals("hall")) fx.gain(p, "music", 0.42f, 60);
            });
        });
    }

    private boolean lookingAt(Player p, double x, double y, double z, double reach, double dot) {
        Location eye = p.getEyeLocation();
        Vector to = new Vector(x - eye.getX(), y - eye.getY(), z - eye.getZ());
        if (to.length() > reach) return false;
        return to.normalize().dot(eye.getDirection()) > dot;
    }

    private void windowTick(Player p) {
        if (revealed || !zone.equals("hall") || p.getEyeLocation().getZ() < 1) return;
        if (lookingAt(p, ParkPlan.FIGURE_X, ParkPlan.FLOOR_HALL + 2, ParkPlan.FIGURE_Z, 18, 0.82)) {
            if (lookTimer < 0) lookTimer = 500 + rnd.nextInt(200);
        }
        // 盯着关着的卷帘看久了，它会自己升起来一点
        if (lookTimer > 0 && --lookTimer == 0) reveal(p, false);
    }

    /** 卷帘升起来：先听见电机，帘子一条一条往上卷，后面是贴在玻璃上的肉，洞里站着它。rated 为真是打了分以后升的。 */
    private void reveal(Player p, boolean rated) {
        if (revealed) return;
        revealed = true;
        showFigure(p, "fig_tourist_1_raised", ParkPlan.FIGURE_X, ParkPlan.FIGURE_Z);
        blindSign.remove();
        sound(p, "block.piston.extend", 0.8f, 0.5f);
        fx.play(p, "sfx", "wiper", 0.5f, 0.6f, false, 0);
        blindTarget = rated ? 1 : 0.55;
        after(14, () -> sound(p, "block.chain.place", 1f, 0.5f));
        after(40, () -> {
            park.flesh.heave = 1;
            sound(p, "entity.warden.ambient", 1.1f, 0.5f);
            sound(p, "block.glass.step", 1f, 0.5f);
            fx.gain(p, "music", 0f, 3);
            music = "";
        });
        after(70, () -> park.flesh.heave = 0.4);
        after(90, () -> park.flesh.heave = 0);
        int gone = 170 + rnd.nextInt(40);
        after(gone, () -> flashCut = 1);
        after(gone + 8, () -> flashCut = 2);
        after(gone + 12, this::hideFigure);
        after(gone + 16, () -> flashCut = 1);
        after(gone + 22, () -> flashCut = 0);
        after(gone + 28, () -> sound(p, "entity.warden.heartbeat", 1.5f, 0.75f));
        // 安静两秒。手电再暗一下，再亮起来的时候它已经贴在你面前的玻璃上
        after(gone + 62, () -> glassScare(p));
        // 不打分的话也会停电
        after(gone + (rated ? 140 : 800), () -> lightsOut(p));
    }

    /** 惊吓：手电灭一下，再亮的时候它已经不在玻璃后面了，就站在你面前一米多，比你高一头，低着头看你。 */
    void glassScare(Player p) {
        flashCut = 2;
        after(4, () -> {
            Location eye = p.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new org.bukkit.util.Vector(0, 0, 1);
            dir.normalize();
            // 跟前有墙就站近一点。站进墙里这一下就白吓了
            double reach = 1.35;
            while (reach > 0.6 && !eye.clone().add(dir.clone().multiply(reach + 0.25)).getBlock().isPassable()) reach -= 0.15;
            double x = eye.getX() + dir.getX() * reach, z = eye.getZ() + dir.getZ() * reach;
            hideFigure();
            holeFigure = item("fig_cook_2", x, p.getLocation().getY() + 1, z, yawTo(x, z, eye));
            holeFigure.setBrightness(new Display.Brightness(8, 0));
            org.bukkit.Bukkit.getLogger().info(String.format("惊吓：人形在 %.1f %.1f %.1f，人在 %.1f %.1f %.1f，有效 %s", x, p.getLocation().getY() + 1, z, eye.getX(), eye.getY(), eye.getZ(), holeFigure.isValid()));
        });
        after(6, () -> {
            flashCut = 0;
            fx.play(p, "sfx", "scare", 1f, 1f, false, 0);
            fx.send(p, "jolt");
            sound(p, "block.glass.hit", 1.2f, 0.4f);
        });
        after(18, () -> flashCut = 2);
        after(20, this::hideFigure);
        after(28, () -> flashCut = 0);
    }

    /** 打了低分：它贴到玻璃跟前来。 */
    private void pressAgainstGlass(Player p) {
        showFigure(p, "fig_tourist_1_raised", ParkPlan.FIGURE_X - 3.2, 12.62);
        sound(p, "entity.warden.nearby_closer", 1.2f, 0.7f);
        sound(p, "block.glass.hit", 1f, 0.5f);
        park.flesh.heave = 1;
        after(56, () -> flashCut = 2);
        after(60, this::hideFigure);
        after(64, () -> {
            flashCut = 0;
            park.flesh.heave = 0;
        });
    }

    private void blindTick() {
        if (Math.abs(blindOpen - blindTarget) < 1e-4 || slats.isEmpty()) return;
        blindOpen += Math.signum(blindTarget - blindOpen) * Math.min(Math.abs(blindTarget - blindOpen), 0.008);
        double top = ParkPlan.FLOOR_HALL + BLIND_BOTTOM + SLATS * SLAT;
        for (int i = 0; i < slats.size(); i++) {
            double rest = ParkPlan.FLOOR_HALL + BLIND_BOTTOM + i * SLAT;
            // 下面的板先走，走到顶就卷进盒子里
            double y = Math.min(top - 0.05, rest + blindOpen * (top - ParkPlan.FLOOR_HALL - BLIND_BOTTOM));
            BlockDisplay s = slats.get(i);
            s.teleport(new Location(world, BLIND_X0, y, 11.74));
            boolean rolled = y >= top - 0.06;
            s.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) (BLIND_X1 - BLIND_X0), rolled ? 0.001f : (float) (SLAT - 0.035), 0.05f), new AxisAngle4f()));
        }
    }

    private void showFigure(Player p, String model, double x, double z) {
        hideFigure();
        holeFigure = item(model, x, ParkPlan.FLOOR_HALL + 1, z, 180f);
    }

    private void hideFigure() {
        if (holeFigure != null) holeFigure.remove();
        holeFigure = null;
    }

    /** 测试用：让人形站着。 */
    void poseFigure() {
        showFigure(player, "fig_tourist_1_raised", ParkPlan.FIGURE_X, ParkPlan.FIGURE_Z);
        blindTarget = 1;
    }

    private void lightsOut(Player p) {
        if (lightsOut) return;
        lightsOut = true;
        fx.pitch(p, "music", 0.55f, 80);
        if (music.isEmpty()) fx.play(p, "music", "mall", 0.3f, 0.8f, true, 10);
        music = "mall";
        park.ring(3, false);
        park.ring(2, false);
        sound(p, "block.lever.click", 1f, 0.5f);
        after(30, () -> {
            park.ring(1, false);
            sound(p, "block.lever.click", 1f, 0.45f);
        });
        after(45, () -> {
            fx.send(p, "audio play voice pa_stopped 1.0 0.86 0 0");
            fx.subtitle(p, "广播", "pa_stopped");
            fx.send(p, "glitch 14");
        });
        after(60, () -> {
            park.ring(0, false);
            sound(p, "block.lever.click", 1f, 0.4f);
            fx.fog(p, 6, 34, 1f, "080404", 1f);
        });
        after(90, () -> {
            fx.gain(p, "music", 0f, 30);
            music = "";
        });
        after(100, () -> {
            stairsDue = true;
            tryOpenStairs(p);
        });
        after(130, () -> {
            fx.send(p, "glitch 12");
        });
    }

    private static final double TAPE_Z0 = 4.56, TAPE_LEN = 3.88;
    private static final int TAPE_PIECES = 16;
    private static final double TAPE_PIECE = TAPE_LEN / TAPE_PIECES;

    /** 警戒线的一小段：起点相对南边立柱 (x, y, z)，往 +z 方向、向下倾 tilt 弧度。 */
    private static Matrix4f tapePiece(double x, double y, double z, double tilt) {
        return new Matrix4f().translate((float) x, (float) y, (float) z).rotateX((float) tilt).translate(-0.01f, -0.03f, 0).scale(0.02f, 0.06f, (float) TAPE_PIECE + 0.005f);
    }

    private void openStairs(Player p) {
        int hall = ParkPlan.FLOOR_HALL, x = ParkPlan.STAIR_TOP_X + 1;
        for (int y = hall; y <= hall + 1; y++) for (int z = 5; z <= 7; z++) world.getBlockAt(ParkPlan.STAIR_TOP_X, y, z).setType(Material.AIR, false);
        // 警戒线从北边的立柱上脱钩：贴着南边立柱垂下去，剩下的摊在地上
        double drop = 0.92, run = 0.25, hang = Math.hypot(drop, run), steep = Math.atan2(drop, run);
        for (int i = 0; i < stairTape.size(); i++) {
            double s = i * TAPE_PIECE;
            double[] at;
            if (s < hang) {
                at = new double[]{0, -drop * s / hang, run * s / hang, steep};
            } else {
                double k = (s - hang) / (TAPE_LEN - hang);
                at = new double[]{0.22 * k * k, -drop, run + (s - hang), 0};
            }
            BlockDisplay piece = stairTape.get(i);
            piece.setInterpolationDelay(0);
            piece.setInterpolationDuration(6 + i / 3);
            piece.setTransformationMatrix(tapePiece(at[0], at[1], at[2], at[3]));
        }
        Location hook = new Location(world, x, hall + 0.95, 8.5);
        world.playSound(hook, "entity.leash_knot.break", SoundCategory.MASTER, 1f, 0.8f);
        after(6, () -> world.playSound(hook, "block.wool.fall", SoundCategory.MASTER, 0.8f, 0.7f));
        // 吊牌上半块亮成绿的：下层入口。黑暗里老远就看得见
        TextDisplay sign = world.spawn(new Location(world, ParkPlan.STAIR_TOP_X - 1.5, hall + 2.88, 8.54, 0f, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text("↓  下层入口  ·  休闲花园层").color(TextColor.color(0xb8ffc8)));
            d.setBackgroundColor(Color.fromARGB(255, 13, 90, 42));
            d.setBrightness(new Display.Brightness(15, 0));
            d.setLineWidth(600);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        scene.add(sign);
        world.getBlockAt(x - 1, hall + 2, 9).setBlockData(Bukkit.createBlockData("minecraft:light[level=9]"), false);
        // 应急灯带一格一格亮过去，从窗前沿着栏杆一路指到楼梯口，最后拐进入口
        int step = 0;
        for (double at = 125; at <= x + 0.6; at += 1.4) {
            double spot = at;
            after(step * 2, () -> {
                scene.add(box("minecraft:lime_concrete", spot, hall + 0.015, 9.06, 0.8, 0.03, 0.09, 13));
                sound(p, "block.lever.click", 0.25f, 1.6f);
            });
            step++;
        }
        for (double z : new double[]{8.0, 6.6}) {
            after(step * 2, () -> {
                scene.add(box("minecraft:lime_concrete", x + 0.55, hall + 0.015, z, 0.09, 0.03, 0.8, 13));
                sound(p, "block.lever.click", 0.25f, 1.6f);
            });
            step++;
        }
    }

    // ---------- 温泉 ----------

    private void poolTick(Player p, Location at) {
        boolean inPools = zone.equals("pools");
        if (!inPools) {
            if (poolStage != 0) {
                poolStage = 0;
                if (!zone.equals("pools")) p.setWalkSpeed(0.2f);
            }
            if (zone.equals("lockers") && rnd.nextDouble() < 0.012) {
                double[] l = plan.lower.lockers.get(rnd.nextInt(37));
                p.playSound(new Location(world, l[0], at.getY() + 1, l[1]), "entity.zombie.attack_wooden_door", SoundCategory.MASTER, 0.35f, 0.5f);
            }
            return;
        }
        int stage = at.getX() < Lower.POOL2_X ? 1 : at.getX() < Lower.POOL3_X ? 2 : 3;
        if (stage != poolStage) {
            poolStage = stage;
            fx.pa(p, "pa_pool" + stage);
            fx.send(p, "warm " + (stage == 1 ? 0.2 : stage == 2 ? 0.4 : 0.6) + " 80");
            mus(p, "spa", stage == 1 ? 0.3f : stage == 2 ? 0.45f : 0.6f);
            p.setWalkSpeed(stage == 1 ? 0.13f : stage == 2 ? 0.1f : 0.08f);
        }
        if (age >= heartAt) {
            sound(p, "entity.warden.heartbeat", 0.8f, 0.6f);
            heartAt = age + 50 + poolStage * 16;
        }
        if (p.getVelocity().lengthSquared() > 0 && age % 18 == 0 && p.isOnGround() && (p.getCurrentInput().isForward() || p.getCurrentInput().isBackward() || p.getCurrentInput().isLeft() || p.getCurrentInput().isRight())) {
            sound(p, "entity.player.swim", 0.14f, 0.55f);
        }
        if (age % 6 == 0) {
            world.spawnParticle(Particle.WHITE_SMOKE, at.getX() + (rnd.nextDouble() - 0.5) * 16, at.getY() + 1.25, at.getZ() + (rnd.nextDouble() - 0.5) * 16, 0, 0, 0.02, 0, 1);
        }
        if (stage == 3 && !rowTurned) {
            if (rowTimer < 0 && at.getX() > 209 && lookingAt(p, Lower.ROW_X, Lower.POOL3_FLOOR + 1.4, at.getZ(), 30, 0.55)) rowTimer = 30 + rnd.nextInt(100);
            if (rowTimer > 0 && --rowTimer == 0) turnRow(p);
        }
        if (rowTurned && age % 10 == 0) {
            double target = at.getX() + 4;
            if (rowX > target && !lookingAt(p, rowX, Lower.POOL3_FLOOR + 1.4, at.getZ(), 40, 0.5)) {
                rowX -= scores[4] > 0 && scores[4] <= 2 ? 0.7 : 0.35;
                for (ItemDisplay figure : row) {
                    Location l = figure.getLocation();
                    figure.teleport(new Location(world, rowX + (l.getZ() * 7 % 1) * 0.4, l.getY(), l.getZ()));
                }
                sound(p, "entity.player.swim", 0.18f, 0.5f);
            }
        }
        if (stage == 3 && !specialSaid && at.getX() > 208 && rowTurned) {
            specialSaid = true;
            after(70, () -> fx.pa(p, "pa_special"));
        }
    }

    private void turnRow(Player p) {
        rowTurned = true;
        flashCut = 1;
        after(6, () -> {
            flashCut = 2;
            for (ItemDisplay figure : row) {
                figure.setInterpolationDelay(0);
                figure.setInterpolationDuration(3);
                figure.setTransformationMatrix(new Matrix4f().rotateY((float) Math.PI));
            }
            fx.gain(p, "music", 0f, 2);
            music = "";
        });
        after(10, () -> flashCut = 1);
        after(16, () -> flashCut = 0);
        after(34, () -> sound(p, "entity.warden.ambient", 0.6f, 0.5f));
    }

    private void crawl(Player p) {
        if (!areaDone("pools")) {
            blocked(p, "pools");
            return;
        }
        crawlNow(p);
    }

    private void crawlNow(Player p) {
        crawled = true;
        fx.send(p, "untag hole");
        fx.black(p, 1f, 8);
        sound(p, "block.mud.step", 1f, 0.5f);
        sound(p, "entity.slime.squish", 0.9f, 0.5f);
        after(10, () -> {
            p.setWalkSpeed(0.2f);
            p.teleport(new Location(world, Lower.BURROW_X0 + 0.5, Lower.BURROW_Y, Lower.BURROW_Z + 0.5, -90, 4));
            fx.send(p, "warm 0 40");
            for (ItemDisplay figure : row) figure.setViewRange(0f);
            fx.black(p, 0f, 40);
            // 身后跟着一个：灰的，趴着爬，离你五六米。回头就看得见，它追不上，你一看它就停
            if (follower != null) follower.remove();
            follower = item("fig_bather_38_bust", Lower.BURROW_X0 - 4.5, Lower.BURROW_Y - 0.62, Lower.BURROW_Z + 0.5, -90f);
            follower.setTeleportDuration(3);
            follower.setBrightness(new Display.Brightness(3, 0));
        });
    }

    private Matrix4f burrowMatrix(double phase) {
        float wide = (float) (1.5 + 0.13 * Math.sin(phase));
        return new Matrix4f().scale(3.02f, wide, wide);
    }

    private void burrowTick() {
        if (!zone.equals("burrow")) {
            if (follower != null && !zone.isEmpty() && !zone.equals("pools")) {
                follower.remove();
                follower = null;
            }
            return;
        }
        if (age % 8 == 0) {
            int i = 0;
            for (ItemDisplay piece : burrow) {
                piece.setInterpolationDelay(0);
                piece.setInterpolationDuration(8);
                piece.setTransformationMatrix(burrowMatrix(age * 0.09 - i * 0.9));
                i++;
            }
        }
        if (player != null && age % 62 == 0) sound(player, "entity.warden.heartbeat", 1.1f, 0.6f);
        if (player != null && rnd.nextDouble() < 0.05) sound(player, "block.honey_block.slide", 0.5f, 0.5f);
        if (player != null && follower != null && follower.isValid()) {
            Location at = player.getLocation(), f = follower.getLocation();
            boolean seen = lookingAt(player, f.getX(), f.getY() + 0.5, f.getZ(), 12, 0.6);
            double want = at.getX() - 5.5;
            if (!seen && f.getX() < want) {
                // 你不看它的时候它跟上来，最多跟到离你五米半；你回头，它就停在那儿
                double nx = Math.min(want, f.getX() + 0.06);
                follower.teleport(new Location(world, nx, f.getY(), f.getZ(), -90f, 0));
                if (age % 14 == 0) world.playSound(follower.getLocation(), "block.honey_block.slide", SoundCategory.MASTER, 0.35f, 0.4f);
            }
            if (seen && age % 40 == 0) world.playSound(follower.getLocation(), "entity.warden.sniff", SoundCategory.MASTER, 0.5f, 0.6f);
        }
    }

    // ---------- 森林 ----------

    private void forestTick(Player p, Location at) {
        if (!zone.equals("forest")) return;
        if (at.getY() < 60 && !atNewStation) {
            p.teleport(lastBoard != null ? lastBoard : new Location(world, Forest.STATION[0], Forest.STATION[1], Forest.STATION[2]));
        } else if (((org.bukkit.entity.Entity) p).isOnGround()) {
            lastBoard = at.clone();
        }
        if (age >= moanAt && !cabGoing) {
            if (moanAt > 0 || at.getX() > 293) fx.play(p, "moan", "moan", exhaled ? 0.7f : 0.42f, 0.9f + rnd.nextFloat() * 0.2f, false, 0);
            moanAt = age + 600 + rnd.nextInt(600);
        }
        if (exhaled) chaseTick(p, at);
    }

    private void readReport(Player p) {
        fx.send(p, "diary 十四号护林员车 · 值班日志|2007.07.04|13:20  出车，巡第三段栈道。|14:10  气压下降，耳朵发胀，估计这两天要呼气。系绳检查了一遍，都结实。|14:40  第三段又被肉盖住一截，车过不去，铲了半个小时。这段每个月都要铲，改道申请交了三次，没人批。|15:30  对讲机有回音，我说一句它重复一句，换频道也一样。回站里换一台。|15:47  发动机熄火，打不着。走回站里叫人拖车。|许");
        sound(p, "item.book.page_turn", 0.9f, 0.8f);
        stash(p, "十四号护林员车 · 值班日志", "2007.07.04");
        dropTag(p, Notes.VEHICLE_LOG);
        if (read.add(Notes.VEHICLE_LOG)) progress(p, "forest");
        if (reportRead) return;
        reportRead = true;
        fx.gain(p, "amb", 0.3f, 40);
        maybeExhale(p);
    }

    /** 森林的纸读完，过一会儿它提前呼气。 */
    private void maybeExhale(Player p) {
        if (exhaled || !areaDone("forest")) return;
        exhaled = true;
        after(200 + rnd.nextInt(200), () -> exhale(p));
    }

    /** 最后的报告：先显示您打的分，再当着您的面改成满分。 */
    private String correctedFields() {
        LocalDate today = LocalDate.now();
        StringBuilder b = new StringBuilder(today.getYear() + " 年 " + today.getMonthValue() + " 月 " + today.getDayOfMonth() + " 日");
        for (int i = 0; i < scores.length; i++) b.append("|5");
        b.append("|5.0|fix");
        for (int i = 0; i < scores.length; i++) {
            int first = i < asked.length && asked[i] > 0 ? asked[i] : scores[i];
            b.append('|').append(first == 0 ? "—" : String.valueOf(first));
        }
        return b.toString();
    }

    private String reportFields() {
        LocalDate today = LocalDate.now();
        StringBuilder b = new StringBuilder(today.getYear() + " 年 " + today.getMonthValue() + " 月 " + today.getDayOfMonth() + " 日");
        for (int s : scores) b.append('|').append(s == 0 ? "—" : String.valueOf(s));
        b.append('|').append(String.format("%.1f", average()));
        return b.toString();
    }

    private double average() {
        int sum = 0, n = 0;
        for (int s : scores) {
            if (s > 0) {
                sum += s;
                n++;
            }
        }
        return n == 0 ? 3 : (double) sum / n;
    }

    /** 它半年才喘一口气，现在提前呼气了。 */
    private void exhale(Player p) {
        Bukkit.getLogger().info("呼气");
        amb(p, "wind_cold", 0f);
        ambient = "";
        sound(p, "block.respawn_anchor.deplete", 1f, 0.6f);
        after(70, () -> {
            fx.play(p, "amb", "wind_hot", 0.9f, 1f, true, 60);
            ambient = "wind_hot";
            // 雾只是颜色微微变暖，远近跟平时一样
            fx.fog(p, EXHALE_FOG[0], EXHALE_FOG[1], 1f, EXHALE_TINT, 1f);
            // 风里飘的东西多起来，肉一鼓一鼓地动
            fx.send(p, "ember 1.4");
            exhaleSmsAt = clock();
            fx.parkSms(p, "紧急通告：本区域正在呼气。请{码:屏住}呼吸，沿栈道前往电梯口。");
            fx.play(p, "moan", "moan", 1f, 0.8f, false, 0);
            for (int[] c : plan.forest.exitGate) world.getBlockAt(c[0], c[1], c[2]).setType(Material.AIR, false);
            spawnChase(p);
        });
        // 手电只闪一下
        after(74, () -> flashCut = 1);
        after(78, () -> flashCut = 0);
        for (int t = 70; t < 70 + 20 * 40; t += 60) {
            after(t, () -> park.flesh.heave = 0.35);
            after(t + 30, () -> park.flesh.heave = 0);
        }
    }

    /** 呼气时森林的雾：远近不变，只是颜色暖一点。 */
    private static final float[] EXHALE_FOG = {6, 70};
    private static final String EXHALE_TINT = "140806";

    private void spawnChase(Player p) {
        // 枝上站着的：2007 年迷在森林里的游客，和几个护林员。每个都不一样
        int who = 0;
        for (double[] w : plan.forest.watchers) {
            String model = who % 3 == 2 ? "fig_ranger_" + (2 + who / 3 % 4) : "fig_tourist_" + (30 + who);
            who++;
            ItemDisplay figure = item(model, w[0], w[1] + 1, w[2], yawTo(w[0], w[2], p.getLocation()));
            watchers.add(figure);
        }
        chaserS = new double[]{-14, -19, -24};
        for (int i = 0; i < 3; i++) {
            ItemDisplay chaser = item(i == 0 ? "fig_ranger_0_reach" : "fig_tourist_" + (26 + i) + "_reach", Forest.VEHICLE[0], Forest.VEHICLE[1] + 1, Forest.VEHICLE[2] - 4, 90f);
            chaser.setTeleportDuration(8);
            chasers.add(chaser);
        }
    }

    private static float yawTo(double x, double z, Location target) {
        return (float) Math.toDegrees(Math.atan2(-(target.getX() - x), target.getZ() - z));
    }

    private void chaseTick(Player p, Location at) {
        if (chasers.isEmpty() || cabGoing || age % 8 != 0) return;
        List<double[]> line = plan.forest.exitLine;
        int here = Forest.nearest(line, at.getX(), at.getY(), at.getZ());
        double me = here * 0.25;
        boolean onPath = Forest.dist2(line.get(here), at.getX(), at.getY(), at.getZ()) < 25;
        for (int i = 0; i < chasers.size(); i++) {
            ItemDisplay chaser = chasers.get(i);
            if (!onPath && chaserS[i] < 0) continue;
            int index = (int) Math.max(0, Math.min(line.size() - 1, chaserS[i] / 0.25));
            double[] pos = line.get(index);
            Location eyeTarget = new Location(world, pos[0], pos[1] + 1.2, pos[2]);
            boolean seen = chaserS[i] >= 0 && lookingAt(p, eyeTarget.getX(), eyeTarget.getY(), eyeTarget.getZ(), 40, 0.62);
            // 看着它的时候它不动；一扫开，它就近一段
            if (!seen) chaserS[i] = Math.min(me - 1.2, chaserS[i] + 2.0);
            index = (int) Math.max(0, Math.min(line.size() - 1, chaserS[i] / 0.25));
            pos = line.get(index);
            double h = Math.round(pos[1] * 2) / 2.0;
            double side = (i - 1) * 0.9;
            if (chaserS[i] >= 0) chaser.teleport(new Location(world, pos[0] + side, h + 1, pos[2] + side * 0.4, yawTo(pos[0], pos[2], at), 0));
            if (chaserS[i] >= 0 && me - chaserS[i] < 1.5) {
                caught(p, line, here);
                return;
            }
        }
        for (ItemDisplay figure : watchers) {
            Location l = figure.getLocation();
            figure.setRotation(yawTo(l.getX(), l.getZ(), at), 0);
        }
    }

    /** 栈道每一格的高度和路线差一点，直接取路线的高度会把人传送进木板里，穿下去。 */
    private double boardAt(double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int h = (int) Math.floor(y) + 3; h >= y - 4; h--) {
            B b = plan.block(bx, h, bz);
            if (b == B.WALK_B) return h + 0.5;
            if (b == B.WALK_T) return h + 1;
        }
        return Math.round(y * 2) / 2.0;
    }

    private void caught(Player p, List<double[]> line, int here) {
        catches++;
        // 黑屏之前，它的脸先贴到你眼前
        Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new org.bukkit.util.Vector(0, 0, 1);
        dir.normalize();
        double fx0 = eye.getX() + dir.getX() * 0.75, fz0 = eye.getZ() + dir.getZ() * 0.75;
        ItemDisplay face = item("grey_figure", fx0, eye.getY() - 1.02, fz0, yawTo(fx0, fz0, eye));
        face.setBrightness(new Display.Brightness(9, 0));
        flashCut = 0;
        fx.play(p, "sfx", "scare", 1f, 1f, false, 0);
        fx.send(p, "jolt");
        after(9, () -> {
            face.remove();
            fx.black(p, 1f, 2);
            flashCut = 2;
            sound(p, "entity.warden.nearby_closest", 1.2f, 0.7f);
        });
        after(39, () -> {
            int back = Math.max(0, here - 60);
            double[] s = line.get(back);
            p.teleport(new Location(world, s[0], boardAt(s[0], s[1], s[2]), s[2], yawTo(s[0], s[2], new Location(world, line.get(Math.min(line.size() - 1, back + 20))[0], 0, line.get(Math.min(line.size() - 1, back + 20))[2])), 0));
            for (int i = 0; i < chaserS.length; i++) chaserS[i] = back * 0.25 - 14 - i * 5;
            flashCut = 0;
            fx.black(p, 0f, 24);
        });
    }

    // ---------- 电梯和结局 ----------

    private void cabTick(Player p, Location at) {
        if (inCab || !exhaled || atNewStation) return;
        if (at.getX() >= Forest.CAB_X && at.getX() < Forest.CAB_X + 3 && at.getZ() >= Forest.CAB_Z && at.getZ() < Forest.CAB_Z + 3 && Math.abs(at.getY() - Forest.CAB_Y) < 2) {
            inCab = true;
            Bukkit.getLogger().info("电梯 进门");
            cabDoor(0, Forest.CAB_X, Forest.CAB_Y, Forest.CAB_Z, false);
            chasers.forEach(Entity::remove);
            chasers.clear();
            watchers.forEach(Entity::remove);
            watchers.clear();
            after(14, () -> {
                sound(p, "block.anvil.land", 0.7f, 0.6f);
                fx.gain(p, "amb", 0.14f, 6);
                fx.send(p, "sick 0 40");
                flashWeak = false;
                fx.fog(p, 30, 120, 1f, "100c0a", 1f);
            });
            after(50, () -> {
                fx.play(p, "music", "mall", 0.4f, 1f, true, 40);
                music = "mall";
            });
            // 门关了就得有人告诉你该干什么：广播叫你评分，评分机上挂提示
            after(80, () -> {
                if (cabGoing) return;
                fx.pa(p, "pa_rate");
                fx.send(p, "tag cabkiosk|" + (Forest.CAB_X + 0.05) + "|" + (Forest.CAB_Y + 1.3 + TAG_ABOVE) + "|" + (Forest.CAB_Z + 1.5) + "|0|为本次体验评分");
            });
            after(300, () -> {
                if (!cabGoing) fx.pa(p, "pa_rate");
            });
            // 再不按，它替你按了满分。流程不能停在一部关着门的电梯里
            after(460, () -> {
                if (cabGoing || finalKiosk.used) return;
                Bukkit.getLogger().info("电梯 没人按，它自己打了 5 分");
                finalKiosk.pressItself(5);
                leaveInCab(p, 5);
            });
        }
    }

    private void smash(Player p) {
        if (finalKiosk.used) return;
        finalKiosk.kill();
        smashed = true;
        sound(p, "block.glass.break", 1f, 0.7f);
        sound(p, "entity.iron_golem.damage", 0.6f, 1.2f);
        leaveInCab(p, 0);
    }

    /**
     * 回程电梯：打完"本次体验"这一分，电梯往上回下游客中心。呼气那一下把井道震歪了，走了一小段卡住，
     * 楼层牌只报一个故障码，没有广播、没有短信，只有钢缆和井道咯吱咯吱响。十几秒以后钢缆断了，往下坠。
     * 这是事故：它要你回上面去，没想让你掉下来。
     */
    private void leaveInCab(Player p, int stars) {
        if (cabGoing) return;
        cabGoing = true;
        fx.send(p, "untag cabkiosk");
        if (stars > 0) sound(p, "block.note_block.chime", 0.7f, 1.2f);
        after(30, () -> fx.pa(p, "pa_thanks2"));
        cabScore = stars;
        cabScoreAt = clock();
        // 往上走
        after(80, () -> {
            floorSign("▲  下游客中心", 0xffb040);
            sound(p, "block.chain.place", 1f, 0.5f);
        });
        for (int t = 90; t < 170; t += 24) after(t, () -> sound(p, "block.chain.step", 0.5f, 0.6f));
        // 卡住：一声闷响，音乐断了，楼层牌报故障码
        after(175, () -> {
            sound(p, "block.anvil.land", 0.8f, 0.5f);
            fx.send(p, "jolt");
            fx.gain(p, "music", 0f, 3);
            music = "";
            floorSign("E-07", 0xff3a28);
        });
        for (int t = 186; t < 240; t += 6) {
            boolean on = (t / 6) % 2 == 0;
            after(t, () -> floorSign(on ? "E-07" : "", 0xff3a28));
        }
        after(242, () -> floorSign("E-07", 0xff3a28));
        // 安静十几秒：只有钢缆和井道在响，一下比一下近
        int[] creaks = {215, 270, 318, 360, 395, 425, 450, 468};
        for (int i = 0; i < creaks.length; i++) {
            float loud = 0.4f + i * 0.07f;
            String key = i % 2 == 0 ? "block.chain.step" : "entity.iron_golem.step";
            after(creaks[i], () -> {
                sound(p, key, loud, 0.45f);
                if (loud > 0.7f) fx.send(p, "jolt");
            });
        }
        // 钢缆断了
        after(480, () -> {
            sound(p, "block.chain.break", 1f, 0.5f);
            sound(p, "entity.iron_golem.damage", 1f, 0.5f);
            fx.send(p, "jolt");
        });
        after(486, () -> {
            sound(p, "item.elytra.flying", 1f, 1.25f);
            fx.send(p, "sick 0.35 20");
        });
        for (int t = 490; t < 570; t += 8) after(t, () -> fx.send(p, "jolt"));
        // 楼层牌乱跳，最后是一串乱码
        String pool = "锟斤拷烫屯囧¤§□▼";
        for (int t = 488; t < 570; t += 4) {
            int seed = t;
            after(t, () -> {
                StringBuilder b2 = new StringBuilder();
                java.util.Random r = new java.util.Random(seed);
                for (int k = 0; k < 4 + r.nextInt(3); k++) b2.append(pool.charAt(r.nextInt(pool.length())));
                floorSign(b2.toString(), 0xff3a28);
            });
        }
        after(572, () -> {
            sound(p, "entity.generic.explode", 0.9f, 0.5f);
            sound(p, "block.anvil.land", 1f, 0.4f);
            fx.black(p, 1f, 1);
            flashCut = 2;
            fx.send(p, "sick 0 10");
        });
        after(576, () -> {
            hop(p, Forest.CAB_X, Forest.CAB_Y, Forest.CAB_Z, Well.CAB_X, Well.CAB_Y, Well.CAB_Z);
            wellArrive(p);
        });
    }

    private void floorSign(String body, int color) {
        if (cabFloor != null && cabFloor.isValid()) cabFloor.text(Component.text(body).color(TextColor.color(color)));
    }

    // ---------- 第四关：深井 ----------
    // 森林那部电梯掉下来，卡在喉咙口的井壁里。抬头，上面那座游客中心底下没有地基，是肉根。
    // 门外起，肉壁上长出一道台阶，贴着井壁一圈一圈螺旋往下。井隔一阵喘一口（整圈肉往里一收，走不动），两处肉箍压下来要蹲着过，
    // 头顶斜插着 4 号支撑，脚下掉着它的巡检牌；搜救队的绳每一圈在肩膀边上露一截。最后一圈，对面井壁上一串头灯隔着井心跟着你挪。
    // 台阶到底是一块平台：扔一根冷光棒，坐 807 号车顺着断缆滑出喉咙，看见底下躺着一整栋楼。
    // ---------- 第五关：井底的游客中心 ----------
    // 这座楼不演：没有广播，没有人形站起来。照着第一关的路线再走一遍，找原件。最后是机房里的改进系统，看完那块屏，疯一小段，接片尾。

    private boolean inWell, cabSlipped, carRiding, carDown, sticksThrown, madness, handsGrey, lookedUp, crouchHinted;
    private int breathNext, breathHold, breaths, carT, upperPa, cabScore;
    private String cabScoreAt = "", cabSmsAt = "", parkSmsAt = "", exhaleSmsAt = "";
    private final String[] scoreAt = new String[6];
    private Tram car807;
    private final List<BlockDisplay> headlamps = new ArrayList<>();
    private BlockDisplay stickGlow;
    /** 头灯上一次跟着挪的时候，人在哪（x、y、z）。 */
    private double[] lampAt;
    /** 正被肉索缠着（第几处，-1 没有）、什么时候缠上的、扯了几下；肉索从井壁哪一点伸出来。 */
    private int grabbing = -1, grabStart, grabTugs;
    private double[] grabFrom;
    /** 正被肉垫吸着脚（第几处，-1 没有）、什么时候吸上的、蹬了几下；吸住时人站在哪。 */
    private int sucking = -1, suckStart, suckTugs;
    private Location stuckAt;
    private final boolean[] grabbed = new boolean[Well.GRAB.length], sucked = new boolean[Well.SUCK.length];
    private boolean grabHinted, suckHinted;
    private final List<ItemDisplay> tendril = new ArrayList<>(), pads = new ArrayList<>();
    private Location wellSafe;
    /** 最后站稳的是第几处落脚的地方；在湿滑的肉块上站了多久（tick）。 */
    private int safeHold, slipFor;
    /** 井喘一口：多久一次、吸气时停多久（tick）。 */
    private static final int BREATH_EVERY = 460, BREATH_HOLD = 70;
    /** 807 号车顺着断缆往下滑：一共多少 tick。 */
    private static final int CAR_TICKS = 560;

    private static String clock() {
        java.time.LocalTime t = java.time.LocalTime.now();
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private void resetWell() {
        inWell = cabSlipped = carRiding = carDown = sticksThrown = madness = handsGrey = lookedUp = crouchHinted = endingDue = false;
        walkieState = 0;
        walkieLed = null;
        walkieAt = null;
        replayOn = false;
        crowd.clear();
        rescuers.clear();
        bagAt = carHintAt = -1;
        breathNext = breathHold = breaths = carT = upperPa = cabScore = 0;
        cabScoreAt = cabSmsAt = parkSmsAt = exhaleSmsAt = "";
        java.util.Arrays.fill(scoreAt, "");
        lampAt = null;
        grabbing = sucking = -1;
        grabFrom = null;
        stuckAt = null;
        java.util.Arrays.fill(grabbed, false);
        java.util.Arrays.fill(sucked, false);
        grabHinted = suckHinted = false;
        tendril.clear();
        pads.clear();
        wellSafe = null;
        safeHold = slipFor = 0;
        if (car807 != null) car807.remove();
        car807 = null;
        headlamps.clear();
        stickGlow = null;
    }

    private void buildWell() {
        cabDoors(3, Well.CAB_X, Well.CAB_Y, Well.CAB_Z, false);
        // 807 号车挂在最底下那块平台北边，车门朝着平台
        car807 = new Tram(world, plan.route, b -> {
        });
        car807.spawnAt(Well.CAR_FROM[0], Well.CAR_FROM[1], Well.CAR_FROM[2], "807");
        car807.power(false);
        Interaction carHit = interaction(Well.CAR_FROM[0] + (TramModel.DOOR_X0 + TramModel.DOOR_X1) / 2, Well.CAR_FROM[1], Well.BAG_Z0 - 0.5, 2.0f, 2.4f);
        park.onClick(carHit, this::boardCar);
        // 两块楼板上长了一层薄薄的肉：湿的，贴着楼板摊开一大片，踩上去会把脚吸住
        for (int i : Well.SUCK) {
            Well.Hold h = Well.HOLDS.get(i);
            double[] c = h.center();
            ItemDisplay pad = item("flesh_lump_dark", c[0], c[1] + 0.02, c[2], 0f);
            pad.setTransformationMatrix(padShape(h.yaw(), 0.05f));
            pads.add(pad);
        }
        // 没用完的冷光棒：一捆插在平台角上
        double[] k = Well.STICKS;
        for (int i = 0; i < 5; i++) box("minecraft:lime_concrete", k[0] + i * 0.07, k[1], k[2] + (i % 2) * 0.06, 0.04, 0.42, 0.04, 9);
        Interaction sticks = interaction(k[0] + 0.15, k[1], k[2], 0.6f, 0.6f);
        park.onClick(sticks, this::throwStick);
        // 对面井壁上那串头灯：五盏。最后一圈你往下走，它们就隔着井心在你正对面跟着挪，你停它们就停。先挂在最后一圈起点对面的井壁上
        double[] from = Well.HOLDS.get(Well.LAMPS_FROM).in;
        double[] start = lampSpot(new double[]{from[0], from[1], from[2]});
        for (int i = 0; i < 5; i++) {
            double side = (i - 2) * 0.9;
            BlockDisplay lamp = box("minecraft:ochre_froglight", start[0] + start[3] * side, start[1] - i * 1.3, start[2] + start[4] * side, 0.5, 0.36, 0.5, 15);
            lamp.setTeleportDuration(6);
            headlamps.add(lamp);
            BlockDisplay hat = box("minecraft:yellow_concrete", start[0] - 0.15 + start[3] * side, start[1] + 0.36 - i * 1.3, start[2] - 0.15 + start[4] * side, 0.8, 0.4, 0.8, 4);
            hat.setTeleportDuration(6);
            headlamps.add(hat);
        }
        // 中庭：几十排折叠椅朝着舞台，横幅还挂着。椅子上没有人，只有东西
        int hf = ParkPlan.FLOOR_HALL + Well.DY;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 9; c++) {
                double x = 120.5 + Well.DX + c * 1.15, z = -2.6 + r * 1.3;
                if (rnd.nextDouble() < 0.18) continue;
                boolean fallen = rnd.nextDouble() < 0.22;
                box("minecraft:dark_oak_planks", x, hf + (fallen ? 0 : 0.45), z, 0.5, 0.06, 0.5, -1);
                if (!fallen) box("minecraft:dark_oak_planks", x, hf + 0.5, z + 0.44, 0.5, 0.5, 0.06, -1);
                if (rnd.nextDouble() < 0.25) box(rnd.nextBoolean() ? "minecraft:brown_wool" : "minecraft:blue_wool", x + 0.08, hf + (fallen ? 0.06 : 0.51), z + 0.1, 0.3, 0.22, 0.2, -1);
            }
        }
        text("独立日烟花晚会  ·  今晚 21:00\n全家一起，不见不散", 125.5 + Well.DX, hf + 3.4, 10.9, 180f, 0xa89a80, 0.6f, 0xff3a1410, 2);
        // 真的那面观景窗：玻璃内侧全是手印，一层压一层。整面窗都是，从窗台一直到人够得着的高处
        for (int i = 0; i < 12; i++) {
            for (int row = 0; row < 2; row++) {
                ItemDisplay hands = item("handprints", 113.7 + Well.DX + i * 2.66, hf + 1.95 + row * 1.98, ParkPlan.HZ1 - 0.08, 180f);
                hands.setTransformationMatrix(new Matrix4f().scale(2.7f, 2.0f, 1));
                hands.setBrightness(new Display.Brightness(4, 0));
            }
        }
        // 缆车拍照点：肉壁上一排灰脸，十个穿着公司的背心。闪光那一下才看得见
        photoWall();
    }

    /**
     * 拍照点的相机：车窗外右手边一个看不见的盔甲架，脸朝着车里的你。客户端从它那儿看一帧、截下来，留给井底 3 号码头的照片墙。
     * 你身后（车左边）那面肉壁上贴着一排灰脸，十个穿着公司的背心。
     */
    private void photoCam(Player p) {
        Bukkit.getLogger().info("拍照点 闪光");
        Location eye = p.getEyeLocation();
        double d = park.ride.distance();
        double[] q = new double[3], r = new double[3];
        plan.route.at(d, q);
        plan.route.at(Math.min(plan.route.length, d + 2), r);
        double fx0 = r[0] - q[0], fz0 = r[2] - q[2], len = Math.max(1e-6, Math.hypot(fx0, fz0));
        fx0 /= len;
        fz0 /= len;
        double rx = -fz0, rz = fx0;
        double cx = eye.getX() + rx * 2.9 + fx0 * 0.7, cy = eye.getY() + 0.15, cz = eye.getZ() + rz * 2.9 + fz0 * 0.7;
        double dx = eye.getX() - cx, dy = eye.getY() - 0.1 - cy, dz = eye.getZ() - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        // 闪光灯把车厢和身后那面肉壁照亮几下
        photoLight = new double[]{cx, cy, cz, eye.getX() - rx * 3.5, eye.getY(), eye.getZ() - rz * 3.5};
        photoLightUntil = age + 8;
    }

    /** 拍照点在缆车路线第几米：闪光大概在这里。 */
    private static final double PHOTO_D = 205;

    private void photoWall() {
        double[] q = new double[3], r = new double[3];
        plan.route.at(PHOTO_D, q);
        plan.route.at(PHOTO_D + 2, r);
        double fx0 = r[0] - q[0], fz0 = r[2] - q[2], len = Math.hypot(fx0, fz0);
        fx0 /= len;
        fz0 /= len;
        // 车左边那面肉壁（从拍照的机位看过去，在你身后）
        double nx = fz0, nz = -fx0;
        for (int i = 0; i < 16; i++) {
            double along = (i - 8) * 1.05, out = 4.6 + (i % 3) * 0.35;
            double x = q[0] + Route.CABIN_CENTER_X + fx0 * along + nx * out, z = q[2] + fz0 * along + nz * out;
            String model = i >= 3 && i < 13 ? "fig_tester_" + (i - 2) : "fig_tourist_" + (40 + i) + "_raised";
            item(model, x, q[1] + 0.1 + (i % 2) * 0.3, z, (float) Math.toDegrees(Math.atan2(-(-nx), -nz)) + 180f);
        }
    }

    /** 森林电梯坠到底：卡在井壁上。门撬开一半，外面是旧员工平台。 */
    private void wellArrive(Player p) {
        inWell = true;
        fx.send(p, "zone well");
        flashCut = 2;
        after(30, () -> {
            flashCut = 0;
            fx.black(p, 0f, 40);
            sound(p, "block.chain.step", 1f, 0.4f);
        });
        after(70, () -> {
            sound(p, "block.iron_door.open", 1f, 0.5f);
            cabDoor(3, Well.CAB_X, Well.CAB_Y, Well.CAB_Z, true);
            fx.send(p, "sub 提示|门撬开了一半。外面是井。|80");
        });
        after(200, () -> {
            if (!lookedUp) fx.send(p, "sub 提示|往上看。|60");
        });
        wellSafe = new Location(world, Well.CAB_X + 5.5, Well.CAB_Y, Well.CAB_Z + 1.5, -90, 0);
        safeHold = 0;
    }

    private void wellAir(Player p, String z) {
        if (z.equals("well")) {
            fx.fog(p, 6, 62, 1f, "120504", 1f);
            amb(p, "wind_hot", 0.32f);
            mus(p, "", 0);
        } else {
            // 井底那座楼：又黑又静，只有滴水
            fx.fog(p, 8, 48, 1f, "0a0605", 1f);
            amb(p, "drips", 0.35f);
            mus(p, "", 0);
        }
    }

    private double[] photoLight;
    private int photoLightUntil;

    private void wellLights(java.util.Map<Long, Integer> desired) {
        if (photoLight != null && age < photoLightUntil) {
            for (int k = 0; k < 6; k += 3) desired.merge(Lights.key((int) Math.floor(photoLight[k]), (int) Math.floor(photoLight[k + 1]), (int) Math.floor(photoLight[k + 2])), 15, Math::max);
        }
        if (stickGlow != null && stickGlow.isValid()) {
            Location l = stickGlow.getLocation();
            desired.merge(Lights.key(l.getBlockX(), l.getBlockY(), l.getBlockZ()), 12, Math::max);
        }
        if (carRiding && car807 != null && car807.root != null) {
            Location l = car807.root.getLocation();
            desired.merge(Lights.key(l.getBlockX() - 3, l.getBlockY() - 2, l.getBlockZ()), 12, Math::max);
        }
    }

    private void wellTick(Player p, Location at) {
        if (!inWell) return;
        double x = at.getX(), y = at.getY(), z = at.getZ();
        // 脚踩在哪一处上（悬在半空、正往下落的时候是 -1）
        int hold = ((Entity) p).isOnGround() ? Well.holdAt(x, y, z) : -1;
        // 电梯在身后又往下沉了一截，回不去了
        if (!cabSlipped && (x > Well.CAB_X + 5.2 || Math.abs(z - Well.CAB_Z - 1.5) > 3.2)) {
            cabSlipped = true;
            after(20, () -> {
                sound(p, "block.chain.break", 1f, 0.5f);
                sound(p, "block.anvil.land", 0.6f, 0.4f);
                fx.send(p, "jolt");
                cabDoor(3, Well.CAB_X, Well.CAB_Y, Well.CAB_Z, false);
            });
        }
        // 抬头看上面那座楼的底
        if (!lookedUp && at.getPitch() < -50 && y > Well.CAB_Y - 12) {
            lookedUp = true;
        }
        // 上面那座楼的广播隐隐约约传下来，越往下越远，到钢梁那一层就听不见了
        if (y > 120 && age >= upperPa) {
            upperPa = age + 700 + rnd.nextInt(500);
            fx.play(p, "voice", "pa_improve", (float) (0.05 + 0.11 * (y - 120) / (Well.CAB_Y - 120)), 0.97f, false, 0);
        }
        // 井喘气只在贴着井壁的断楼梯、肉块上（钢梁上没有墙可贴）
        boolean onWall = !carRiding && !carDown && hold > 0 && hold < Well.BAG && Well.HOLDS.get(hold).kind != 'B';
        breathTick(p, onWall);
        lampTick(p, at);
        crouchTick(p, hold);
        grabTick(p, at, hold);
        suckTick(p, at, hold);
        slipTick(p, at, hold);
        carTick(p);
        fallTick(p, at, hold);
        bagTick(p, at);
    }

    /**
     * 踩空了：往下落过 6 格还没踩到东西，就当是落在了下面一处上——黑一下、闷一声，人趴在低一点的那一处上接着走，最多跳过一处。
     * 还没缠过腿、吸过脚的那一处不跳过。真掉到最底下那块平台下面，回到最后站稳的地方。
     */
    private void fallTick(Player p, Location at, int hold) {
        if (carRiding || carDown) return;
        if (hold >= 0) {
            safeHold = hold;
            wellSafe = at.clone();
            return;
        }
        double y = at.getY();
        if (wellSafe != null && y < Well.BAG_Y - 6 && y > Well.REAL_FLOOR + 8) {
            p.teleport(wellSafe);
            return;
        }
        if (safeHold >= Well.BAG || ((Entity) p).isOnGround()) return;
        Well.Hold from = Well.HOLDS.get(safeHold);
        // 真往下掉了一段才算踩空（传送过来、还没落地的那一下不算）
        if (y > from.bottom() - 6 || p.getFallDistance() < 4) return;
        int to = Math.min(Well.BAG, safeHold + 1);
        if (to < Well.BAG && !keyHold(to) && Well.HOLDS.get(to).top() > from.bottom() - 4) to++;
        Well.Hold h = Well.HOLDS.get(to);
        Bukkit.getLogger().info("掉下去 接住 " + safeHold + "→" + to);
        safeHold = to;
        Location land = new Location(world, h.in[0], h.in[1] + 0.05, h.in[2], h.yaw(), 35);
        p.setVelocity(new Vector());
        p.setFallDistance(0);
        p.teleport(land);
        wellSafe = land.clone();
        fx.black(p, 1f, 1);
        after(10, () -> fx.black(p, 0f, 30));
        sound(p, "entity.player.big_fall", 1f, 0.7f);
        sound(p, "entity.slime.squish", 0.9f, 0.45f);
        fx.send(p, "jolt");
        fx.send(p, "sick 0.4 20");
    }

    /** 这一处有事还没发生（缠腿、吸脚）：掉下去接住的时候不能跳过它。 */
    private boolean keyHold(int i) {
        for (int k = 0; k < Well.GRAB.length; k++) if (Well.GRAB[k] == i && !grabbed[k]) return true;
        for (int k = 0; k < Well.SUCK.length; k++) if (Well.SUCK[k] == i && !sucked[k]) return true;
        return false;
    }

    /** 湿滑的肉块：站上去一会儿，脚底下开始往井心那边滑。别停，接着跳。 */
    private void slipTick(Player p, Location at, int hold) {
        boolean on = false;
        for (int s : Well.SLIP) on |= hold == s;
        if (!on || grabbing >= 0 || sucking >= 0 || breathHold > 0) {
            slipFor = 0;
            return;
        }
        if (++slipFor == 1) sound(p, "block.honey_block.step", 0.9f, 0.6f);
        if (slipFor < 12) return;
        if (slipFor == 12) Bukkit.getLogger().info("肉块 滑脚 " + hold);
        Vector out = new Vector(Well.SX - at.getX(), 0, Well.SZ - at.getZ());
        if (out.lengthSquared() > 1e-6) p.setVelocity(p.getVelocity().add(out.normalize().multiply(0.03)));
        if (slipFor % 9 == 0) sound(p, "block.honey_block.slide", 0.7f, 0.55f + rnd.nextFloat() * 0.1f);
    }

    // ---------- 肉会对人有反应：缠腿、吸脚（手电照到就缩是光影里做的） ----------

    /** 落到那一处：前面一点的井壁里扑出来一根肉索，缠住腿往墙里拽。连按左键扯开；不扯，过八秒它自己松开缩回去。 */
    private void grabTick(Player p, Location at, int hold) {
        if (grabbing >= 0) {
            holdGrab(p, at);
            return;
        }
        if (hold < 0 || breathHold > 0 || sucking >= 0 || carRiding || carDown) return;
        for (int i = 0; i < Well.GRAB.length; i++) {
            if (grabbed[i] || hold != Well.GRAB[i]) continue;
            grabbed[i] = true;
            grabbing = i;
            grabStart = age;
            grabTugs = 0;
            Bukkit.getLogger().info("肉索 缠腿 " + i);
            // 从前面一点、肩膀边上的井壁里扑出来，人眼角就看得见
            Well.Hold h = Well.HOLDS.get(hold);
            double phi = Well.phiNear(at.getX(), at.getZ(), h.midPhi());
            double[] w = Well.spot(hold, phi + 0.11, 2.2);
            grabFrom = new double[]{w[0], w[1] + 1.1, w[2]};
            p.setWalkSpeed(0.03f);
            sound(p, "entity.slime.squish", 1f, 0.45f);
            sound(p, "block.honey_block.slide", 1f, 0.5f);
            sound(p, "entity.warden.tendril_clicks", 0.9f, 0.6f);
            fx.send(p, "jolt");
            fx.send(p, "sick 0.45 12");
            for (int k = 0; k < 6; k++) {
                ItemDisplay seg = item(k % 2 == 0 ? "flesh_lump" : "flesh_lump_dark", grabFrom[0], grabFrom[1], grabFrom[2], 0f);
                seg.setTransformationMatrix(new Matrix4f().scale(0.01f));
                seg.setTeleportDuration(2);
                tendril.add(seg);
            }
            if (!grabHinted) {
                grabHinted = true;
                fx.send(p, "sub 提示|被缠住了。连按左键，扯开它。|110");
            }
            return;
        }
    }

    private void holdGrab(Player p, Location at) {
        int t = age - grabStart;
        layTendril(at, Math.min(1, t / 5.0));
        if (t > 5) {
            // 往井壁里拽：拽到墙边那圈看不见的墙为止
            Vector pull = new Vector(grabFrom[0] - at.getX(), 0, grabFrom[2] - at.getZ());
            double len = pull.length();
            if (len > 0.7) p.setVelocity(p.getVelocity().multiply(0.5).add(pull.multiply(0.085 / len)));
            if (t % 11 == 0) sound(p, "entity.slime.squish", 0.6f, 0.4f + rnd.nextFloat() * 0.2f);
        }
        if (grabTugs >= 7 || t > 160) releaseGrab(p, grabTugs >= 7);
    }

    /** 肉索从井壁里那一点弯着伸到腿上：六节，根粗梢细，一直在蠕。reach 是伸出来多少（0 到 1）。 */
    private void layTendril(Location at, double reach) {
        double ax = grabFrom[0], ay = grabFrom[1], az = grabFrom[2];
        double bx = at.getX(), by = at.getY() + 0.35, bz = at.getZ();
        double cx = (ax + bx) / 2, cy = Math.max(ay, by) + 0.9, cz = (az + bz) / 2;
        int n = tendril.size();
        double[] prev = {ax, ay, az};
        for (int k = 0; k < n; k++) {
            double s = reach * (k + 1) / n, u = 1 - s;
            double[] q = {u * u * ax + 2 * u * s * cx + s * s * bx, u * u * ay + 2 * u * s * cy + s * s * by, u * u * az + 2 * u * s * cz + s * s * bz};
            double dx = q[0] - prev[0], dy = q[1] - prev[1], dz = q[2] - prev[2], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            ItemDisplay seg = tendril.get(k);
            if (len > 1e-3) {
                float r = (float) ((0.36 - 0.2 * k / (double) n) * (1 + 0.15 * Math.sin(age * 0.9 + k * 1.7)));
                Quaternionf turn = new Quaternionf().rotationTo(1, 0, 0, (float) (dx / len), (float) (dy / len), (float) (dz / len));
                seg.setInterpolationDelay(0);
                seg.setInterpolationDuration(2);
                seg.setTransformationMatrix(new Matrix4f().rotate(turn).scale((float) (len * 1.35), r, r));
                seg.teleport(new Location(world, (q[0] + prev[0]) / 2, (q[1] + prev[1]) / 2, (q[2] + prev[2]) / 2));
            }
            prev = q;
        }
    }

    private void releaseGrab(Player p, boolean tore) {
        Bukkit.getLogger().info(tore ? "肉索 扯开" : "肉索 自己松开");
        grabbing = -1;
        if (breathHold == 0) p.setWalkSpeed(0.2f);
        fx.send(p, "sick 0 20");
        sound(p, tore ? "block.slime_block.break" : "entity.slime.squish", 1f, tore ? 0.5f : 0.35f);
        if (tore) sound(p, "entity.player.breath", 0.6f, 0.6f);
        Location root = new Location(world, grabFrom[0], grabFrom[1], grabFrom[2]);
        List<ItemDisplay> gone = new ArrayList<>(tendril);
        tendril.clear();
        for (ItemDisplay seg : gone) {
            seg.setTeleportDuration(10);
            seg.setInterpolationDelay(0);
            seg.setInterpolationDuration(10);
            seg.setTransformationMatrix(new Matrix4f().scale(0.01f));
            seg.teleport(root);
        }
        after(12, () -> gone.forEach(Entity::remove));
    }

    /** 挥一下手：缠着的时候算扯一下。 */
    void wellSwing(Player p) {
        if (grabbing < 0) return;
        grabTugs++;
        fx.send(p, "jolt");
        sound(p, "entity.slime.squish", 0.8f, 0.6f + grabTugs * 0.05f);
    }

    /** 踩上那层肉垫：脚被吸住，走不动，肉垫鼓起来裹住脚。连按空格拔出来；不蹬，过十秒它自己松开。 */
    private void suckTick(Player p, Location at, int hold) {
        if (sucking >= 0) {
            holdSuck(p, at);
            return;
        }
        if (hold < 0 || breathHold > 0 || grabbing >= 0 || carRiding || carDown) return;
        for (int i = 0; i < Well.SUCK.length; i++) {
            if (sucked[i] || hold != Well.SUCK[i]) continue;
            sucked[i] = true;
            sucking = i;
            suckStart = age;
            suckTugs = 0;
            stuckAt = at.clone();
            Bukkit.getLogger().info("肉垫 吸脚 " + i);
            p.setWalkSpeed(0f);
            sound(p, "block.honey_block.step", 1f, 0.5f);
            sound(p, "entity.slime.squish", 1f, 0.4f);
            fx.send(p, "sick 0.3 20");
            padSwell(i, 0.22f, 10);
            if (!suckHinted) {
                suckHinted = true;
                fx.send(p, "sub 提示|脚被吸住了。连按空格，拔出来。|110");
            }
            return;
        }
    }

    private void holdSuck(Player p, Location at) {
        int t = age - suckStart;
        // 走不了：脚底下挪开了就拉回原地，脸朝哪不动
        if (Math.hypot(at.getX() - stuckAt.getX(), at.getZ() - stuckAt.getZ()) > 0.25) {
            Location back = stuckAt.clone();
            back.setYaw(at.getYaw());
            back.setPitch(at.getPitch());
            p.teleport(back);
        }
        if (t % 23 == 0) sound(p, "block.honey_block.slide", 0.7f, 0.4f + rnd.nextFloat() * 0.15f);
        if (suckTugs >= 5 || t > 200) {
            boolean tore = suckTugs >= 5;
            Bukkit.getLogger().info(tore ? "肉垫 拔出来" : "肉垫 自己松开");
            sound(p, "block.slime_block.break", 1f, 0.45f);
            sound(p, "entity.slime.jump", 0.8f, 0.5f);
            padSwell(sucking, 0.05f, 14);
            sucking = -1;
            stuckAt = null;
            fx.send(p, "sick 0 20");
            if (breathHold == 0) p.setWalkSpeed(0.2f);
        }
    }

    /** 跳一下：脚被吸着的时候算蹬一下，肉垫跟着一抽。 */
    void wellJump(Player p) {
        if (sucking < 0) return;
        suckTugs++;
        sound(p, "entity.slime.squish", 0.9f, 0.55f + suckTugs * 0.06f);
        padSwell(sucking, 0.16f + (suckTugs % 2) * 0.06f, 4);
    }

    private void padSwell(int i, float h, int ticks) {
        double yaw = Well.HOLDS.get(Well.SUCK[i]).yaw();
        for (int k = i; k < i + 1 && k < pads.size(); k++) {
            ItemDisplay pad = pads.get(k);
            pad.setInterpolationDelay(0);
            pad.setInterpolationDuration(ticks);
            pad.setTransformationMatrix(padShape(yaw, h));
        }
    }

    /** 肉垫：一层肉压得很扁，在楼板上摊开一大片，长的那头顺着往下走的方向。吸住脚的时候鼓起来一点。 */
    private static Matrix4f padShape(double yaw, float h) {
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        return new Matrix4f().rotateY((float) Math.atan2(-dz, dx)).scale(2.6f, h, 1.9f);
    }

    /** 肉箍那一截：落上去就提示蹲下。 */
    private void crouchTick(Player p, int hold) {
        if (crouchHinted || hold != Well.SQUEEZE) return;
        crouchHinted = true;
        fx.send(p, "sub 提示|蹲下，钻过去。|80");
    }

    /** 井喘一口：整圈肉往里一收，脚下那一块跟着一沉，头顶的 4 号支撑、钢梁咯吱响。吸气的时候走不动，等它呼出去再走。 */
    private void breathTick(Player p, boolean onPath) {
        // 正被肉缠着、吸着的时候不喘，免得两头都在改走路的速度
        if (breathHold == 0 && (grabbing >= 0 || sucking >= 0)) return;
        if (breathHold > 0) {
            if (--breathHold == 0) {
                p.setWalkSpeed(0.2f);
                fx.play(p, "voice", "breath_out", 0.55f, 0.8f, false, 0);
                fx.send(p, "sick 0 30");
            }
            return;
        }
        if (!onPath) {
            breathNext = Math.max(breathNext, age + 120);
            return;
        }
        if (breathNext == 0) breathNext = age + 200;
        if (age < breathNext) return;
        breathNext = age + BREATH_EVERY + rnd.nextInt(160);
        breathHold = BREATH_HOLD;
        p.setWalkSpeed(0f);
        fx.play(p, "voice", "breath_in", 0.7f, 0.75f, false, 0);
        sound(p, "block.chain.break", 0.9f, 0.45f);
        sound(p, "entity.iron_golem.damage", 0.5f, 0.4f);
        fx.send(p, "jolt");
        fx.send(p, "sick 0.5 10");
        if (breaths++ < 2) fx.send(p, "sub 提示|贴着墙，别动。|60");
    }

    /** 肉块那一段：对面井壁上那串头灯隔着井心在你正对面，比你高一点。你动它就挪，你停它就停。 */
    private void lampTick(Player p, Location at) {
        if (headlamps.isEmpty() || sticksThrown || carRiding || carDown) return;
        if (at.getY() > Well.LAMPS_BELOW || at.getY() < Well.BAG_Y + 1.5 || onBag(at)) return;
        double[] now = {at.getX(), at.getY(), at.getZ()};
        if (lampAt != null && Math.abs(now[0] - lampAt[0]) + Math.abs(now[1] - lampAt[1]) + Math.abs(now[2] - lampAt[2]) < 0.15) return;
        lampAt = now;
        double[] q = lampSpot(now);
        for (int i = 0; i < headlamps.size(); i++) {
            int k = i / 2;
            boolean hat = i % 2 == 1;
            double side = (k - 2) * 0.9;
            headlamps.get(i).teleport(new Location(world, q[0] + q[3] * side - (hat ? 0.15 : 0), q[1] - k * 1.3 + (hat ? 0.36 : 0), q[2] + q[4] * side - (hat ? 0.15 : 0)));
        }
    }

    /** 人在 at，头灯挂在哪：井心另一边的井壁上，比人眼高一点。返回 x、y、z 和顺着井壁横过去的方向。 */
    private static double[] lampSpot(double[] at) {
        double a = Math.atan2(at[2] - Well.SZ, at[0] - Well.SX) + Math.PI, y = at[1] + 3.5;
        double r = Well.radius(y) - 0.7;
        return new double[]{Well.SX + Math.cos(a) * r, y, Well.SZ + Math.sin(a) * r, -Math.sin(a), Math.cos(a)};
    }

    /** 扔一根冷光棒：绿光往下掉了很久，在很深的地方磕了一下，照出一小片斜着的玻璃，又滚下去灭了。 */
    private void throwStick(Player p) {
        if (sticksThrown) return;
        sticksThrown = true;
        Bukkit.getLogger().info("冷光棒");
        sound(p, "item.trident.throw", 0.7f, 1.4f);
        Location eye = p.getEyeLocation();
        // 从平台北边扔出去，落进喉咙中间
        double startX = Math.max(Well.BAG_X0 + 0.5, Math.min(Well.BAG_X1 + 0.5, eye.getX())), startZ = Well.BAG_Z0 - 1.6, startY = eye.getY();
        stickGlow = box("minecraft:lime_concrete", startX, startY, startZ, 0.06, 0.4, 0.06, 15);
        stickGlow.setTeleportDuration(3);
        int fall = 64;
        for (int t = 1; t <= fall; t++) {
            int tt = t;
            after(t, () -> {
                if (stickGlow == null || !stickGlow.isValid()) return;
                double k = tt / (double) fall;
                double y = startY + (Well.REAL_FLOOR + 6 - startY) * k * k;
                stickGlow.teleport(new Location(world, startX + (Well.SX - startX) * 0.6 * k, y, startZ + (Well.SZ - startZ) * 0.6 * k));
            });
        }
        after(fall + 10, () -> {
            sound(p, "block.glass.hit", 0.35f, 1.4f);
            if (stickGlow != null) stickGlow.setBrightness(new Display.Brightness(15, 0));
        });
        after(fall + 40, () -> {
            if (stickGlow != null) stickGlow.remove();
            stickGlow = null;
        });
    }

    /** 上 807 号车：顺着断缆往下滑。 */
    private void boardCar(Player p) {
        if (carRiding || carDown || car807 == null) return;
        carRiding = true;
        carT = 0;
        Bukkit.getLogger().info("807 发车");
        for (int x = Well.BAG_X0; x <= Well.BAG_X1; x++) for (int y = Well.BAG_Y; y <= Well.BAG_Y + 1; y++) {
            if (world.getBlockAt(x, y, Well.BAG_Z0 - 1).getType() == Material.BARRIER) world.getBlockAt(x, y, Well.BAG_Z0 - 1).setType(Material.AIR, false);
        }
        car807.root.addPassenger(p);
        sound(p, "block.iron_door.close", 1f, 0.6f);
        sound(p, "block.chain.place", 1f, 0.5f);
        after(20, () -> {
            fx.send(p, "jolt");
            sound(p, "block.chain.break", 1f, 0.6f);
            car807.power(true);
        });
    }

    private void carTick(Player p) {
        if (!carRiding || car807 == null) return;
        carT++;
        if (carT < 30) return;
        double t = Math.min(1, (carT - 30) / (double) CAR_TICKS);
        // 一开始慢慢松开，中间滑得快，到底前刹一下
        double e = t * t * (3 - 2 * t);
        double[] a = Well.CAR_FROM, b = Well.CAR_TO;
        car807.moveTo(a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e, a[2] + (b[2] - a[2]) * e);
        if (carT % 7 == 0) sound(p, "block.chain.step", 0.6f, 0.55f + rnd.nextFloat() * 0.1f);
        if (carT % 53 == 0) sound(p, "block.iron_trapdoor.close", 0.7f, 0.5f);
        if (carT == 30 + CAR_TICKS * 45 / 100) {
            // 出雾，底下那栋楼露出来
            fx.fog(p, 14, 110, 1f, "100605", 1f);
            sound(p, "entity.warden.ambient", 0.4f, 0.5f);
        }
        if (t >= 1) {
            carRiding = false;
            carDown = true;
            Bukkit.getLogger().info("807 到底");
            sound(p, "block.anvil.land", 1f, 0.5f);
            sound(p, "entity.iron_golem.damage", 0.8f, 0.4f);
            fx.send(p, "jolt");
            allowBusDismount = true;
            p.leaveVehicle();
            allowBusDismount = false;
            p.teleport(new Location(world, b[0] - 3, Well.REAL_FLOOR, b[2] + 4.4, -143, 10));
            car807.power(false);
        }
    }

    /**
     * 防水袋平台：先提示扔冷光棒，再提示车门开着。每一步 40 秒不动，就自己往下走，流程不卡在这里。
     */
    private int bagAt = -1, carHintAt = -1;

    private void bagTick(Player p, Location at) {
        if (carRiding || carDown) return;
        boolean onBag = ((org.bukkit.entity.Entity) p).isOnGround() && onBag(at);
        if (bagAt < 0) {
            if (!onBag) return;
            bagAt = age;
            fx.send(p, "sub 提示|扔一根下去。|70");
            return;
        }
        if (carHintAt < 0 && (sticksThrown || age > bagAt + HINT_WAIT)) {
            carHintAt = age + (sticksThrown ? 90 : 0);
            after(sticksThrown ? 90 : 0, () -> {
                if (!carRiding && !carDown) fx.send(p, "sub 提示|车门开着。|70");
            });
            return;
        }
        if (carHintAt >= 0 && age > carHintAt + HINT_WAIT) boardCar(p);
    }

    private static boolean onBag(Location at) {
        return Math.abs(at.getY() - Well.BAG_Y) < 0.6 && at.getX() >= Well.BAG_X0 - 0.5 && at.getX() <= Well.BAG_X1 + 1 && at.getZ() >= Well.BAG_Z0 - 0.5 && at.getZ() <= Well.BAG_Z1 + 1;
    }

    /** 要玩家动手的地方，等多久就自己往下走（tick）。 */
    private static final int HINT_WAIT = 800;

    /**
     * 第五关怎么收：花园层那三张原件（留言卡、就诊登记、林静的录音带标签）都读完，跟前的水面上漂着搜救二队的对讲机。
     * 点它放最后录下的一段；放完，井口上面隐隐约约传下来林静的声音（上面那座楼在照着那一晚播停电广播），然后疯。
     */
    private static final List<String> LAST_ORIGINALS = List.of("rg1", "rg2", "rg7");
    private boolean endingDue;

    private void maybeEnd(Player p) {
        if (endingDue || madness || !read.containsAll(LAST_ORIGINALS)) return;
        endingDue = true;
        Bukkit.getLogger().info("花园层原件读完，收尾");
        after(60, () -> walkieIn(p));
    }

    // ---------- 收尾：搜救二队的对讲机 ----------

    /** 对讲机录音的字幕：开始(tick)、长度(tick)、谁、字幕。客户端资源 生成对讲机录音.py 写的；第一行 total 是整段多长。 */
    // 先声明长度、后读文件：反过来的话 loadWalkie 写进去的长度会被这里的初始值盖回 1600
    private static int walkieTotal = 1600;
    private static final List<String[]> WALKIE = loadWalkie();

    private static List<String[]> loadWalkie() {
        List<String[]> rows = new ArrayList<>();
        try (java.io.InputStream in = Story.class.getResourceAsStream("/walkie.txt")) {
            if (in == null) return rows;
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\t", -1);
                if (f.length < 2) continue;
                if (f[0].equals("total")) walkieTotal = Integer.parseInt(f[1]);
                else if (f.length >= 4) rows.add(f);
            }
        } catch (java.io.IOException e) {
            Bukkit.getLogger().warning("对讲机录音的字幕读不到：" + e);
        }
        return rows;
    }

    /** 0 没有，1 漂在水里一闪一闪，2 在放，3 放完了。 */
    private int walkieState;
    private BlockDisplay walkieLed;
    private double[] walkieAt;

    /**
     * 花园层三张原件读完：跟前的水面上沙沙响，漂着一台橙色的对讲机，"搜救二队"，录音灯一闪一闪。
     * 7 月 6 日搜救二队上去取绳，第二天凌晨五个人接上绳下到了底，没再上去。这是那台对讲机自动录下的最后一段。
     */
    private void walkieIn(Player p) {
        if (walkieState != 0) return;
        Location eye = p.getEyeLocation();
        double[] q = null;
        for (double[] at : new double[][]{{0, 2.4}, {-25, 2.4}, {25, 2.4}, {0, 3.2}, {-50, 2.2}, {50, 2.2}}) {
            q = spotAt(eye, at[0], at[1]);
            if (q != null) break;
        }
        if (q == null) q = new double[]{eye.getX() + eye.getDirection().getX() * 1.5, Well.REAL_GARDEN, eye.getZ() + eye.getDirection().getZ() * 1.5};
        double x = q[0], y = Well.REAL_GARDEN + 0.33, z = q[2];
        walkieAt = new double[]{x, y, z};
        walkieState = 1;
        // 橙色的壳子平躺在水面上，一根黑天线，上面一块灰的喇叭网，一颗红灯，一张白标签
        box("minecraft:orange_concrete", x - 0.07, y, z - 0.19, 0.14, 0.06, 0.38, 7);
        box("minecraft:black_concrete", x - 0.012, y + 0.02, z - 0.33, 0.024, 0.024, 0.15, 5);
        box("minecraft:gray_concrete", x - 0.05, y + 0.06, z - 0.12, 0.1, 0.004, 0.14, 6);
        box("minecraft:white_concrete", x - 0.05, y + 0.06, z + 0.06, 0.1, 0.004, 0.08, 8);
        walkieLed = box("minecraft:red_concrete", x + 0.03, y + 0.06, z - 0.17, 0.025, 0.012, 0.025, 15);
        Interaction hit = interaction(x, y - 0.15, z, 0.7f, 0.4f);
        park.onClick(hit, this::playWalkie);
        fx.send(p, "tag walkie|" + x + "|" + (y + 0.15 + TAG_ABOVE) + "|" + z + "|0|搜救二队 · 对讲机");
        // 一阵一阵的沙沙声，就在跟前
        fx.play(p, "walkiebed", "radio_static", 0.22f, 0.8f, true, 10);
        sound(p, "block.note_block.bit", 0.3f, 0.6f);
        Bukkit.getLogger().info(String.format("对讲机 在 %.2f %.2f %.2f", x, y, z));
        after(400, () -> {
            if (walkieState == 1) fx.send(p, "sub 提示|水里有东西在响。|60");
        });
    }

    /** 录音灯：没点的时候一闪一闪，放的时候一直亮。 */
    private void walkieTick() {
        if (walkieLed == null || !walkieLed.isValid()) return;
        boolean on = walkieState == 2 || walkieState == 1 && age / 10 % 2 == 0;
        walkieLed.setBlock(Bukkit.createBlockData(on ? "minecraft:red_concrete" : "minecraft:black_concrete"));
    }

    /** 点一下：放最后录下的那一段。放完，上面传下来林静的停电广播，然后疯。 */
    void playWalkie(Player p) {
        if (walkieState != 1) return;
        walkieState = 2;
        dropTag(p, "walkie");
        Bukkit.getLogger().info("对讲机 放录音 " + walkieTotal + " tick");
        sound(p, "block.lever.click", 0.7f, 1.5f);
        fx.gain(p, "walkiebed", 0f, 10);
        after(10, () -> fx.play(p, "walkie", "walkie_rec", 1f, 1f, false, 0));
        replay(p);
        for (String[] row : WALKIE) {
            int at = 10 + Integer.parseInt(row[0]), len = Integer.parseInt(row[1]);
            // 长句一行放不下：按句号拆成几条，按字数分时间，一条接一条出
            List<String> parts = subParts(row[3]);
            int total = 0;
            for (String part : parts) total += part.length();
            int done = 0;
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                int from = at + len * done / total, to = at + len * (done + part.length()) / total;
                done += part.length();
                int show = i == parts.size() - 1 ? to - from + 20 : to - from + 2;
                after(from, () -> fx.send(p, "sub " + row[2] + "|" + part + "|" + show));
            }
        }
        int end = 10 + walkieTotal;
        after(end, () -> {
            walkieState = 3;
            sound(p, "block.lever.click", 0.6f, 1.0f);
        });
        after(end + 50, () -> {
            fx.subtitle(p, "广播", "pa_stopped");
            fx.play(p, "voice", "pa_stopped", 0.22f, 0.94f, false, 0);
        });
        after(end + 50 + 150, () -> madness(p));
    }

    // ---------- 收尾：老周头盔记录仪那一段 ----------

    /**
     * 录音放的时候，画面换成老周头盔上记录仪拍下的那一段（2007 年 7 月 7 日凌晨）：从大厅的楼梯下到花园层，
     * 一屋子人坐在水里，全背对着楼梯朝西坐，叫不醒，一起喘气；老周绕到前面看他们的脸，抬头看井口；
     * 往楼梯走的时候小罗已经坐进人堆里，"这边"一出来，所有人一起转过来看镜头；老周也坐下了，"别下来"掐断，回到现在。
     * 记录仪是一段一段录的，跳一下（花屏）就是中间没录上的。镜头每段的起点都先把人传送过去，不然一下挪十几格服务端会拽回来。
     */
    private boolean replayOn;
    private Location replayBack;
    private final List<ItemDisplay> crowd = new ArrayList<>(), rescuers = new ArrayList<>();
    private ItemDisplay luo;
    /** 坐在地上的人形往下沉多少：坐姿是照坐椅子雕的，沉下去腿埋进水底，像坐在水里。 */
    private static final double SINK = 0.42;
    private static final double RF = Well.REAL_GARDEN, EW = RF + 1.62;
    /** 一口气多长（tick）：吸 30，呼 36。整屋子都是这个节奏。 */
    private static final int BREATH_IN = 30, BREATH_OUT = 36;

    /** 录音里第 i 句开始的那一刻（从点下去算，tick）。 */
    private static int line(int i) {
        return i < WALKIE.size() ? 10 + Integer.parseInt(WALKIE.get(i)[0]) : 10 + walkieTotal;
    }

    private static int lineLen(int i) {
        return i < WALKIE.size() ? Integer.parseInt(WALKIE.get(i)[1]) : 40;
    }

    private static String k(double x, double eye, double z, double yaw, double pitch) {
        return String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f", x, eye, z, yaw, pitch);
    }

    /** 从 from 到 to 这一段镜头：起点先把人传过去，再让客户端沿着机位匀速走。cut 是跳一下（记录仪中间没录上）。 */
    private void camSeg(Player p, int from, int to, boolean cut, String... keys) {
        after(from, () -> {
            if (!replayOn) return;
            String[] f = keys[0].split(" ");
            p.teleport(new Location(world, Double.parseDouble(f[0]), Double.parseDouble(f[1]) - 1.62, Double.parseDouble(f[2]), Float.parseFloat(f[3]), Float.parseFloat(f[4])));
            if (cut) {
                fx.send(p, "glitch 9");
                sound(p, "block.note_block.hat", 0.25f, 0.5f);
            }
            fx.send(p, "cam lin " + Math.max(1, to - from) + " " + String.join(";", keys));
        });
    }

    private static float face(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    private void replay(Player p) {
        replayOn = true;
        replayBack = p.getLocation();
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        p.setFlySpeed(0f);
        java.util.Random r = new java.util.Random(707);
        // 坐了一屋子：楼梯西边那一半，避开野餐桌和花槽，楼梯口那一块空着
        double[][] tables = {{500, 3}, {505, 0}, {498, -1}};
        String[] kinds = {"tourist", "tourist", "tourist", "tourist", "kid", "cook", "guide", "tester"};
        int[] counts = {60, 60, 60, 60, 10, 6, 6, 11};
        int n = 0;
        for (double x = 496.6; x <= 506.4; x += 1.45) {
            for (double z = -4.3; z <= 10.2; z += 1.55) {
                double jx = x + (r.nextDouble() - 0.5) * 0.5, jz = z + (r.nextDouble() - 0.5) * 0.5;
                if (jx > 503.8 && jz > 3.4) continue;
                if (jx < 496.2 && jz > -0.6 && jz < 4.4) continue;
                boolean blocked = false;
                for (double[] t : tables) if (jx > t[0] - 0.9 && jx < t[0] + 2.4 && jz > t[1] - 0.5 && jz < t[1] + 2.0) blocked = true;
                if (jz < -2.4 && jz > -3.6 && jx > 495.6 && jx < 500.4) blocked = true;
                if (jz > 8.4 && jz < 9.6 && jx > 501.6 && jx < 506.4) blocked = true;
                if (blocked) continue;
                int kind = r.nextInt(kinds.length);
                String model = "fig_" + kinds[kind] + "_" + r.nextInt(counts[kind]) + "_sit";
                ItemDisplay d = item(model, jx, RF + 1 - SINK, jz, 90f + (float) ((r.nextDouble() - 0.5) * 18));
                d.setTeleportDuration(40);
                crowd.add(d);
                n++;
            }
        }
        // 野餐桌两边的长凳上也坐着，脸朝桌子
        for (double[] t : tables) {
            crowd.add(item("fig_tourist_" + r.nextInt(60) + "_sit", t[0] - 0.25, RF + 1, t[1] + 0.75, -90f));
            crowd.add(item("fig_tourist_" + r.nextInt(60) + "_sit", t[0] + 1.75, RF + 1, t[1] + 0.75, 90f));
        }
        for (ItemDisplay d : crowd) d.setTeleportDuration(40);
        Bukkit.getLogger().info("回放 人形 " + crowd.size());
        // 二队另外四个：小罗在楼梯口前面，一个在前头拽人，一个站在前面看，一个在桌子边上
        luo = item("fig_rescue_0", 507.0, RF + 1, 4.9, 105f);
        ItemDisplay pull = item("fig_rescue_1_reach", 495.9, RF + 1, 3.2, face(495.9, 3.2, 496.9, 2.0));
        ItemDisplay look = item("fig_rescue_2", 494.6, RF + 1, 6.6, -70f);
        ItemDisplay side = item("fig_rescue_3", 507.6, RF + 1, -2.2, 120f);
        rescuers.addAll(List.of(luo, pull, look, side));
        crowd.add(item("fig_tourist_7_sit", 496.9, RF + 1 - SINK, 2.0, 90f));

        fx.send(p, "helmet 03:24:16");
        fx.send(p, "camshake 1");
        // 一屋子人一起喘：人形一起鼓起来、瘪下去，水里一片喘气声，一开始轻，说到"同步"那句最响
        int end = line(15) + lineLen(15);
        for (int at = 12, i = 0; at < end; at += BREATH_IN + BREATH_OUT, i++) {
            int when = at;
            float vol = when < line(3) - 20 ? 0.25f : when < line(4) ? 0.75f : 0.5f;
            after(when, () -> breatheCrowd(p, true, vol));
            after(when + BREATH_IN, () -> breatheCrowd(p, false, vol));
        }

        double e = EW;
        // 0 → 1：下楼梯，小罗在前头，水在底下
        camSeg(p, 10, line(1), false, k(516.5, 13.1, 6, 90, 30), k(513.5, 10.1, 6, 90, 28), k(510.6, 7.2, 6, 92, 22), k(508.7, e, 5.7, 96, 14));
        // 1 → 2："人找着了，好多，都坐着呢"：看着一排排背影扫过去
        camSeg(p, line(1), line(2) - 4, false, k(508.7, e, 5.7, 96, 14), k(508.4, e, 5.2, 68, 10), k(508.1, e, 4.8, 118, 11), k(507.9, e, 4.6, 92, 13));
        // 2 → 3："叫不醒"：跳到前面，拽人的那个跟前，一张灰脸
        camSeg(p, line(2) - 4, line(3), true, k(495.3, e - 0.1, 0.7, -38, 24), k(495.2, e - 0.1, 0.9, -46, 22));
        // 3 → 4："喘气是同步的"：往后退，一排排脸，一起鼓一起瘪
        camSeg(p, line(3), line(4), false, k(495.2, e - 0.1, 0.9, -46, 22), k(494.2, e, 1.7, -78, 14), k(493.5, e + 0.15, 2.5, -90, 9), k(493.5, e + 0.15, 2.5, -90, 9));
        // 4 → 7："药？断了几天了？那它是醒着的？"：抬头四处看
        camSeg(p, line(4), line(7), false, k(493.5, e + 0.15, 2.5, -90, 9), k(493.6, e + 0.1, 2.4, -78, -24), k(493.6, e + 0.1, 2.3, -112, -34), k(493.6, e + 0.1, 2.3, -95, -8));
        // 7 → 9："那这些人呢？救不了是吧"：又看回那些脸，最后低头看水
        camSeg(p, line(7), line(9), false, k(493.6, e + 0.1, 2.3, -95, -8), k(494.2, e, 2.4, -88, 17), k(494.3, e - 0.05, 2.4, -90, 56));
        // 9 → 11："啥楼？井口？"：猛抬头
        camSeg(p, line(9), line(11) - 4, false, k(494.3, e - 0.05, 2.4, -90, 56), k(494.4, e, 2.4, -90, -50), k(494.5, e, 2.4, -82, -62));
        // 11 → 12："我们上去。小罗！走了！"：跳到楼梯口，朝上
        camSeg(p, line(11) - 4, line(12), true, k(508.1, e, 5.7, -90, -6), k(509.4, e + 0.5, 6.0, -90, -14));
        // 12 → 13："小罗？"：回头，小罗已经坐进人堆里，慢慢走过去
        camSeg(p, line(12), line(13), false, k(509.4, e + 0.5, 6.0, -90, -14), k(509.4, e + 0.5, 6.0, 25, 4), k(509.2, e + 0.45, 5.9, 95, 10), k(507.6, e, 5.2, 96, 14), k(505.7, e, 4.9, 97, 20));
        // 13 → 15："队长，这边。""这边。这边。"：所有人转过来，往后一退，然后慢慢坐下去
        camSeg(p, line(13), line(15), false, k(505.7, e, 4.9, 97, 20), k(505.8, e, 4.9, 95, 17), k(506.4, e, 5.1, 93, 11), k(506.4, e - 0.85, 5.1, 93, 5));

        // 往楼梯走的那几秒（镜头朝着楼梯），四个人都坐下了：小罗坐进人堆前头
        after(line(11) + 4, () -> {
            if (!replayOn) return;
            for (ItemDisplay d : rescuers) d.remove();
            rescuers.clear();
            luo = item("fig_rescue_0_sit", 504.1, RF + 1 - SINK, 4.7, 92f);
            rescuers.add(luo);
            rescuers.add(item("fig_rescue_1_sit", 495.9, RF + 1 - SINK, 3.2, 90f));
            rescuers.add(item("fig_rescue_2_sit", 494.8, RF + 1 - SINK, 6.4, 90f));
            rescuers.add(item("fig_rescue_3_sit", 507.4, RF + 1 - SINK, -2.0, 90f));
            for (ItemDisplay d : rescuers) d.setTeleportDuration(40);
        });
        // "这边。这边。"：一屋子人一起转过来，看着镜头
        after(line(14), () -> {
            if (!replayOn) return;
            double cx = 506.3, cz = 5.0;
            List<ItemDisplay> all = new ArrayList<>(crowd);
            all.addAll(rescuers);
            for (ItemDisplay d : all) {
                if (!d.isValid()) continue;
                Location at = d.getLocation();
                at.setYaw(face(at.getX(), at.getZ(), cx, cz));
                d.teleport(at);
            }
            sound(p, "block.sculk_sensor.clicking", 0.5f, 0.5f);
        });
        // 老周也跟上了：晃得越来越少
        after(line(14) + 50, () -> fx.send(p, "camshake 0.4"));
        after(line(15), () -> fx.send(p, "camshake 0.15"));
        // "别下来——"掐断：花屏，回到现在，水里只剩那台对讲机
        after(end - 6, () -> fx.send(p, "glitch 16"));
        after(end, () -> endReplay(p));
    }

    private void breatheCrowd(Player p, boolean in, float vol) {
        if (!replayOn) return;
        org.bukkit.util.Transformation big = new org.bukkit.util.Transformation(new org.joml.Vector3f(0, 0.02f, 0), new org.joml.Quaternionf(), new org.joml.Vector3f(1.035f, 1.05f, 1.035f), new org.joml.Quaternionf());
        org.bukkit.util.Transformation small = new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(1, 1, 1), new org.joml.Quaternionf());
        for (ItemDisplay d : crowd) {
            if (!d.isValid()) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(in ? BREATH_IN : BREATH_OUT);
            d.setTransformation(in ? big : small);
        }
        fx.play(p, "crowd", in ? "breath_in" : "breath_out", vol, 0.62f, false, 0);
        fx.play(p, "amb", in ? "breath_in" : "breath_out", vol * 0.6f, 0.48f, false, 0);
    }

    private void endReplay(Player p) {
        if (!replayOn) return;
        replayOn = false;
        for (ItemDisplay d : crowd) d.remove();
        for (ItemDisplay d : rescuers) d.remove();
        crowd.clear();
        rescuers.clear();
        fx.send(p, "cam off");
        fx.send(p, "camshake 0");
        fx.send(p, "helmet off");
        fx.send(p, "glitch 6");
        p.setFlySpeed(0.1f);
        p.setGameMode(org.bukkit.GameMode.ADVENTURE);
        if (replayBack != null) p.teleport(replayBack);
        Bukkit.getLogger().info("回放 结束");
    }

    /** 字幕一条最多这么多字：再长一行放不下。 */
    private static final int SUB_MAX = 22;

    /** 按句号、感叹号、问号拆开，短的并到一起，每条不超过 SUB_MAX 个字。 */
    private static List<String> subParts(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            cur.append(c);
            boolean end = c == '。' || c == '！' || c == '？';
            // 句号后面紧跟着的引号、省略号、破折号算在这一句里
            while (end && i + 1 < text.length() && "”……—".indexOf(text.charAt(i + 1)) >= 0) cur.append(text.charAt(++i));
            if (end) {
                sentences.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) sentences.add(cur.toString());
        // 一句太长放不下：按逗号再拆成几段，每段尽量凑满
        List<String> pieces = new ArrayList<>();
        for (String sentence : sentences) {
            if (sentence.length() <= SUB_MAX) {
                pieces.add(sentence);
                continue;
            }
            StringBuilder seg = new StringBuilder();
            for (String clause : sentence.split("(?<=，)")) {
                if (seg.length() > 0 && seg.length() + clause.length() > SUB_MAX) {
                    pieces.add(seg.toString());
                    seg.setLength(0);
                }
                seg.append(clause);
            }
            if (seg.length() > 0) pieces.add(seg.toString());
        }
        sentences = pieces;
        List<String> parts = new ArrayList<>();
        for (String sentence : sentences) {
            int last = parts.size() - 1;
            if (last >= 0 && parts.get(last).length() + sentence.length() <= SUB_MAX) parts.set(last, parts.get(last) + sentence);
            else parts.add(sentence);
        }
        return parts;
    }

    /** 花园层这一处能不能站人：返回脚底的高度，站不了是 NaN。 */
    private double gardenFloor(double x, double z) {
        if (x < 111 + Well.DX || x > 151 + Well.DX || z < -13 || z > 11) return Double.NaN;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int y = Well.REAL_GARDEN + 1; y >= Well.REAL_GARDEN - 1; y--) {
            if (!world.getBlockAt(bx, y - 1, bz).isPassable() && world.getBlockAt(bx, y, bz).isPassable()
                    && world.getBlockAt(bx, y + 1, bz).isPassable()) return y;
        }
        return Double.NaN;
    }

    /** 在你朝的方向偏 angle 度、离 dist 远的地方找一处空着、看得见的。 */
    private double[] spotAt(Location eye, double angle, double dist) {
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(eye.getYaw() + angle + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * 9);
            double x = eye.getX() - Math.sin(a) * dist, z = eye.getZ() + Math.cos(a) * dist;
            double y = gardenFloor(x, z);
            if (Double.isNaN(y)) continue;
            Vector to = new Vector(x - eye.getX(), y + 0.4 - eye.getY(), z - eye.getZ());
            double d = to.length();
            if (world.rayTraceBlocks(eye, to.normalize(), d, org.bukkit.FluidCollisionMode.NEVER, true) == null) return new double[]{x, y, z};
        }
        return null;
    }

    /**
     * 疯。不出声，只有呼吸：先是"我"自己喘，又急又乱；井的呼吸一下一下压过来，"我"的呼吸被带过去，跟它一个节奏
     * （跟对讲机里小罗一样）。手电一闪一闪，手变灰。手机自己举起来：它替你打了五星传上去，公司收到了，下一个下周出发。
     * 然后视野撑开、变红，硬切黑，接片尾。
     */
    private void madness(Player p) {
        if (madness) return;
        madness = true;
        amb(p, "", 0);
        // 我：喘得又急又乱
        int[] fast = {10, 26, 40, 58, 70, 88};
        for (int i = 0; i < fast.length; i++) {
            boolean in = i % 2 == 0;
            int at = fast[i];
            after(at, () -> fx.play(p, "voice", in ? "breath_in" : "breath_out", 0.55f, 1.45f, false, 0));
        }
        // 它：一口一口压过来，慢、沉。我的呼吸跟着它，越来越齐
        for (int i = 0, at = 100; at <= 480; i++, at += 46) {
            boolean in = i % 2 == 0;
            after(at, () -> {
                fx.play(p, "amb", in ? "breath_in" : "breath_out", 0.8f, 0.7f, false, 0);
                fx.play(p, "voice", in ? "breath_in" : "breath_out", 0.5f, 1.15f, false, 0);
            });
        }
        after(120, () -> flashCut = 1);
        after(150, () -> greyHands(p));
        // 手机自己举起来，停在屏幕上让人读完
        after(220, () -> {
            fx.sms(p, "息壤国家公园", "07-04 23:05", "感谢您的评价。体验官 #0011：★★★★★，已上传。本终端已上传体验官 11 位，平均 5.0 分。我们会改进。");
            fx.send(p, "phone show");
        });
        // 公司那条：派人下来到底要什么（体验官本人回到地面确认评分，满三份就申请重开），一个没回来，接着派
        after(330, () -> {
            fx.sms(p, "二叠纪公司", "", "体验官 #0011：评分已收到，5.0 分。重新开放评估：已收到体验官评分 11 份，平均 5.0；体验官本人返回地面确认 0 份。确认满 3 份即提交重开申请。第十二位体验官下周出发。");
            fx.send(p, "phone show");
        });
        after(480, () -> fx.send(p, "sick 0.35 40"));
        after(498, () -> fx.send(p, "phone hide"));
        // 最后那几秒：视野往外撑开，画面一层层变红，心跳越来越快，底下一股声音往上顶
        int f = 500;
        after(f, () -> {
            fx.send(p, "fov 1.9 78");
            fx.send(p, "red 1 78");
            sound(p, "entity.warden.sonic_charge", 1f, 0.55f);
        });
        int[] beats = {0, 21, 37, 49, 58, 65, 70, 74};
        for (int i = 0; i < beats.length; i++) {
            float pitch = 0.75f + i * 0.04f;
            after(f + beats[i], () -> sound(p, "entity.warden.heartbeat", 1.4f, pitch));
        }
        after(f + 33, () -> sound(p, "ambient.basalt_deltas.mood", 1f, 0.5f));
        after(f + 47, () -> sound(p, "block.sculk_shrieker.shriek", 0.8f, 0.5f));
        after(f + 55, () -> fx.play(p, "voice", "breath_in", 1f, 0.5f, false, 0));
        // 一下掐断：黑，什么声音都没有
        after(f + 78, () -> {
            fx.gain(p, "voice", 0f, 1);
            fx.gain(p, "amb", 0f, 1);
            p.stopAllSounds();
            fx.send(p, "audio stopall");
            fx.black(p, 1f, 1);
            flashCut = 2;
            fx.send(p, "sick 0 1");
            fx.send(p, "red 0 1");
            fx.send(p, "fov 1 1");
        });
        after(f + 118, () -> {
            Bukkit.getLogger().info("片尾");
            fx.send(p, "endcard");
        });
    }

    // ---------- 零件（从旧后三关留下来的） ----------

    /** 截了哪几张：0 拍照点。 */
    private final boolean[] snapped = new boolean[1];

    private TextDisplay text(String body, double x, double y, double z, float yaw, int color, float scale, int background, int glow) {
        TextDisplay d = world.spawn(new Location(world, x, y, z, yaw, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.text(Component.text(body).color(TextColor.color(color)));
            t.setBackgroundColor(Color.fromARGB(background));
            t.setShadowed(false);
            t.setLineWidth(400);
            if (glow >= 0) t.setBrightness(new Display.Brightness(glow, 0));
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        scene.add(d);
        return d;
    }

    /** 三部电梯一模一样，门关着的时候换过去看不出来。 */
    private void hop(Player p, int ax, int ay, int az, int bx, int by, int bz) {
        Location l = p.getLocation();
        p.teleport(new Location(world, l.getX() - ax + bx, l.getY() - ay + by, l.getZ() - az + bz, l.getYaw(), l.getPitch()));
    }

    /** 手变了：快捷栏后八格都是那只灰手。手电放回第一格，副手腾出来。 */
    private void greyHands(Player p) {
        if (handsGrey) return;
        handsGrey = true;
        var inv = p.getInventory();
        if (Park.isFlashlight(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            inv.setItem(0, park.flashlightStack());
        }
        // 快捷栏一格一格换成灰手
        for (int slot = 1; slot <= 8; slot++) {
            int k = slot;
            after(k * 6, () -> inv.setItem(k, park.modelStack("grey_hand")));
        }
        sound(p, "entity.warden.heartbeat", 1.2f, 0.7f);
        sound(p, "block.honey_block.slide", 0.6f, 0.5f);
    }

    /** 让客户端把眼下这一帧截下来，留给结尾的电视。每张只截一次。 */
    private void snap(Player p, int i) {
        if (snapped[i]) return;
        snapped[i] = true;
        Bukkit.getLogger().info("截图 " + i);
        fx.send(p, "snap " + i);
    }

    // ---------- 测试跳转 ----------

    /**
     * 导演栏跳到某一关的入口：整局先清回开局前，再把开场的结果给上。
     * 这样从哪儿跳过来、跳几次都一样，前面玩到一半的状态（门开了、阀拧了、手变了）不会带过去。
     */
    private void fresh(Player p) {
        reset();
        p.getInventory().clear();
        p.getInventory().setItemInOffHand(null);
        finishOpening(p);
    }

    /** 往下那一路第 i 处的落脚点，脸朝着往下走的方向。 */
    private Location holdSpot(int i, float pitch) {
        Well.Hold h = Well.HOLDS.get(i);
        return new Location(world, h.in[0], h.in[1], h.in[2], h.yaw(), pitch);
    }

    /** 导演栏跳进深井或井底：当作电梯已经掉下来、平台已经走过；down 为真时 807 号车也已经滑到底。 */
    private void wellReady(Player p, boolean down) {
        if (!inWell) {
            fresh(p);
            exhaled = inCab = cabGoing = true;
            inWell = true;
            cabSlipped = lookedUp = true;
            fx.send(p, "zone well");
        }
        if (down && !carDown) {
            carDown = sticksThrown = true;
            carRiding = false;
            if (car807 != null) car807.moveTo(Well.CAR_TO[0], Well.CAR_TO[1], Well.CAR_TO[2]);
        }
        fx.black(p, 0f, 1);
        flashCut = 0;
    }

    void jump(String name, Player p) {
        switch (name) {
            case "garden" -> {
                lightsOut(p);
                p.teleport(new Location(world, 130, Lower.GARDEN_FLOOR, 2, -90, 0));
            }
            case "lockers" -> p.teleport(new Location(world, 166, Lower.GARDEN_FLOOR, 1.5, -90, 0));
            case "pool1" -> p.teleport(new Location(world, Lower.POOL1_X + 1, Lower.POOL1_FLOOR, 1.5, -90, 0));
            case "pool2" -> p.teleport(new Location(world, Lower.POOL2_X + 3, Lower.POOL2_FLOOR, 1.5, -90, 0));
            case "pool3" -> p.teleport(new Location(world, Lower.POOL3_X + 1.5, Lower.POOL3_FLOOR + 1, 1.5, -90, 0));
            case "burrow" -> crawlNow(p);
            case "forest" -> p.teleport(new Location(world, Forest.STATION[0], Forest.STATION[1], Forest.STATION[2], -60, 0));
            case "vehicle" -> p.teleport(new Location(world, 321.5, Forest.VEHICLE[1], 30.5, 90, 0));
            case "exit" -> {
                exhaled = true;
                reportRead = radioDone = true;
                p.teleport(new Location(world, 309, Forest.VEHICLE[1], 24, 90, 0));
                exhale(p);
            }
            case "cab" -> {
                // 当作森林已经走完、呼过气：人站进电梯，门关上，评分、往上、停住、坠到 B4，和走过来的一模一样
                fresh(p);
                exhaled = reportRead = radioDone = true;
                for (Notes.Note n : notes.values()) if (n.area().equals("forest")) read.add(n.id());
                p.teleport(new Location(world, Forest.CAB_X + 1.5, Forest.CAB_Y, Forest.CAB_Z + 1.5, -90, 0));
            }
            case "well" -> {
                // 电梯刚卡住：和从森林掉下来一模一样
                fresh(p);
                exhaled = inCab = cabGoing = true;
                p.teleport(new Location(world, Well.CAB_X + 1.5, Well.CAB_Y, Well.CAB_Z + 1.5, -90, 0));
                fx.black(p, 1f, 1);
                flashCut = 2;
                wellArrive(p);
            }
            case "rim", "truss", "rope", "car" -> {
                wellReady(p, false);
                Location to = switch (name) {
                    case "rim" -> new Location(world, Well.CAB_X + 6.5, Well.CAB_Y, Well.CAB_Z + 1.5, 120, -50);
                    case "truss" -> holdSpot(Well.TRUSS, 15);
                    case "rope" -> holdSpot(Well.LAMPS_FROM, 10);
                    default -> new Location(world, Well.BAG_X0 + 3.5, Well.BAG_Y, Well.BAG_Z0 + 2.5, 180, 10);
                };
                p.teleport(to);
                // 踩空接住按"最后站稳的是哪一处"算：跳过来的那一处就当站稳了，不然一落地就被当成从电梯门口掉下来，拽回上面
                safeHold = switch (name) {
                    case "truss" -> Well.TRUSS;
                    case "rope" -> Well.LAMPS_FROM;
                    case "car" -> Well.BAG;
                    default -> 0;
                };
                wellSafe = to.clone();
            }
            case "ending" -> {
                // 收尾：花园层原件当作都读过，跟前的水里漂着对讲机；放完录音发疯，接片尾
                wellReady(p, true);
                p.teleport(new Location(world, 130 + Well.DX, Well.REAL_GARDEN, 2, -90, 0));
                for (String id : LAST_ORIGINALS) read.add(id);
                endingDue = true;
                after(20, () -> walkieIn(p));
            }
            case "bottom", "dock2", "hall2", "window2", "garden2" -> {
                wellReady(p, true);
                int f = Well.REAL_FLOOR, d = Well.DX;
                Location to = switch (name) {
                    case "bottom" -> new Location(world, Well.CAR_TO[0] - 3, f, Well.CAR_TO[2] + 4.4, -143, 10);
                    case "dock2" -> new Location(world, ParkPlan.DOCK_OFF[0] + d, f, ParkPlan.DOCK_OFF[2], -80, 0);
                    case "hall2" -> new Location(world, 113 + d, f, 4.5, -70, 0);
                    case "window2" -> new Location(world, 130.5 + d, f, 7.5, -10, -2);
                    default -> new Location(world, 130 + d, Well.REAL_GARDEN, 2, -90, 0);
                };
                p.teleport(to);
            }
            default -> {
            }
        }
    }

    void audioStatus(Player p) {
        fx.send(p, "audio stat");
    }

    String status() {
        StringBuilder b = new StringBuilder("段落 " + zone + "，打分");
        for (int s : scores) b.append(' ').append(s);
        b.append(lightsOut ? "，已停电" : "").append(rowTurned ? "，人形转头" : "").append(exhaled ? "，已呼气" : "").append(inCab ? "，进电梯" : "").append(atNewStation ? "，到重开车站" : "").append(finale ? "，结局" : "").append(catches > 0 ? "，被抓 " + catches : "")
                .append(inWell ? "，深井" + (carDown ? " 到底了" : carRiding ? " 车上" : "") + (endingDue ? " 原件读完" : "") + (madness ? " 疯了" : "") : "")
                .append("，截图 ").append(java.util.Arrays.toString(snapped).replace("true", "1").replace("false", "0"));
        return b.toString();
    }

    // ---------- 零件 ----------

    private BlockDisplay box(String block, double x, double y, double z, double sx, double sy, double sz, int glow) {
        BlockDisplay display = world.spawn(new Location(world, x, y, z), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(Bukkit.createBlockData(block));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) sx, (float) sy, (float) sz), new AxisAngle4f()));
            d.setShadowRadius(0);
            if (glow >= 0) d.setBrightness(new Display.Brightness(glow, 0));
        });
        scene.add(display);
        return display;
    }

    private ItemDisplay item(String model, double x, double y, double z, float yaw) {
        ItemStack stack = park.modelStack(model);
        ItemDisplay display = world.spawn(new Location(world, x, y, z, yaw, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(stack);
            d.setShadowRadius(0);
        });
        scene.add(display);
        return display;
    }

    private Interaction interaction(double x, double y, double z, float width, float height) {
        Interaction hit = world.spawn(new Location(world, x, y, z), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
        });
        scene.add(hit);
        return hit;
    }
}
