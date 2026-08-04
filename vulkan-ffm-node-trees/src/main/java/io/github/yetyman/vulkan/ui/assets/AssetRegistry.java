package io.github.yetyman.vulkan.ui.assets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed service registry for shared platform resources.
 *
 * Services are registered at startup and looked up by layers during their initialize() call.
 * Layers cache the reference locally - lookups are not on the hot path.
 *
 * Close order: reverse of registration order (last registered closes first).
 */
public class AssetRegistry implements AutoCloseable {

    private final Map<AssetType<?>, Object> services = new LinkedHashMap<>();

    /** Registers a service by type token. */
    public <T> void register(AssetType<T> type, T service) {
        if (services.containsKey(type)) {
            throw new IllegalStateException("Service already registered: " + type);
        }
        services.put(type, service);
    }

    /** Registers a service by class (unnamed). */
    public <T> void register(Class<T> type, T service) {
        register(AssetType.of(type), service);
    }

    /** @return the registered service for the given type token. */
    @SuppressWarnings("unchecked")
    public <T> T get(AssetType<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type);
        }
        return (T) service;
    }

    /** @return the registered service for the given class (unnamed). */
    public <T> T get(Class<T> type) {
        return get(AssetType.of(type));
    }

    /** @return true if a service is registered under the given class (unnamed). */
    public <T> boolean has(Class<T> type) {
        return services.containsKey(AssetType.of(type));
    }

    /** @return true if a service is registered under the given type token. */
    public <T> boolean has(AssetType<T> type) {
        return services.containsKey(type);
    }

    /** Closes all AutoCloseable services in reverse registration order. */
    @Override
    public void close() {
        var entries = new ArrayList<>(services.values());
        Collections.reverse(entries);
        for (Object service : entries) {
            if (service instanceof AutoCloseable ac) {
                try {
                    ac.close();
                } catch (Exception e) {
                    // log and continue - one service's failure must not prevent others from closing
                }
            }
        }
        services.clear();
    }
}
