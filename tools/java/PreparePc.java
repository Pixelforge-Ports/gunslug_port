import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Import only the owner's PC game and resources, replacing its desktop runtime. */
public final class PreparePc {
    static final String SHA = "d4492bd452c0e81e8ac1696d9c0e439b0a074489555b764298e1e2381343af8a";
    private static final String FORMAT = "gunslugs-pc-3.3.0-adapter-1\n" + SHA + "\n";

    static Path findDat(Path data) throws IOException {
        if (!Files.isDirectory(data)) return null;
        Path found = null;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(data)) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && file.getFileName().toString().equalsIgnoreCase("gunslugs.dat")) {
                    if (found != null) throw new IOException("Multiple gunslugs.dat files found. Keep only one in gamedata.");
                    found = file;
                }
            }
        }
        return found;
    }

    static boolean ready(Path data) {
        return validCache(data.resolve("pc"));
    }

    private static boolean validCache(Path cache) {
        try (JarFile jar = new JarFile(cache.resolve("GAME.JAR").toFile())) {
            return FORMAT.equals(new String(Files.readAllBytes(cache.resolve("source.txt")), "UTF-8"))
                && jar.getEntry("com/orangepixel/gunslugs/myCanvas.class") != null
                && jar.getEntry("com/orangepixel/controller/GameInput.class") != null
                && jar.getEntry("spl2.png") != null && jar.getEntry("audio/tune1.mp3") != null;
        } catch (IOException error) { return false; }
    }

    private static void progress(int percent, String message) {
        System.out.printf(Locale.ROOT, "GUNSLUGS_PROGRESS\t%d\t%s\n", percent, message);
    }

    private static void verify(Path dat) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = Math.max(1, Files.size(dat)), completed = 0;
        int last = -1;
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(dat)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n); completed += n;
                int percent = (int)(completed * 100 / size);
                if (percent / 5 != last / 5 || last < 0) { progress(percent, "Verifying PC data"); last = percent; }
            }
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
        if (!SHA.equals(hash.toString())) throw new IOException("Unsupported PC fingerprint: " + hash
            + ". Use the supported GOG Gunslugs 3.3.0 gunslugs.dat. See README.md.");
    }

    private static boolean include(String name) {
        if (name.endsWith(".class")) {
            // Old controller API types are needed by the game's class signatures.
            // Its native discovery is disabled; gptokeyb2 supplies keyboard input.
            if (name.startsWith("com/badlogic/gdx/controllers/") && name.indexOf('/', 29) < 0) return true;
            if (!name.startsWith("com/orangepixel/")) return false;
            return !name.equals("com/orangepixel/gunslugs/Main.class")
                && !name.startsWith("com/orangepixel/gunslugs/Steam")
                && !name.equals("com/orangepixel/gunslugs/SDLRumble.class")
                && !name.equals("com/orangepixel/gunslugs/NewsletterPopper.class");
        }
        return name.startsWith("audio/") || (name.indexOf('/') < 0 && name.endsWith(".png"));
    }

    private static byte[] adapt(byte[] code) {
        ClassReader reader = new ClassReader(code);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (reader.getClassName().equals("com/orangepixel/controller/GameInput")
                        && name.equals("initControllers") && descriptor.equals("()V")) {
                    target.visitCode(); target.visitInsn(Opcodes.RETURN); target.visitMaxs(0, 0); target.visitEnd();
                    return null;
                }
                return target;
            }
        }, 0);
        return writer.toByteArray();
    }

    static void prepare(Path dat, Path data) throws Exception {
        if (ready(data)) { System.out.println("PC_PREPARATION_REUSED"); return; }
        verify(dat);
        Files.createDirectories(data);
        try (FileChannel channel = FileChannel.open(data.resolve(".extraction.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another Gunslugs extraction is already running.");
            if (ready(data)) return;
            Path stage = Files.createTempDirectory(data, ".prepare-pc-");
            try {
                try (ZipFile input = new ZipFile(dat.toFile());
                     JarOutputStream output = new JarOutputStream(Files.newOutputStream(stage.resolve("GAME.JAR")))) {
                    List<? extends ZipEntry> entries = Collections.list(input.entries());
                    long total = entries.stream().filter(e -> !e.isDirectory() && include(e.getName())).count();
                    long completed = 0;
                    int last = -1;
                    for (ZipEntry entry : entries) {
                        String name = entry.getName();
                        if (entry.isDirectory() || !include(name)) continue;
                        if (name.startsWith("/") || name.contains("..") || name.contains("\\")) throw new IOException("Unsafe PC archive path.");
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        try (InputStream in = input.getInputStream(entry)) {
                            byte[] buffer = new byte[65536]; int n;
                            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
                        }
                        byte[] payload = bytes.toByteArray();
                        if (name.endsWith(".class")) payload = adapt(payload);
                        JarEntry destination = new JarEntry(name); destination.setTime(0);
                        output.putNextEntry(destination); output.write(payload); output.closeEntry();
                        int percent = (int)(++completed * 100 / Math.max(1, total));
                        if (percent / 5 != last / 5 || last < 0) { progress(percent, "Preparing PC game and assets"); last = percent; }
                    }
                }
                Files.write(stage.resolve("source.txt"), FORMAT.getBytes("UTF-8"));
                // Validate before replacing any previous PC cache. APK output and saves stay separate.
                if (!validCache(stage)) throw new IOException("PC game classes or assets missing.");
                Path pc = data.resolve("pc");
                if (Files.exists(pc)) Files.move(pc, data.resolve(".previous-pc-" + UUID.randomUUID()));
                Files.move(stage, pc);
                progress(100, "Gunslugs PC ready");
                System.out.println("PC_PREPARATION_OK: PC code and assets ready; gunslugs.dat retained.");
            } finally {
                if (Files.exists(stage)) {
                    try (java.util.stream.Stream<Path> paths = Files.walk(stage)) {
                        for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
                    }
                }
            }
        }
    }
}
