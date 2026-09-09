import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/** First-launch preparation using only a JRE and bundled Java tools. */
public final class PrepareDevice {
    private static final String SHA = "d2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PrepareDevice APK gamedata");
        Path apk = Paths.get(args[0]).toAbsolutePath();
        Path data = Paths.get(args[1]).toAbsolutePath();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(apk)) {
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
        if (!SHA.equals(hash.toString())) throw new IOException("Unsupported APK. Requires the exact Gunslugs 3.2.4 versionCode 52 build documented in README.");
        Files.createDirectories(data);
        if (Files.exists(data.resolve("game.jar")) || Files.exists(data.resolve("assets")))
            throw new IOException("Existing or incomplete gamedata found. Back up and move game.jar/assets before preparing again. Saves are separate.");
        Path stage = Files.createTempDirectory(data, ".prepare-");
        try {
            Path converted = stage.resolve("converted.jar");
            String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            Path tools = Paths.get(PrepareDevice.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            Process process = new ProcessBuilder(java, "-Xmx256m", "-XX:+UseSerialGC", "-cp",
                tools.resolve("dex").toString() + File.separator + "*", "com.googlecode.dex2jar.tools.Dex2jarCmd",
                "--force", "--output", converted.toString(), apk.toString()).inheritIO().start();
            if (process.waitFor() != 0) throw new IOException("APK conversion failed; see log.txt");
            PrepareGame.main(new String[]{converted.toString(), stage.resolve("game.jar").toString()});
            try (JarFile jar = new JarFile(stage.resolve("game.jar").toFile())) {
                if (jar.getEntry("B/j.class") == null) throw new IOException("Converted game class is missing");
            }
            try (ZipFile zip = new ZipFile(apk.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith("assets/") || name.startsWith("assets/dexopt/") || entry.isDirectory()) continue;
                    Path target = stage.resolve(name).normalize();
                    if (!target.startsWith(stage.resolve("assets"))) throw new IOException("Unsafe APK path");
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(entry)) { Files.copy(in, target); }
                }
            }
            if (!Files.isRegularFile(stage.resolve("assets/logo.png"))) throw new IOException("Game assets missing");
            Files.write(stage.resolve("source.json"), ("{\"package\":\"com.orangepixel.gunslugshandy\",\"version\":\"3.2.4\",\"versionCode\":52,\"apk_sha256\":\"" + SHA + "\",\"prepared_on_device\":true}\n").getBytes("UTF-8"));
            Files.move(stage.resolve("assets"), data.resolve("assets"));
            Files.move(stage.resolve("source.json"), data.resolve("source.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(stage.resolve("game.jar"), data.resolve("game.jar"));
            System.out.println("DEVICE_PREPARATION_OK: game data ready; original APK retained.");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(stage)) {
                for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
}
