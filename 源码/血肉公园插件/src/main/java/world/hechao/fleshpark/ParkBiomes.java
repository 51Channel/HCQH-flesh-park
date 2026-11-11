package world.hechao.fleshpark;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/** 全部用下界荒地：它没有飘的粒子。雾的颜色按段落由客户端模组设置，灰尘在光影里画。 */
final class ParkBiomes extends BiomeProvider {
    @Override
    public Biome getBiome(WorldInfo info, int x, int y, int z) {
        return Biome.NETHER_WASTES;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo info) {
        return List.of(Biome.NETHER_WASTES);
    }
}
