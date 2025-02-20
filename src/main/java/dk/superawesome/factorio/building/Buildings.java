package dk.superawesome.factorio.building;

import com.google.common.util.concurrent.Runnables;
import dk.superawesome.factorio.building.impl.*;
import dk.superawesome.factorio.mechanics.Mechanic;
import dk.superawesome.factorio.util.Array;
import dk.superawesome.factorio.util.ChainRunnable;
import dk.superawesome.factorio.util.statics.BlockUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Buildings {

    public static final Building ASSEMBLER;
    public static final Building ASSEMBLER_TRIGGER;
    public static final Building CAULDRON;
    public static final Building COLLECTOR;
    public static final Building COMPARATOR;
    public static final Building CONSTRUCTOR;
    public static final Building EMERALD_FORGE;
    public static final BuildingCollection GATE;
    public static final Building GENERATOR;
    public static final Building LIQUID_TANK;
    public static final Building SMELTER;
    public static final Building SOLAR_CELL;
    public static final Building STORAGE_BOX;
    public static final Building POWER_CENTRAL;
    public static final Building POWER_LIFTER;
    public static final Building REFINERY;

    static {
        buildings = new Array<>();

        ASSEMBLER         = loadBuilding(new AssemblerBuilding());
        ASSEMBLER_TRIGGER = loadBuilding(new AssemblerTriggerBuilding());
        CAULDRON          = loadBuilding(new CauldronBuilding());
        COLLECTOR         = loadBuilding(new CollectorBuilding());
        COMPARATOR        = loadBuilding(new ComparatorBuilding());
        CONSTRUCTOR       = loadBuilding(new ConstructorBuilding());
        EMERALD_FORGE     = loadBuilding(new EmeraldForgeBuilding());
        GATE              = loadBuilding(new GateBuilding());
        GENERATOR         = loadBuilding(new GeneratorBuilding());
        LIQUID_TANK       = loadBuilding(new LiquidTankBuilding());
        SMELTER           = loadBuilding(new SmelterBuilding());
        SOLAR_CELL        = loadBuilding(new SolarCellBuilding());
        STORAGE_BOX       = loadBuilding(new StorageBoxBuilding());
        POWER_CENTRAL     = loadBuilding(new PowerCentralBuilding());
        POWER_LIFTER      = loadBuilding(new PowerLifterBuilding());
        REFINERY          = loadBuilding(new RefineryBuilding());
    }

    private static final Array<BlockCollection> buildings;

    public static <B extends BlockCollection> B loadBuilding(B building) {
        buildings.set(building, building);
        return building;
    }

    public static Array<BlockCollection> getBuildings() {
        return buildings;
    }

    public static List<Location> getLocations(Building building, Location rel, BlockFace rot) {
        List<Location> locs = new ArrayList<>();
        for (BlockVector relVec : building.getRelatives()) {
            Location loc = BlockUtil.getRel(rel, BlockUtil.rotateVec(relVec, Building.DEFAULT_ROTATION, rot));
            locs.add(loc);
        }

        return locs;
    }

    public static List<Location> getLocations(Mechanic<?> mechanic) {
        return getLocations(mechanic.getBuilding(), mechanic.getLocation(), mechanic.getRotation());
    }

    public static boolean intersects(Location loc, Mechanic<?> mechanic) {
        for (Location relLoc : getLocations(mechanic)) {
            if (BlockUtil.blockEquals(relLoc, loc)) {
                return true;
            }
        }

        return false;
    }

    public static boolean canMoveTo(Location to, BlockFace rotation, Mechanic<?> mechanic) {
        for (Location relLoc : getLocations(mechanic.getBuilding(), to, rotation)) {
            // check if this block can be placed in the world
            if (relLoc.getBlock().getType() != Material.AIR && !getLocations(mechanic).contains(relLoc)) {
                return false;
            }
        }

        return true;
    }

    public static boolean hasSpaceFor(Block sign, Mechanic<?> mechanic) {
        if (mechanic.getBuilding() instanceof Buildable) {
            for (Location relLoc : getLocations(mechanic)) {
                // check if this block can be placed in the world
                if (!relLoc.getBlock().equals(sign)
                        && !relLoc.getBlock().getLocation().equals(mechanic.getLocation())
                        && relLoc.getBlock().getType() != Material.AIR) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean checkCanBuild(Mechanic<?> mechanic) {
        Building building = mechanic.getBuilding();
        if (building instanceof Matcher matcher) {
            int i = 0;
            // check if all the placed blocks matches the matcher
            for (Location relLoc : getLocations(mechanic)) {
                if (!matcher.getMaterials().get(i++).test(relLoc.getBlock().getType())) {
                    return false;
                }
            }
        }

        return true;
    }

    public static void build(World world, Mechanic<?> mechanic, List<Location> ignore) {
        Building building = mechanic.getBuilding();
        if (building instanceof Buildable buildable) {
            int i = 0;
            for (Location relLoc : getLocations(mechanic)) {
                if (ignore.contains(relLoc)) {
                    i++;
                    continue;
                }

                Block block = world.getBlockAt(relLoc);
                if (block.getType() != Material.AIR) {
                    for (ItemStack drop : block.getDrops()) {
                        world.dropItemNaturally(relLoc, drop);
                    }
                }

                buildable.getBlocks().get(i++)
                        .accept(block, mechanic.getRotation());
            }
        }
    }

    public static void copy(World fromWorld, World toWorld, Location from, BlockFace fromRot, Location to, BlockFace toRot, Mechanic<?> mechanic) {
        int i = 0;
        List<Location> locations = getLocations(mechanic.getBuilding(), from, fromRot);

        ChainRunnable runnable = ChainRunnable.empty();
        for (Location relLoc : getLocations(mechanic.getBuilding(), to, toRot)) {
            Block relBlock = fromWorld.getBlockAt(relLoc);
            Block fromBlock = toWorld.getBlockAt(locations.get(i++));

            BlockFace rot = BlockUtil.getFacing(fromBlock);
            if (rot != null) {
                BlockUtil.rotate(fromBlock, BlockUtil.getRotationRelative(fromRot, rot, toRot));
            }

            // we have to store the block data to be copied
            Material type = fromBlock.getType();
            BlockData data = fromBlock.getBlockData().clone();
            runnable = runnable.thenDo(() -> {
                relBlock.setType(type, false);
                relBlock.setBlockData(data);
            });
        }

        runnable.run();
    }

    public static void remove(Mechanic<?> mechanic, Location loc, BlockFace rot, boolean dropSign) {
        Block sign = mechanic.getBuilding().getSign(mechanic);
        if (dropSign) {
            for (ItemStack drops : sign.getDrops()) {
                loc.getWorld().dropItemNaturally(loc, drops);
            }
        }

        if (mechanic.getBuilding() instanceof Buildable) {
            destroy(mechanic, loc, rot);
        } else {
            sign.setType(Material.AIR);
        }
    }

    public static void destroy(Mechanic<?> mechanic, Location loc, BlockFace rot) {
        destroy(mechanic, loc, rot, Collections.emptyList());
    }

    public static void destroy(Mechanic<?> mechanic, Location loc, BlockFace rot, List<Location> ignore) {
        for (Location relLoc : getLocations(mechanic.getBuilding(), loc, rot)) {
            if (!ignore.contains(relLoc)) {
                loc.getWorld().getBlockAt(relLoc).setType(Material.AIR, false); // don't apply physics
            }
        }
    }

    public static void remove(Mechanic<?> mechanic, boolean dropSign) {
        remove(mechanic, mechanic.getLocation(), mechanic.getRotation(), dropSign);
    }
}
