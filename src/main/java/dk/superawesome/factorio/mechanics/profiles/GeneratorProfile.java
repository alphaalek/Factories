package dk.superawesome.factorio.mechanics.profiles;

import dk.superawesome.factorio.building.Building;
import dk.superawesome.factorio.building.Buildings;
import dk.superawesome.factorio.gui.GuiFactory;
import dk.superawesome.factorio.gui.impl.GeneratorGui;
import dk.superawesome.factorio.mechanics.MechanicFactory;
import dk.superawesome.factorio.mechanics.MechanicLevel;
import dk.superawesome.factorio.mechanics.MechanicProfile;
import dk.superawesome.factorio.mechanics.MechanicStorageContext;
import dk.superawesome.factorio.mechanics.impl.Generator;
import dk.superawesome.factorio.mechanics.transfer.ItemCollection;
import dk.superawesome.factorio.util.Array;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.concurrent.atomic.AtomicReference;

public class GeneratorProfile implements MechanicProfile<Generator, GeneratorGui> {

    private static final MechanicFactory<Generator> factory = new GeneratorMechanicFactory();
    private static final GuiFactory<Generator, GeneratorGui> guiFactory = new GeneratorGuiFactory();

    @Override
    public String getName() {
        return "Generator";
    }

    @Override
    public Building getBuilding() {
        return Buildings.GENERATOR;
    }

    @Override
    public MechanicFactory<Generator> getFactory() {
        return factory;
    }

    @Override
    public GuiFactory<Generator, GeneratorGui> getGuiFactory() {
        return guiFactory;
    }

    @Override
    public MechanicLevel.Registry getLevelRegistry() {
        return MechanicLevel.Registry.Builder
                .make(1)
                .mark(ItemCollection.CAPACITY_MARK, Array.fromData(64 * 12))
                .build();
    }

    @Override
    public int getID() {
        return 5;
    }

    private static class GeneratorMechanicFactory implements MechanicFactory<Generator> {

        @Override
        public Generator create(Location loc, BlockFace rotation, MechanicStorageContext context) {
            return new Generator(loc, rotation, context);
        }
    }

    private static class GeneratorGuiFactory implements GuiFactory<Generator, GeneratorGui> {

        @Override
        public GeneratorGui create(Generator mechanic, AtomicReference<GeneratorGui> inUseReference) {
            return new GeneratorGui(mechanic, inUseReference);
        }
    }
}
