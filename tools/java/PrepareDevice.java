import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/** On-device APK import. Requires only the PortMaster JRE and bundled tools. */
public final class PrepareDevice {
    private static final String SHA = "d2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8";

    static boolean validJar(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getEntry("B/j.class") != null && jar.getEntry("i/h.class") != null
                && jar.getEntry("org/portmaster/gunslugs/apk/com/badlogic/gdx/utils/BufferUtils.class") != null;
        } catch (IOException e) { return false; }
    }

    static boolean ready(Path data) {
        Path current = Files.exists(data.resolve("GAME.JAR")) ? data.resolve("GAME.JAR") : data.resolve("game.jar");
        return Files.isRegularFile(data.resolve("assets/logo.png")) && validJar(current);
    }

    private static Path findApk(Path directory) throws IOException {
        Path named = directory.resolve("gunslugs.apk");
        if (Files.isRegularFile(named)) return named;
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files)
                if (Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".apk")) found.add(file);
        }
        if (found.size() != 1) throw new IOException(found.isEmpty()
            ? "Copy your backed-up Gunslugs 3.2.4 APK into the gunslugs folder, then launch again."
            : "Multiple APKs found. Name the intended one gunslugs.apk, then launch again.");
        return found.get(0);
    }

    private static void verifyApk(Path apk) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(apk)) {
            int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
        if (!SHA.equals(hash.toString())) throw new IOException("Unsupported APK fingerprint: " + hash
            + ". This adapter requires the supported Gunslugs 3.2.4, versionCode 52 build. See README.");
    }

    private static void prepare(Path apk, Path data) throws Exception {
        if (ready(data)) { System.out.println("DEVICE_PREPARATION_REUSED"); return; }
        verifyApk(apk); // Reject bad input before touching any existing game data.
        Files.createDirectories(data);
        try (FileChannel channel = FileChannel.open(data.resolve(".extraction.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another Gunslugs extraction is already running.");
            if (ready(data)) return;
            Path stage = Files.createTempDirectory(data, ".prepare-");
            try {
                Path converted = stage.resolve("converted.jar");
                String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
                String java = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
                Path tools = Paths.get(PrepareDevice.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
                System.out.println("Converting APK code on this device...");
                Process process = new ProcessBuilder(java, "-Xmx256m", "-XX:+UseSerialGC",
                    "-Djava.io.tmpdir=" + stage, "-cp", tools.resolve("dex").toString() + File.separator + "*",
                    "com.googlecode.dex2jar.tools.Dex2jarCmd", "--force", "--output", converted.toString(), apk.toString())
                    .directory(stage.toFile()).inheritIO().start();
                try {
                    if (process.waitFor() != 0) throw new IOException("APK conversion failed; see log.txt.");
                } finally { if (process.isAlive()) process.destroy(); }
                PrepareGame.main(new String[]{converted.toString(), stage.resolve("GAME.JAR").toString()});
                if (!validJar(stage.resolve("GAME.JAR"))) throw new IOException("Converted game classes are missing.");
                System.out.println("Extracting game assets...");
                try (ZipFile zip = new ZipFile(apk.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.startsWith("assets/") || name.startsWith("assets/dexopt/") || entry.isDirectory()) continue;
                        Path target = stage.resolve(name).normalize();
                        if (!target.startsWith(stage.resolve("assets"))) throw new IOException("Unsafe APK path.");
                        Files.createDirectories(target.getParent());
                        try (InputStream in = zip.getInputStream(entry)) { Files.copy(in, target); }
                    }
                }
                if (!ready(stage)) throw new IOException("Game assets are missing.");
                Files.write(stage.resolve("source.json"), ("{\"package\":\"com.orangepixel.gunslugshandy\",\"version\":\"3.2.4\",\"versionCode\":52,\"apk_sha256\":\"" + SHA + "\",\"prepared_on_device\":true}\n").getBytes("UTF-8"));
                // Retain partial output from an interrupted older import, never saves.
                Path previous = data.resolve(".previous-" + UUID.randomUUID());
                for (String name : new String[]{"assets", "source.json", "GAME.JAR", "game.jar"}) {
                    Path existing = data.resolve(name);
                    if (Files.exists(existing)) {
                        Files.createDirectories(previous);
                        Files.move(existing, previous.resolve(name));
                    }
                }
                Files.move(stage.resolve("assets"), data.resolve("assets"));
                Files.move(stage.resolve("source.json"), data.resolve("source.json"));
                // Publish the JAR last. Missing/partial output is retried on next launch.
                Files.move(stage.resolve("GAME.JAR"), data.resolve("GAME.JAR"));
                System.out.println("DEVICE_PREPARATION_OK: GAME.JAR and assets ready; APK retained.");
            } finally {
                if (!stage.normalize().startsWith(data.normalize())) throw new IOException("Invalid staging directory.");
                try (java.util.stream.Stream<Path> paths = Files.walk(stage)) {
                    for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 2 && args[0].equals("--check")) { System.exit(ready(Paths.get(args[1])) ? 0 : 1); return; }
            if (args.length == 1) {
                Path game = Paths.get(args[0]).toAbsolutePath().normalize();
                if (ready(game.resolve("gamedata"))) { System.out.println("DEVICE_PREPARATION_REUSED"); return; }
                prepare(findApk(game), game.resolve("gamedata"));
            } else if (args.length == 2) {
                prepare(Paths.get(args[0]).toAbsolutePath().normalize(), Paths.get(args[1]).toAbsolutePath().normalize());
            } else throw new IllegalArgumentException("Usage: PrepareDevice game-directory");
        } catch (Exception error) {
            System.err.println("Gunslugs extraction: " + error.getMessage());
            error.printStackTrace();
            System.exit(1);
        }
    }
}
