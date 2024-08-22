package dk.superawesome.factorio.mechanics.routes;

import dk.superawesome.factorio.mechanics.SignalSource;
import dk.superawesome.factorio.mechanics.routes.events.pipe.PipePutEvent;
import dk.superawesome.factorio.mechanics.transfer.TransferCollection;
import dk.superawesome.factorio.util.Array;
import dk.superawesome.factorio.util.statics.BlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.util.BlockVector;

import java.util.*;

@SuppressWarnings("unchecked")
public abstract class AbstractRoute<R extends AbstractRoute<R, P>, P extends OutputEntry> {

    private static final Map<World, Map<BlockVector, List<AbstractRoute<?, ?>>>> cachedRoutes = new HashMap<>();
    private static final Map<World, Map<BlockVector, AbstractRoute<?, ?>>> cachedOriginRoutes = new HashMap<>();

    public static <R extends AbstractRoute<R, ? extends OutputEntry>> R getCachedOriginRoute(World world, BlockVector vec) {
        return (R) cachedOriginRoutes.computeIfAbsent(world, d -> new HashMap<>())
                .get(new BlockVector(vec.getBlockX(), vec.getBlockY(), vec.getBlockZ()));
    }

    public static List<AbstractRoute<?, ?>> getCachedRoutes(World world, BlockVector vec) {
        return cachedRoutes.computeIfAbsent(world, d -> new HashMap<>())
                .getOrDefault(new BlockVector(vec.getBlockX(), vec.getBlockY(), vec.getBlockZ()), new ArrayList<>());
    }

    public static <R extends AbstractRoute<R, ? extends OutputEntry>> void addRouteToCache(World world, AbstractRoute<?, ?> route) {
        if (cachedRoutes.isEmpty()) {
            cachedRoutes.put(world, new HashMap<>());
        }

        if (!route.getLocations().isEmpty()) {
            cachedOriginRoutes.get(world).put(route.getStart(), route);
            for (BlockVector loc : route.getLocations()) {
                cachedRoutes.get(world)
                        .computeIfAbsent(loc, __ -> new ArrayList<>())
                        .add(route);
            }

            ((R) route).getFactory().callBuildEvent((R) route);
        }
    }

    public static <R extends AbstractRoute<R, ? extends OutputEntry>> void removeRouteFromCache(World world, AbstractRoute<?, ?> route) {
        if (cachedRoutes.isEmpty()) {
            return;
        }

        cachedOriginRoutes.get(world).remove(route.getStart());
        for (BlockVector loc : route.getLocations()) {
            cachedRoutes.get(world).getOrDefault(loc, new ArrayList<>()).remove(route);
        }

        ((R) route).getFactory().callRemoveEvent((R) route);
    }

    public static class TransferOutputEntry implements OutputEntry<TransferCollection> {

        protected final Block block;

        private TransferOutputEntry(World world, BlockVector vec) {
            this.block = BlockUtil.getPointingBlock(BlockUtil.getBlock(world, vec), false);
        }

        public boolean handle(TransferCollection collection) {
            PipePutEvent event = new PipePutEvent(block, collection);
            Bukkit.getPluginManager().callEvent(event);

            return event.transferred();
        }

        @Override
        public BlockVector getVec() {
            return BlockUtil.getVec(block);
        }
    }

    public static class SignalOutputEntry implements OutputEntry<SignalSource> {

        protected final Block block;

        private SignalOutputEntry(World world, BlockVector vec) {
            this.block = BlockUtil.getBlock(world, vec);
        }

        public boolean handle(SignalSource source) {
            return source.handleOutput(block);
        }

        @Override
        public BlockVector getVec() {
            return BlockUtil.getVec(block);
        }
    }

    protected final Array<Queue<P>> outputs = new Array<>();
    protected final Set<BlockVector> locations = new HashSet<>();
    protected final Map<BlockVector, List<BlockVector>> visited = new HashMap<>();

    private final BlockVector start;

    public AbstractRoute(BlockVector start) {
        this.start = start;
    }

    public BlockVector getStart() {
        return start;
    }

