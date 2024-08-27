package dk.superawesome.factorio.mechanics.profiles.accesible;

import dk.superawesome.factorio.building.Building;
import dk.superawesome.factorio.building.Buildings;
import dk.superawesome.factorio.gui.GuiFactory;
import dk.superawesome.factorio.gui.impl.StorageBoxGui;
import dk.superawesome.factorio.mechanics.*;
import dk.superawesome.factorio.mechanics.impl.accessible.StorageBox;
import dk.superawesome.factorio.mechanics.transfer.ItemCollection;
import dk.superawesome.factorio.util.Array;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StorageBoxProfile implements GuiMechanicProfile<StorageBox> {

    private final MechanicFactory<StorageBox> factory = new StorageBoxMechanicFactory();
    private final GuiFactory<StorageBox, StorageBoxGui> guiFactory = new StorageBoxGuiFactory();

    @Override
    public String getName() {
        return "Storage Box";
    }

    @Override
    public Building getBuilding() {
        return Buildings.STORAGE_BOX;
    }

    @Override
    public MechanicFactory<StorageBox> getFactory() {
        return factory;
    }

    @Override
    public StorageProvider<StorageBox> getStorageProvider() {
        return StorageProvider.Builder.<StorageBox>makeContext()
                .set(StorageBoxGui.STORAGE_CONTEXT, IntStream.range(0, StorageBoxGui.STORED_SIZE).boxed().collect(Collectors.toList()), m -> m)
                .build();
    }

    @Override
    public GuiFactory<StorageBox, StorageBoxGui> getGuiFactory() {
        return guiFactory;
    }

    @Override
    public MechanicLevel.Registry getLevelRegistry() {
        return MechanicLevel.Registry.Builder
                .make(5)
                .setDescription(2, Arrays.asList("Storage: §e36 §f-> §e75"))
                .setDescription(3, Arrays.asList("Storage: §e75 §f-> §e150"))
                .setDescription(4, Arrays.asList("Storage: §e150 §f-> §e250"))
                .setDescription(5, Arrays.asList("Storage: §e250 §f-> §e400"))

                .mark(ItemCollection.CAPACITY_MARK, Array.fromData(36, 75, 150, 250, 400))
                .mark(MechanicLevel.XP_REQUIRES_MARK, Array.fromData(0, 0, 0, 0))
                .build();
    }

    @Override
    public int getID() {
        return 2;
    }

    private static class StorageBoxMechanicFactory implements MechanicFactory<StorageBox> {

        @Override
        public StorageBox create(Location loc, BlockFace rotation, MechanicStorageContext context) {
            return new StorageBox(loc, rotation, context);
        }
    }

    private static class StorageBoxGuiFactory implements GuiFactory<StorageBox, StorageBoxGui> {

        @Override
        public StorageBoxGui create(StorageBox mechanic, AtomicReference<StorageBoxGui> inUseReference) {
            return new StorageBoxGui(mechanic, inUseReference);
        }
    }
}
