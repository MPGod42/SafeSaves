package com.hypixel.hytale.plugin.early.example;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Class transformer that replaces {@code BsonUtil.writeDocument(Path, BsonDocument, boolean)}
 * with a safe-write implementation using atomic file operations.
 * 
 * All bytecode is generated inline - NO external helper classes.
 * Uses ASM tree API to generate the complete safe-write algorithm directly.
 */
public final class SafeSaveTransformer implements ClassTransformer {
    private static final String TARGET_CLASS = "com/hypixel/hytale/server/core/util/BsonUtil";
    private static final String TARGET_METHOD = "writeDocument";
    private static final String TARGET_DESC = "(Ljava/nio/file/Path;Lorg/bson/BsonDocument;Z)Ljava/util/concurrent/CompletableFuture;";

    @Override
    public byte[] transform(String name, String path, byte[] bytes) {
        if (!TARGET_CLASS.equals(path)) {
            return null;
        }

        try {
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(classNode, 0);
            
            boolean found = false;
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(TARGET_METHOD) && method.desc.equals(TARGET_DESC)) {
                    transformMethod(method);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                System.err.println("[SafeSaveTransformer] Warning: Could not find target method");
                return null;
            }
            
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            byte[] result = writer.toByteArray();
            System.out.println("[SafeSaveTransformer] Successfully transformed BsonUtil.writeDocument (inline)");
            return result;
        }
        catch (Exception e) {
            System.err.println("[SafeSaveTransformer] Transformation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private void transformMethod(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables.clear();
        
        InsnList insns = new InsnList();
        
        // Local variables:
        // 0: Path file
        // 1: BsonDocument document
        // 2: boolean backup
        // 3: String json
        // 4: Path tempFile
        // 5: Path backupFile
        // 6: LinkOption[]
        // 7: OpenOption[]
        
        // Create empty LinkOption array: new LinkOption[0]
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/nio/file/LinkOption"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 6));
        
        // Create WriteOptions array with 3 elements
        insns.add(new InsnNode(Opcodes.ICONST_3));
        insns.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/nio/file/OpenOption"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 7));
        
        // Set WriteOptions[0] = StandardOpenOption.CREATE
        insns.add(new VarInsnNode(Opcodes.ALOAD, 7));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new org.objectweb.asm.tree.FieldInsnNode(
            Opcodes.GETSTATIC,
            "java/nio/file/StandardOpenOption",
            "CREATE",
            "Ljava/nio/file/StandardOpenOption;"
        ));
        insns.add(new InsnNode(Opcodes.AASTORE));
        
        // Set WriteOptions[1] = StandardOpenOption.WRITE
        insns.add(new VarInsnNode(Opcodes.ALOAD, 7));
        insns.add(new InsnNode(Opcodes.ICONST_1));
        insns.add(new org.objectweb.asm.tree.FieldInsnNode(
            Opcodes.GETSTATIC,
            "java/nio/file/StandardOpenOption",
            "WRITE",
            "Ljava/nio/file/StandardOpenOption;"
        ));
        insns.add(new InsnNode(Opcodes.AASTORE));
        
        // Set WriteOptions[2] = StandardOpenOption.TRUNCATE_EXISTING
        insns.add(new VarInsnNode(Opcodes.ALOAD, 7));
        insns.add(new InsnNode(Opcodes.ICONST_2));
        insns.add(new org.objectweb.asm.tree.FieldInsnNode(
            Opcodes.GETSTATIC,
            "java/nio/file/StandardOpenOption",
            "TRUNCATE_EXISTING",
            "Ljava/nio/file/StandardOpenOption;"
        ));
        insns.add(new InsnNode(Opcodes.AASTORE));
        
        // Call BsonUtil.toJson(document) - get the JSON string
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "com/hypixel/hytale/server/core/util/BsonUtil",
            "toJson",
            "(Lorg/bson/BsonDocument;)Ljava/lang/String;",
            false
        ));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 3));
        
        // Get filename from file.getFileName()
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "java/nio/file/Path",
            "getFileName",
            "()Ljava/nio/file/Path;",
            true
        ));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "java/nio/file/Path",
            "toString",
            "()Ljava/lang/String;",
            true
        ));
        
        // Create tempFile path: file.resolveSibling(fileName + ".new")
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new InsnNode(Opcodes.SWAP));
        insns.add(new org.objectweb.asm.tree.LdcInsnNode(".new"));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/String",
            "concat",
            "(Ljava/lang/String;)Ljava/lang/String;",
            false
        ));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "java/nio/file/Path",
            "resolveSibling",
            "(Ljava/lang/String;)Ljava/nio/file/Path;",
            true
        ));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 4));
        
        // Create backupFile path: file.resolveSibling(fileName + ".bak")
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "java/nio/file/Path",
            "getFileName",
            "()Ljava/nio/file/Path;",
            true
        ));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "java/nio/file/Path",
            "toString",
            "()Ljava/lang/String;",
            true
        ));
        insns.add(new org.objectweb.asm.tree.LdcInsnNode(".bak"));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/String",
            "concat",
            "(Ljava/lang/String;)Ljava/lang/String;",
            false
        ));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "java/nio/file/Path",
            "resolveSibling",
            "(Ljava/lang/String;)Ljava/nio/file/Path;",
            true
        ));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 5));
        
        // Create Runnable: CompletableFuture.runAsync(new Runnable() { ... })
        // For now, wrap the file operations and return a completed future
        // Files.writeString(tempFile, json, WRITE_OPTIONS)
        insns.add(new VarInsnNode(Opcodes.ALOAD, 4));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 7));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/nio/file/Files",
            "writeString",
            "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;",
            false
        ));
        insns.add(new InsnNode(Opcodes.POP));
        
        // if (backup && Files.isRegularFile(file)) { Files.move(file, backupFile, REPLACE_EXISTING); }
        insns.add(new VarInsnNode(Opcodes.ILOAD, 2));
        LabelNode skipBackupLabel = new LabelNode();
        insns.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, skipBackupLabel));
        
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 6));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/nio/file/Files",
            "isRegularFile",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
            false
        ));
        insns.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, skipBackupLabel));
        
        // Move original to backup
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 5));
        insns.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
        insns.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/nio/file/CopyOption"));
        insns.add(new InsnNode(Opcodes.DUP));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new org.objectweb.asm.tree.FieldInsnNode(
            Opcodes.GETSTATIC,
            "java/nio/file/StandardCopyOption",
            "REPLACE_EXISTING",
            "Ljava/nio/file/StandardCopyOption;"
        ));
        insns.add(new InsnNode(Opcodes.AASTORE));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/nio/file/Files",
            "move",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;",
            false
        ));
        insns.add(new InsnNode(Opcodes.POP));
        
        insns.add(skipBackupLabel);
        
        // Move tempFile to file (atomic swap)
        insns.add(new VarInsnNode(Opcodes.ALOAD, 4));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
        insns.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/nio/file/CopyOption"));
        insns.add(new InsnNode(Opcodes.DUP));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new org.objectweb.asm.tree.FieldInsnNode(
            Opcodes.GETSTATIC,
            "java/nio/file/StandardCopyOption",
            "REPLACE_EXISTING",
            "Ljava/nio/file/StandardCopyOption;"
        ));
        insns.add(new InsnNode(Opcodes.AASTORE));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/nio/file/Files",
            "move",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;",
            false
        ));
        insns.add(new InsnNode(Opcodes.POP));
        
        // Return CompletableFuture.completedFuture(null)
        insns.add(new InsnNode(Opcodes.ACONST_NULL));
        insns.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/util/concurrent/CompletableFuture",
            "completedFuture",
            "(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;",
            false
        ));
        insns.add(new InsnNode(Opcodes.ARETURN));
        
        method.instructions = insns;
        method.maxStack = 10;
        method.maxLocals = 8;
    }
}