    public boolean hasVisited(BlockVector vec, BlockVector rel) {
        return visited.containsKey(vec) && visited.get(vec).contains(rel);
    }

    public void visit(BlockVector vec, BlockVector rel) {
        visited.computeIfAbsent(vec, __ -> new ArrayList<>()).add(rel);
    }

    public void add(BlockVector vec) {
        locations.add(vec);
    }

    public Collection<BlockVector> getLocations() {
        return locations;
    }

    public Queue<P> getOutputs(int context) {
        return outputs.get(context, LinkedList::new);
    }

    public void addOutput(World world, BlockVector vec, int context) {
        outputs.get(context, LinkedList::new).add(createOutputEntry(world, vec));
    }

    public void addOutput(World world, BlockVector vec) {
        addOutput(world, vec, Routes.DEFAULT_CONTEXT);
    }

    public abstract RouteFactory<R> getFactory();

    public abstract void search(Block from, Material fromMat, BlockVector relVec, Block rel);

    protected abstract P createOutputEntry(World world, BlockVector vec);

    public static class Pipe extends AbstractRoute<Pipe, TransferOutputEntry> {

        public Pipe(BlockVector start) {
            super(start);
        }

        @Override
        public RouteFactory<Pipe> getFactory() {
            return new RouteFactory.PipeRouteFactory();
        }

        @Override
        public void search(Block from, Material fromMat, BlockVector relVec, Block rel) {
            Material mat = rel.getType();

            // piston = pipe output
            if (mat == Material.PISTON) {
                // ... however only if the piston is not pointing towards the block where the pipe search came from
                if (!BlockUtil.getPointingBlock(rel, false).equals(from)) {
                    add(relVec);
                    addOutput(from.getWorld(), relVec);
                }
            // glass = pipe expand
            } else if (
                    mat == Material.GLASS
                    || BlockUtil.anyStainedGlass.test(mat)
                        && (fromMat == mat
                            || fromMat == Material.GLASS
                            // if this route is allowing to expand if the origin route is invalid, check for a sticky piston
                            || fromMat == Material.STICKY_PISTON
                        )
            ) {
                add(relVec);
                Routes.expandRoute(this, rel, from);
            }
        }

        @Override
        protected TransferOutputEntry createOutputEntry(World world, BlockVector vec) {
            return new TransferOutputEntry(world, vec);
        }

        public boolean start(TransferCollection collection) {
            boolean transferred = false;
            for (TransferOutputEntry entry : outputs.get(Routes.DEFAULT_CONTEXT, LinkedList::new)) {
                if (entry.handle(collection)) {
                    transferred = true;
                }

                if (collection.isTransferEmpty()) {
                    break;
                }
            }

            return transferred;
        }
    }

    public static class Signal extends AbstractRoute<Signal, SignalOutputEntry> {

        private final Map<BlockVector, Integer> signals = new HashMap<>();

        public Signal(BlockVector start) {
            super(start);
        }

        @Override
        public RouteFactory<Signal> getFactory() {
            return new RouteFactory.SignalRouteFactory();
        }

