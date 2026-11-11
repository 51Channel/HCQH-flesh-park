package world.hechao.fleshpark;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 所有的肉：网格在客户端模组里，这里只摆锚点，一段网格一个实体。实体不存盘，开服时区块是强制加载的。
 * 夹车、顶玻璃、心跳这些都是发给客户端的数字，顶点在客户端和光影里动。
 */
final class FleshField {
    private final World world;
    private final List<Entity> anchors = new ArrayList<>();

    /** 夹车的程度和车厢中心。 */
    double squeeze;
    final double[] squeezeAt = new double[3];
    /** 窗外的肉往玻璃顶的程度。 */
    double heave;
    /** 心跳亮度，0 到 1。 */
    double pulse;

    private String lastDeform = "";
    private int lastPulse = -1;

    FleshField(World world) {
        this.world = world;
    }

    void spawn(InputStream index) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(" ");
                int id = Integer.parseInt(a[0]);
                double x = Double.parseDouble(a[1]), y = Double.parseDouble(a[2]), z = Double.parseDouble(a[3]);
                float sx = Float.parseFloat(a[4]), sy = Float.parseFloat(a[5]), sz = Float.parseFloat(a[6]);
                ItemStack stack = new ItemStack(Material.PAPER);
                ItemMeta meta = stack.getItemMeta();
                meta.setItemModel(new NamespacedKey("fleshpark", "flesh_mesh"));
                CustomModelDataComponent data = meta.getCustomModelDataComponent();
                data.setFloats(List.of((float) id));
                meta.setCustomModelDataComponent(data);
                stack.setItemMeta(meta);
                anchors.add(world.spawn(new Location(world, x, y, z), ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(stack);
                    d.setShadowRadius(0);
                    d.setViewRange(3f);
                    // 客户端按这个盒子做视野剔除
                    d.setDisplayWidth(Math.max(sx, sz) * 1.5f + 2);
                    d.setDisplayHeight(sy + 2);
                }));
            }
        }
    }

    void tick(Player player, Fx fx) {
        if (player == null) return;
        String deform = squeeze < 0.002 && heave < 0.002 ? "0" : String.format("%.1f %.1f %.1f %.2f %.2f", squeezeAt[0], squeezeAt[1], squeezeAt[2], squeeze, heave);
        if (!deform.equals(lastDeform)) {
            lastDeform = deform;
            fx.send(player, deform.equals("0") ? "deform 0 0 0 0 0" : "deform " + deform);
        }
        int beat = (int) Math.round(pulse * 12);
        if (beat != lastPulse) {
            lastPulse = beat;
            fx.send(player, "pulse " + beat / 12f);
        }
    }

    int size() {
        return anchors.size();
    }

    void remove() {
        anchors.forEach(Entity::remove);
        anchors.clear();
    }
}
