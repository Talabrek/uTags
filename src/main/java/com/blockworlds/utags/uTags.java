package com.blockworlds.utags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class uTags extends JavaPlugin {

    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;
    private DatabaseManager databaseManager;
    private Map<UUID, String> previewTags;

    @Override
    public void onEnable() {
        setupLuckPerms();
        loadConfig();
        
        // Initialize database manager
        databaseManager = new DatabaseManager(this);
        
        registerCommandsAndEvents();
        setupTagMenuManager();
        
        // Update database schema if needed
        int currentSchemaVersion = getConfig().getInt("database.schema");
        int latestSchemaVersion = 3; // Update this value when the schema changes
        
        if (databaseManager.updateDatabaseSchema(currentSchemaVersion, latestSchemaVersion)) {
            // Update config with new schema version
            getConfig().set("database.schema", latestSchemaVersion);
            saveConfig();
        }
        
        getLogger().info("uTags has been enabled!");
    }

    @Override
    public void onDisable() {
        // Close database connections
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("uTags has been disabled!");
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private void setupLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPerms = LuckPermsProvider.get();
        } else {
            getLogger().warning("LuckPerms not found! Disabling uTags...");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommandsAndEvents() {
        previewTags = new HashMap<>();
        TagCommand tagCommand = new TagCommand(this);
        getCommand("tag").setExecutor(tagCommand);
        getCommand("tag").setTabCompleter(tagCommand);
        getServer().getPluginManager().registerEvents(new TagMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new RequestMenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new TagCommandPreviewListener(this), this);
        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        
        // Schedule tag request check
        long delay = 5 * 60 * 20; // 5 minutes in ticks (20 ticks per second)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!databaseManager.getCustomTagRequests().isEmpty()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("utags.staff")) {
                        player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + 
                                          ChatColor.YELLOW + "/tag admin requests" + 
                                          ChatColor.RED + " to check them.");
                    }
                }
            }
        }, delay, delay);
    }

    public boolean hasPendingTagRequests() {
        List<CustomTagRequest> requests = databaseManager.getCustomTagRequests();
        return requests != null && !requests.isEmpty();
    }
    
    private void setupTagMenuManager() {
        this.tagMenuManager = new TagMenuManager(this);
    }

    private void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        defaultTag = config.getString("default-tag");
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public TagMenuManager getTagMenuManager() {
        return tagMenuManager;
    }

    public List<Tag> getAvailableTags(TagType tagType) {
        return databaseManager.getAvailableTags(tagType);
    }

    public void addTagToDatabase(Tag tag) {
        databaseManager.addTagToDatabase(tag);
    }

    public void deleteTagFromDatabase(String tagName) {
        databaseManager.deleteTagFromDatabase(tagName);
    }

    public void purgeTagsTable() {
        databaseManager.purgeTagsTable();
    }

    public void purgeRequestsTable() {
        databaseManager.purgeRequestsTable();
    }

    public void createCustomTagRequest(Player player, String tagDisplay) {
        int endIndex = tagDisplay.indexOf(']') + 1;
        if (endIndex < tagDisplay.length()) {
            tagDisplay = tagDisplay.substring(0, endIndex);
        }
        
        boolean success = databaseManager.createCustomTagRequest(player.getUniqueId(), player.getName(), tagDisplay);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
        } else {
            player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
        }
    }

    public int countCustomTags(String playerName) {
        return databaseManager.countCustomTags(playerName);
    }

    public List<CustomTagRequest> getCustomTagRequests() {
        return databaseManager.getCustomTagRequests();
    }

    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        return databaseManager.getCustomTagRequestByPlayerName(playerName);
    }

    public void acceptCustomTagRequest(CustomTagRequest request) {
        try {
            String permission = "utags.tag." + request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1);
            
            // Add the new tag to the tags table
            Tag newTag = new Tag(
                request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1),
                request.getTagDisplay(),
                TagType.PREFIX,
                false,
                false,
                new ItemStack(Material.PLAYER_HEAD),
                1
            );
            
            databaseManager.addTagToDatabase(newTag);
            
            // Remove the request
            databaseManager.removeCustomTagRequest(request.getId());
            
            // Add the permission to the player
            getLuckPerms().getUserManager().loadUser(request.getPlayerUuid()).thenAcceptAsync(user -> {
                user.data().add(Node.builder(permission).build());
                getLuckPerms().getUserManager().saveUser(user);
                
                // Execute the configured command to notify the player
                String command = getConfig().getString("accept-command", "mail send %player% Your custom tag request has been accepted!");
                command = command.replace("%player%", request.getPlayerName());
                String finalCommand = command;
                Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
            });
        } catch (Exception e) {
            getLogger().severe("Error accepting custom tag request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void denyCustomTagRequest(CustomTagRequest request) {
        if (databaseManager.removeCustomTagRequest(request.getId())) {
            // Execute the configured command to notify the player
            String command = getConfig().getString("deny-command", "mail send %player% Your custom tag request has been denied.");
            command = command.replace("%player%", request.getPlayerName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            getLogger().severe("Failed to remove custom tag request with ID: " + request.getId());
        }
    }

    public void openRequestsMenu(Player player) {
        List<CustomTagRequest> requests = getCustomTagRequests();
        openRequestsMenu(player, requests);
    }

    public void openRequestsMenu(Player player, List<CustomTagRequest> requests) {
        int size = 9 * (int) Math.ceil(requests.size() / 9.0);
        if (size < 9)
            size = 9;
        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.BLUE + "Custom Tag Requests");

        for (CustomTagRequest request : requests) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
            skullMeta.setDisplayName(ChatColor.GREEN + request.getPlayerName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left-click to accept");
            lore.add(ChatColor.RED + "Right-click to deny");
            skullMeta.setLore(lore);
            item.setItemMeta(skullMeta);
            inventory.addItem(item);
        }

        player.openInventory(inventory);
    }

    public boolean editTagAttribute(String tagName, String attribute, String newValue) {
        return databaseManager.editTagAttribute(tagName, attribute, newValue);
    }

    public void setPlayerTag(Player player, String tagName, TagType tagType) {
        User user = getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            if (tagType == TagType.PREFIX) {
                user.data().clear(NodeType.PREFIX.predicate());
                user.data().add(PrefixNode.builder(tagName, 10000).build());
            } else {
                user.data().clear(NodeType.SUFFIX.predicate());
                user.data().add(SuffixNode.builder(tagName, 10000).build());
            }
            getLuckPerms().getUserManager().saveUser(user);
        }
    }

    public String getTagNameByDisplay(String display) {
        return databaseManager.getTagNameByDisplay(display);
    }

    public String getTagDisplayByName(String name) {
        return databaseManager.getTagDisplayByName(name);
    }

    public void addPreviewTag(Player player, String tag) {
        previewTags.put(player.getUniqueId(), tag);
    }

    public Map<UUID, String> getPreviewTags() {
        return previewTags;
    }
    
    /**
     * Logs the current database connection pool status
     */
    public void logDatabaseStatus() {
        databaseManager.logPoolStatus();
    }
}
