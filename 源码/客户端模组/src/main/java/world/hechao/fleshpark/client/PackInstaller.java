package world.hechao.fleshpark.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** 这个实例就是用来拍这一期的：游戏启动前把专用光影放好并启用。之后想换光影，在 Iris 的菜单里换就行，这里不会改回来。 */
public final class PackInstaller implements PreLaunchEntrypoint {
    private static final String PACK = "FleshPark.zip";

    /**
     * Sodium 默认把雾的结束距离当视距，还只画它认为看得见的区块里的实体。肉壁都挂在实体上，这两项开着，
     * 远一点的肉壁就直接不画，窗外是一片天空的颜色。这个实例里必须关掉。
     */
    /** 新电脑上第一次启动还没有这个文件：写一份完整的，只把那两项关掉。 */
    private static final String DEFAULT_SODIUM = "{\n  \"quality\": {\n    \"hidden_fluid_culling\": true,\n    \"improved_fluid_shaping\": false,\n    \"use_closest_point_entity_sort\": false,\n    \"pixel_filtering_mode\": \"NEAREST\"\n  },\n  \"performance\": {\n    \"chunk_builder_threads\": 0,\n    \"chunk_build_defer_mode\": \"ALWAYS\",\n    \"animate_only_visible_textures\": true,\n    \"use_entity_culling\": false,\n    \"use_fog_occlusion\": false,\n    \"use_block_face_culling\": true,\n    \"use_no_error_g_l_context\": true,\n    \"quad_splitting_mode\": \"SAFE\"\n  },\n  \"advanced\": {\n    \"enable_memory_tracing\": false,\n    \"use_advanced_staging_buffers\": true,\n    \"cpu_render_ahead_limit\": 3\n  },\n  \"debug\": {\n    \"terrain_sorting_enabled\": true\n  },\n  \"notifications\": {\n    \"has_cleared_donation_button\": false,\n    \"has_seen_donation_prompt\": false\n  }\n}";

    private static void sodiumOptions() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("sodium-options.json");
        try {
            Files.createDirectories(file.getParent());
            String json = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : DEFAULT_SODIUM;
            String fixed = json.replaceAll("\"use_entity_culling\"\\s*:\\s*true", "\"use_entity_culling\": false").replaceAll("\"use_fog_occlusion\"\\s*:\\s*true", "\"use_fog_occlusion\": false");
            if (!fixed.equals(json) || !Files.exists(file)) Files.writeString(file, fixed, StandardCharsets.UTF_8);
        } catch (IOException error) {
            System.err.println("[血肉公园] 改不了 Sodium 设置：" + error);
        }
    }

    @Override
    public void onPreLaunch() {
        sodiumOptions();
        Path game = FabricLoader.getInstance().getGameDir();
        try (InputStream in = PackInstaller.class.getResourceAsStream("/assets/fleshpark/shaderpack/" + PACK)) {
            if (in == null) return;
            Path packs = game.resolve("shaderpacks");
            Files.createDirectories(packs);
            Files.copy(in, packs.resolve(PACK), StandardCopyOption.REPLACE_EXISTING);
            Path config = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");
            List<String> lines = Files.exists(config) ? new ArrayList<>(Files.readAllLines(config, StandardCharsets.UTF_8)) : new ArrayList<>();
            if (lines.stream().anyMatch(line -> line.startsWith("shaderPack=") && line.length() > 11)) return;
            lines.removeIf(line -> line.startsWith("shaderPack=") || line.startsWith("enableShaders="));
            lines.add("enableShaders=true");
            lines.add("shaderPack=" + PACK);
            Files.createDirectories(config.getParent());
            Files.write(config, lines, StandardCharsets.UTF_8);
        } catch (IOException error) {
            System.err.println("[血肉公园] 放不好光影包：" + error);
        }
    }
}
