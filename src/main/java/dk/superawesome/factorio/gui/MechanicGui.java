package dk.superawesome.factorio.gui;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import dk.superawesome.factorio.Factorio;
import dk.superawesome.factorio.mechanics.FuelMechanic;
import dk.superawesome.factorio.mechanics.Mechanic;
import dk.superawesome.factorio.mechanics.Storage;
import dk.superawesome.factorio.mechanics.StorageProvider;
import dk.superawesome.factorio.util.Callback;
import dk.superawesome.factorio.util.helper.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class MechanicGui<G extends BaseGui<G>, M extends Mechanic<M>> extends BaseGuiAdapter<G> {

    private final M mechanic;

    public MechanicGui(M mechanic, AtomicReference<G> inUseReference, Supplier<Callback> initCallback, String title) {
        super(initCallback, inUseReference, BaseGui.DOUBLE_CHEST, title, true);
        this.mechanic = mechanic;
    }

    public MechanicGui(M mechanic, AtomicReference<G> inUseReference, Supplier<Callback> initCallback) {
        this(mechanic, inUseReference, initCallback, mechanic.toString());
    }

    @Override
    public void loadItems() {
        int i = 0;
        for (GuiElement element : getGuiElements()) {
            int slot = 51 + i++;
            registerEvent(slot, event -> element.handle(event, (Player) event.getWhoClicked(), this));
            getInventory().setItem(slot, element.getItem());
        }

        loadInputOutputItems();
    }

    public abstract void loadInputOutputItems();

    protected List<GuiElement> getGuiElements() {
        return Arrays.asList(Elements.UPGRADE, Elements.MEMBERS, Elements.DELETE);
    }

    public M getMechanic() {
        return mechanic;
    }

    protected void openSignGuiAndCall(Player p, String total, Consumer<Double> function) {
        SignGUI gui = SignGUI.builder()
                .setLines("", "/ " + total, "---------------", "Vælg antal")
                // ensure synchronous call
                .callHandlerSynchronously(Factorio.get())
                // calls when the gui is closed
                .setHandler((player, result) -> {
                    // get the amount chosen and apply this to the function
                    double amount = 0;
                    try {
                        amount = Double.parseDouble(result.getLine(0));
                    } catch (NumberFormatException ex) {
                        // ignore
                    }

                    // ony apply the function if the player wrote a valid amount
                    if (amount <= 0) {
                        player.sendMessage("§cUgyldigt antal valgt!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                    } else {
                        function.accept(amount);
                    }

                    // return to the gui and reload view
                    return Arrays.asList(
                            SignGUIAction.openInventory(Factorio.get(), getInventory()), SignGUIAction.runSync(Factorio.get(), this::loadInputOutputItems));
                })
                .build();
        gui.open(p);
    }

    public void updateFuelState(List<Integer> slots) {
        if (getMechanic() instanceof FuelMechanic fuelMechanic) {
            int blaze = Math.round(fuelMechanic.getCurrentFuelAmount() * slots.size());
            int times = 0;
            for (int slot : slots) {
                if (++times > blaze) {
                    getInventory().setItem(slot, new ItemStack(Material.RED_STAINED_GLASS_PANE));
                    continue;
                }

                getInventory().setItem(slot, new ItemStack(Material.BLAZE_POWDER));
            }
        }
    }

    protected List<ItemStack> findItems(List<Integer> slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int i : slots) {
            ItemStack item = getInventory().getItem(i);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    public Storage getStorage(int context) {
        StorageProvider<M> provider = mechanic.getProfile().getStorageProvider();
        if (provider != null) {
            return provider.createStorage(mechanic, context);
        }

        return null;
    }

    protected int getAffectedAmountForAction(InventoryAction action, ItemStack item) {
        return switch (action) {
            case DROP_ALL_CURSOR, DROP_ALL_SLOT, PICKUP_ALL, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY -> item.getAmount();
            case DROP_ONE_SLOT, DROP_ONE_CURSOR, PICKUP_ONE -> 1;
            case PICKUP_HALF -> (int) Math.round(item.getAmount() / 2d);
            default -> 0;
        };
    }

    protected boolean handleOnlyCollectInteraction(InventoryClickEvent event, Storage storage) {
        if (event.getClickedInventory() != getInventory()) {
            return false;
        }

        if (event.getAction() == InventoryAction.PLACE_ONE
                || event.getAction() == InventoryAction.PLACE_ALL
                || event.getAction() == InventoryAction.PLACE_SOME
                || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            return true;
        }

        if (event.getCurrentItem() != null
                && event.getAction() != InventoryAction.COLLECT_TO_CURSOR
                && event.getAction() != InventoryAction.HOTBAR_SWAP
                && event.getAction() != InventoryAction.HOTBAR_MOVE_AND_READD) {
            int affected = getAffectedAmountForAction(event.getAction(), event.getCurrentItem());
            if (affected > 0) {
                if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    affected = Math.min(affected, getSpaceFor(event.getWhoClicked().getInventory(), event.getCurrentItem()));
                }

                storage.setAmount(storage.getAmount() - affected);
            }
        }

        return false;
    }

    private int getSpaceFor(Inventory inv, ItemStack item) {
        int space = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack at = inv.getItem(i);
            if (at == null) {
                space += 64;
            } else if (at.isSimilar(item)) {
                space += at.getMaxStackSize() - at.getAmount();
            }
        }

        return space;
    }

    protected boolean handleOnlyHotbarCollectInteraction(InventoryClickEvent event, Storage storage) {
        ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
        ItemStack at  = event.getView().getItem(event.getRawSlot());

        if (at != null && hotbarItem != null) {
            if (hotbarItem.isSimilar(at)) {
                int add = Math.min(at.getAmount(), hotbarItem.getMaxStackSize() - hotbarItem.getAmount());
                hotbarItem.setAmount(hotbarItem.getAmount() + add);
                at.setAmount(at.getAmount() - add);

                storage.setAmount(storage.getAmount() - add);
            }

            return true;
        } else if (at != null) {
            storage.setAmount(storage.getAmount() - at.getAmount());
        }

        // don't allow inserting items
        if (at == null) {
            return true;
        }

        return false;
    }

    public int calculateAmount(List<Integer> slots) {
        return findItems(slots).stream().mapToInt(ItemStack::getAmount).sum();
    }

    protected void updateAmount(Storage storage, Inventory source, List<Integer> slots, Consumer<Integer> leftover) {
        getMechanic().getTickThrottle().throttle();

        int before = calculateAmount(slots);
        Bukkit.getScheduler().runTask(Factorio.get(), () -> {
            // get the difference in items of the storage box inventory view
            int after = calculateAmount(slots);
            int diff = after - before;

            updateAmount(storage, source, diff, leftover);
        });
    }

    private Map<Integer, ItemStack> getMatchingSlots(ItemStack stack, boolean onlyPuttable) {
        return getSlots().entrySet().stream()
                .filter(e -> e.getValue() == null || (stack.isSimilar(e.getValue()) && (!onlyPuttable || e.getValue().getAmount() < 64)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<Integer, ItemStack> getSlots() {
        int[] j = {0}; // force to heap for lambda access

        Map<Integer, ItemStack> slots = new HashMap<>();
        IntStream.range(0, getInventory().getSize())
                .mapToObj(i -> getInventory().getItem(j[0] = i))
                .map(i -> i != null ? i.clone() : null)
                .forEach(i -> slots.put(j[0], i));
        return slots;
    }

    protected void fixSlotsPut(ItemStack item, Inventory source, List<Integer> ignoreSlots) {
        getMechanic().getTickThrottle().throttle();

        ItemStack stack = item.clone();
        Map<Integer, ItemStack> prev = getMatchingSlots(stack, true);
        Bukkit.getScheduler().runTask(Factorio.get(), () -> {
            Map<Integer, ItemStack> after = getMatchingSlots(stack, false);

            for (Map.Entry<Integer, ItemStack> entry : after.entrySet()) {
                if (!ignoreSlots.contains(entry.getKey())) {
                    int diff = Optional.ofNullable(entry.getValue()).map(ItemStack::getAmount).orElse(0)
                             - Optional.ofNullable(prev.get(entry.getKey())).map(ItemStack::getAmount).orElse(0);
                    if (diff > 0) { // diff can only be above zero if the Map$Entry#getValue is not null, no NPE inspection
                        getInventory().setItem(entry.getKey(), prev.get(entry.getKey()));
                        source.addItem(new ItemBuilder(entry.getValue()).setAmount(diff).build());
                    }
                }
            }
        });
    }

    protected void fixSlotsTake(Inventory source, InventoryView view, List<Integer> ignoreSlots) {
        getMechanic().getTickThrottle().throttle();

        Map<Integer, ItemStack> prev = getSlots();
        Bukkit.getScheduler().runTask(Factorio.get(), () -> {
            Map<Integer, ItemStack> after = getSlots();

            for (Map.Entry<Integer, ItemStack> entry : prev.entrySet()) {
                if (!ignoreSlots.contains(entry.getKey())) {
                    int diff = Optional.ofNullable(entry.getValue()).map(ItemStack::getAmount).orElse(0)
                            - Optional.ofNullable(after.get(entry.getKey())).map(ItemStack::getAmount).orElse(0);
                    if (diff > 0) { // diff can only be above zero if the Map$Entry#getValue is not null, no NPE inspection
                        getInventory().setItem(entry.getKey(), prev.get(entry.getKey()));

                        Map<Integer, ItemStack> left = source.removeItem(new ItemBuilder(entry.getValue()).setAmount(diff).build());
                        ItemStack cursor = view.getCursor();
                        if (!left.isEmpty() && cursor != null && cursor.isSimilar(entry.getValue())) {
                            // not all items which the action removed was found in the inventory afterward, check for cursor item

                            int leftLiteral = 0;
                            for (ItemStack leftItem : left.values()) {
                                leftLiteral += leftItem.getAmount();
                            }

                            // update cursor and remove the items left which couldn't be removed from the inventory
                            cursor.setAmount(cursor.getAmount() - leftLiteral);
                        }
                    }
                }
            }
        });
    }

    protected void updateAmount(Storage storage, Inventory source, int diff, Consumer<Integer> leftover) {
        getMechanic().getTickThrottle().throttle();

        // check if the storage box has enough space for these items
        if (diff > 0 && storage.getAmount() + diff > storage.getCapacity()) {
            // evaluate leftovers
            int left = storage.getAmount() + diff - storage.getCapacity();
            leftover.accept(left);
            storage.setAmount(storage.getCapacity());

            // add leftovers to player inventory again
            ItemStack item = storage.getStored().clone();
            item.setAmount(left);
            source.addItem(item);
        } else {
            // update storage amount in storage box
            storage.setAmount(storage.getAmount() + diff);
        }
    }

    protected int updateAddedItems(Inventory inventory, int amount, ItemStack stored, List<Integer> slots) {
        // find all the slots with items similar to the stored item and add to these slots
        int left = amount;
        for (int i : slots) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.isSimilar(stored)) {
                int add = Math.min(left, item.getMaxStackSize() - item.getAmount());

                left -= add;
                item.setAmount(item.getAmount() + add);

                if (left == 0) {
                    break;
                }
            }
        }

        // we still have some items left to be added, find the empty slots and add to them
        if (left > 0) {
            for (int i : slots) {
                ItemStack item = inventory.getItem(i);
                if (item == null) {
                    int add = Math.min(left, stored.getMaxStackSize());

                    left -= add;
                    ItemStack added = stored.clone();
                    added.setAmount(add);

                    inventory.setItem(i, added);
                }

                if (left == 0) {
                    break;
                }
            }
        }

        return left;
    }

    protected int updateRemovedItems(Inventory inventory, int amount, ItemStack stored, List<Integer> slots) {
        int left = amount;
        for (int i : slots) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.isSimilar(stored)) {
                int remove = Math.min(left, item.getAmount());

                left -= remove;
                item.setAmount(item.getAmount() - remove);

                if (left == 0) {
                    break;
                }
            }
        }

        return left;
    }

    protected void addItemsToSlots(ItemStack item, List<Integer> slots) {
        int left = item.getAmount();
        int i = 0;
        while (left > 0 && i < slots.size()) {
            int slot = slots.get(i++);
            ItemStack crafting = getInventory().getItem(slot);

            if (crafting != null
                    && crafting.isSimilar(item)
                    && crafting.getAmount() < crafting.getMaxStackSize()) {
                int add = Math.min(left, crafting.getMaxStackSize() - crafting.getAmount());

                left -= add;
                crafting.setAmount(crafting.getAmount() + add);
            }
        }

        item.setAmount(left);

        // we still have some items left, iterate over the crafting grid slots again
        // and check if any of them are empty
        if (left > 0) {
            i = 0;
            while (i < slots.size()) {
                int slot = slots.get(i++);
                ItemStack crafting = getInventory().getItem(slot);

                if (crafting == null) {
                    getInventory().setItem(slot, item.clone());
                    item.setAmount(0);
                    break;
                }
            }
        }
    }

    protected int loadStorageTypesWithoutClear(ItemStack stored, int amount, List<Integer> slots) {
        int left = amount;
        int i = 0;
        while (left > 0 && i < slots.size()) {
            ItemStack item = stored.clone();
            int add = Math.min(item.getMaxStackSize(), left);

            item.setAmount(add);
            left -= add;

            getInventory().setItem(slots.get(i++), item);
        }

        return left;
    }

    protected int loadStorageTypes(ItemStack stored, int amount, List<Integer> slots) {
        clearSlots(slots);

        return loadStorageTypesWithoutClear(stored, amount, slots);
    }

    protected void clearSlots(List<Integer> slots) {
        for (int slot : slots) {
            getInventory().setItem(slot, null);
        }
    }

    public static List<Integer> reverseSlots(List<Integer> slots) {
        return IntStream.range(0, slots.size())
                .boxed()
                .map(slots::get)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }
}
