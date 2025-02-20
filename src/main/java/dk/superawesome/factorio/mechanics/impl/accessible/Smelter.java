package dk.superawesome.factorio.mechanics.impl.accessible;

import dk.superawesome.factorio.gui.impl.SmelterGui;
import dk.superawesome.factorio.mechanics.*;
import dk.superawesome.factorio.mechanics.routes.events.pipe.PipePutEvent;
import dk.superawesome.factorio.mechanics.stackregistry.Fuel;
import dk.superawesome.factorio.mechanics.transfer.ItemCollection;
import dk.superawesome.factorio.mechanics.transfer.ItemContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.util.BlockVector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;

public class Smelter extends AbstractMechanic<Smelter> implements FuelMechanic, AccessibleMechanic, ThinkingMechanic, ItemCollection, ItemContainer {

    public static final int INGREDIENT_CAPACITY_MARK = 1;
    public static final int FUEL_CAPACITY_MARK = 2;

    private static final List<BlockVector> WASTE_OUTPUT_RELATIVES = Arrays.asList(
            new BlockVector(0, 2, 0),

            new BlockVector(0, 1, 1),
            new BlockVector(0, 1, -1),
            new BlockVector(1, 1, 0),
            new BlockVector(-1, 1, 0),

            new BlockVector(0, 0, 1),
            new BlockVector(0, 0, -1),
            new BlockVector(1, 0, 0),
            new BlockVector(-1, 0, 0)
    );

    private static final List<FurnaceRecipe> FURNACE_RECIPES = new ArrayList<>();

    static {
        Iterator<Recipe> recipeIterator = Bukkit.recipeIterator();
        while (recipeIterator.hasNext()) {
            Recipe recipe = recipeIterator.next();

            if (recipe instanceof FurnaceRecipe furnaceRecipe) {
                FURNACE_RECIPES.add(furnaceRecipe);
            }
        }
    }

    private final Storage ingredientStorage = getProfile().getStorageProvider().createStorage(this, SmelterGui.INGREDIENT_CONTEXT);
    private final Storage storedStorage = getProfile().getStorageProvider().createStorage(this, SmelterGui.STORED_CONTEXT);
    private final XPDist xpDist = new XPDist(100, 0.001, 0.01);
    private final DelayHandler thinkDelayHandler = new DelayHandler(level.get(MechanicLevel.THINK_DELAY_MARK));
    private final DelayHandler transferDelayHandler = new DelayHandler(10);

    private ItemStack ingredient;
    private int ingredientAmount;

    private ItemStack cachedSmeltResult;
    private ItemStack smeltResult;
    private Fuel fuel;
    private int fuelAmount;
    private Fuel currentFuel;
    private float currentFuelAmount;

    private boolean declinedState;
    private ItemStack storageType;
    private int storageAmount;

    public Smelter(Location loc, BlockFace rotation, MechanicStorageContext context, boolean hasWallSign, boolean isBuild) {
        super(loc, rotation, context, hasWallSign, isBuild);
        loadFromStorage();
    }

    @Override
    public void load(MechanicStorageContext context) throws Exception {
        ByteArrayInputStream str = context.getData();
        this.ingredient = context.getSerializer().readItemStack(str);
        this.smeltResult = context.getSerializer().readItemStack(str);
        setIngredientAmount(context.getSerializer().readInt(str)); // ensure no zero if ingredient set

        loadFuel(context, str);

        this.storageType = context.getSerializer().readItemStack(str);
        setStorageAmount(context.getSerializer().readInt(str)); // ensure no zero if storage set

        clearSmeltResult: {
            if (this.ingredientAmount > 0 && this.ingredient == null) {
                this.ingredientAmount = 0;
            } else if (this.ingredientAmount == 0 && this.ingredient != null) {
                this.ingredient = null;
            } else break clearSmeltResult;

            this.smeltResult = null;
            this.cachedSmeltResult = null;
        }
        if (this.fuelAmount > 0 && this.fuel == null) {
            this.fuelAmount = 0;
        } else if (this.fuelAmount == 0 && this.fuel != null) {
            this.fuel = null;
        }
        if (this.storageAmount > 0 && this.storageType == null) {
            this.storageAmount = 0;
        } else if (this.storageAmount == 0 && this.storageType != null) {
            this.storageType = null;
        }
    }

