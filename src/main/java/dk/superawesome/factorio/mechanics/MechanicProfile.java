package dk.superawesome.factorio.mechanics;

import dk.superawesome.factorio.building.Building;
import dk.superawesome.factorio.gui.BaseGui;
import dk.superawesome.factorio.gui.GuiFactory;
import dk.superawesome.factorio.util.Identifiable;

public interface MechanicProfile<M extends Mechanic<M>> extends Identifiable {

    String getName();

    Building getBuilding();

    MechanicFactory<M> getFactory();

    MechanicLevel.Registry getLevelRegistry();
}
