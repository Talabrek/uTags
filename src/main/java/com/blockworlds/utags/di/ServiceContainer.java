package com.blockworlds.utags.di;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A lightweight dependency injection container for uTags.
 * Manages service instantiation and dependency resolution.
 */
public class ServiceContainer {
    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>> bindings = new ConcurrentHashMap<>();
    private final Logger logger;

    /**
     * Creates a new ServiceContainer with the specified logger.
     *
     * @param logger The logger to use for messages
     */
    public ServiceContainer(Logger logger) {
        this.logger = logger;
    }

    /**
     * Binds an interface to an implementation class.
     *
     * @param <T> The interface type
     * @param interfaceClass The interface class
     * @param implementationClass The implementation class
     * @return This ServiceContainer for method chaining
     */
    public <T> ServiceContainer bind(Class<T> interfaceClass, Class<? extends T> implementationClass) {
        bindings.put(interfaceClass, implementationClass);
        return this;
    }

    /**
     * Registers an instance for an interface or class.
     *
     * @param <T> The interface or class type
     * @param type The interface or class
     * @param instance The instance to register
     * @return This ServiceContainer for method chaining
     */
    public <T> ServiceContainer instance(Class<T> type, T instance) {
        instances.put(type, instance);
        return this;
    }

    /**
     * Gets or creates an instance of the specified type.
     *
     * @param <T> The type to get
     * @param type The class of the type to get
     * @return An instance of the requested type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        // Check if instance already exists
        T instance = (T) instances.get(type);
        if (instance != null) {
            return instance;
        }

        // Get implementation class
        Class<?> implementationClass = bindings.get(type);
        if (implementationClass == null) {
            if (!type.isInterface()) {
                implementationClass = type;
            } else {
                throw new DIException("No implementation bound for " + type.getName());
            }
        }

        // Create instance through constructor injection
        try {
            instance = createInstance(type, (Class<? extends T>) implementationClass);
            instances.put(type, instance);
            return instance;
        } catch (Exception e) {
            throw new DIException("Failed to create instance of " + implementationClass.getName(), e);
        }
    }

    /**
     * Creates a new instance of a class using constructor injection.
     *
     * @param <T> The interface type
     * @param interfaceType The interface class
     * @param implementationType The implementation class
     * @return A new instance of the implementation
     * @throws Exception If instance creation fails
     */
    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> interfaceType, Class<? extends T> implementationType) throws Exception {
        Constructor<?>[] constructors = implementationType.getConstructors();
        if (constructors.length == 0) {
            return implementationType.getDeclaredConstructor().newInstance();
        }

        // Use the first constructor with the most parameters
        Constructor<?> constructor = constructors[0];
        for (Constructor<?> c : constructors) {
            if (c.getParameterCount() > constructor.getParameterCount()) {
                constructor = c;
            }
        }

        // Resolve constructor dependencies
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = get(paramTypes[i]);
        }

        return (T) constructor.newInstance(params);
    }

    /**
     * Clears all registered instances.
     */
    public void clear() {
        instances.clear();
    }

    /**
     * Exception thrown when dependency injection fails.
     */
    public static class DIException extends RuntimeException {
        public DIException(String message) {
            super(message);
        }

        public DIException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
