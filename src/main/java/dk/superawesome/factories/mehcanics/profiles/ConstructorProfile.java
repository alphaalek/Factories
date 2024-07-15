package dk.superawesome.factories.mehcanics.profiles;

import dk.superawesome.factories.building.Building;
import dk.superawesome.factories.building.Buildings;
import dk.superawesome.factories.gui.BaseGui;
import dk.superawesome.factories.gui.GuiFactory;
import dk.superawesome.factories.gui.impl.ConstructorGui;
import dk.superawesome.factories.mehcanics.MechanicFactory;
import dk.superawesome.factories.mehcanics.MechanicProfile;
import dk.superawesome.factories.mehcanics.impl.Constructor;
import org.bukkit.Location;

import java.util.concurrent.atomic.AtomicReference;

public class ConstructorProfile implements MechanicProfile<Constructor> {

    private final MechanicFactory<Constructor> factory = new ConstructorMechanicFactory();
    private final GuiFactory<Constructor, ConstructorGui> guiFactory = new ConstructorGuiFactory();

    @Override
    public String getName() {
        return "Constructor";
    }

    @Override
    public Building getBuilding() {
        return Buildings.CONSTRUCTOR;
    }

    @Override
    public MechanicFactory<Constructor> getFactory() {
        return factory;
    }

    @Override
    public GuiFactory<Constructor, ConstructorGui> getGuiFactory() {
        return guiFactory;
    }

    @Override
    public int getID() {
        return 0;
    }

    private static class ConstructorMechanicFactory implements MechanicFactory<Constructor> {

        @Override
        public Constructor create(Location loc) {
            return new Constructor(loc);
        }
    }

    private static class ConstructorGuiFactory implements GuiFactory<Constructor, ConstructorGui> {

        @Override
        public ConstructorGui create(Constructor constructor, AtomicReference<BaseGui> inUseReference) {
            return new ConstructorGui(constructor, inUseReference);
        }
    }
}
