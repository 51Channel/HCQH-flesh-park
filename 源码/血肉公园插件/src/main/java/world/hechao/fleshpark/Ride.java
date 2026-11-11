package world.hechao.fleshpark;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import world.hechao.fleshpark.core.ParkPlan;
import world.hechao.fleshpark.core.RideProfile;
import world.hechao.fleshpark.core.Route;

import java.util.Map;
import java.util.Random;

/**
 * 坐缆车这一段的时间线。按红按钮开车；出站 → 过肉口 → 食道里被肉夹停、断电 → 掉进空腔 → 到 3 号码头。
 * 声音全用原版音效，低音调拼出来：心跳是监守者的心跳，“嘚嘚嘚”是铁链和铁活板门。
 */
final class Ride {
    enum State { PARKED, RUNNING, ARRIVED, PINNED }

    private static final double DT = 0.05;
    private static final int AUTO_DEPART_TICKS = 100;

    private final World world;
    private final ParkPlan plan;
    private final Tram tram;
    private final FleshField flesh;
    private final Random rnd = new Random();
    private final double[] p = new double[3], q = new double[3];

    State state = State.PARKED;
    Player rider;
    RideProfile profile;
    boolean allowDismount;
    private double t, pinnedD, lastD, nextBeat, nextRattle, nextMotor, holdAge;
    private boolean moanedAtExit, pinnedSqueeze;
    private int boardTicks;
    /** 第一站打了好评，车厢灯就不闪了。 */
    boolean steadyLamp;

    /** 手电被时间线掐掉：0 正常，1 闪，2 灭。 */
    int flashCut;
    private boolean headlamps;
    private int cabinLight;

    Ride(World world, ParkPlan plan, Tram tram, FleshField flesh) {
        this.world = world;
        this.plan = plan;
        this.tram = tram;
        this.flesh = flesh;
    }

    /** 发车前问剧情：车站的纸读完没有。 */
    java.util.function.Predicate<Player> departGate = player -> true;
    /** 给客户端发一行：车厢里走动要知道人站在车厢哪儿。 */
    java.util.function.BiConsumer<Player, String> send = (player, line) -> {
    };

    /** 开车和到站时告诉剧情总控。 */
    Runnable onDepart = () -> {
    }, onArrive = () -> {
    };

    private Location seat(double d) {
        plan.route.at(d, p);
        return new Location(world, p[0], p[1] - 0.6, p[2], -90, 8);
    }

    void start(Player player) {
        start(player, false);
    }

    /** force：导演栏和测试直接发车，不管车站的纸读没读完。 */
    void start(Player player, boolean force) {
        if (state != State.PARKED) return;
        if (!force && !departGate.test(player)) {
            boardTicks = 0;
            return;
        }
        // 人在车厢里站在哪，开车以后还站在哪，能在车厢里走
        plan.route.at(0, p);
        String cabin = String.format(java.util.Locale.ROOT, "cabin %.3f %.3f %.3f", player.getLocation().getX() - p[0], ParkPlan.FLOOR_STATION - p[1], player.getLocation().getZ() - p[2]);
        rider = player;
        profile = new RideProfile(plan.route);
        t = 0;
        lastD = 0;
        nextBeat = 7;
        nextRattle = 2.2;
        nextMotor = 1.4;
        moanedAtExit = false;
        holdAge = 0;
        tram.removeButton();
        mount(player, 0);
        send.accept(player, cabin);
        state = State.RUNNING;
        play("block.stone_button.click_on", 1f, 0.7f);
        play("block.lever.click", 0.8f, 0.6f);
        onDepart.run();
    }

    private void mount(Player player, double d) {
        allowDismount = true;
        player.leaveVehicle();
        player.teleport(seat(d));
        allowDismount = false;
        if (!tram.root.addPassenger(player)) player.sendActionBar(net.kyori.adventure.text.Component.text("上不了缆车"));
    }

    /** 测试和录制用：把缆车停在路线的某一点，人坐在里面，车不走。 */
    void pin(Player player, double d, boolean squeezed) {
        reset(false);
        tram.removeButton();
        tram.moveTo(positionX(d), p[1], p[2]);
        mount(player, d);
        plan.route.at(0, q);
        send.accept(player, String.format(java.util.Locale.ROOT, "cabin 0 %.3f 0", ParkPlan.FLOOR_STATION - q[1]));
        rider = player;
        pinnedD = d;
        pinnedSqueeze = squeezed;
        state = State.PINNED;
        headlamps = true;
        cabinLight = 8;
        tram.lamp(12);
    }

    private double positionX(double d) {
        plan.route.at(d, p);
        return p[0];
    }

    void tick() {
        double cx = 0;
        switch (state) {
            case PARKED -> {
                flesh.squeeze = 0;
                flesh.pulse = idlePulse();
                waitForRider();
            }
            case ARRIVED -> {
                flesh.squeeze = 0;
                flesh.pulse = idlePulse();
            }
            case PINNED -> {
                plan.route.at(pinnedD, p);
                squeezeCenter();
                flesh.squeeze = pinnedSqueeze ? 1 : 0;
                flesh.pulse = idlePulse();
            }
            case RUNNING -> running();
        }
    }

