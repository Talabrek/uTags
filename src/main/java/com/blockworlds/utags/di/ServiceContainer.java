package com.blockworlds.utags.di;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple dependency injection container for managing service instances.
 * This class implements the Service Locator pattern to provide centralized
 * dependency management and inversion of control.
 */
public class ServiceContainer {
    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>> implementations = new ConcurrentHashMap<>();
    private final Logger logger;

    /**
     * Creates a new ServiceContainer with the specified logger.
     * 
     * @param logger The logger for diagnostic messages
     */
    public ServiceContainer(Logger logger) {
        this.logger = logger;
    }

    /**
     * Registers an interface with its implementation class.
     * 
     * @param <T> The interface type
     * @param interfaceClass The interface class
     * @param implementationClass The implementation class
     * @return This ServiceContainer for chaining
     */
    public <T> ServiceContainer register(Class<T> interfaceClass, Class<? extends T> implementationClass) {
        implementations.put(interfaceClass, implementationClass);
        logger.fine("Registered implementation " + implementationClass.getSimpleName() + 
                   " for interface " + interfaceClass.getSimpleName());
        return this;
    }

    /**
     * Registers an existing instance for an interface.
     * 
     * @param <T> The interface type
     * @param interfaceClass The interface class
     * @param instance The instance to register
     * @return This ServiceContainer for chaining
     */
    public <T> ServiceContainer registerInstance(Class<T> interfaceClass, T instance) {
        instances.put(interfaceClass, instance);
        logger.fine("Registered instance of " + instance.getClass().getSimpleName() + 
                   " for interface " + interfaceClass.getSimpleName());
        return this;
    }

    /**
     * Gets an instance of the specified interface.
     * If the instance doesn't exist, it will be created.
     * 
     * @param <T> The interface type
     * @param interfaceClass The interface class
     * @return The instance
     * @throws ServiceResolutionException If the instance can't be created
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> interfaceClass) {
        // Check if we already have an instance
        T instance = (T) instances.get(interfaceClass);
        if (instance != null) {
            return instance;
        }

        // Check if we have a registered implementation
        Class<?> implementationClass = implementations.get(interfaceClass);
        if (implementationClass == null) {
            // If the requested class is not an interface, try to instantiate it directly
            if (!interfaceClass.isInterface()) {
                implementationClass = interfaceClass;
            } else {
                throw new ServiceResolutionException("No implementation registered for " + interfaceClass.getName());
            }
        }

        // Create a new instance
        try {
            instance = createInstance(interfaceClass, (Class<? extends T>) implementationClass);
            instances.put(interfaceClass, instance);
            return instance;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create instance of " + implementationClass.getName(), e);
            throw new ServiceResolutionException("Failed to create instance of " + implementationClass.getName(), e);
        }
    }

    /**
     * Creates a new instance of the specified implementation class.
     * 
     * @param <T> The interface type
     * @param interfaceClass The interface class
     * @param implementationClass The implementation class
     * @return A new instance
     * @throws Exception If the instance can't be created
     */
    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> interfaceClass, Class<? extends T> implementationClass) throws Exception {
        // Get all constructors
        Constructor<?>[] constructors = implementationClass.getConstructors();
        if (constructors.length == 0) {
            // Try to use the default constructor
            return implementationClass.getDeclaredConstructor().newInstance();
        }

        // Use the first constructor we find
        Constructor<?> constructor = constructors[0];
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];

        // Resolve dependencies for the constructor parameters
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = get(paramTypes[i]);
        }

        // Create the instance
        return (T) constructor.newInstance(params);
    }

    /**
     * Exception thrown when a service can't be resolved.
     */
    public static class ServiceResolutionException extends RuntimeException {
        public ServiceResolutionException(String message) {
            super(message);
        }

        public ServiceResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
