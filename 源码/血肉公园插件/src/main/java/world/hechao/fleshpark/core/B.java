package world.hechao.fleshpark.core;

/** 公园用到的方块。肉的三种是换了贴图的原版方块，贴图在客户端资源模组里。 */
public enum B {
    FLESH_DARK("minecraft:nether_wart_block"),
    FLESH_MID("minecraft:red_mushroom_block"),
    FLESH_PALE("minecraft:mushroom_stem"),
    ROCK_A("minecraft:tuff"),
    ROCK_B("minecraft:deepslate"),
    ROCK_C("minecraft:cobbled_deepslate"),
    STEEL("minecraft:polished_deepslate"),
    WALL_A("minecraft:gray_concrete"),
    WALL_B("minecraft:light_gray_concrete"),
    WALL_C("minecraft:cracked_stone_bricks"),
    WALL_STRIP("minecraft:cyan_terracotta"),
    PLATFORM("minecraft:polished_andesite"),
    EDGE("minecraft:yellow_terracotta"),
    TRACKBED("minecraft:polished_blackstone"),
    ROOF("minecraft:stone_bricks"),
    HULL("minecraft:waxed_weathered_copper"),
    HULL_DARK("minecraft:waxed_weathered_cut_copper"),
    HULL_GLASS("minecraft:tinted_glass"),
    TILE_A("minecraft:white_terracotta"),
    TILE_B("minecraft:light_gray_terracotta"),
    TILE_STAIN("minecraft:brown_terracotta"),
    HALL_WALL("minecraft:white_concrete"),
    HALL_WALL_DIRTY("minecraft:light_gray_terracotta"),
    HALL_CEIL("minecraft:smooth_quartz"),
    MULLION("minecraft:black_concrete"),
    GLASS_X("minecraft:white_stained_glass_pane[east=true,west=true]"),
    BARS_X("minecraft:iron_bars[east=true,west=true]"),
    BARS_Z("minecraft:iron_bars[north=true,south=true]"),
    BARS_ES("minecraft:iron_bars[east=true,south=true]"),
    BARS_EN("minecraft:iron_bars[east=true,north=true]"),
    BENCH("minecraft:dark_oak_slab[type=bottom]"),
    COUNTER("minecraft:smooth_stone"),
    SHELF("minecraft:barrel[facing=up]"),
    DEBRIS("minecraft:white_carpet"),
    DEBRIS_DARK("minecraft:gray_carpet"),
    BARRIER("minecraft:barrier"),
    STAIR_E("minecraft:polished_andesite_stairs[facing=east]"),
    WALK_B("minecraft:dark_oak_slab[type=bottom]"),
    WALK_T("minecraft:dark_oak_slab[type=top]"),
    CAB_WALL("minecraft:iron_block"),
    GRATE("minecraft:waxed_oxidized_cut_copper"),
    HUT("minecraft:spruce_planks"),
    TERRAZZO("minecraft:calcite"),
    TERRAZZO_DARK("minecraft:polished_diorite"),
    BRASS("minecraft:waxed_copper_block"),
    WOOD("minecraft:spruce_planks"),
    WOOD_DARK("minecraft:dark_oak_planks"),
    SHOP_GLASS("minecraft:light_gray_stained_glass"),
    AWNING_RED("minecraft:red_wool"),
    AWNING_WHITE("minecraft:white_wool"),
    TABLE("minecraft:smooth_stone_slab[type=top]"),
    BIN("minecraft:cauldron"),
    DEAD_PLANT("minecraft:potted_dead_bush"),
    BUSH("minecraft:dead_bush"),
    DESERT("minecraft:brown_terracotta"),
    DESERT_B("minecraft:coarse_dirt"),
    LIGHT_3("minecraft:light[level=3]"),
    LIGHT_5("minecraft:light[level=5]"),
    LIGHT_7("minecraft:light[level=7]"),
    LIGHT_9("minecraft:light[level=9]"),
    LIGHT_15("minecraft:light[level=15]"),
    MIRROR("minecraft:gray_stained_glass"),
    /** 南北走向的玻璃：改进部办公室的观察窗。 */
    GLASS_Z("minecraft:white_stained_glass_pane[north=true,south=true]"),
    /** 深井里往东下去的台阶：高的那半在西边。 */
    STAIR_W("minecraft:polished_andesite_stairs[facing=west]"),
    /** 搜救队的绳梯：贴在东边那根钢柱上。 */
    LADDER_W("minecraft:ladder[facing=west]"),
    /** 深井的螺旋台阶：半格的那一级。整格的用 PLATFORM（和楼里的楼梯一个料）。 */
    STEP_HALF("minecraft:polished_andesite_slab[type=bottom]"),
    /** 电梯门口那块旧员工平台：半格的铁格栅。 */
    GRATE_HALF("minecraft:waxed_oxidized_cut_copper_slab[type=bottom]"),
    /** 深井里落脚的地方：样子是模型（Rig），脚下是看不见的方块。整格的就是屏障；半格的是换成空模型的铜台阶（客户端资源里改的）。 */
    FOOT("minecraft:barrier"),
    FOOT_HALF("minecraft:waxed_weathered_cut_copper_slab[type=bottom]");

    public final String state;

    B(String state) {
        this.state = state;
    }

    public boolean isFlesh() {
        return this == FLESH_DARK || this == FLESH_MID || this == FLESH_PALE;
    }

    /** 不透光的方块：展示实体的中心埋进去会变成全黑。 */
    public boolean isOpaque() {
        return switch (this) {
            case STAIR_E, STAIR_W, LADDER_W, WALK_B, WALK_T, STEP_HALF, GRATE_HALF, FOOT, FOOT_HALF, GLASS_X, BARS_X, BARS_Z, BARS_ES, BARS_EN, HULL_GLASS, SHOP_GLASS, BENCH, TABLE, BIN, DEAD_PLANT, BUSH, DEBRIS, DEBRIS_DARK, BARRIER, LIGHT_3, LIGHT_5, LIGHT_7, LIGHT_9, LIGHT_15, MIRROR, GLASS_Z -> false;
            default -> true;
        };
    }

    /** 人能走过去的方块。 */
    public boolean isWalkable() {
        return switch (this) {
            case STAIR_E, STAIR_W, LADDER_W, WALK_B, WALK_T, STEP_HALF, GRATE_HALF, DEBRIS, DEBRIS_DARK, LIGHT_3, LIGHT_5, LIGHT_7, LIGHT_9, LIGHT_15 -> true;
            default -> false;
        };
    }
}
