package com.foxsrv.noenchant;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NoEnchant extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Set<String> whitelistedEnchants;
    private final Map<UUID, Set<ItemStack>> pendingInventoryChecks = new ConcurrentHashMap<>();
    private boolean isPaper = false;
    
    // Permission constants
    private static final String BYPASS_PERMISSION = "noenchant.bypass";
    private static final String RELOAD_PERMISSION = "noenchant.reload";
    private static final String MODIFY_PERMISSION = "noenchant.modify";
    private static final String USE_PERMISSION = "noenchant.use";

    @Override
    public void onEnable() {
        // Check if server is running Paper (for async events)
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            isPaper = true;
            getLogger().info("Paper detected - enabling async event support");
        } catch (ClassNotFoundException e) {
            getLogger().info("Running on Spigot - using sync events");
        }

        saveDefaultConfig();
        loadWhitelist();
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        
        // Register commands
        PluginCommand command = getCommand("noenchant");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("NoEnchant has been enabled!");
        
        // Schedule periodic inventory checks for all online players
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission(BYPASS_PERMISSION)) {
                        checkPlayerInventory(player);
                    }
                }
            }
        }.runTaskTimer(this, 100L, 200L); // Check every 10 seconds
    }

    @Override
    public void onDisable() {
        getLogger().info("NoEnchant has been disabled!");
    }

    private void loadWhitelist() {
        whitelistedEnchants = new HashSet<>(getConfig().getStringList("Whitelist"));
        // Convert to uppercase for consistency
        whitelistedEnchants = whitelistedEnchants.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        getLogger().info("Loaded " + whitelistedEnchants.size() + " whitelisted enchantments");
    }

    // Event Handlers
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player, event.getView().getTopInventory(), event.getView().getBottomInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            if (isPaper) {
                scheduleInventoryCheckAsync(player);
            } else {
                scheduleInventoryCheck(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            scheduleInventoryCheck(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Check source inventory if it's a player inventory
        if (event.getSource().getHolder() instanceof Player) {
            Player player = (Player) event.getSource().getHolder();
            if (!player.hasPermission(BYPASS_PERMISSION)) {
                scheduleInventoryCheck(player, event.getSource(), event.getDestination());
            }
        }
        
        // Check destination inventory if it's a player inventory
        if (event.getDestination().getHolder() instanceof Player) {
            Player player = (Player) event.getDestination().getHolder();
            if (!player.hasPermission(BYPASS_PERMISSION)) {
                scheduleInventoryCheck(player, event.getSource(), event.getDestination());
            }
        }
    }

    private void scheduleInventoryCheck(Player player) {
        scheduleInventoryCheck(player, (Inventory[]) null);
    }

    private void scheduleInventoryCheck(Player player, Inventory... additionalInventories) {
        if (isPaper) {
            scheduleInventoryCheckAsync(player, additionalInventories);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkPlayerInventory(player, additionalInventories);
                }
            }.runTask(this);
        }
    }

    private void scheduleInventoryCheckAsync(Player player, Inventory... additionalInventories) {
        Set<ItemStack> itemsToCheck = new HashSet<>();
        
        // Add player inventory items
        Collections.addAll(itemsToCheck, player.getInventory().getContents());
        Collections.addAll(itemsToCheck, player.getInventory().getArmorContents());
        Collections.addAll(itemsToCheck, player.getInventory().getExtraContents());
        
        // Add items from additional inventories
        if (additionalInventories != null) {
            for (Inventory inv : additionalInventories) {
                if (inv != null) {
                    Collections.addAll(itemsToCheck, inv.getContents());
                }
            }
        }
        
        pendingInventoryChecks.put(player.getUniqueId(), itemsToCheck);
        
        // Process asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<ItemStack> items = pendingInventoryChecks.remove(player.getUniqueId());
                if (items != null) {
                    processItemsAsync(player, items);
                }
            }
        }.runTaskAsynchronously(this);
    }

    private void processItemsAsync(Player player, Set<ItemStack> items) {
        boolean modified = false;
        List<String> removedEnchants = new ArrayList<>();
        
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) continue;
            
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasEnchants()) continue;
            
            Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
            boolean itemModified = false;
            
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                String enchantName = entry.getKey().getKey().getKey().toUpperCase();
                if (!whitelistedEnchants.contains(enchantName)) {
                    meta.removeEnchant(entry.getKey());
                    itemModified = true;
                    removedEnchants.add(enchantName + " (level " + entry.getValue() + ")");
                }
            }
            
            if (itemModified) {
                modified = true;
                item.setItemMeta(meta);
            }
        }
        
        if (modified) {
            final List<String> finalRemovedEnchants = new ArrayList<>(removedEnchants);
            final String location = player.getLocation().toString();
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Update player inventory on main thread
                    player.updateInventory();
                    
                    // Log to console
                    getLogger().info("Removed enchants from " + player.getName() + 
                                   " at " + location + ": " + 
                                   String.join(", ", finalRemovedEnchants));
                }
            }.runTask(this);
        }
    }

    private void checkPlayerInventory(Player player, Inventory... additionalInventories) {
        boolean modified = false;
        List<String> removedEnchants = new ArrayList<>();
        
        // Check player's main inventory
        ItemStack[] mainInventory = player.getInventory().getStorageContents();
        for (int i = 0; i < mainInventory.length; i++) {
            ItemStack item = mainInventory[i];
            if (processItem(item, removedEnchants)) {
                modified = true;
                mainInventory[i] = item;
            }
        }
        
        // Check armor contents
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack item = armorContents[i];
            if (processItem(item, removedEnchants)) {
                modified = true;
                armorContents[i] = item;
            }
        }
        
        // Check extra contents (offhand)
        ItemStack[] extraContents = player.getInventory().getExtraContents();
        for (int i = 0; i < extraContents.length; i++) {
            ItemStack item = extraContents[i];
            if (processItem(item, removedEnchants)) {
                modified = true;
                extraContents[i] = item;
            }
        }
        
        // Update inventories if modified
        if (modified) {
            player.getInventory().setStorageContents(mainInventory);
            player.getInventory().setArmorContents(armorContents);
            player.getInventory().setExtraContents(extraContents);
        }
        
        // Check additional inventories
        if (additionalInventories != null) {
            for (Inventory inv : additionalInventories) {
                if (inv == null || inv.getHolder() instanceof Player) continue; // Skip player inventories (already checked)
                
                boolean invModified = false;
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() != Material.AIR && processItem(item, removedEnchants)) {
                        inv.setItem(i, item);
                        invModified = true;
                    }
                }
                
                if (invModified) {
                    modified = true;
                }
            }
        }
        
        if (modified) {
            player.updateInventory();
            getLogger().info("Removed enchants from " + player.getName() + 
                           " at " + player.getLocation().toString() + ": " + 
                           String.join(", ", removedEnchants));
        }
    }
    
    private boolean processItem(ItemStack item, List<String> removedEnchants) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) return false;
        
        Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
        boolean itemModified = false;
        
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            String enchantName = entry.getKey().getKey().getKey().toUpperCase();
            if (!whitelistedEnchants.contains(enchantName)) {
                meta.removeEnchant(entry.getKey());
                itemModified = true;
                removedEnchants.add(enchantName + " (level " + entry.getValue() + ")");
            }
        }
        
        if (itemModified) {
            item.setItemMeta(meta);
            return true;
        }
        
        return false;
    }

    // Command handling
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /noenchant <reload|enchantname> <true/false>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
            reloadConfig();
            loadWhitelist();
            sender.sendMessage(ChatColor.GREEN + "NoEnchant configuration reloaded!");
            
            // Check all online players after reload (except bypass)
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(BYPASS_PERMISSION)) {
                    scheduleInventoryCheck(player);
                }
            }
            return true;
        }

        if (args.length == 2) {
            if (!sender.hasPermission(MODIFY_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to modify the whitelist!");
                return true;
            }
            
            String enchantName = args[0].toUpperCase();
            boolean addToWhitelist = Boolean.parseBoolean(args[1]);
            
            // Validate enchantment exists
            boolean enchantExists = false;
            for (Enchantment ench : Enchantment.values()) {
                if (ench.getKey().getKey().toUpperCase().equals(enchantName)) {
                    enchantExists = true;
                    break;
                }
            }
            
            if (!enchantExists) {
                sender.sendMessage(ChatColor.RED + "Enchantment '" + enchantName + "' not found!");
                return true;
            }
            
            if (addToWhitelist) {
                whitelistedEnchants.add(enchantName);
                sender.sendMessage(ChatColor.GREEN + "Added " + enchantName + " to whitelist!");
            } else {
                whitelistedEnchants.remove(enchantName);
                sender.sendMessage(ChatColor.GREEN + "Removed " + enchantName + " from whitelist!");
            }
            
            // Save to config
            getConfig().set("Whitelist", new ArrayList<>(whitelistedEnchants));
            saveConfig();
            
            // Check all online players after modification (except bypass)
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(BYPASS_PERMISSION)) {
                    scheduleInventoryCheck(player);
                }
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /noenchant <reload|enchantname> <true/false>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                completions.add("reload");
            }
            
            if (sender.hasPermission(MODIFY_PERMISSION)) {
                // Add all enchantment names
                for (Enchantment enchant : Enchantment.values()) {
                    String name = enchant.getKey().getKey().toUpperCase();
                    if (!completions.contains(name)) {
                        completions.add(name);
                    }
                }
            }
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && sender.hasPermission(MODIFY_PERMISSION)) {
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
