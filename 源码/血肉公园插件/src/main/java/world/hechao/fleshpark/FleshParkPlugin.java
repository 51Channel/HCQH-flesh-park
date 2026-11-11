package world.hechao.fleshpark;

import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import world.hechao.fleshpark.core.ParkPlan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 血肉公园原型：公园封了以后一个人下去探险。
 * 用下界环境是为了它自带的东西：没有天空、浓雾、雾的颜色和飘的孢子可以按生物群系换。
 */
public final class FleshParkPlugin extends JavaPlugin {
    static final String WORLD_NAME = "hechao_fleshpark";

    private Park park;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        File marker = new File(getDataFolder(), "built.txt");
        ParkPlan plan = new ParkPlan();
        // 标记对得上，世界也真的在，才算铺好。整个实例拷到别的电脑上时标记会跟着走，世界不一定
        // 下界环境的世界，区块存在 DIM-1/region 里
        Path folder = getServer().getWorldContainer().toPath().resolve(WORLD_NAME);
        boolean markerOk = readMarker(marker, plan), regionOk = hasRegionFiles(folder.resolve("DIM-1").resolve("region")) || hasRegionFiles(folder.resolve("region"));
        boolean built = markerOk && regionOk;
        if (!built) getLogger().info("要重铺：标记" + (markerOk ? "对得上" : "对不上") + "，世界" + (regionOk ? "在" : "不在") + "（" + folder + "）");
        if (!built) deleteOldWorld();
        World world = new WorldCreator(WORLD_NAME)
                .generator(new VoidGen())
                .biomeProvider(new ParkBiomes())
                .environment(World.Environment.NETHER)
                .generateStructures(false)
                .createWorld();
        if (world == null) {
            getLogger().severe("建不了世界 " + WORLD_NAME);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        prepareWorld(world);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Fx.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Park.HELLO, (channel, player, message) -> {
            if (park != null) park.setShader(player, message.length > 0 && message[0] != 0);
        });
        park = new Park(this, world, plan, built, marker);
        getServer().getPluginManager().registerEvents(new Guard(park), this);
        FpCommand handler = new FpCommand(park);
        PluginCommand command = getCommand("fp");
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getScheduler().runTask(this, park::start);
        getServer().getScheduler().runTaskTimer(this, park::tick, 2, 1);
    }

    @Override
    public void onDisable() {
        if (park != null) park.shutdown();
    }

    private static boolean hasRegionFiles(Path region) {
        try (Stream<Path> files = Files.list(region)) {
            return files.anyMatch(f -> f.getFileName().toString().endsWith(".mca"));
        } catch (IOException | RuntimeException missing) {
            return false;
        }
    }

    private static boolean readMarker(File marker, ParkPlan plan) {
        try {
            return marker.exists() && Files.readString(marker.toPath()).trim().equals(String.valueOf(plan.fingerprint()));
        } catch (IOException error) {
            return false;
        }
    }

    /** 地图内容变了就从头铺。只删这个插件自己的世界。 */
    private void deleteOldWorld() {
        Path folder = getServer().getWorldContainer().toPath().resolve(WORLD_NAME);
        if (!Files.isDirectory(folder)) return;
        getLogger().info("公园规划换了版本，删掉旧世界重铺");
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException error) {
            getLogger().warning("删旧世界失败：" + error);
        }
    }

    private static void prepareWorld(World world) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
    }
}
