package dk.superawesome.factories.mehcanics.impl;

import dk.superawesome.factories.gui.impl.ConstructorGui;
import dk.superawesome.factories.items.ItemCollection;
import dk.superawesome.factories.mehcanics.Mechanic;
import dk.superawesome.factories.mehcanics.MechanicProfile;
import dk.superawesome.factories.mehcanics.Profiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class Constructor implements Mechanic<Constructor> {

    private final ItemStack[] craftingGridItems = new ItemStack[9];
    private ItemStack recipeResult;
    private final Location loc;

    public Constructor(Location loc) {
        this.loc = loc;
    }

    public ItemStack[] getCraftingGridItems() {
        return craftingGridItems;
    }

    public ItemStack getRecipeResult() {
        return recipeResult;
    }

    public void setRecipeResult(ItemStack result) {
        this.recipeResult = result;
    }

    @Override
    public Location getLocation() {
        return loc;
    }

    @Override
    public int getLevel() {
        return 1;
    }

    @Override
    public MechanicProfile<Constructor> getProfile() {
        return Profiles.CONSTRUCTOR;
    }

    @Override
    public void openInventory(Player player) {
        ConstructorGui gui = new ConstructorGui(this);
        player.openInventory(gui.getInventory());
    }

    @Override
    public void pipePut(ItemCollection collection) {
        for (ItemStack craft : craftingGridItems) {
            // check if this slot contains anything and can hold more
            if (craft == null || craft.getAmount() == craft.getMaxStackSize()) {
                continue;
            }

            // poll the item for this crafting slot from the item collection
            ItemStack req = craft.clone();
            req.setAmount(1);

            List<ItemStack> stacks = collection.take(req);
            if (!stacks.isEmpty() && stacks.get(0).isSimilar(craft)) {
                craft.setAmount(craft.getAmount() + stacks.get(0).getAmount());
            }
        }

        Bukkit.getLogger().info("Putting items into " + getProfile().getName() + " " + collection);
    }
}
