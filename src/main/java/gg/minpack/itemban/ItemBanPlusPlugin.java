package gg.minpack.itemban;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.EventPriority;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ItemBanPlusPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private File configFile;
    private FileConfiguration cfg;
    private final List<ItemStack> banned = new ArrayList<>();

    private static final String GUI_TITLE = ChatColor.DARK_RED + "아이템 금지 설정";

    @Override
    public void onEnable() {
        // Load config (YAML serializes ItemStacks natively)
        saveDefaultConfig();
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            saveResource("config.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(configFile);
        loadBannedFromConfig();

        // Register
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("아이템금지")).setExecutor(this);

        getLogger().info("ItemBanPlus enabled. Banned entries: " + banned.size());
    }

    private void loadBannedFromConfig() {
        banned.clear();
        List<?> list = cfg.getList("banned-items");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack) {
                    ItemStack is = ((ItemStack) o).clone();
                    is.setAmount(1);
                    banned.add(is);
                }
            }
        }
    }

    private void saveBannedToConfig(List<ItemStack> items) {
        List<ItemStack> serial = new ArrayList<>();
        for (ItemStack is : items) {
            if (is == null || is.getType() == Material.AIR) continue;
            ItemStack copy = is.clone();
            copy.setAmount(1); // normalize
            serial.add(copy);
        }
        cfg.set("banned-items", serial);
        try {
            cfg.save(configFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save config: " + e.getMessage());
        }
        loadBannedFromConfig();
    }

    // ---------- Command ----------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("minpack.itemban")) {
            p.sendMessage(ChatColor.RED + "이 명령을 사용할 권한이 없습니다.");
            return true;
        }
        openGui(p);
        return true;
    }

    private void openGui(Player p) {
        Inventory inv = Bukkit.createInventory(new ItemBanHolder(), 54, GUI_TITLE);
        // Pre-fill with existing banned entries
        for (int i = 0; i < banned.size() && i < inv.getSize(); i++) {
            inv.setItem(i, banned.get(i));
        }
        p.openInventory(inv);
    }

    // ---------- GUI Close -> Save ----------
    @EventHandler
    public void onGuiClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof ItemBanHolder)) return;
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;

        Player p = (Player) e.getPlayer();
        if (!p.hasPermission("minpack.itemban")) return;

        List<ItemStack> collected = new ArrayList<>();
        for (ItemStack is : e.getInventory().getContents()) {
            if (is == null || is.getType() == Material.AIR) continue;
            ItemStack copy = is.clone();
            copy.setAmount(1);
            collected.add(copy);
        }
        saveBannedToConfig(collected);
        p.sendMessage(ChatColor.GRAY + "금지 아이템 " + ChatColor.RED + banned.size() + ChatColor.GRAY + "개로 설정되었습니다.");
    }

    // ---------- Enforcement: Crafting ----------
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (e.getInventory() == null) return;
        ItemStack[] matrix = e.getInventory().getMatrix();
        if (matrix == null) return;
        for (ItemStack is : matrix) {
            if (is == null) continue;
            if (isBanned(is)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        ItemStack[] matrix = e.getInventory().getMatrix();
        if (matrix == null) return;
        for (ItemStack is : matrix) {
            if (is == null) continue;
            if (isBanned(is)) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player) {
                    ((Player)e.getWhoClicked()).sendMessage(ChatColor.RED + "해당 아이템은 조합에 사용할 수 없습니다.");
                }
                return;
            }
        }
    }

    // ---------- Enforcement: Right Click ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().toString().contains("RIGHT_CLICK")) return;
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (isBanned(main) || isBanned(off)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "해당 아이템은 사용이 금지되어 있습니다.");
        }
    }

    private boolean isBanned(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemStack probe = stack.clone();
        probe.setAmount(1);
        for (ItemStack bannedIs : banned) {
            if (bannedIs == null) continue;
            if (probe.isSimilar(bannedIs)) return true;
            // Fallback: material-only check
            if (probe.getType() == bannedIs.getType() && (!probe.hasItemMeta() || !bannedIs.hasItemMeta())) {
                return true;
            }
        }
        return false;
    }

    // Simple holder to identify our GUI
    private static class ItemBanHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    

// ---------- Enforcement: Block Placement ----------
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPlace(BlockPlaceEvent e) {
    if (isBanned(e.getItemInHand())) {
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.RED + "금지 아이템은 설치할 수 없습니다.");
    

// ---------- Enforcement: Right-Click on Entities ----------
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onInteractEntity(PlayerInteractAtEntityEvent e) {
    if (isBanned(e.getPlayer().getInventory().getItemInMainHand()) ||
        isBanned(e.getPlayer().getInventory().getItemInOffHand())) {
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.RED + "금지 아이템은 생명체에 사용할 수 없습니다.");
    }
}

}
