package world.hechao.fleshpark;

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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 满意度评价机：一根立柱、一块面板、五个发亮的按钮。黑暗里只有它亮着，走过去右键按就行。 */
final class Kiosk {
    private static final String[] BUTTONS = {"minecraft:red_concrete", "minecraft:orange_concrete", "minecraft:yellow_concrete", "minecraft:lime_concrete", "minecraft:green_concrete"};

    private final Park park;
    private final World world;
    private final Location at;
    private final String question;
    private final List<Entity> parts = new ArrayList<>();
    private final List<BlockDisplay> lit = new ArrayList<>();
    private final List<TextDisplay> numbers = new ArrayList<>();
    private final boolean[] dead = new boolean[5];
    /** 这一下算不算数。不算数就接着问。 */
    interface Gate {
        boolean accept(Player player, int stars);
    }

    Gate gate;
    private TextDisplay label;
    boolean used;

    Kiosk(Park park, World world, double x, double y, double z, float yaw, String question) {
        this.park = park;
        this.world = world;
        at = new Location(world, x, y, z, yaw, 0);
        this.question = question;
    }

    /** rate 为空时只是个摆设。 */
    void spawn(BiConsumer<Player, Integer> rate, Consumer<Player> smash) {
        box("minecraft:polished_deepslate", -0.2, 0, -0.1, 0.4, 1.05, 0.16, -1);
        box("minecraft:black_concrete", -0.58, 1.02, -0.12, 1.16, 0.62, 0.14, -1);
        label = text(question, 0, 1.4, 0.03, 0.42f, 0xfffbf5);
        for (int i = 0; i < 5; i++) {
            double lx = -0.46 + i * 0.23;
            BlockDisplay button = box(BUTTONS[i], lx - 0.08, 1.12, 0.02, 0.16, 0.16, 0.03, 15);
            lit.add(button);
            numbers.add(text(String.valueOf(i + 1), lx, 1.285, 0.03, 0.3f, 0xfffbf5));
            int slot = i;
            if (rate == null) continue;
            int stars = i + 1;
            Interaction hit = interaction(lx, 1.1, 0.1, 0.21f, 0.2f);
            park.onClick(hit, player -> {
                if (used) return;
                if (dead[slot]) {
                    player.playSound(player.getLocation(), "block.stone_button.click_off", 0.6f, 0.5f);
                    return;
                }
                if (gate != null && !gate.accept(player, stars)) return;
                used = true;
                label.text(Component.text("感谢您的评价").color(TextColor.color(0xfffbf5)));
                for (BlockDisplay b : lit) if (b != button) b.setBrightness(new Display.Brightness(2, 0));
                rate.accept(player, stars);
            });
            if (smash != null) park.onAttack(hit, smash);
        }
        if (smash != null) {
            Interaction body = interaction(0, 0, -0.02, 0.7f, 1.05f);
            park.onAttack(body, smash);
            park.onClick(body, player -> {
            });
        }
    }

    /** 没人按，它自己按了：只剩这一颗亮着，屏上写“感谢您的评价”。stars 从 1 开始。 */
    void pressItself(int stars) {
        used = true;
        label.text(Component.text("感谢您的评价").color(TextColor.color(0xfffbf5)));
        for (int i = 0; i < lit.size(); i++) if (i != stars - 1) lit.get(i).setBrightness(new Display.Brightness(2, 0));
        lit.get(stars - 1).setBrightness(new Display.Brightness(15, 0));
    }

    /** 换一句问话。 */
    void say(String text) {
        label.text(Component.text(text).color(TextColor.color(0xfffbf5)));
    }

    /** 这个分数不能选了：按钮灭掉，数字变成一道杠。stars 从 1 开始。 */
    void killButton(int stars) {
        dead[stars - 1] = true;
        lit.get(stars - 1).setBrightness(new Display.Brightness(0, 0));
        numbers.get(stars - 1).text(Component.text("—").color(TextColor.color(0x6b645e)));
    }

    /** 砸了：灯全灭。 */
    void kill() {
        used = true;
        for (BlockDisplay b : lit) b.setBrightness(new Display.Brightness(0, 0));
        label.text(Component.empty());
    }

    void remove() {
        parts.forEach(Entity::remove);
        parts.clear();
        lit.clear();
    }

    private BlockDisplay box(String block, double x, double y, double z, double sx, double sy, double sz, int glow) {
        BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(Bukkit.createBlockData(block));
            d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, (float) z), new AxisAngle4f(), new Vector3f((float) sx, (float) sy, (float) sz), new AxisAngle4f()));
            d.setShadowRadius(0);
            if (glow >= 0) d.setBrightness(new Display.Brightness(glow, 0));
        });
        parts.add(display);
        return display;
    }

    private TextDisplay text(String content, double x, double y, double z, float scale, int color) {
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text(content).color(TextColor.color(color)));
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 0));
            d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, (float) z), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        parts.add(display);
        return display;
    }

    private Interaction interaction(double x, double y, double z, float width, float height) {
        double yaw = Math.toRadians(at.getYaw());
        double wx = at.getX() + x * Math.cos(yaw) - z * Math.sin(yaw), wz = at.getZ() + x * Math.sin(yaw) + z * Math.cos(yaw);
        Interaction hit = world.spawn(new Location(world, wx, at.getY() + y, wz), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
        });
        parts.add(hit);
        return hit;
    }
}
