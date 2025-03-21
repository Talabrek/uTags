package com.blockworlds.utags;

import com.blockworlds.utags.di.ServiceContainer;
import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.repository.RequestRepository;
import com.blockworlds.utags.repository.TagRepository;
import com.blockworlds.utags.service.TagService;
import com.blockworlds.utags.utils.Utils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class uTags extends JavaPlugin {
    private LuckPerms luckPerms;
    private DatabaseManager databaseManager;
    private TagMenuManager tagMenuManager;
    private TagService tagService;
    private Map<UUID, String> previewTags = new HashMap<>();
    
    private TagMenuListener tagMenuListener;
    private RequestMenuClickListener requestMenuClickListener;
    private TagCommandPreviewListener tagCommandPreviewListener;
    private LoginListener loginListener;
    
    private ServiceContainer serviceContainer;

    @Override
    public void onEnable() {
        try {
            if (!setupLuckPerms()) {
                getLogger().severe("LuckPerms not found! Disabling uTags...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            if (!loadConfig()) {
                getLogger().severe("Failed to load configuration! Disabling uTags...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            setupServices();
            registerCommandsAndEvents();
            
            getLogger().info("uTags has been enabled successfully!");
        } catch (Exception e) {
            getLogger().severe("Critical error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("uTags has been disabled!");
    }

    private boolean setupLuckPerms() {
        try {
            if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                return true;
            }
            return false;
        } catch (Exception e) {
            getLogger().severe("Error setting up LuckPerms: " + e.getMessage());
            return false;
        }
    }

    private boolean loadConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                getLogger().info("Creating default configuration file...");
                saveDefaultConfig();
            }
            
            reloadConfig();
            return true;
        } catch (Exception e) {
            getLogger().severe("Error loading configuration: " + e.getMessage());
            return false;
        }
    }

    private void setupServices() {
        try {
            databaseManager = new DatabaseManager(this);
            
            serviceContainer = new ServiceContainer(getLogger());
            
            serviceContainer.registerInstance(JavaPlugin.class, this);
            serviceContainer.registerInstance(LuckPerms.class, luckPerms);
            serviceContainer.registerInstance(TagRepository.class, databaseManager);
            serviceContainer.registerInstance(RequestRepository.class, databaseManager);
            
            tagService = new TagService(this, databaseManager, databaseManager, luckPerms);
            serviceContainer.registerInstance(TagService.class, tagService);
            
            tagMenuManager = new TagMenuManager(this);
            
            getLogger().info("Services initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Error setting up services: " + e.getMessage());
            throw new RuntimeException("Failed to initialize services", e);
        }
    }

    private void registerCommandsAndEvents() {
        try {
            TagCommand tagCommand = new TagCommand(this);
            getCommand("tag").setExecutor(tagCommand);
            getCommand("tag").setTabCompleter(tagCommand);
            
            tagMenuListener = new TagMenuListener(this);
            requestMenuClickListener = new RequestMenuClickListener(this);
            tagCommandPreviewListener = new TagCommandPreviewListener(this);
            loginListener = new LoginListener(this);
            
            getServer().getPluginManager().registerEvents(tagMenuListener, this);
            getServer().getPluginManager().registerEvents(requestMenuClickListener, this);
            getServer().getPluginManager().registerEvents(tagCommandPreviewListener, this);
            getServer().getPluginManager().registerEvents(loginListener, this);
            
            long checkInterval = 5 * 60 * 20; // 5 minutes in ticks
            Bukkit.getScheduler().runTaskTimer(this, this::checkTagRequests, checkInterval, checkInterval);
        } catch (Exception e) {
            getLogger().severe("Error registering commands and events: " + e.getMessage());
            throw new RuntimeException("Failed to register commands and events", e);
        }
    }
    
    private void checkTagRequests() {
        if (tagService.hasPendingRequests()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(Utils.PERM_STAFF)) {
                    player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + 
                                     ChatColor.YELLOW + "/tag admin requests" + 
                                     ChatColor.RED + " to check them.");
                }
            }
        }
    }

    public TagMenuManager getTagMenuManager() {
        return tagMenuManager;
    }

    public TagService getTagService() {
        return tagService;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public Map<UUID, String> getPreviewTags() {
        return previewTags;
    }

    public void addPreviewTag(Player player, String tag) {
        if (player == null || tag == null) return;
        
        String validationResult = Utils.validateTagFormat(tag);
        if (validationResult != null) {
            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED + validationResult);
            }
            return;
        }
        
        previewTags.put(player.getUniqueId(), tag);
        
        Utils.logSecurityEvent(Level.FINE, player, "TAG_PREVIEW", "Player previewing tag: " + tag);
        
        long timeoutTicks = getConfig().getLong("security.menu-timeout-seconds", 300) * 20;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (previewTags.containsKey(player.getUniqueId()) && 
                previewTags.get(player.getUniqueId()).equals(tag)) {
                previewTags.remove(player.getUniqueId());
            }
        }, timeoutTicks);
    }

    // Database facade methods
    public List<Tag> getAvailableTags(TagType tagType) {
        return tagService.getAvailableTags(tagType);
    }

    public boolean hasPendingTagRequests() {
        return tagService.hasPendingRequests();
    }

    public List<CustomTagRequest> getCustomTagRequests() {
        return tagService.getCustomTagRequests();
    }

    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        return tagService.getCustomTagRequestByPlayerName(playerName);
    }

    public boolean createCustomTagRequest(Player player, String tagDisplay) {
        return tagService.createCustomTagRequest(player, tagDisplay);
    }

    public boolean acceptCustomTagRequest(CustomTagRequest request) {
        return tagService.acceptCustomTagRequest(request);
    }

    public boolean denyCustomTagRequest(CustomTagRequest request) {
        return tagService.denyCustomTagRequest(request);
    }

    public boolean setPlayerTag(Player player, String tagDisplay, TagType tagType) {
        return tagService.setPlayerTag(player, tagDisplay, tagType);
    }

    public String getTagNameByDisplay(String display) {
        return tagService.getTagNameByDisplay(display);
    }

    public String getTagDisplayByName(String name) {
        return tagService.getTagDisplayByName(name);
    }

    public boolean addTagToDatabase(Tag tag) {
        return tagService.addTag(tag);
    }

    public boolean deleteTagFromDatabase(String tagName) {
        return tagService.deleteTag(tagName);
    }

    public boolean editTagAttribute(String tagName, String attribute, String newValue) {
        return tagService.editTagAttribute(tagName, attribute, newValue);
    }

    public int countCustomTags(String playerName) {
        return tagService.countCustomTags(playerName);
    }

    public boolean purgeTagsTable() {
        return tagService.purgeTagsTable();
    }

    public boolean purgeRequestsTable() {
        return tagService.purgeRequestsTable();
    }

    public void openRequestsMenu(Player player) {
        if (!Utils.hasAdminPermission(player)) {
            Utils.sendError(player, "You don't have permission to do that.");
            return;
        }
        
        List<CustomTagRequest> requests = getCustomTagRequests();
        int rows = Math.max(1, (int) Math.ceil(requests.size() / 9.0));
        Inventory requestsMenu = Bukkit.createInventory(player, rows * 9, "Custom Tag Requests");

        for (CustomTagRequest request : requests) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
                meta.setDisplayName(ChatColor.GREEN + request.getPlayerName());
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
                lore.add("");
                lore.add(ChatColor.YELLOW + "Left-click to accept");
                lore.add(ChatColor.RED + "Right-click to deny");
                meta.setLore(lore);
                
                playerHead.setItemMeta(meta);
            }

            requestsMenu.addItem(playerHead);
        }

        player.openInventory(requestsMenu);
    }
}