        @Override
        public void search(Block from, Material fromMat, BlockVector relVec, Block rel) {
            int signal = signals.getOrDefault(BlockUtil.getVec(from), 16);
            Material mat = rel.getType();
            if (mat == Material.REPEATER) {
                // check if this repeater continues the signal route or triggers an output
                if (!BlockUtil.getPointingBlock(rel, false).equals(from)) {
                    // this repeater does not connect with the input
                    return;
                }
                add(relVec);

                Block facing = BlockUtil.getPointingBlock(rel, true);
                // facing sticky piston - signal output (for power-central to mechanics)
                if (facing.getType() == Material.STICKY_PISTON) {
                    addOutput(from.getWorld(), BlockUtil.getVec(facing), SignalSource.FROM_POWER_CENTRAL);
                    return;
                }

                if (!expandWire(facing, rel, 16)
                        && facing.getType().isSolid() && facing.getType().isOccluding()) {
                    signals.put(relVec, 16);
                    Routes.expandRoute(this, rel, relVec, ((Directional)rel.getBlockData()).getFacing().getOppositeFace());
                }

            // comparator - signal output (for generator to power-central)
            } else if (mat == Material.COMPARATOR && BlockUtil.getPointingBlock(rel, false).equals(from)) {
                Block facing = BlockUtil.getPointingBlock(rel, true);

                // check if the comparator is facing outwards
                if (!facing.equals(from)) {
                    add(relVec);
                    addOutput(from.getWorld(), BlockUtil.getVec(facing), SignalSource.TO_POWER_CENTRAL);

                    expandWire(facing, rel, 16);
                }

            // check for expand signal route
            } else if (signal > 1) {
                if (mat == Material.REDSTONE_WIRE) {
                    expandWire(from, rel, relVec, signal - 1);
                    return;
                }

                if (from.getType() == Material.REPEATER && mat.isSolid() && mat.isOccluding()) {
                    add(relVec);
                    for (BlockFace face : Routes.SIGNAL_EXPAND_DIRECTIONS) {
                        Block sourceRel = rel.getRelative(face);
                        if (!sourceRel.equals(from)) {
                            expandWire(sourceRel, rel, 16);
                        }
                    }
                }

                Block up = rel.getRelative(BlockFace.UP);
                Block down = rel.getRelative(BlockFace.DOWN);
                Block insulatorUp = from.getRelative(BlockFace.UP);

                if (up.getType() == Material.REDSTONE_WIRE
                        && (from.getType() == Material.REDSTONE_WIRE && !insulatorUp.getType().isSolid() && !insulatorUp.getType().isOccluding()
                        || from.getType() == Material.REPEATER && BlockUtil.getPointingBlock(from, true).equals(rel)
                        )
                ) {
                    expandWire(up, from, from.getType() == Material.REPEATER ? 16 : signal - 1);
                }

                if (from.getType() == Material.REDSTONE_WIRE
                        && (down.getType() == Material.REDSTONE_WIRE && !mat.isSolid() && !mat.isOccluding()
                        || down.getType() == Material.REPEATER && BlockUtil.getPointingBlock(down, false).equals(from.getRelative(BlockFace.DOWN)))) {
                    expandWire(down, from, signal - 1);
                } else if (from.getType() == Material.REPEATER
                        && mat.isSolid() && mat.isOccluding()
                        && down.getType() == Material.REDSTONE_WIRE) {
                    expandWire(from, down, BlockUtil.getVec(down), signal - 1);
                }
            }
        }

        private boolean expandWire(Block block, Block from, int signal) {
            add(BlockUtil.getVec(block));
            if (block.getType() == Material.REDSTONE_WIRE) {
                expandWire(from, block, BlockUtil.getVec(block), signal);
                return true;
            } else if (block.getType() == Material.REPEATER && BlockUtil.getPointingBlock(block, false).equals(from)) {
                signals.put(BlockUtil.getVec(block), 16);
                Routes.expandRoute(this, block, BlockUtil.getVec(block), ((Directional)block.getBlockData()).getFacing().getOppositeFace());
                return true;
            }

            return false;
        }

        private void expandWire(Block from, Block rel, BlockVector relVec, int signal) {
            add(relVec);
            signals.put(relVec, signal);
            Routes.expandRoute(this, rel, from);
        }

        @Override
        protected SignalOutputEntry createOutputEntry(World world, BlockVector vec) {
            return new SignalOutputEntry(world, vec);
        }

        public boolean start(SignalSource source) {
            if (!source.preSignal(this)) {
                return false;
            }

            // handle signal outputs
            int mechanics = 0;
            for (SignalOutputEntry entry : outputs.get(source.getContext(), LinkedList::new)) {
                if (entry.handle(source)) {
                    mechanics++;
                }
            }

            // handle power related mechanic stress
            if (outputs.get(source.getContext()).isEmpty() || mechanics < outputs.get(source.getContext()).size()) {
                source.postSignal(this, mechanics);
            }

            return mechanics > 0;
        }
    }
}
