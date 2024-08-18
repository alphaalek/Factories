package dk.superawesome.factorio.listeners;

import dk.superawesome.factorio.Factorio;
import dk.superawesome.factorio.building.Matcher;
import dk.superawesome.factorio.mechanics.Mechanic;
import dk.superawesome.factorio.mechanics.MechanicBuildResponse;
import dk.superawesome.factorio.mechanics.MechanicManager;
import dk.superawesome.factorio.util.db.Types;
import dk.superawesome.factorio.util.statics.BlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

public class SignChangeListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignUpdate(SignChangeEvent event) {
        MechanicManager manager = Factorio.get().getMechanicManager(event.getBlock().getWorld());

        if (manager.getMechanicPartially(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        } else if (Tag.WALL_SIGNS.isTagged(event.getBlock().getType())) {
            Block on = BlockUtil.getPointingBlock(event.getBlock(), true);
            if (on == null
                    || manager.getProfileFrom((Sign) event.getBlock().getState()).isPresent() && on.getState() instanceof TileState) {
                event.getPlayer().sendMessage("§cDu kan ikke placere en maskine på denne block!");
                event.setCancelled(true);
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(event.getPlayer().getInventory().getItemInMainHand().getType()));
                event.getBlock().setType(Material.AIR);
                return;
            }

            ItemStack drop = new ItemStack(on.getType());
            Bukkit.getScheduler().runTask(Factorio.get(), () -> {
                MechanicBuildResponse response = manager.buildMechanic((Sign) event.getBlock().getState(), event.getPlayer());
                build: {
                    switch (response) {
                        case SUCCESS -> {
                            Mechanic<?> mechanic = manager.getMechanicPartially(event.getBlock().getLocation());
                            event.getPlayer().sendMessage("§eDu oprettede maskinen " + mechanic.getProfile().getName() + " ved " + Types.LOCATION.convert(event.getBlock().getLocation()) + ".");
                            if (!(mechanic.getProfile().getBuilding() instanceof Matcher)) {
                                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
                            }
                            break build;
                        }
                        case NO_SUCH -> {
                            break build;
                        }

                        case ALREADY_EXISTS -> event.getPlayer().sendMessage("§cDer er allerede en maskine her.");
                        case ERROR -> event.getPlayer().sendMessage("§cDer skete en fejl under oprettelse af maskinen.");
                        case NOT_ENOUGH_SPACE -> event.getPlayer().sendMessage("§cDer er ikke nok plads til at bygge maskinen.");
                        case NOT_PLACED_BLOCKS -> event.getPlayer().sendMessage("§cMaskinen er ikke blevet bygget rigtigt.");
                    }

                    event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                    event.getBlock().setType(Material.AIR);
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(event.getPlayer().getInventory().getItemInMainHand().getType()));
                }
            });
        }
    }
}
