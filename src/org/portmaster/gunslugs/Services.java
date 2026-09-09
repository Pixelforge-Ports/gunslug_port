package org.portmaster.gunslugs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Services for the original, obfuscated Gunslugs 3.2.4 libGDX interfaces.
 * Mappings come from this APK's Android backend, not guessed method ordering.
 * The original game and its core classes remain in their original namespaces;
 * desktop audio and the Application lifecycle use the official libGDX backend.
 */
public final class Services implements AutoCloseable {
    private final Path assets;
    private final Path saves;
    private final Constructor<?> handleConstructor;
    private final Object internalFileType;
    private final Method handleFile;
    private final Map<String, SavedPreferences> preferences = new LinkedHashMap<>();
    private final List<AudioResource> audioResources = new ArrayList<>();
    private final List<LifecycleListener> lifecycleListeners = new ArrayList<>();
    private final Object files;
    private final Object audio;
    private final Object net;
    private boolean closed;

    public Services(Path assets, Path saves) throws IOException, ReflectiveOperationException {
        this.assets = assets.toRealPath();
        this.saves = saves.toAbsolutePath().normalize();
        Files.createDirectories(this.saves);
        Class<?> fileType = type("i.g$a");
        handleConstructor = type("o.a").getDeclaredConstructor(String.class, fileType);
        handleConstructor.setAccessible(true);
        internalFileType = enumValue(fileType, "Internal");
        handleFile = type("o.a").getMethod("c");

        files = proxy("i.g", (p, m, a) -> {
            switch (m.getName()) {
                case "a": return internal((String) a[0]);
                case "b": // local storage: k.j.b uses Context.getFilesDir
                case "c": // external storage: k.j.c uses getExternalFilesDir
                    return this.saves.toString() + File.separator;
                default: throw unsupported(m);
            }
        });
        audio = proxy("i.f", (p, m, a) -> {
            FileHandle source = new FileHandle((File) invoke(handleFile, a[0]));
            switch (m.getName()) {
                case "v": return sound(Gdx.audio.newSound(source));
                case "w": return music(Gdx.audio.newMusic(source));
                default: throw unsupported(m);
            }
        });
        net = proxy("i.m", (p, m, a) -> {
            if (m.getName().equals("a")) return Gdx.net.openURI((String) a[0]);
            throw unsupported(m);
        });
    }

    public Object files() { return files; }
    public Object audio() { return audio; }
    public Object net() { return net; }

    /** Call after the official backend has initialized Gdx.app. */
    public Object application(Object game, Object input, Object graphics)
            throws ReflectiveOperationException {
        Object desktop = enumValue(type("i.c$a"), "Desktop");
        return proxy("i.c", (p, m, a) -> {
            switch (m.getName()) {
                case "e": return desktop;
                case "f": return input;
                case "g": Gdx.app.log((String) a[0], (String) a[1], (Throwable) a[2]); return null;
                case "h": Gdx.app.error((String) a[0], (String) a[1]); return null;
                case "i": Gdx.app.log((String) a[0], (String) a[1]); return null;
                case "j": Gdx.app.exit(); return null;
                case "k": addLifecycle(a[0]); return null;
                case "l": return preferences((String) a[0]);
                case "m": return graphics;
                case "n": return game;
                case "o": Gdx.app.postRunnable((Runnable) a[0]); return null;
                default: throw unsupported(m);
            }
        });
    }

    private Object internal(String name) throws ReflectiveOperationException {
        // The original FileHandle's read/readBytes methods already handle real
        // files. An absolute Internal path preserves its type and needs no JNI.
        Path path = assets.resolve(name.replace('\\', '/')).normalize();
        if (!path.startsWith(assets)) {
            throw new IllegalArgumentException("Asset path escapes the extracted APK assets: " + name);
        }
        return handleConstructor.newInstance(path.toString(), internalFileType);
    }

    private void addLifecycle(Object listener) throws ReflectiveOperationException {
        Class<?> api = type("i.l");
        Method dispose = api.getMethod("a");
        Method resume = api.getMethod("b");
        Method pause = api.getMethod("c");
        LifecycleListener adapter = new LifecycleListener() {
            public void pause() { callLifecycle(pause, listener); }
            public void resume() { callLifecycle(resume, listener); }
            public void dispose() { callLifecycle(dispose, listener); }
        };
        Gdx.app.addLifecycleListener(adapter);
        lifecycleListeners.add(adapter);
    }

    private static void callLifecycle(Method method, Object listener) {
        try {
            invoke(method, listener);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Original lifecycle callback failed: " + method, e);
        }
    }

