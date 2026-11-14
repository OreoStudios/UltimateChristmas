package fr.elias.ultimateChristmas.core.hook;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Recognises ItemsAdder custom blocks and items.
 *
 * <p>Lets the shard economy reward custom blocks by their {@code namespace:id}
 * and lets configs hand out custom items by the same name.
 */
public final class ItemsAdderHook extends PluginHook {

    public ItemsAdderHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "ItemsAdder";
    }

    @Override
    protected boolean setUp() {
        return CustomBlock.class.getName() != null;
    }

    /** The {@code namespace:id} of a custom block, or {@code null} if vanilla. */
    public String blockIdAt(Block block) {
        if (!active() || block == null) {
            return null;
        }
        try {
            CustomBlock custom = CustomBlock.byAlreadyPlaced(block);
            return custom == null ? null : custom.getNamespacedID();
        } catch (Exception | NoClassDefFoundError error) {
            return null;
        }
    }

    /** Builds a custom item by {@code namespace:id}, or {@code null}. */
    public ItemStack item(String namespacedId, int amount) {
        if (!active() || namespacedId == null || namespacedId.isBlank()) {
            return null;
        }
        try {
            CustomStack stack = CustomStack.getInstance(namespacedId);
            if (stack == null) {
                return null;
            }
            ItemStack item = stack.getItemStack().clone();
            item.setAmount(Math.max(1, amount));
            return item;
        } catch (Exception | NoClassDefFoundError error) {
            return null;
        }
    }
}
