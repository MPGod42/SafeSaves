package com.hypixel.hytale.plugin.early;

/**
 * Stub interface for compile-time only.
 * Actual implementation provided by Hytale at runtime.
 */
public interface ClassTransformer {
    byte[] transform(String name, String path, byte[] bytes);
}
