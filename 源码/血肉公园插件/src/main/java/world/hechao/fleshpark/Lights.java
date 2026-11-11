package world.hechao.fleshpark;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 会动的光：手电、车头灯、车厢灯都是每 tick 挪动的光方块。这里算差异，只改变化了的格子。 */
final class Lights {
    private final World world;
    private final Map<Long, Integer> placed = new HashMap<>();
    private final BlockData[] data = new BlockData[16];

    Lights(World world) {
        this.world = world;
        for (int i = 0; i < 16; i++) data[i] = Bukkit.createBlockData("minecraft:light[level=" + i + "]");
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 8192)) << 40 | ((long) (y + 1024)) << 20 | (long) (z + 8192);
    }

    private Block at(long k) {
        return world.getBlockAt((int) ((k >>> 40) & 0xffff) - 8192, (int) ((k >>> 20) & 0xfffff) - 1024, (int) (k & 0xfffff) - 8192);
    }

    void sync(Map<Long, Integer> desired) {
        for (Iterator<Map.Entry<Long, Integer>> it = placed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Integer> entry = it.next();
            if (desired.containsKey(entry.getKey())) continue;
            Block block = at(entry.getKey());
            if (block.getType() == Material.LIGHT) block.setType(Material.AIR, false);
            it.remove();
        }
        for (Map.Entry<Long, Integer> entry : desired.entrySet()) {
            Integer now = placed.get(entry.getKey());
            if (now != null && now.equals(entry.getValue())) continue;
            Block block = at(entry.getKey());
            // 只占空气格。地图上写死的光方块不动。
            if (block.getType() == Material.AIR || now != null) {
                block.setBlockData(data[entry.getValue()], false);
                placed.put(entry.getKey(), entry.getValue());
            }
        }
    }

    void clear() {
        sync(Map.of());
    }
}
