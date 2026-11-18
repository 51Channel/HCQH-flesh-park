package world.hechao.fleshpark;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import world.hechao.fleshpark.core.ParkPlan;
import world.hechao.fleshpark.core.RideProfile;

import java.util.List;

/** 测试和录制用的命令：直接开车、跳到某一段、站到某个位置。 */
final class FpCommand implements CommandExecutor, TabCompleter {
    private static final List<String> JUMPS = List.of("portal", "mouth", "throat", "squeeze", "exit", "cavity", "far", "approach");
    private static final List<String> SPOTS = List.of("station", "door", "dock", "hall", "atrium", "window", "figure", "garden", "lockers", "pool1", "pool2", "pool3", "burrow", "forest", "vehicle", "exit", "cab", "well", "rim", "truss", "rope", "car", "bottom", "dock2", "hall2", "window2", "garden2", "ending");

    private final Park park;

    FpCommand(Park park) {
        this.park = park;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0];
        Player player = sender instanceof Player p ? p : null;
        switch (action) {
            case "go" -> {
                if (player == null) return false;
                park.story.finishOpening(player);
                if (park.ride.state != Ride.State.PARKED) park.ride.reset(true);
                park.ride.start(player, true);
            }
            case "fx" -> {
                // 测试用：直接给客户端发一行，比如 fx phone、fx director
                if (player != null && args.length > 1) park.fx.send(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            case "unlock" -> {
                if (player != null) park.story.unlock(player);
            }
            case "reset" -> park.reset();
            case "skip" -> park.story.skipIntro();
            // 测试用：收尾那台对讲机，不用点直接放
            case "walkie" -> {
                if (player != null) park.story.playWalkie(player);
            }
            case "scare" -> {
                if (player != null) park.story.glassScare(player);
            }
            case "audio" -> {
                if (player != null) park.story.audioStatus(player);
            }
            case "jump" -> {
                if (player == null || args.length < 2) return false;
                park.story.finishOpening(player);
                RideProfile profile = new RideProfile(park.plan.route);
                double d = switch (args[1]) {
                    case "portal" -> park.plan.route.distAtX(12);
                    case "mouth" -> profile.dMouth - 12;
                    case "throat" -> (profile.dMouth + profile.dSqueeze) / 2;
                    case "squeeze" -> profile.dSqueeze;
                    case "exit" -> profile.dThroatEnd + 2;
                    case "cavity" -> profile.dThroatEnd + 34;
                    case "far" -> profile.dEnd - 62;
                    case "approach" -> profile.dEnd - 22;
                    default -> -1;
                };
                if (d < 0) return false;
                park.ride.pin(player, d, args[1].equals("squeeze"));
            }
            case "tp" -> {
                if (player == null || args.length < 2) return false;
                park.story.finishOpening(player);
                int floor = ParkPlan.FLOOR_HALL;
                Location to = switch (args[1]) {
                    case "entry" -> new Location(park.world, ParkPlan.ENTRY[0], ParkPlan.ENTRY[1], ParkPlan.ENTRY[2], -90, 0);
                    case "station" -> new Location(park.world, ParkPlan.SPAWN[0], ParkPlan.SPAWN[1], ParkPlan.SPAWN[2], -90, 4);
                    case "door" -> new Location(park.world, -4, ParkPlan.FLOOR_STATION, 4.5, 180, 5);
                    case "dock" -> new Location(park.world, ParkPlan.DOCK_OFF[0], floor, ParkPlan.DOCK_OFF[2], -80, 0);
                    case "hall" -> new Location(park.world, 113, floor, 4.5, -70, 0);
                    case "atrium" -> new Location(park.world, 128, floor, 6, 180, -6);
                    case "window", "figure" -> new Location(park.world, 130.5, floor, 7.5, -10, -2);
                    default -> null;
                };
                // 先下车：坐在缆车上传送不动
                park.ride.reset(true);
                if (to == null) {
                    park.story.jump(args[1], player);
                    return true;
                }
                player.teleport(to);
                if (args[1].equals("figure")) park.story.poseFigure();
            }
            default -> sender.sendMessage("血肉公园：" + (park.ready() ? "就绪" : "铺设中") + "，状态 " + park.ride.state
                    + "，走了 " + String.format("%.1f", park.ride.distance()) + " 米，肉块 " + park.flesh.size() + "，方块 " + park.plan.blockCount());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("go", "reset", "skip", "jump", "tp", "status", "unlock");
        if (args.length == 2 && args[0].equals("jump")) return JUMPS;
        if (args.length == 2 && args[0].equals("tp")) return SPOTS;
        return List.of();
    }
}
