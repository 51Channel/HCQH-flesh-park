package world.hechao.fleshpark;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import world.hechao.fleshpark.core.B;
import world.hechao.fleshpark.core.Lower;
import world.hechao.fleshpark.core.ParkPlan;
import world.hechao.fleshpark.core.Parts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** 把 ParkPlan 变成真东西，并且每 tick 带着缆车、手电、肉一起动。 */
final class Park {
    private static final NamespacedKey FLASHLIGHT = new NamespacedKey("fleshpark", "flashlight");
    private static final int AROUND_LIGHT = 10;
    static final String HELLO = "fleshpark:client";
    private final Set<UUID> shader = new HashSet<>();

    final FleshParkPlugin plugin;
    final World world;
    final ParkPlan plan;
    final Lights lights;
    final FleshField flesh;
    final Tram tram;
    final Ride ride;
    final Story story;
    final Fx fx;
    private final Map<UUID, Consumer<Player>> clicks = new HashMap<>(), attacks = new HashMap<>();
    private final Map<Integer, List<Display>> lampRings = new HashMap<>();
    private final File marker;
    private final boolean built;
    private final Set<UUID> flashOff = new HashSet<>();
    private final List<Entity> decor = new ArrayList<>();
    private final Random rnd = new Random();
    private boolean ready;
    private double time;

    Park(FleshParkPlugin plugin, World world, ParkPlan plan, boolean built, File marker) {
        this.plan = plan;
        this.plugin = plugin;
        this.world = world;
        this.built = built;
        this.marker = marker;
        lights = new Lights(world);
        flesh = new FleshField(world);
        tram = new Tram(world, plan.route, button -> onClick(button, this::pressButton));
        ride = new Ride(world, plan, tram, flesh);
        fx = new Fx(plugin);
        ride.send = fx::send;
        story = new Story(this);
    }

    void start() {
        for (int cx = -5; cx <= 14; cx++) for (int cz = -6; cz <= 5; cz++) world.setChunkForceLoaded(cx, cz, true);
        for (int cx = 15; cx <= 28; cx++) for (int cz = -9; cz <= 7; cz++) world.setChunkForceLoaded(cx, cz, true);
        for (int cx = -3; cx <= 1; cx++) for (int cz = 17; cz <= 20; cz++) world.setChunkForceLoaded(cx, cz, true);
        // 开局的夜路
        for (int cx = -42; cx <= -6; cx++) for (int cz = -4; cz <= 3; cz++) world.setChunkForceLoaded(cx, cz, true);
        keepDecorLoaded();
        if (built) finish();
        else pave();
    }

    /**
     * 摆设都是不存盘的展示实体，区块一卸就跟着没了。上面几块是早几关的范围；
     * 后面几关（麻醉科、改进部、运营中心）在更东边，凡是放了摆设的区块都一直加载着。
     */
    private void keepDecorLoaded() {
        Set<Long> chunks = new HashSet<>();
        java.util.function.BiConsumer<Double, Double> add = (x, z) -> chunks.add(((long) Math.floorDiv((int) Math.floor(x), 16) << 32) | (Math.floorDiv((int) Math.floor(z), 16) & 0xffffffffL));
        for (Parts.Box b : plan.boxes) {
            add.accept(b.x(), b.z());
            add.accept(b.x() + b.sx(), b.z() + b.sz());
        }
        for (Parts.Beam b : plan.beams) {
            add.accept(b.ax(), b.az());
            add.accept(b.bx(), b.bz());
        }
        for (Parts.Text t : plan.texts) add.accept(t.x(), t.z());
        for (Parts.Flat f : plan.flats) add.accept(f.x(), f.z());
        for (Parts.Pipe p : plan.pipes) add.accept((p.ax() + p.bx()) / 2, (p.az() + p.bz()) / 2);
        for (Parts.Poster p : plan.posters) add.accept(p.x(), p.z());
        for (Parts.Panel p : plan.panels) add.accept(p.ax(), p.az());
        for (Parts.Lamp l : plan.lamps) add.accept(l.ax(), l.az());
        int before = world.getForceLoadedChunks().size();
        for (long key : chunks) world.setChunkForceLoaded((int) (key >> 32), (int) key, true);
        plugin.getLogger().info("常驻区块 " + before + " → " + world.getForceLoadedChunks().size());
    }

