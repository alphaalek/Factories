package dk.superawesome.factories;

import dk.superawesome.factories.listeners.ChunkLoadListener;
import dk.superawesome.factories.listeners.InteractListener;
import dk.superawesome.factories.listeners.PistonExtendListener;
import dk.superawesome.factories.listeners.RedstoneSignalListener;
import dk.superawesome.factories.mehcanics.MechanicManager;
import dk.superawesome.factories.util.Tick;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class Factories extends JavaPlugin implements Listener {

    private static Factories instance;

    private final Map<World, MechanicManager> mechanicManagers = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        for (World world : Bukkit.getServer().getWorlds()) {
            MechanicManager mm = new MechanicManager(world);
            mechanicManagers.put(world, mm);
        }

        Bukkit.getPluginManager().registerEvents(new RedstoneSignalListener(), this);
        Bukkit.getPluginManager().registerEvents(new ChunkLoadListener(), this);
        Bukkit.getPluginManager().registerEvents(new InteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new PistonExtendListener(), this);

        Tick.start();

        // TODO

        // add more fuel types
        // buildings
        // power central
        // thinking mechanic put into storage declined (barrier in gui)
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Factories get() {
        return instance;
    }

    public <E extends Event> void registerEvent(Class<? extends E> clazz, EventPriority priority, Consumer<E> listener) {
        Bukkit.getPluginManager().registerEvent(clazz, this, priority, (l, event) -> listener.accept((E) event), this);
    }

    public MechanicManager getMechanicManager(World world) {
        return mechanicManagers.computeIfAbsent(world, d -> new MechanicManager(world));
    }
}
