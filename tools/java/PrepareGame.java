import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Relocates APK libGDX wrappers and delegates JNI methods to the desktop runtime. */
public final class PrepareGame {
    private static final String PREFIX = "com/badlogic/gdx/";
    private static final String RELOCATED = "org/portmaster/gunslugs/apk/";
    private static int nativeCount;

    private static final Remapper REMAPPER = new Remapper() {
        @Override public String map(String internalName) {
            return internalName.startsWith(PREFIX) ? RELOCATED + internalName : internalName;
        }
    };

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) mv.visitInsn(Opcodes.ICONST_0 + value);
        else if (value <= Byte.MAX_VALUE) mv.visitIntInsn(Opcodes.BIPUSH, value);
        else if (value <= Short.MAX_VALUE) mv.visitIntInsn(Opcodes.SIPUSH, value);
        else mv.visitLdcInsn(value);
    }

    private static String boxed(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: return "java/lang/Boolean";
            case Type.BYTE: return "java/lang/Byte";
            case Type.CHAR: return "java/lang/Character";
            case Type.SHORT: return "java/lang/Short";
            case Type.INT: return "java/lang/Integer";
            case Type.FLOAT: return "java/lang/Float";
            case Type.LONG: return "java/lang/Long";
            case Type.DOUBLE: return "java/lang/Double";
            default: return null;
        }
    }

    private static void nativeBody(MethodVisitor mv, String owner, String name, String desc) {
        mv.visitCode();
        mv.visitLdcInsn(owner.replace('/', '.'));
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(desc);
        Type[] args = Type.getArgumentTypes(desc);
        pushInt(mv, args.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        int local = 0;
        for (int i = 0; i < args.length; i++) {
            Type arg = args[i];
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            mv.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), local);
            String wrapper = boxed(arg);
            if (wrapper != null) mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper,
                "valueOf", "(" + arg.getDescriptor() + ")L" + wrapper + ";", false);
            mv.visitInsn(Opcodes.AASTORE);
            local += arg.getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/portmaster/gunslugs/NativeCalls", "invoke",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        Type ret = Type.getReturnType(desc);
        if (ret.getSort() == Type.VOID) {
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        } else {
            String wrapper = boxed(ret);
            if (wrapper == null) mv.visitTypeInsn(Opcodes.CHECKCAST, ret.getInternalName());
            else {
                mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, ret.getClassName() + "Value",
                    "()" + ret.getDescriptor(), false);
            }
            mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static byte[] convert(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final String owner = reader.getClassName();
        reader.accept(new ClassRemapper(writer, REMAPPER) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_NATIVE) == 0)
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!owner.startsWith(PREFIX) || (access & Opcodes.ACC_STATIC) == 0)
                    throw new IllegalStateException("Unsupported native method: " + owner + "." + name);
                MethodVisitor mv = super.visitMethod(access & ~Opcodes.ACC_NATIVE,
                    name, descriptor, signature, exceptions);
                nativeBody(mv, owner, name, descriptor);
                nativeCount++;
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PrepareGame input.jar output.jar");
        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (input.equals(output)) throw new IllegalArgumentException("Input and output must differ");
        Files.createDirectories(output.getParent());
        Set<String> names = new HashSet<>();
        int classes = 0;
        try (JarFile source = new JarFile(input.toFile());
             JarOutputStream sink = new JarOutputStream(Files.newOutputStream(output))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) continue;
                byte[] bytes;
                try (InputStream data = source.getInputStream(entry)) { bytes = readAll(data); }
                if (name.endsWith(".class")) {
                    bytes = convert(bytes);
                    name = REMAPPER.map(name.substring(0, name.length() - 6)) + ".class";
                    classes++;
                }
                if (!names.add(name)) throw new IllegalStateException("Duplicate output entry: " + name);
                JarEntry result = new JarEntry(name);
                result.setTime(0L);
                sink.putNextEntry(result);
                sink.write(bytes);
                sink.closeEntry();
            }
        }
        System.out.println("Prepared " + classes + " classes; bridged " + nativeCount + " native methods.");
    }
}
