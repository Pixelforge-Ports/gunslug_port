import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.portmaster.gunslugs.Services;

/** Offline integration checks against the converted APK, requiring no display or audio device. */
public final class ServicesCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: ServicesCheck assets-dir temporary-check-root");
        Path assets = Paths.get(args[0]).toRealPath();
        Path checkRoot = Paths.get(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(checkRoot);
        Path saves = Files.createTempDirectory(checkRoot, "services-check-");
        List<String> events = new ArrayList<>();
        Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class},
                (p, m, a) -> {
                    events.add("sound." + m.getName());
                    if (m.getName().equals("play")) {
                        require(a.length == 1 && a[0].equals(0.4f), "sound volume routing");
                        return 7L;
                    }
                    if (m.getName().equals("dispose")) return null;
                    throw new AssertionError("Unexpected Sound call " + m);
                });
        Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class},
                (p, m, a) -> {
                    events.add("music." + m.getName());
                    if (m.getName().equals("isPlaying")) return true;
                    if (m.getName().equals("setLooping")) require(a[0].equals(true), "music looping routing");
                    if (m.getName().equals("setVolume")) require(a[0].equals(0.7f), "music volume routing");
                    return null;
                });
        Gdx.audio = (Audio) Proxy.newProxyInstance(Audio.class.getClassLoader(), new Class<?>[]{Audio.class},
                (p, m, a) -> {
                    FileHandle file = (FileHandle) a[0];
                    require(file.file().toPath().startsWith(assets), "audio must use extracted assets");
                    require(file.exists(), "audio asset exists");
                    if (m.getName().equals("newSound")) return sound;
                    if (m.getName().equals("newMusic")) return music;
                    throw new AssertionError("Unexpected Audio call " + m);
                });

        try (Services service = new Services(assets, saves)) {
            Object app = service.application(null, null, null);
            require(((Enum<?>) call(app, "i.c", "e")).name().equals("Desktop"), "ApplicationType.Desktop");
            Object prefs = call(app, "i.c", "l", "gunslugs");
            require(call(prefs, "i.n", "b", "score", 19).equals(19), "integer default");
            require(call(prefs, "i.n", "c", "usemusic", false).equals(false), "boolean default");
            require(call(prefs, "i.n", "d", "score", 12345) == prefs, "putInteger chaining");
            require(call(prefs, "i.n", "a", "usemusic", true) == prefs, "putBoolean chaining");
            call(prefs, "i.n", "flush");

            Object handle = call(service.files(), "i.g", "a", "icon.png");
            require(Arrays.equals((byte[]) call(handle, "o.a", "h"), Files.readAllBytes(assets.resolve("icon.png"))),
                    "original FileHandle.readBytes preserves asset contents");
            require(call(service.files(), "i.g", "b").equals(saves.toString() + java.io.File.separator), "local save root");
            require(call(service.files(), "i.g", "c").equals(saves.toString() + java.io.File.separator), "external save root");
            try {
                call(service.files(), "i.g", "a", "../outside-assets");
                throw new AssertionError("Escaping asset path was accepted");
            } catch (IllegalArgumentException expected) { }

            Object originalSound = call(service.audio(), "i.f", "v",
                    call(service.files(), "i.g", "a", "audio/fxclick.mp3"));
            require(call(originalSound, "j.b", "t", 0.4f).equals(7L), "sound play ID");
            call(originalSound, "x.d", "a");
            Object originalMusic = call(service.audio(), "i.f", "w",
                    call(service.files(), "i.g", "a", "audio/tune1.ogg"));
            call(originalMusic, "j.a", "f", true);
            call(originalMusic, "j.a", "p", 0.7f);
            call(originalMusic, "j.a", "r");
            require(call(originalMusic, "j.a", "g").equals(true), "music playing state");
            call(originalMusic, "j.a", "j");
            call(originalMusic, "j.a", "a");
        }
        require(events.equals(Arrays.asList("sound.play", "sound.dispose", "music.setLooping", "music.setVolume",
                "music.play", "music.isPlaying", "music.stop", "music.dispose")), "audio call sequence / exactly one disposal");
        try (Services reopened = new Services(assets, saves)) {
            Object prefs = call(reopened.application(null, null, null), "i.c", "l", "gunslugs");
            require(call(prefs, "i.n", "b", "score", -1).equals(12345), "integer survives reopen");
            require(call(prefs, "i.n", "c", "usemusic", false).equals(true), "boolean survives reopen");
            call(prefs, "i.n", "clear");
            call(prefs, "i.n", "flush");
        }
        try (Services cleared = new Services(assets, saves)) {
            Object prefs = call(cleared.application(null, null, null), "i.c", "l", "gunslugs");
            require(call(prefs, "i.n", "b", "score", -1).equals(-1), "clear survives reopen");
        }
        // This check intentionally keeps its newly created save directory for inspection.
        System.out.println("PASS: original APK FileHandle reads, isolated save/reopen/clear, all audio adapter calls.");
        System.out.println("Mock audio only: actual rendering, hardware sound, and handheld gameplay are not tested here.");
        System.out.println("Check files: " + saves);
    }

    private static Object call(Object target, String api, String name, Object... args) throws Exception {
        for (Method method : Class.forName(api).getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try { return method.invoke(target, args); }
                catch (InvocationTargetException e) {
                    if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
                    if (e.getCause() instanceof Error) throw (Error) e.getCause();
                    throw e;
                }
            }
        }
        throw new NoSuchMethodException(api + "." + name);
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
