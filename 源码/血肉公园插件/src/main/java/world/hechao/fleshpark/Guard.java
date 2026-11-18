package world.hechao.fleshpark;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import world.hechao.fleshpark.core.ParkPlan;

/** 单人探险用不到的东西都关掉：不掉血、不饿、不丢东西。缆车开着时不能下车。 */
final class Guard implements Listener {
    private final Park park;

    Guard(Park park) {
        this.park = park;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 重新进服就从头来。客户端模组的频道要等它注册完才收得到，晚一点再开演
        park.plugin.getServer().getScheduler().runTaskLater(park.plugin, park::autoReset, 30);
    }

    /** 不弹原版"解锁新配方"：快捷栏换成灰手的时候会弹一个，片子里很出戏。 */
    @EventHandler
    public void onRecipe(org.bukkit.event.player.PlayerRecipeDiscoverEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        park.ride.quit(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(new Location(park.world, ParkPlan.SPAWN[0], ParkPlan.SPAWN[1], ParkPlan.SPAWN[2], -90, 4));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        // 手电可以换到副手，腾出右手；到了新车站以后不让换，结尾要收手电
        boolean flashlight = Park.isFlashlight(event.getMainHandItem()) || Park.isFlashlight(event.getOffHandItem());
        if (!flashlight || park.story.atNewStation()) event.setCancelled(true);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player rider && (park.story.busHolds(rider) || park.story.carHolds(rider))) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() == park.ride.rider && !park.ride.allowDismount
                && (park.ride.state == Ride.State.RUNNING || park.ride.state == Ride.State.PINNED)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onUseFlashlight(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 主手拿着手电就只认主手；手电在副手时右键也能开关
        boolean mainHas = Park.isFlashlight(event.getPlayer().getInventory().getItemInMainHand());
        if (event.getHand() == EquipmentSlot.OFF_HAND && mainHas) return;
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        if (Park.isFlashlight(event.getItem())) park.toggleFlashlight(event.getPlayer());
    }

    @EventHandler
    public void onClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (park.click(event.getRightClicked(), event.getPlayer())) event.setCancelled(true);
    }

    /** 深井里被肉索缠住时连按左键扯开：挥一下手就算一下。 */
    @EventHandler
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent event) {
        if (event.getAnimationType() == org.bukkit.event.player.PlayerAnimationType.ARM_SWING) park.story.wellSwing(event.getPlayer());
    }

    /** 深井里脚被肉垫吸住时连按空格拔出来：跳一下算一下。 */
    @EventHandler
    public void onJump(com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
        park.story.wellJump(event.getPlayer());
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (park.attack(event.getAttacked(), event.getPlayer())) event.setCancelled(true);
    }
}