    /** 车停在站里时灯管半死不活地亮着，整个站台只有它是亮的，人自然会走过去。走进车厢就自己开，不用非得找到按钮。 */
    private void waitForRider() {
        Player player = world.getPlayers().stream().findFirst().orElse(null);
        boardTicks = player != null && inCabin(player.getLocation()) ? boardTicks + 1 : 0;
        cabinLight = steadyLamp || rnd.nextDouble() > 0.04 ? 8 : 3;
        tram.lamp(cabinLight == 3 ? 4 : 11);
        if (boardTicks == 1) player.playSound(player.getLocation(), "block.iron_trapdoor.close", SoundCategory.MASTER, 0.8f, 0.6f);
        if (boardTicks >= AUTO_DEPART_TICKS) start(player);
    }

    /** 人在停靠的车厢里。 */
    boolean inCabin(Location at) {
        plan.route.at(0, p);
        return at.getX() > p[0] + Route.CABIN_X0 - 0.6 && at.getX() < p[0] + Route.CABIN_X1 && at.getZ() > -Route.CABIN_HALF_Z - 1 && at.getZ() < Route.CABIN_HALF_Z - 0.1 && at.getY() < p[1] + 3;
    }

    private void running() {
        if (rider == null || !rider.isOnline()) {
            reset(true);
            return;
        }
        t += DT;
        if (t > 1.5) profile.step(DT);
        double d = profile.d;
        plan.route.at(d, p);
        tram.moveTo(p[0], p[1], p[2]);
        squeezeCenter();

        // 灯：按钮按下去先抽两下才亮
        double lampBase = t < 0.9 ? (Math.sin(t * 50) > 0.2 ? 0.5 : 0) : 0.8 + 0.2 * Math.sin(t * 23) * Math.sin(t * 7);
        headlamps = t > 0.5;

        // 夹车：肉从四面压上来，电断了
        double hold = RideProfile.STOP_SECONDS - profile.holdLeft;
        boolean holding = profile.holding();
        if (holding) holdAge += DT;
        double squeeze = 0;
        flashCut = 0;
        if (holding) {
            squeeze = Math.min(1, holdAge / 2.2);
            if (holdAge < 0.25) {
                play("block.iron_door.close", 1f, 0.5f);
                play("entity.iron_golem.step", 1f, 0.5f);
            }
            if (rnd.nextDouble() < 0.14) play(rnd.nextBoolean() ? "block.mud.step" : "block.honey_block.slide", 1f, 0.5f + rnd.nextFloat() * 0.2f);
            if (rnd.nextDouble() < 0.05) play("entity.slime.squish", 0.8f, 0.5f);
            if (rnd.nextDouble() < 0.02) play("block.glass.step", 1f, 0.5f + rnd.nextFloat() * 0.1f);
            if (holdAge > 1.8 && holdAge < 1.85) play("entity.warden.ambient", 1f, 0.5f);
            if (holdAge > 0.7) {
                lampBase = holdAge < 1.5 ? (Math.sin(holdAge * 60) > 0 ? 0.6 : 0) : 0;
                headlamps = holdAge < 1.2;
            }
            if (holdAge > 1.7) flashCut = holdAge < 2.2 ? 1 : holdAge < 6.4 ? 2 : holdAge < 7.0 ? 1 : 0;
            if (holdAge > 13.4 && holdAge < 13.45) play("block.chain.break", 1f, 0.5f);
        } else if (profile.squeezed && holdAge > 0) {
            // 放开：肉慢慢退回去，灯抽着回来
            double since = (d - profile.dSqueeze) / 2.4;
            squeeze = Math.max(0, 1 - since);
            if (since < 1.2) lampBase *= Math.sin(t * 40) > -0.2 ? 1 : 0;
        }
        flesh.squeeze = squeeze;
        cabinLight = (int) Math.round(9 * lampBase);
        tram.lamp((int) Math.round(13 * lampBase));

        // 过钢环：咣咣咣
        for (double hoop : plan.hoopDistances) {
            if (lastD < hoop && d >= hoop) {
                play("block.iron_trapdoor.close", 1f, 0.55f);
                play("block.chain.place", 1f, 0.6f);
                nextRattle = t + 0.15;
            }
        }
        // 缆绳“嘚嘚嘚”：车越快越密
        if (t >= nextRattle && profile.v > 0.3) {
            play("block.chain.step", 0.55f, 0.55f + rnd.nextFloat() * 0.1f);
            play("block.iron_trapdoor.close", 0.16f, 1.4f);
            nextRattle = t + 0.42 / Math.max(0.7, profile.v * 0.45);
        }
        // 电机
        if (t >= nextMotor && profile.v > 0.1) {
            play("entity.minecart.riding", 0.3f + 0.1f * (float) profile.v, 0.5f + (float) profile.v * 0.05f);
            nextMotor = t + 2.5;
        }
        // 心跳：进了肉口才开始，空腔里最重。肉上的血管跟着亮
        double age = t - lastBeatTime;
        flesh.pulse = heartEnv(age) * (d > profile.dThroatEnd ? 1 : 0.55);
        if (d > profile.dMouth - 4 && t >= nextBeat) {
            lastBeatTime = t;
            float weight = d > profile.dThroatEnd ? 1.4f : 0.9f;
            play("entity.warden.heartbeat", weight, 0.65f);
            nextBeat = t + (d > profile.dThroatEnd ? 3.1 : 3.8);
        }
        // 掉出食道：底下这个东西动了一下
        if (!moanedAtExit && d > profile.dThroatEnd + 4) {
            moanedAtExit = true;
            play("entity.warden.ambient", 1.2f, 0.5f);
            play("ambient.crimson_forest.mood", 1f, 0.5f);
        }
        lastD = d;
        if (profile.done()) arrive();
    }

