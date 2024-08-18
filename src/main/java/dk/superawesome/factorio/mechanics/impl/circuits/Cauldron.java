package dk.superawesome.factorio.mechanics.impl.circuits;

import dk.superawesome.factorio.Factorio;
import dk.superawesome.factorio.mechanics.*;
import dk.superawesome.factorio.mechanics.stackregistry.Fluid;
import dk.superawesome.factorio.mechanics.transfer.FluidCollection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.CauldronLevelChangeEvent;

import java.util.Arrays;

import static org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.UNKNOWN;

public class Cauldron extends AbstractMechanic<Cauldron> implements FluidCollection {

    private Fluid fluid;
    private int amount;

    public Cauldron(Location loc, BlockFace rotation, MechanicStorageContext context) {
        super(loc, rotation, context);
    }

    @Override
    public MechanicProfile<Cauldron> getProfile() {
        return Profiles.CAULDRON;
    }

    private void checkCauldron() {
        Levelled cauldron = (Levelled) loc.getBlock().getBlockData();
        amount = cauldron.getLevel();

        if (amount == 0) {
            this.fluid = null;
        } else if (fluid == null) {
            switch (loc.getBlock().getType()) {
                case WATER_CAULDRON -> fluid = Fluid.WATER;
                case LAVA_CAULDRON -> fluid = Fluid.LAVA;
                case POWDER_SNOW_CAULDRON -> fluid = Fluid.SNOW;
            }
        }
    }

    @EventHandler
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        if (event.getBlock().equals(loc.getBlock())) {
            Bukkit.getScheduler().runTask(Factorio.get(), this::checkCauldron);
        }
    }

    @Override
    public void onBlocksLoaded() {
        checkCauldron();
    }

    @Override
    public boolean isTransferEmpty() {
        return this.fluid == null;
    }

    @Override
    public DelayHandler getTransferDelayHandler() {
        return DelayHandler.NO_DELAY;
    }

    @Override
    public int getMaxTransfer() {
        return this.fluid.getMaxTransfer();
    }

    @Override
    public int getTransferAmount() {
        return this.amount;
    }

    @Override
    public double getTransferEnergyCost() {
        return 1d / 4d;
    }

    @Override
    public Fluid getFluid() {
        return this.fluid;
    }

    @Override
    public boolean hasFluid(Fluid fluid) {
        return this.fluid != null && this.fluid.equals(fluid);
    }

    @Override
    public int take(int amount) {
        int take = Math.min(this.amount, amount);
        this.amount -= take;
        if (this.amount == 0) {
            this.fluid = null;
            loc.getBlock().setType(Material.CAULDRON);
        } else {
            Levelled cauldron = (Levelled) loc.getBlock().getBlockData();
            cauldron.setLevel(this.amount);
            loc.getBlock().setBlockData(cauldron);
        }

        return take;
    }
}
