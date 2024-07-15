package dk.superawesome.factories.mehcanics.profiles;

import dk.superawesome.factories.building.Building;
import dk.superawesome.factories.building.Buildings;
import dk.superawesome.factories.mehcanics.MechanicFactory;
import dk.superawesome.factories.mehcanics.MechanicProfile;
import dk.superawesome.factories.mehcanics.impl.StorageBox;
import org.bukkit.Location;

public class StorageBoxProfile implements MechanicProfile<StorageBox> {

    private final MechanicFactory<StorageBox> factory = new StorageBoxFactory();

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
    public int getID() {
        return 1;
    }

    private static class StorageBoxFactory implements MechanicFactory<StorageBox> {

        @Override
        public StorageBox create(Location loc) {
            return new StorageBox(loc);
        }
    }
}