    private double lastBeatTime = -100;

    private void arrive() {
        state = State.ARRIVED;
        headlamps = false;
        flashCut = 0;
        cabinLight = 3;
        tram.lamp(4);
        play("block.anvil.land", 0.5f, 0.5f);
        play("block.chain.break", 0.9f, 0.5f);
        // 广播坏了，只响两个音
        play("block.note_block.chime", 0.8f, 0.71f);
        Player player = rider;
        allowDismount = true;
        if (player != null) send.accept(player, "cabin off");
        if (player != null) {
            player.leaveVehicle();
            player.teleport(new Location(world, ParkPlan.DOCK_OFF[0], ParkPlan.DOCK_OFF[1], ParkPlan.DOCK_OFF[2], -70, 0));
        }
        allowDismount = false;
        onArrive.run();
    }

    /** 开着车掉线：先放人下车再让车回站。等到下一 tick 才发现的话，人已经带着“在车上”的状态存盘了，重进以后传送不动。 */
    void quit(Player player) {
        if (player.equals(rider)) reset(true);
    }

    /** 回到开车前。 */
    void reset(boolean respawnButton) {
        if (rider != null) {
            send.accept(rider, "cabin off");
            allowDismount = true;
            rider.leaveVehicle();
            allowDismount = false;
        }
        rider = null;
        profile = null;
        state = State.PARKED;
        boardTicks = 0;
        steadyLamp = false;
        flashCut = 0;
        headlamps = false;
        cabinLight = 0;
        flesh.squeeze = 0;
        lastBeatTime = -100;
        plan.route.at(0, p);
        tram.moveTo(p[0], p[1], p[2]);
        tram.lamp(0);
        if (respawnButton) tram.spawnButton();
    }

    /** 车停在站里时车厢亮不亮。开局是黑的，人走上站台才亮。 */
    void parkedLight(int level) {
        cabinLight = level;
        headlamps = level > 0;
    }

    void addLights(Map<Long, Integer> desired) {
        if (state == State.ARRIVED) return;
        double d = state == State.PINNED ? pinnedD : state == State.PARKED ? 0 : profile.d;
        plan.route.at(d, p);
        if (cabinLight > 0) desired.merge(Lights.key(floor(p[0] - 1), floor(p[1] + 1), floor(p[2])), cabinLight, Math::max);
        if (headlamps) {
            lamp(desired, d + 9, 15);
            lamp(desired, d + 18, 14);
            lamp(desired, d + 28, 12);
        }
    }

    private void lamp(Map<Long, Integer> desired, double d, int level) {
        plan.route.at(d, q);
        desired.merge(Lights.key(floor(q[0] + 2), floor(q[1] - 1.5), floor(q[2])), level, Math::max);
    }

    private void squeezeCenter() {
        flesh.squeezeAt[0] = p[0] + Route.CABIN_CENTER_X;
        flesh.squeezeAt[1] = p[1] + 1;
        flesh.squeezeAt[2] = p[2];
    }

    private double idlePulse() {
        double period = 4.2, now = System.currentTimeMillis() / 1000.0;
        return heartEnv(now % period) * 0.45;
    }

    private static double heartEnv(double a) {
        if (a < 0 || a > 2.5) return 0;
        return Math.min(1, Math.exp(-a * 5) + (a > 0.42 ? 0.7 * Math.exp(-(a - 0.42) * 6) : 0));
    }

    void play(String sound, float volume, float pitch) {
        if (rider != null && rider.isOnline()) rider.playSound(rider.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    /** 剪辑：黑一下以后车直接到前面 d 的地方。只往前跳。 */
    void cutTo(double d) {
        if (profile != null && state == State.RUNNING && d > profile.d && !profile.holding()) profile.d = Math.min(d, profile.dEnd - 1);
    }

    double distance() {
        return state == State.PINNED ? pinnedD : profile == null ? 0 : profile.d;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
