package com.hypixel.hytale.plugin.early.example;

import org.bson.BsonDocument;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

/**
 * Implements safe, atomic writes to save files by using a temporary file approach:
 * 1. Write to a temporary "<filename>.new" file
 * 2. If backup is enabled and the file exists, move it to "<filename>.bak"
 * 3. Move the temporary file to the final location
 * 
 * This prevents data corruption if the write process is interrupted.
 * 
 * Uses reflection to avoid compile-time dependencies on Hytale stubs,
 * allowing the helper to be loaded by the early plugin classloader.
 */
public final class SafeSaveHelper {
    private static final LinkOption[] NO_LINK_OPTIONS = new LinkOption[0];
    private static final OpenOption[] WRITE_OPTIONS = new OpenOption[]{
        StandardOpenOption.CREATE, 
        StandardOpenOption.WRITE, 
        StandardOpenOption.TRUNCATE_EXISTING
    };

    private SafeSaveHelper() {
    }

    public static CompletableFuture<Void> writeDocument(Path file, BsonDocument document, boolean backup) {
        try {
            // Use reflection to call BsonUtil.toJson() to avoid compile-time dependency
            String json = bsonUtilToJson(document);
            
            String fileName = file.getFileName().toString();
            Path tempFile = file.resolveSibling(fileName + ".new");
            Path backupFile = file.resolveSibling(fileName + ".bak");
            
            // Get parent and create directories if needed
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent, NO_LINK_OPTIONS)) {
                Files.createDirectories(parent);
            }
            
            return CompletableFuture.runAsync(() -> {
                try {
                    // 1. Write to temporary file
                    Files.writeString(tempFile, json, WRITE_OPTIONS);
                    
                    // 2. Backup existing file if needed
                    if (backup && Files.isRegularFile(file, NO_LINK_OPTIONS)) {
                        Files.move(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    // 3. Move temporary to final location (atomic operation)
                    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write document", e);
                }
            });
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Calls BsonUtil.toJson(document) using reflection to avoid needing
     * to exclude BsonUtil stubs from the final JAR.
     */
    private static String bsonUtilToJson(BsonDocument document) throws Exception {
        Class<?> bsonUtilClass = Class.forName("com.hypixel.hytale.server.core.util.BsonUtil");
        Method toJsonMethod = bsonUtilClass.getMethod("toJson", BsonDocument.class);
        return (String) toJsonMethod.invoke(null, document);
    }
}
