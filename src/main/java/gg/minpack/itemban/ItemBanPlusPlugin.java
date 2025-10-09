package gg.minpack.itemban;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ItemBanPlusPlugin extends JavaPlugin implements Listener {

    private FileConfiguration cfg;
    private String msgDenied;
    private List<Pattern> rightClickMatchers = new ArrayList<>();
    private List<Pattern> leftClickMatchers = new ArrayList<>();
    private List<Pattern> craftMatchers = new ArrayList<>();
    private List<Pattern> interactMatchers = new ArrayList<>();
    private boolean detectPixelmonInvByTitle = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ItemBanPlus 1.2.0 enabled with " + rightClickMatchers.size() + "/" + craftMatchers.size() + "/" + interactMatchers.size() + " rules.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadLocal();
    }

    private void reloadLocal() {
        this.cfg = getConfig();
        this.msgDenied = ChatColor.translateAlternateColorCodes('&', cfg.getString("messages.denied", "&c사용 금지 아이템입니다."));
        detectPixelmonInvByTitle = cfg.getBoolean("options.detect-pixelmon-inventory-by-title", true);

        rightClickMatchers = compile(cfg.getStringList("bans.right-click"));
        leftClickMatchers = compile(cfg.getStringList("bans.left-click"));
        craftMatchers = compile(cfg.getStringList("bans.craft"));
        interactMatchers = compile(cfg.getStringList("bans.pixelmon-interact"));
    }

    private List<Pattern> compile(List<String> raw) {
        List<Pattern> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            s = s == null ? "" : s.trim();
            if (s.isEmpty()) continue;
            // normalize prefix for readability; store as (?i) case-insensitive
            String body = s;
            if (s.startsWith("id:")) body = "id:" + s.substring(3).trim();
            else if (s.startsWith("name:")) body = "name:" + s.substring(5).trim();
            else if (s.startsWith("material:")) body = "material:" + s.substring(9).trim();
            else {
                // default treat as id:*
                body = "id:" + s;
            }
            out.add(Pattern.compile("(?i)" + Pattern.quote(body.split(":", 2)[0] + ":").replace("\\Q", "").replace("\\E", "") + body.split(":", 2)[1]));
        }
        return out;
    }

    private boolean matchesAny(ItemStack is, List<Pattern> tests) {
        if (is == null || is.getType() == Material.AIR) return false;
        // Build probe strings
        String id = NmsIds.getRegistryId(is); // e.g. pixelmon:mystery_box or minecraft:diamond_sword
        String name = NmsIds.getDisplayNamePlain(is); // plain display name
        String material = is.getType().name(); // fallback

        for (Pattern p : tests) {
            String prefix = p.pattern().startsWith("(?i)id:") ? "id:"
                    : p.pattern().startsWith("(?i)name:") ? "name:"
                    : "material:";
            if (id != null && p.matcher("id:" + id).find()) return true;
            if (name != null && p.matcher("name:" + name).find()) return true;
            if (material != null && p.matcher("material:" + material).find()) return true;
        }
        return false;
    }

    private void deny(Player p, String reason) {
        if (p != null) {
            p.sendMessage(msgDenied.replace("%reason%", reason));
        }
    }

    // ===== Commands =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 실행할 수 있습니다.");
            return true;
        }
        Player p = (Player) sender;
        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "[ItemBanPlus] /" + label + " reload  - 설정 리로드");
            p.sendMessage(ChatColor.GRAY + "이 버전은 GUI 대신 config.yml 기반으로 동작합니다.");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            reloadConfig();
            p.sendMessage(ChatColor.GREEN + "리로드 완료.");
            return true;
        }
        return true;
    }

    // ===== Right-click block (air/block & entity) =====
    // ===== Right-click block (air/block) =====
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack is = e.getItem();
        if (matchesAny(is, rightClickMatchers)) {
            e.setCancelled(true);
            deny(e.getPlayer(), "우클릭");
        }
    }

    // ===== Left-click block (air/block) =====
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;
        ItemStack is = e.getItem();
        if (matchesAny(is, leftClickMatchers)) {
            e.setCancelled(true);
            deny(e.getPlayer(), "좌클릭");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        ItemStack is = e.getPlayer().getInventory().getItemInMainHand();
        if (matchesAny(is, interactMatchers) || matchesAny(is, rightClickMatchers)) {
            e.setCancelled(true);
            deny(e.getPlayer(), "픽셀몬 상호작용");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        ItemStack is = e.getPlayer().getInventory().getItemInMainHand();
        if (matchesAny(is, interactMatchers) || matchesAny(is, rightClickMatchers)) {
            e.setCancelled(true);
            deny(e.getPlayer(), "픽셀몬 상호작용");
        }
    }

    // ===== Inventory clicks (Pixelmon GUIs etc.) =====
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        // Rough Pixelmon GUI detection
        if (detectPixelmonInvByTitle) {
            String title = e.getView() != null ? e.getView().getTitle() : "";
            String holder = e.getInventory() != null && e.getInventory().getHolder() != null
                    ? e.getInventory().getHolder().getClass().getName() : "";
            boolean looksPixelmon = (title != null && title.toLowerCase().contains("pixelmon")) ||
                    (holder != null && holder.toLowerCase().contains("pixelmon"));

            if (looksPixelmon) {
                ItemStack cur = e.getCurrentItem();
                ItemStack cursor = e.getCursor();
                if (matchesAny(cur, interactMatchers) || matchesAny(cursor, interactMatchers)) {
                    e.setCancelled(true);
                    deny((Player) e.getWhoClicked(), "픽셀몬 GUI");
                    return;
                }
            }
        }

        // Also prevent moving banned items into crafting slots etc.
        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (matchesAny(cur, rightClickMatchers) || matchesAny(cur, interactMatchers) ||
                matchesAny(cursor, rightClickMatchers) || matchesAny(cursor, interactMatchers)) {
            if (e.getSlotType() == InventoryType.SlotType.CRAFTING || e.getSlotType() == InventoryType.SlotType.RESULT) {
                e.setCancelled(true);
                deny((Player) e.getWhoClicked(), "제작/이동");
            }
        }
    }

    // ===== Craft result blocking =====
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (matchesAny(result, craftMatchers)) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        ItemStack result = e.getRecipe() != null ? e.getRecipe().getResult() : null;
        if (matchesAny(result, craftMatchers)) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player) {
                deny((Player) e.getWhoClicked(), "조합");
            }
        }
    }
}