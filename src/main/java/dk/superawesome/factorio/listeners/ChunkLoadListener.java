package dk.superawesome.factorio.listeners;

import dk.superawesome.factorio.Factorio;
import dk.superawesome.factorio.mechanics.MechanicManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashSet;
import java.util.Set;

public class ChunkLoadListener implements Listener {

    private static final Set<Long> loadedChunks = new HashSet<>();

    private static long getChunkIndex(Chunk chunk) {
        return (chunk.getX() & Integer.MAX_VALUE) | ((long) chunk.getZ() & Integer.MAX_VALUE) << 32;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        long index = getChunkIndex(event.getChunk());
        if (loadedChunks.contains(index)) {
            // already loaded
            return;
        }
        loadedChunks.add(index);

        MechanicManager manager = Factorio.get().getMechanicManager(event.getWorld());
        Bukkit.getScheduler().runTask(Factorio.get(), () -> manager.loadMechanics(event.getChunk()));
    }

    // removed to optimize db connections
    /*
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        World world = event.getWorld();
        Factorio.get().getMechanicManager(world).unloadMechanics(event.getChunk(), true);
    }
    */
}
