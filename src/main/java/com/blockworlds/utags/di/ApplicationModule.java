package com.blockworlds.utags.di;

import com.blockworlds.utags.UTags;
import com.blockworlds.utags.controller.CommandController;
import com.blockworlds.utags.controller.impl.AdminCommandController;
import com.blockworlds.utags.controller.impl.HelpCommandController;
import com.blockworlds.utags.controller.impl.MenuClickController;
import com.blockworlds.utags.controller.impl.PlayerLoginController;
import com.blockworlds.utags.controller.impl.TagCommandController;
import com.blockworlds.utags.controller.impl.TagPreviewController;
import com.blockworlds.utags.repository.RequestRepository;
import com.blockworlds.utags.repository.TagRepository;
import com.blockworlds.utags.repository.impl.DatabaseConnector;
import com.blockworlds.utags.repository.impl.RequestRepositoryImpl;
import com.blockworlds.utags.repository.impl.TagRepositoryImpl;
import com.blockworlds.utags.service.MenuService;
import com.blockworlds.utags.service.RequestService;
import com.blockworlds.utags.service.TagService;
import com.blockworlds.utags.service.impl.MenuServiceImpl;
import com.blockworlds.utags.service.impl.RequestServiceImpl;
import com.blockworlds.utags.service.impl.TagServiceImpl;
import com.blockworlds.utags.util.ErrorHandler;
import com.blockworlds.utags.util.MessageUtils;
import com.blockworlds.utags.util.PermissionUtils;
import com.blockworlds.utags.util.ValidationUtils;
import com.blockworlds.utags.view.InventoryFactory;
import com.blockworlds.utags.view.MenuBuilder;

import net.luckperms.api.LuckPerms;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Configures and bootstraps the application's dependency injection.
 * Registers all services, repositories, controllers, and utilities.
 */
public class ApplicationModule {
    private final ServiceContainer container;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final LuckPerms luckPerms;

    /**
     * Creates a new ApplicationModule.
     *
     * @param plugin The plugin instance
     * @param logger The logger to use
     * @param luckPerms The LuckPerms API instance
     */
    public ApplicationModule(JavaPlugin plugin, Logger logger, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.logger = logger;
        this.luckPerms = luckPerms;
        this.container = new ServiceContainer(logger);
        
        registerCoreComponents();
    }

    /**
     * Registers all core application components.
     */
    private void registerCoreComponents() {
        // Register plugin and core dependencies
        container.instance(JavaPlugin.class, plugin);
        container.instance(UTags.class, (UTags) plugin);
        container.instance(Logger.class, logger);
        container.instance(LuckPerms.class, luckPerms);
        container.instance(ErrorHandler.class, new ErrorHandler(plugin));

        // Register utilities
        registerUtilities();
        
        // Register data layer
        registerDataLayer();
        
        // Register services
        registerServices();
        
        // Register UI components
        registerUIComponents();
        
        // Register controllers
        registerControllers();
        
        logger.info("Application components registered");
    }

    /**
     * Registers utility classes.
     */
    private void registerUtilities() {
        container.instance(MessageUtils.class, new MessageUtils());
        container.instance(ValidationUtils.class, new ValidationUtils());
        container.instance(PermissionUtils.class, new PermissionUtils());
    }

    /**
     * Registers data access layer components.
     */
    private void registerDataLayer() {
        container.instance(DatabaseConnector.class, new DatabaseConnector(plugin));
        container.bind(TagRepository.class, TagRepositoryImpl.class);
        container.bind(RequestRepository.class, RequestRepositoryImpl.class);
    }

    /**
     * Registers service layer components.
     */
    private void registerServices() {
        container.bind(TagService.class, TagServiceImpl.class);
        container.bind(RequestService.class, RequestServiceImpl.class);
        container.bind(MenuService.class, MenuServiceImpl.class);
    }

    /**
     * Registers UI components.
     */
    private void registerUIComponents() {
        container.instance(InventoryFactory.class, new InventoryFactory(plugin));
        container.bind(MenuBuilder.class, MenuBuilder.class);
    }

    /**
     * Registers controllers.
     */
    private void registerControllers() {
        // Command controllers
        container.bind(TagCommandController.class, TagCommandController.class);
        container.bind(AdminCommandController.class, AdminCommandController.class);
        container.bind(HelpCommandController.class, HelpCommandController.class);
        
        // Event controllers
        container.bind(MenuClickController.class, MenuClickController.class);
        container.bind(PlayerLoginController.class, PlayerLoginController.class);
        container.bind(TagPreviewController.class, TagPreviewController.class);
    }

    /**
     * Gets the service container.
     *
     * @return The service container
     */
    public ServiceContainer getContainer() {
        return container;
    }

    /**
     * Gets a service by type.
     *
     * @param <T> The service type
     * @param type The service class
     * @return An instance of the requested service
     */
    public <T> T get(Class<T> type) {
        return container.get(type);
    }
}