    /**
     * 摆设画多远：前几关有大空腔和远景，摆设要隔着几十米看得见；麻醉科往后都是屋子和窄洞，雾也近，
     * 六十米外的东西看不见还照样画，帧数掉得厉害（中控室里能同时画上千个）。
     */
    private static float range(double x, float normal) {
        return x > 440 ? Math.min(normal, 0.9f) : normal;
    }

    /** 第一次开服才铺方块。分到多个 tick，别卡住服务器。 */
    private void pave() {
        int[] counted = {0};
        plan.forEachPaved((x, y, z, b) -> counted[0]++);
        int n = counted[0];
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        byte[] kinds = new byte[n];
        int[] i = {0};
        plan.forEachPaved((x, y, z, b) -> {
            xs[i[0]] = x;
            ys[i[0]] = y;
            zs[i[0]] = z;
            kinds[i[0]++] = (byte) b.ordinal();
        });
        B[] all = B.values();
        BlockData[] data = new BlockData[all.length];
        for (int k = 0; k < all.length; k++) data[k] = Bukkit.createBlockData(all[k].state);
        plugin.getLogger().info("第一次开服，开始铺公园：" + n + " 个方块");
        new BukkitRunnable() {
            int at;

            @Override
            public void run() {
                long end = System.nanoTime() + 35_000_000L;
                while (at < n && System.nanoTime() < end) {
                    world.getBlockAt(xs[at], ys[at], zs[at]).setBlockData(data[kinds[at]], false);
                    at++;
                }
                if (at < n) return;
                cancel();
                world.save();
                try {
                    Files.writeString(marker.toPath(), String.valueOf(plan.fingerprint()));
                } catch (IOException error) {
                    plugin.getLogger().warning("写不了标记文件，下次开服会重铺：" + error);
                }
                finish();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void finish() {
        for (Parts.Box box : plan.boxes) {
            decor.add(world.spawn(new Location(world, box.x(), box.y(), box.z()), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData(box.block()));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) box.sx(), (float) box.sy(), (float) box.sz()), new AxisAngle4f()));
                d.setViewRange(range(box.x(), 3f));
                d.setShadowRadius(0);
                if (box.glow() >= 0) d.setBrightness(new Display.Brightness(box.glow(), 0));
            }));
        }
        for (Parts.Beam beam : plan.beams) {
            decor.add(world.spawn(new Location(world, beam.ax(), beam.ay(), beam.az()), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData(beam.block()));
                float dx = (float) (beam.bx() - beam.ax()), dy = (float) (beam.by() - beam.ay()), dz = (float) (beam.bz() - beam.az());
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz), th = (float) beam.thick();
                Quaternionf rotation = new Quaternionf().rotationTo(0, 1, 0, dx / len, dy / len, dz / len);
                d.setTransformationMatrix(new Matrix4f().rotate(rotation).scale(th, len, th).translate(-0.5f, 0, -0.5f));
                d.setViewRange(range(beam.ax(), 4f));
                d.setShadowRadius(0);
                if (beam.glow() >= 0) d.setBrightness(new Display.Brightness(beam.glow(), 0));
            }));
        }
        for (Parts.Text text : plan.texts) {
            decor.add(world.spawn(new Location(world, text.x(), text.y(), text.z(), text.yaw(), 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.text(Component.text(text.text()).color(TextColor.color(text.color())));
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(text.background()));
                d.setShadowed(false);
                d.setLineWidth(600);
                d.setViewRange(range(text.x(), 2f));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(text.scale(), text.scale(), text.scale()), new AxisAngle4f()));
                if (text.glow() >= 0) d.setBrightness(new Display.Brightness(text.glow(), 0));
            }));
        }
        for (Parts.Flat flat : plan.flats) {
            ItemStack stack = new ItemStack(Material.matchMaterial(flat.item()));
            decor.add(world.spawn(new Location(world, flat.x(), flat.y(), flat.z()), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(stack);
                d.setShadowRadius(0);
                d.setTransformationMatrix(new Matrix4f().rotateY((float) flat.angle()).rotateX((float) (Math.PI / 2)).scale(flat.scale()));
            }));
        }
        for (Parts.Pipe pipe : plan.pipes) {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(new NamespacedKey("fleshpark", new String[]{"pipe_yellow", "pipe_blue", "pipe_grey"}[pipe.kind()]));
            stack.setItemMeta(meta);
            float dx = (float) (pipe.bx() - pipe.ax()), dy = (float) (pipe.by() - pipe.ay()), dz = (float) (pipe.bz() - pipe.az());
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz), wide = (float) pipe.rad() * 2;
            Quaternionf rotation = new Quaternionf().rotationTo(1, 0, 0, dx / len, dy / len, dz / len);
            decor.add(world.spawn(new Location(world, (pipe.ax() + pipe.bx()) / 2, (pipe.ay() + pipe.by()) / 2, (pipe.az() + pipe.bz()) / 2), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(stack);
                d.setShadowRadius(0);
                d.setViewRange(range(pipe.ax(), 3f));
                d.setTransformationMatrix(new Matrix4f().rotate(rotation).scale(len, wide, wide));
            }));
        }
        for (Parts.Poster poster : plan.posters) {
            ItemStack stack = modelStack(poster.model());
            decor.add(world.spawn(new Location(world, poster.x(), poster.y(), poster.z(), poster.yaw(), 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(stack);
                d.setShadowRadius(0);
                d.setViewRange(2f);
                d.setTransformationMatrix(new Matrix4f().scale((float) poster.w(), (float) poster.h(), 1));
                if (poster.glow() >= 0) d.setBrightness(new Display.Brightness(poster.glow(), 0));
            }));
        }
        for (Parts.Panel panel : plan.panels) {
            decor.add(world.spawn(new Location(world, panel.ax(), panel.ay(), panel.az()), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData(panel.block()));
                float dx = (float) (panel.bx() - panel.ax()), dy = (float) (panel.by() - panel.ay()), dz = (float) (panel.bz() - panel.az());
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                d.setTransformationMatrix(new Matrix4f().rotate(new Quaternionf().rotationTo(1, 0, 0, dx / len, dy / len, dz / len)).scale(len, (float) panel.h(), (float) panel.d()).translate(0, -0.5f, -0.5f));
                d.setViewRange(3f);
                d.setShadowRadius(0);
                if (panel.glow() >= 0) d.setBrightness(new Display.Brightness(panel.glow(), 0));
            }));
        }
        for (Parts.Lamp lamp : plan.lamps) {
            BlockDisplay display = world.spawn(new Location(world, lamp.ax(), lamp.ay(), lamp.az()), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(Bukkit.createBlockData(lamp.block()));
                float dx = (float) (lamp.bx() - lamp.ax()), dy = (float) (lamp.by() - lamp.ay()), dz = (float) (lamp.bz() - lamp.az());
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz), th = (float) lamp.thick();
                d.setTransformationMatrix(new Matrix4f().rotate(new Quaternionf().rotationTo(1, 0, 0, dx / len, dy / len, dz / len)).scale(len, 0.1f, th).translate(0, -0.5f, -0.5f));
                d.setViewRange(3f);
                d.setShadowRadius(0);
            });
            decor.add(display);
            lampRings.computeIfAbsent(lamp.ring(), k -> new ArrayList<>()).add(display);
        }
        for (Parts.Pool pool : plan.pools) {
            ItemStack stack = modelStack(pool.item());
            float sx = (float) (pool.x1() - pool.x0()), sz = (float) (pool.z1() - pool.z0());
            // 模型里液面在 y=0.5，原点在格子中心，所以实体就放在液面高度
            decor.add(world.spawn(new Location(world, (pool.x0() + pool.x1()) / 2, pool.y(), (pool.z0() + pool.z1()) / 2), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(stack);
                d.setShadowRadius(0);
                d.setViewRange(3f);
                d.setTransformationMatrix(new Matrix4f().scale(sx, 1, sz));
            }));
        }
        for (double[] grape : plan.lower.grapes) {
            ItemStack stack = modelStack("grape");
            decor.add(world.spawn(new Location(world, grape[0], grape[1], grape[2]), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(stack);
                d.setShadowRadius(0);
                d.setTransformationMatrix(new Matrix4f().scale((float) grape[3]));
            }));
        }
        Tram.spawnStatic(world, plan.route, ParkPlan.NEW_STATION_DZ, decor);
        try (java.io.InputStream index = plugin.getResource("mesh_index.txt")) {
            flesh.spawn(index);
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().severe("摆不了肉的网格：" + error);
        }
        tram.spawn();
        tram.lamp(0);
        ride.onDepart = story::onDepart;
        ride.onArrive = story::onArrive;
        ready = true;
        plugin.getLogger().info("血肉公园就绪：方块 " + plan.blockCount() + "，肉块 " + flesh.size());
        autoReset();
    }

    private void pressButton(Player player) {
        ride.start(player);
    }

    ItemStack modelStack(String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("fleshpark", model));
        stack.setItemMeta(meta);
        return stack;
    }

    void setShader(Player player, boolean on) {
        if (on) shader.add(player.getUniqueId());
        else shader.remove(player.getUniqueId());
    }

    void onClick(Entity entity, Consumer<Player> action) {
        clicks.put(entity.getUniqueId(), action);
    }

    void onAttack(Entity entity, Consumer<Player> action) {
        attacks.put(entity.getUniqueId(), action);
    }

    boolean click(Entity entity, Player player) {
        Consumer<Player> action = clicks.get(entity.getUniqueId());
        if (action == null) return false;
        action.accept(player);
        return true;
    }

    boolean attack(Entity entity, Player player) {
        Consumer<Player> action = attacks.get(entity.getUniqueId());
        if (action == null) return false;
        action.accept(player);
        return true;
    }

    /** 大厅的一组灯。 */
    void ring(int ring, boolean on) {
        for (Display display : lampRings.getOrDefault(ring, List.of())) display.setBrightness(new Display.Brightness(on ? 14 : 1, 0));
        for (int[] l : plan.lampLights) {
            if (l[0] != ring) continue;
            Block block = world.getBlockAt(l[1], l[2], l[3]);
            if (on) block.setBlockData(Bukkit.createBlockData("minecraft:light[level=" + l[4] + "]"), false);
            else if (block.getType() == Material.LIGHT) block.setType(Material.AIR, false);
        }
    }

    boolean ready() {
        return ready;
    }

    void enter(Player player) {
        if (!ready) return;
        ride.allowDismount = true;
        player.leaveVehicle();
        ride.allowDismount = false;
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        // 手上什么都没有：手电和手机在入园大厅的桌子上，要自己去拿。人先站在公路边的接驳站等车
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.teleport(new Location(world, ParkPlan.PICKUP_SPAWN[0], ParkPlan.FLOOR_STATION, ParkPlan.PICKUP_SPAWN[1], (float) ParkPlan.PICKUP_SPAWN[2], 4));
        player.getInventory().setItemInOffHand(null);
        player.setWalkSpeed(0.2f);
        flashOff.add(player.getUniqueId());
    }

    private static ItemStack flashlight() {
        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(FLASHLIGHT);
        meta.displayName(Component.text("手电"));
        stack.setItemMeta(meta);
        return stack;
    }

    ItemStack flashlightStack() {
        return flashlight();
    }

    static boolean isFlashlight(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta.hasItemModel() && FLASHLIGHT.equals(meta.getItemModel());
    }

    /** 开场：信收起来，手电才开。 */
    void flashlightOn(Player player) {
        flashOff.remove(player.getUniqueId());
        player.playSound(player.getLocation(), "block.lever.click", org.bukkit.SoundCategory.MASTER, 0.5f, 1.6f);
    }

    void toggleFlashlight(Player player) {
        if (!flashOff.remove(player.getUniqueId())) flashOff.add(player.getUniqueId());
        player.playSound(player.getLocation(), "block.lever.click", org.bukkit.SoundCategory.MASTER, 0.5f, 1.6f);
    }

    /** 进服、铺完地图都会自动从头来。两件事挨得近的时候只来一次，不然片名会放两遍。 */
    void autoReset() {
        if (System.currentTimeMillis() - lastReset < 8000) return;
        reset();
    }

    /** 回到开局前。 */
    void reset() {
        if (!ready) return;
        // 只有真给人开了场才算数：铺完地图时还没人在线，进服那一次照样要开场
        if (!Bukkit.getOnlinePlayers().isEmpty()) lastReset = System.currentTimeMillis();
        ride.reset(true);
        story.reset();
        for (Player player : Bukkit.getOnlinePlayers()) {
            enter(player);
            story.begin(player);
        }
    }

    private long lastReset;

    void tick() {
        if (!ready) return;
        time += 0.05;
        ride.tick();
        Player focus = null;
        Map<Long, Integer> desired = new HashMap<>();
        ride.addLights(desired);
        story.addLights(desired);
        for (Player player : world.getPlayers()) {
            if (focus == null) focus = player;
            int cut = Math.max(ride.flashCut, story.flashCut);
            boolean main = isFlashlight(player.getInventory().getItemInMainHand()), off = !main && isFlashlight(player.getInventory().getItemInOffHand());
            boolean lit = !flashOff.contains(player.getUniqueId()) && (main || off) && cut != 2 && !(cut == 1 && rnd.nextDouble() < 0.5);
            if (shader.contains(player.getUniqueId())) {
                // 手电和身边的光都在光影里算。开关写进饱食度，客户端看不见这个条，光影读得到。拿在副手就少 1，光从左手出来
                int food = cut == 2 ? 8 : !lit ? 10 : (story.flashWeak ? 15 : 20) - (off ? 1 : 0);
                if (player.getFoodLevel() != food) player.setFoodLevel(food);
                continue;
            }
            // 身边一小圈光：恐怖游戏的暗是远处暗，身边要看得清。只有断电那几秒才全黑
            if (cut != 2) around(player, desired);
            if (lit) flashlight(player, desired);
        }
        story.tick(focus);
        lights.sync(desired);
        flesh.tick(focus, fx);
    }

    private void around(Player player, Map<Long, Integer> desired) {
        Location eye = player.getEyeLocation();
        desired.merge(Lights.key(eye.getBlockX(), eye.getBlockY(), eye.getBlockZ()), AROUND_LIGHT, Math::max);
    }

    /**
     * 手电：沿视线往前走，打到不透光的方块就把光放在它前面那格。玻璃和铁栏杆不挡。
     * 中途再放一盏暗的，光柱沿途的肉也能照到。原版的光一格一格跳，光影包下面会柔和很多。
     */
    private void flashlight(Player player, Map<Long, Integer> desired) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double x = eye.getX(), y = eye.getY(), z = eye.getZ();
        double reach = 30;
        long last = Long.MIN_VALUE, mid = Long.MIN_VALUE;
        double lastD = 0;
        for (double d = 0.3; d < reach; d += 0.3) {
            int bx = (int) Math.floor(x + dir.getX() * d), by = (int) Math.floor(y + dir.getY() * d), bz = (int) Math.floor(z + dir.getZ() * d);
            Block block = world.getBlockAt(bx, by, bz);
            Material type = block.getType();
            if (type.isOccluding()) break;
            if (type == Material.AIR || type == Material.LIGHT) {
                last = Lights.key(bx, by, bz);
                lastD = d;
            }
            if (mid == Long.MIN_VALUE && d > 7 && (type == Material.AIR || type == Material.LIGHT)) mid = Lights.key(bx, by, bz);
        }
        if (last == Long.MIN_VALUE) return;
        desired.merge(last, story.flashWeak ? 9 : 15, Math::max);
        if (mid != Long.MIN_VALUE && mid != last && lastD > 12) desired.merge(mid, story.flashWeak ? 5 : 11, Math::max);
    }

    void shutdown() {
        lights.clear();
        story.shutdown();
        tram.remove();
        flesh.remove();
        decor.forEach(Entity::remove);
        decor.clear();
    }
}
