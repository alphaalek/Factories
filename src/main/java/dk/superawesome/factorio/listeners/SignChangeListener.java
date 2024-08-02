package dk.superawesome.factorio.listeners;

import dk.superawesome.factorio.Factorio;
import dk.superawesome.factorio.mechanics.Mechanic;
import dk.superawesome.factorio.mechanics.MechanicBuildResponse;
import dk.superawesome.factorio.mechanics.MechanicManager;
import dk.superawesome.factorio.util.db.Types;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class SignChangeListener implements Listener {

    @EventHandler
    public void onSignUpdate(SignChangeEvent event) {
        MechanicManager manager = Factorio.get().getMechanicManager(event.getBlock().getWorld());

        if (manager.getMechanicPartially(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        } else if (Tag.WALL_SIGNS.isTagged(event.getBlock().getType())) {
            Bukkit.getScheduler().runTask(Factorio.get(), () -> {
                MechanicBuildResponse response = manager.buildMechanic((Sign) event.getBlock().getState(), event.getPlayer());
                build: {
                    switch (response) {
                        case SUCCESS -> {
                            Mechanic<?, ?> mechanic = manager.getMechanicPartially(event.getBlock().getLocation());
                            event.getPlayer().sendMessage("§eDu oprettede maskinen " + mechanic.getProfile().getName() + " ved " + Types.LOCATION.convert(event.getBlock().getLocation()) + ".");
                            break build;
                        }
                        case NO_SUCH -> {
                            break build;
                        }

                        case ERROR -> event.getPlayer().sendMessage("§cDer skete en fejl under oprettelse af maskinen.");
                        case ABORT -> event.getPlayer().sendMessage("§cOprettelse af maskinen blev afbrudt. Kontakt en udvikler.");
                        case NOT_ENOUGH_SPACE -> event.getPlayer().sendMessage("§cDer er ikke nok plads til at bygge maskinen.");
                    }

                    event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                    event.getBlock().setType(Material.AIR);
                }
            });
        }
    }
}