    @Override
    public void save(MechanicStorageContext context) throws IOException, SQLException {
        ByteArrayOutputStream str = new ByteArrayOutputStream();
        context.getSerializer().writeItemStack(str, this.ingredient);
        context.getSerializer().writeItemStack(str, this.smeltResult);
        context.getSerializer().writeInt(str, this.ingredientAmount);

        saveFuel(context, str);

        context.getSerializer().writeItemStack(str, this.storageType);
        context.getSerializer().writeInt(str, this.storageAmount);

        context.uploadData(str);
    }

    @Override
    public void onUpgrade(int newLevel) {
        this.thinkDelayHandler.setDelay(this.level.getInt(MechanicLevel.THINK_DELAY_MARK));

        super.onUpgrade(newLevel);
    }

    @Override
    public MechanicProfile<Smelter> getProfile() {
        return Profiles.SMELTER;
    }

    @Override
    public void pipePut(ItemCollection collection, PipePutEvent event) {
        ingredientStorage.ensureValidStorage();

        if (tickThrottle.isThrottled()) {
            return;
        }

        if (ingredient != null && collection.has(ingredient) || ingredient == null && collection.has(this::canSmelt)) {
            int add = this.<SmelterGui>put(collection, getIngredientCapacity() - ingredientAmount, getGuiInUse(), SmelterGui::updateAddedIngredients, ingredientStorage);
            if (add > 0) {
                ingredientAmount += add;
                event.setTransferred(true);
            }

            if (smeltResult == null) {
                smeltResult = cachedSmeltResult;
            }
        }

        this.<SmelterGui>putFuel(collection, this, event, getGuiInUse(), SmelterGui::updateAddedFuel);
    }

    @Override
    public int getCapacity() {
        return getCapacitySlots(level) *
                Optional.ofNullable(storageType)
                        .map(ItemStack::getMaxStackSize)
                        .orElse(64);
    }

    @Override
    public int getFuelCapacity() {
        return level.getInt(FUEL_CAPACITY_MARK) *
                Optional.ofNullable(fuel)
                        .map(Fuel::material)
                        .map(Material::getMaxStackSize)
                        .orElse(64);
    }

    public int getIngredientCapacity() {
        return level.getInt(INGREDIENT_CAPACITY_MARK) *
                Optional.ofNullable(ingredient)
                        .map(ItemStack::getMaxStackSize)
                        .orElse(64);
    }

    @Override
    public List<BlockVector> getWasteOutputs() {
        return WASTE_OUTPUT_RELATIVES;
    }

    public static boolean canSmeltStatic(ItemStack item) {
        return FURNACE_RECIPES.stream().anyMatch(r -> r.getInputChoice().test(item));
    }

    public boolean canSmelt(ItemStack item) {
        boolean can = FURNACE_RECIPES.stream().peek(r -> cachedSmeltResult = r.getResult()).anyMatch(r -> r.getInputChoice().test(item));

        if (!can) {
            cachedSmeltResult = null;
        }

        return can;
    }

    @Override
    public DelayHandler getThinkDelayHandler() {
        return thinkDelayHandler;
    }

    @Override
    public void think() {
        cachedSmeltResult = null;

        // check if the smelters storage has any previously smelted items which is not the
        // same as the current smelting result.
        // if it has any, we can't smelt the new items until all the previously smelted items are removed
        // from the storage.
        if (storageType != null && smeltResult != null && !storageType.isSimilar(smeltResult)) {
            // set declined state and notify the user that this smelting is not possible yet
            if (!declinedState) {
                declinedState = true;
                SmelterGui gui = this.<SmelterGui>getGuiInUse().get();
                if (gui != null) {
                    gui.updateDeclinedState(true);
                }
            }

            return;
        }

        // remove declined state if set and smelting is available
        if (declinedState) {
            declinedState = false;
            SmelterGui gui = this.<SmelterGui>getGuiInUse().get();
            if (gui != null) {
                gui.updateDeclinedState(false);
            }
        }

        // update smelt result if it failed to do so
        if (ingredient != null && smeltResult == null) {
            canSmelt(ingredient);
            smeltResult = cachedSmeltResult;

            if (smeltResult == null) {
                // this item can not be smelted, it shouldn't be in the smelter
                ingredient = null;
                ingredientAmount = 0;
                return;
            }
        }

        // if there are no ingredients ready to be smelted, don't continue
        if (ingredient == null
                // if there is no space left, don't continue
                || storageAmount + smeltResult.getAmount() > getCapacity()) {
            return;
        }

        FuelState state = useFuel();
        if (state == FuelState.ABORT) {
            return;
        }

        // update storage type if not set
        if (storageType == null) {
            ItemStack stored = smeltResult;
            stored.setAmount(1);
            storageType = stored;
        }

        // do the smelting
        ingredientAmount -= 1;
        storageAmount += smeltResult.getAmount();

        xp += xpDist.poll();

        SmelterGui gui = this.<SmelterGui>getGuiInUse().get();
        if (gui != null) {
            gui.updateRemovedIngredients(1);
            gui.updateAddedStorage(smeltResult.getAmount());
            gui.updateFuelState();
        }

        // the smelter does not have any ingredients left, clear up
        if (ingredientAmount == 0) {
            ingredient = null;
            cachedSmeltResult = null;
            smeltResult = null;
        }
    }

