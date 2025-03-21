package com.blockworlds.utags.di;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ServiceContainer {
    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>> implementations = new ConcurrentHashMap<>();
    private final Logger logger;

    public ServiceContainer(Logger logger) {
        this.logger = logger;
    }

    public <T> ServiceContainer register(Class<T> interfaceClass, Class<? extends T> implementationClass) {
        implementations.put(interfaceClass, implementationClass);
        return this;
    }

    public <T> ServiceContainer registerInstance(Class<T> interfaceClass, T instance) {
        instances.put(interfaceClass, instance);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> interfaceClass) {
        T instance = (T) instances.get(interfaceClass);
        if (instance != null) return instance;

        Class<?> implementationClass = implementations.get(interfaceClass);
        if (implementationClass == null) {
            if (!interfaceClass.isInterface()) {
                implementationClass = interfaceClass;
            } else {
                throw new ServiceResolutionException("No implementation for " + interfaceClass.getName());
            }
        }

        try {
            instance = createInstance(interfaceClass, (Class<? extends T>) implementationClass);
            instances.put(interfaceClass, instance);
            return instance;
        } catch (Exception e) {
            throw new ServiceResolutionException("Failed to create instance of " + implementationClass.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> interfaceClass, Class<? extends T> implementationClass) throws Exception {
        Constructor<?>[] constructors = implementationClass.getConstructors();
        if (constructors.length == 0) {
            return implementationClass.getDeclaredConstructor().newInstance();
        }

        Constructor<?> constructor = constructors[0];
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = get(paramTypes[i]);
        }

        return (T) constructor.newInstance(params);
    }

    public static class ServiceResolutionException extends RuntimeException {
        public ServiceResolutionException(String message) {
            super(message);
        }

        public ServiceResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
