package gg.minpack.itemban;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * Safely fetches a stable identifier for an ItemStack on CatServer/Mohist.
 * 1) Try Forge: stack.getItem().getRegistryName().toString() -> "modid:item_name"
 * 2) Try Spigot NMS (vanilla): IRegistry.ITEM.getKey(item).toString()
 * 3) Fallback to null.
 */
public final class NmsIds {

    private NmsIds() {}

    public static String getRegistryId(ItemStack stack) {
        if (stack == null) return null;
        try {
            // CraftItemStack.asNMSCopy(stack)
            Class<?> craftCls = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNms = craftCls.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class);
            Object nmsStack = asNms.invoke(null, stack);
            if (nmsStack == null) return null;

            // nmsStack.getItem()
            Method getItem = nmsStack.getClass().getMethod("getItem");
            Object nmsItem = getItem.invoke(nmsStack);
            if (nmsItem == null) return null;

            // 1) Forge-style: getRegistryName()
            try {
                Method getRegistryName = nmsItem.getClass().getMethod("getRegistryName");
                Object rl = getRegistryName.invoke(nmsItem);
                if (rl != null) {
                    String s = rl.toString();
                    if (!s.isEmpty()) return s;
                }
            } catch (Throwable ignore) {
            }

            // 2) Spigot NMS (1.16): IRegistry.ITEM.getKey(item) -> MinecraftKey -> toString()
            try {
                // net.minecraft.core.IRegistry / Registry may differ; use reflection loosely
                // Try method: item.getName().getKey() in some mappings
                Method getName = nmsItem.getClass().getMethod("getName");
                Object nameComp = getName.invoke(nmsItem);
                if (nameComp != null) {
                    Method getKey = nameComp.getClass().getMethod("getKey");
                    Object key = getKey.invoke(nameComp);
                    if (key != null) {
                        String s = key.toString();
                        if (!s.isEmpty()) return s;
                    }
                }
            } catch (Throwable ignore) {
            }

            // 2-alt) Some 1.16 mappings: net.minecraft.util.ResourceLocation via static registry
            try {
                Class<?> registryCls = Class.forName("net.minecraft.core.IRegistry");
                // IRegistry.ITEM
                Object itemRegistry = registryCls.getField("ITEM").get(null);
                Method getKey = itemRegistry.getClass().getMethod("getKey", nmsItem.getClass());
                Object key = getKey.invoke(itemRegistry, nmsItem);
                if (key != null) {
                    String s = key.toString();
                    if (!s.isEmpty()) return s;
                }
            } catch (Throwable ignore) {}

        } catch (Throwable t) {
            // ignored – fallback below
        }
        return null;
    }

    public static String getDisplayNamePlain(ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String raw = meta.getDisplayName();
        // strip section color codes § and &
        return raw.replaceAll("§[0-9A-FK-ORa-fk-or]", "")
                .replaceAll("&[0-9A-FK-ORa-fk-or]", "")
                .toLowerCase();
    }
}
