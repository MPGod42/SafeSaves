# SafeSaves - Hytale early plugin

<p align="center"> <img src="assets/SafeSaves.png" alt="SafeSaves logo" width="240"/> </p>

**SafeSaves** provides safe, atomic save-file writes for Hytale servers by replacing or augmenting the default BSON save path. It prevents corruption by using a write-to-temporary-file → backup → atomic-move pattern.

---

##  What it does

- Ensures safe, atomic writes of save files to avoid data loss or corruption if the process is interrupted.
- Two implementation approaches included:
  - **`SafeSaveTransformer`** — an ASM-based, inline class transformer that replaces `BsonUtil.writeDocument(Path, BsonDocument, boolean)` to perform atomic writes (write `.new`, optionally move original to `.bak`, then move `.new` → final file). No helper classes are required at runtime for this approach.
  - **`SafeSaveHelper`** — a helper that performs safe writes using reflection to call `BsonUtil.toJson(...)` and a temporary file approach (creates `<file>.new`, optionally renames existing file to `<file>.bak`, then atomically moves the temp file into place). This is loaded by the early plugin classloader.

##  Internals & Notes

- The transformer is implemented with ASM (ASM9) and generates bytecode inline — it finds the target method and replaces it with a safe-write implementation at classload time.
- A few *stub* classes exist only for compile-time convenience (their real implementations are supplied by Hytale at runtime):
  - `com.hypixel.hytale.server.core.util.BsonUtil` (stub)
  - `com.hypixel.hytale.server.core.util.PathUtil` (stub)
  - `com.hypixel.hytale.sneakythrow.SneakyThrow` (stub)
  - `com.hypixel.hytale.plugin.early.ClassTransformer` (stub interface)
- The project places an entry under `src/main/resources/META-INF/services/com.hypixel.hytale.plugin.early.ClassTransformer` so the runtime can discover the transformer via service loading.

> ⚠️ This code runs at plugin/class-transform time and modifies bytecode. Test thoroughly on staging environments before using in production.

##  Building

Requirements:
- JDK (25)

##  Usage

- Package the plugin JAR and place it in the server’s early plugin directory (default: `/Hytale/UserData/earlyplugins`) or follow your server/plugin loader guidelines.
- The transformer will automatically attempt to replace `BsonUtil.writeDocument(...)` at load time; if not present the helper can be used directly by other code that wants to perform safe writes.