    private Object sound(Sound sound) throws ReflectiveOperationException {
        AudioResource resource = new AudioResource(sound, null);
        audioResources.add(resource);
        return proxy("j.b", (p, m, a) -> {
            switch (m.getName()) {
                case "a": resource.dispose(); return null; // inherited x.d.dispose
                case "t": return sound.play((Float) a[0]);
                default: throw unsupported(m);
            }
        });
    }

    private Object music(Music music) throws ReflectiveOperationException {
        AudioResource resource = new AudioResource(null, music);
        audioResources.add(resource);
        return proxy("j.a", (p, m, a) -> {
            switch (m.getName()) {
                case "a": resource.dispose(); return null;
                case "f": music.setLooping((Boolean) a[0]); return null;
                case "g": return music.isPlaying();
                case "j": music.stop(); return null;
                case "p": music.setVolume((Float) a[0]); return null;
                case "r": music.play(); return null;
                default: throw unsupported(m);
            }
        });
    }

    private static final class AudioResource {
        final Sound sound;
        final Music music;
        boolean disposed;
        AudioResource(Sound sound, Music music) { this.sound = sound; this.music = music; }
        void dispose() {
            if (disposed) return;
            disposed = true;
            if (sound != null) sound.dispose();
            if (music != null) music.dispose();
        }
    }

    private synchronized Object preferences(String name) throws IOException, ReflectiveOperationException {
        SavedPreferences saved = preferences.get(name);
        if (saved == null) {
            saved = new SavedPreferences(saves.resolve(preferenceFileName(name)));
            preferences.put(name, saved);
        }
        return saved.proxy;
    }

    private static String preferenceFileName(String name) {
        if (name.matches("[A-Za-z0-9_-]+")) return name + ".properties";
        return "encoded-" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8)) + ".properties";
    }

    private static final class SavedPreferences implements InvocationHandler {
        final Path file;
        final Properties values = new Properties();
        final Object proxy;
        boolean dirty;

        SavedPreferences(Path file) throws IOException, ReflectiveOperationException {
            this.file = file;
            if (Files.exists(file)) {
                try (InputStream input = Files.newInputStream(file)) { values.load(input); }
            }
            proxy = Services.proxy("i.n", this);
        }

        public synchronized Object invoke(Object p, Method m, Object[] a) throws IOException {
            switch (m.getName()) {
                case "a": // putBoolean(String, boolean)
                case "d": // putInteger(String, int)
                    values.setProperty((String) a[0], String.valueOf(a[1]));
                    dirty = true;
                    return p;
                case "b": { // getInteger(String, int)
                    String value = values.getProperty((String) a[0]);
                    return value == null ? a[1] : Integer.valueOf(value);
                }
                case "c": { // getBoolean(String, boolean)
                    String value = values.getProperty((String) a[0]);
                    if (value == null) return a[1];
                    if (!value.equals("true") && !value.equals("false")) {
                        throw new IllegalStateException("Invalid boolean save value in " + file + ": " + a[0]);
                    }
                    return Boolean.valueOf(value);
                }
                case "clear": values.clear(); dirty = true; return null;
                case "flush": flush(); return null;
                default: throw unsupported(m);
            }
        }

        synchronized void flush() throws IOException {
            if (!dirty) return;
            Path temporary = Files.createTempFile(file.getParent(), ".gunslugs-save-", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    values.store(output, "Gunslugs desktop port preferences");
                }
                // Rename within the same directory so an interrupted write does
                // not leave a partial save. FAT filesystems may lack ATOMIC_MOVE.
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                dirty = false;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public synchronized void flushPreferences() throws IOException {
        for (SavedPreferences saved : preferences.values()) saved.flush();
    }

    public void dispose() throws IOException { close(); }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            flushPreferences();
        } finally {
            for (AudioResource resource : audioResources) resource.dispose();
            audioResources.clear();
            for (LifecycleListener listener : lifecycleListeners) Gdx.app.removeLifecycleListener(listener);
            lifecycleListeners.clear();
        }
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, Services.class.getClassLoader());
    }

    private static Object enumValue(Class<?> type, String name) {
        for (Object value : type.getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) return value;
        }
        throw new IllegalStateException("Supplied APK lacks expected enum " + type.getName() + "." + name);
    }

    private static Object proxy(String typeName, InvocationHandler implementation)
            throws ReflectiveOperationException {
        Class<?> api = type(typeName);
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api }, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) {
                switch (m.getName()) {
                    case "toString": return "Gunslugs desktop adapter for " + typeName;
                    case "hashCode": return System.identityHashCode(p);
                    case "equals": return p == a[0];
                    default: throw unsupported(m);
                }
            }
            return implementation.invoke(p, m, a == null ? new Object[0] : a);
        });
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException("Unmapped original APK service API: " + method
                + ". This bridge supports the supplied Gunslugs 3.2.4 APK only.");
    }
}
