package world.hechao.clientprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 自动测试用：测试脚本把操作写进游戏目录的 probe-commands.txt，这里一行一行执行；每秒记一行状态到 probe-status.csv。
 * 真键盘鼠标按不了，所以这里走客户端自己的动作入口，发给服务器的包和真人操作一样。
 */
public final class ClientProbe implements ClientModInitializer {
    private Path commands;
    private Path status;
    private Path sounds;
    private Path track;
    private int ticks;
    private double[] walkTarget;
    private int walkLeft, sneakLeft, jumpLeft, slot;
    /** hop：走的时候脚前面空了就起跳。 */
    private boolean walkJump;
    private boolean tracking;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (commands == null) {
            Path root = client.gameDirectory.toPath();
            commands = root.resolve("probe-commands.txt");
            status = root.resolve("probe-status.csv");
            sounds = root.resolve("probe-sounds.csv");
            track = root.resolve("probe-track.csv");
            // 静音时音效也会走到这里，能查到服务端发的声音有没有解析成真的音频文件。
            client.getSoundManager().addListener((sound, events, range) -> append(sounds, sound.getIdentifier() + "," + sound.getSource() + "," + sound.getVolume() + "," + sound.getPitch() + "," + sound.getSound().getLocation()));
            // 没有真键盘，游戏会当成挂机把帧数压到 30；不解开就量不出真实帧数。
            client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(260);
            // -Dhechao.probe.hide=true：窗口一出来就藏起来，不弹到别人前台、不抢焦点。画面照常渲染，截图不受影响
            if (Boolean.getBoolean("hechao.probe.hide")) org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());
        }
        if (++ticks % 20 == 0) writeStatus(client);
        // 窗口开在别人屏幕上，真鼠标别把测试的视角带偏
        client.mouseHandler.releaseMouse();
        if (client.player != null) client.player.getInventory().setSelectedSlot(slot);
        stepWalk(client);
        stepSneak(client);
        if (tracking && client.player != null) append(track, client.player.getX() + "," + client.player.getY() + "," + client.player.getZ() + "," + client.player.isPassenger());
        try {
            if (!Files.exists(commands)) return;
            List<String> lines = Files.readAllLines(commands, StandardCharsets.UTF_8);
            Files.delete(commands);
            for (String line : lines) run(client, line.trim());
        } catch (IOException | RuntimeException error) {
            log("命令失败：" + error);
        }
    }

    private void run(Minecraft client, String line) {
        if (line.isEmpty()) return;
        String[] parts = line.split(" ", 2);
        String argument = parts.length > 1 ? parts[1] : "";
        var player = client.player;
        switch (parts[0]) {
            case "shot" -> Screenshot.grab(client.gameDirectory, argument + ".png", client.getMainRenderTarget(), 1, message -> {});
            case "cmd" -> player.connection.sendCommand(argument);
            case "slot" -> slot = Integer.parseInt(argument);
            case "look" -> {
                String[] angles = argument.split(" ");
                player.setYRot(Float.parseFloat(angles[0]));
                player.setXRot(Float.parseFloat(angles[1]));
            }
            case "aim" -> aimAtBoss(client, argument.isEmpty() ? 0 : Double.parseDouble(argument));
            case "attack" -> attack(client);
            case "place" -> place(client);
            // 下面这几个走游戏自己的按键入口，和真人按键走同一条路。
            case "use" -> KeyMapping.click(client.options.keyUse.getDefaultKey());
            case "goto", "hop" -> {
                String[] target = argument.split(" ");
                walkTarget = new double[]{Double.parseDouble(target[0]), Double.parseDouble(target[1])};
                walkLeft = target.length > 2 ? Integer.parseInt(target[2]) * 20 : 400;
                walkJump = parts[0].equals("hop");
            }
            case "sneak" -> sneakLeft = Integer.parseInt(argument);
            // 按住空格几个 tick（默认 3，够跳一下）
            case "jump" -> jumpLeft = argument.isEmpty() ? 3 : Integer.parseInt(argument);
            case "sprint" -> client.options.keySprint.setDown(argument.equals("on"));
            case "lookat" -> {
                String[] target = argument.split(" ");
                player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(Double.parseDouble(target[0]), Double.parseDouble(target[1]), Double.parseDouble(target[2])));
            }
            case "hit" -> logHit(client);
            case "lights" -> logLights(client, argument.isEmpty() ? 32 : Integer.parseInt(argument));
            case "track" -> tracking = argument.equals("on");
            case "hud" -> client.options.hideGui = argument.equals("off");
            case "hide" -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());
            case "show" -> org.lwjgl.glfw.GLFW.glfwShowWindow(client.getWindow().handle());
            // 给人看着测：front [宽 高 左 上]，窗口拉到前台、摆好大小（屏幕点数）
            case "front" -> {
                long window = client.getWindow().handle();
                String[] size = argument.isEmpty() ? new String[]{"1200", "720", "40", "60"} : argument.split(" ");
                org.lwjgl.glfw.GLFW.glfwShowWindow(window);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(window, Integer.parseInt(size[0]), Integer.parseInt(size[1]));
                if (size.length > 3) org.lwjgl.glfw.GLFW.glfwSetWindowPos(window, Integer.parseInt(size[2]), Integer.parseInt(size[3]));
                org.lwjgl.glfw.GLFW.glfwFocusWindow(window);
            }
            // volume 0.6：总音量（测试会话默认是静音的）
            case "volume" -> client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(Double.parseDouble(argument));
            case "rejoin" -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(argument), new ServerData("test", argument, ServerData.Type.OTHER), false, null);
            default -> log("不认识的命令：" + line);
        }
        log("执行：" + line);
    }

    /** 按住前进键朝目标走，一路真碰撞。走不到就记下停在哪。 */
    private void stepWalk(Minecraft client) {
        if (walkTarget == null) return;
        var player = client.player;
        if (player == null) {
            walkTarget = null;
            return;
        }
        double dx = walkTarget[0] - player.getX(), dz = walkTarget[1] - player.getZ(), distance = Math.sqrt(dx * dx + dz * dz);
        boolean arrived = distance < 0.25;
        if (arrived || --walkLeft <= 0) {
            client.options.keyUp.setDown(false);
            if (walkJump) client.options.keyJump.setDown(false);
            log(String.format("%s %.1f,%.1f，人在 %.2f,%.2f,%.2f", arrived ? "走到" : "走不到", walkTarget[0], walkTarget[1], player.getX(), player.getY(), player.getZ()));
            walkTarget = null;
            return;
        }
        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
        client.options.keyUp.setDown(true);
        // 跑酷：站着、脚前面那一点底下是空的，就跳（和真人走到边上按空格一样）
        if (walkJump) {
            boolean jump = false;
            if (player.onGround() && distance > 0.7) {
                double ax = player.getX() + dx / distance * 0.4, az = player.getZ() + dz / distance * 0.4;
                BlockPos below = BlockPos.containing(ax, player.getY() - 0.2, az);
                jump = client.level.getBlockState(below).getCollisionShape(client.level, below).isEmpty();
            }
            client.options.keyJump.setDown(jump);
        }
    }

    private void stepSneak(Minecraft client) {
        if (jumpLeft > 0) client.options.keyJump.setDown(--jumpLeft > 0);
        if (sneakLeft <= 0) return;
        client.options.keyShift.setDown(--sneakLeft > 0);
    }

    /** 准星现在对着什么：真人右键会打到的就是它。 */
    private void logHit(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult entity) {
            log(String.format("准星对着实体 %s，距离 %.2f", entity.getEntity().getType(), Math.sqrt(client.player.getEyePosition().distanceToSqr(hit.getLocation()))));
        } else if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            log(String.format("准星对着方块 %s %s，距离 %.2f", client.level.getBlockState(block.getBlockPos()).getBlock(), block.getBlockPos().toShortString(), Math.sqrt(client.player.getEyePosition().distanceToSqr(hit.getLocation()))));
        } else {
            log("准星什么也没对着");
        }
    }

    /** 客户端世界里玩家周围有多少光方块，手电开没开就看这个。 */
    private void logLights(Minecraft client, int radius) {
        BlockPos center = client.player.blockPosition();
        int count = 0;
        StringBuilder where = new StringBuilder();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (!client.level.getBlockState(pos).is(Blocks.LIGHT)) continue;
            if (++count <= 6) where.append(' ').append(pos.toShortString().replace(", ", "/"));
        }
        log("光方块 " + count + where);
    }

    /** 和按左键一样：对着实体就打，对着方块就开始挖，都没有就挥手。挥手的包是插件收到的左键。 */
    private void attack(Minecraft client) {
        var player = client.player;
        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult entity) {
            client.gameMode.attack(player, entity.getEntity());
        } else if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            client.gameMode.startDestroyBlock(block.getBlockPos(), block.getDirection());
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** 看向最近的 Boss 方块实体上方 height 格：方块实体都骑在脚底的根上，瞄身体要往上抬。真人靠鼠标瞄准，这里没有鼠标。 */
    private void aimAtBoss(Minecraft client, double height) {
        var player = client.player;
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Display.BlockDisplay)) continue;
            double distance = entity.distanceToSqr(player);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        if (best == null) {
            log("附近没有方块实体");
            return;
        }
        player.lookAt(EntityAnchorArgument.Anchor.EYES, best.position().add(0.5, 0.5 + height, 0.5));
        log(String.format("瞄准 %.1f,%.1f,%.1f，距离 %.1f", best.getX(), best.getY(), best.getZ(), Math.sqrt(bestDistance)));
    }

    private void place(Minecraft client) {
        if (client.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, block);
            client.player.swing(InteractionHand.MAIN_HAND);
        } else {
            log("准星没有对着方块，放不了");
        }
    }

    private void writeStatus(Minecraft client) {
        var player = client.player;
        String position = player == null ? ",,," : String.format("%.1f,%.1f,%.1f,%s", player.getX(), player.getY(), player.getZ(), player.level().dimension().identifier());
        int entities = client.level == null ? 0 : client.level.getEntityCount();
        String screen = client.screen == null ? "" : client.screen.getClass().getSimpleName();
        String item = player == null ? "" : player.getMainHandItem().getItem().toString();
        String health = player == null ? "" : String.valueOf(player.getHealth());
        log(client.getFps() + "," + entities + "," + position + "," + item + "," + health + "," + screen);
    }

    private void log(String text) {
        append(status, text);
    }

    private static void append(Path file, String text) {
        try {
            Files.writeString(file, System.currentTimeMillis() + "," + text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 写不了状态不影响测试本身，测试脚本会发现文件没有更新。
        }
    }
}