    @Override
    public boolean has(ItemStack stack) {
        return has(i -> i.isSimilar(stack) && storageAmount >= stack.getAmount());
    }

    @Override
    public boolean has(Predicate<ItemStack> stack) {
        return storageType != null && stack.test(storageType);
    }

    @Override
    public List<ItemStack> pipeTake(int amount) {
        storedStorage.ensureValidStorage();

        if (tickThrottle.isThrottled() || storageType == null || storageAmount == 0) {
            return Collections.emptyList();
        }

        return this.<SmelterGui>take((int) Math.min(getMaxTransfer(), amount), storageType, storageAmount, getGuiInUse(), SmelterGui::updateRemovedStorage, storedStorage);
    }

    @Override
    public boolean isTransferEmpty() {
        return storageType == null;
    }

    @Override
    public DelayHandler getTransferDelayHandler() {
        return transferDelayHandler;
    }

    @Override
    public double getMaxTransfer() {
        return storageType.getMaxStackSize();
    }

    @Override
    public double getTransferAmount() {
        return storageAmount;
    }

    @Override
    public boolean isContainerEmpty() {
        return fuel == null && ingredient == null;
    }

    @Override
    public double getTransferEnergyCost() {
        return 1d / 2d;
    }

    public ItemStack getIngredient() {
        return ingredient;
    }

    public void setIngredient(ItemStack stack) {
        this.ingredient = stack;
    }

    public int getIngredientAmount() {
        return ingredientAmount;
    }

    public void setIngredientAmount(int amount) {
        this.ingredientAmount = amount;

        if (this.ingredientAmount == 0) {
            ingredient = null;
            cachedSmeltResult = null;
            smeltResult = null;
        }
    }

    @Override
    public Fuel getFuel() {
        return fuel;
    }

    @Override
    public void setFuel(Fuel fuel) {
        this.fuel = fuel;

        if (this.fuel == null) {
            this.fuelAmount = 0;
        }
    }

    @Override
    public int getFuelAmount() {
        return fuelAmount;
    }

    @Override
    public void setFuelAmount(int amount) {
        this.fuelAmount = amount;

        if (this.fuelAmount == 0) {
            fuel = null;
        }
    }

    @Override
    public Fuel getCurrentFuel() {
        return currentFuel;
    }

    @Override
    public void setCurrentFuel(Fuel fuel) {
        this.currentFuel = fuel;

        if (this.currentFuel == null) {
            this.currentFuelAmount = 0;
        }
    }

    public ItemStack getSmeltResult() {
        return smeltResult;
    }

    public void setSmeltResult(ItemStack stack) {
        this.smeltResult = stack;
    }

    public ItemStack getStorageType() {
        return storageType;
    }

    public void setStorageType(ItemStack stack) {
        this.storageType = stack;

        if (this.storageType == null) {
            this.storageAmount = 0;
        }
    }

    public int getStorageAmount() {
        return storageAmount;
    }

    public void setStorageAmount(int amount) {
        this.storageAmount = amount;

        if (this.storageAmount == 0) {
            storageType = null;
        }
    }

    @Override
    public float getCurrentFuelAmount() {
        return currentFuelAmount;
    }

    @Override
    public void setCurrentFuelAmount(float amount) {
        this.currentFuelAmount = amount;
    }

    @Override
    public void removeFuel(int amount) {
        SmelterGui gui = this.<SmelterGui>getGuiInUse().get();
        if (gui != null) {
            gui.updateRemovedFuel(amount);
        }
    }

    public boolean isDeclined() {
        return declinedState;
    }

    public ItemStack getCachedSmeltResult() {
        return cachedSmeltResult;
    }
}
