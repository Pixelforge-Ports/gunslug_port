package org.portmaster.gunslugs;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** Desktop host for the exact Gunslugs 3.2.4 APK; no Android runtime is used. */
public final class Main extends ApplicationAdapter {
    private Object game;
    private Object processor;
    private Services services;
    private final Map<String, Method> callbacks = new HashMap<>();
    private int frames;
    private boolean created;
    private final boolean lockDisplay = Boolean.parseBoolean(System.getProperty("gunslugs.lockDisplay", "true"));
    private final DisplayLayout layout = new DisplayLayout();
    private long startNanos;
    private final FramePacer pacer = new FramePacer(Integer.getInteger("gunslugs.frameMillis", 24));
    private final int smokeFrames = Integer.getInteger("gunslugs.smokeFrames", 0);
    private final Path assets = Paths.get(System.getProperty("gunslugs.assets", "gamedata/assets")).toAbsolutePath();
    private final Path saves = Paths.get(System.getProperty("gunslugs.saves", "saves")).toAbsolutePath();

    public static void main(String[] args) {
        System.out.println("Gunslugs desktop bridge 0.4.0 | " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("Game update interval: " + Integer.getInteger("gunslugs.frameMillis", 24) + " ms");
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Gunslugs");
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0);
        int width = Integer.getInteger("gunslugs.width", 640);
        int height = Integer.getInteger("gunslugs.height", 480);
        if (width < 160 || height < 160 || width > 8192 || height > 8192)
            throw new IllegalArgumentException("Display dimensions must be 160..8192 pixels");
        cfg.setWindowedMode(width, height);
        cfg.setResizable(false);
        cfg.setForegroundFPS(0);
        cfg.setIdleFPS(30);
        cfg.useVsync(false);
        cfg.setInitialVisible(!Boolean.getBoolean("gunslugs.hidden"));
        cfg.disableAudio(Boolean.getBoolean("gunslugs.noAudio"));
        if (Boolean.getBoolean("gunslugs.fullscreen")) cfg.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        new Lwjgl3Application(new Main(), cfg);
    }

    @Override public void create() {
        try {
            layout.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            services = new Services(assets, saves);
            Object graphics = graphics();
            Object input = input();
            setGlobal("b", graphics); setGlobal("c", services.audio()); setGlobal("d", input);
            setGlobal("e", services.files()); setGlobal("f", services.net());
            Object gl = GlBridge.create(layout); setGlobal("g", gl); setGlobal("h", gl); setGlobal("i", null);
            // The desktop backend loaded gdx. APK JNI wrappers share that library.
            setBoolean("x.e", "a", true);
            // APK-supported empty controller manager: PortMaster supplies keyboard input.
            Class.forName("n.f").getField("b").set(null, "n.e");
            game = Class.forName("B.j").getDeclaredConstructor().newInstance();
            setGlobal("a", services.application(game, input, graphics));
            // Exact AndroidLauncher branch for devices without a touchscreen.
            setBoolean("C.a", "x", false); setBoolean("A.b", "r", false); setBoolean("B.d", "a", false);
            Class<?> listener = Class.forName("i.d");
            for (Method method : listener.getMethods()) callbacks.put(method.getName(), method);
            invokeGame("e");
            created = true;
            // APK preferences can request a desktop mode during create(). Always
            // initialize its framebuffer using the actual drawable dimensions.
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            startNanos = System.nanoTime();
            System.out.println("GAME_CREATE_OK " + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight());
        } catch (Exception e) { throw failure("initialization", e); }
    }

    @Override public void render() {
        pacer.awaitFrame();
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
        invokeGame("f");
        frames++;
        if (smokeFrames > 0 && frames >= smokeFrames) {
            String capture = System.getProperty("gunslugs.capture");
            if (capture != null) {
                Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                try { PixmapIO.writePNG(Gdx.files.absolute(capture), pixmap, -1, true); }
                finally { pixmap.dispose(); }
            }
            System.out.println("SMOKE_RENDER_OK frames=" + frames + " seconds=" + ((System.nanoTime()-startNanos)/1e9));
            Gdx.app.exit();
        }
    }
    @Override public void resize(int width, int height) {
        // Minimized windows can report zero; the APK divides by height / 160.
        if (created && width >= 160 && height >= 160) {
            layout.resize(width, height);
            invokeGame("d", layout.gameWidth, layout.gameHeight);
            System.out.println("GAME_RESIZE_OK " + width + "x" + height
                + " view=" + layout.gameWidth + "x" + layout.gameHeight
                + " viewport=" + layout.width + "x" + layout.height);
        }
    }
    @Override public void pause() { if (created) { invokeGame("c"); flush(); } }
    @Override public void resume() { pacer.reset(); if (created) invokeGame("b"); }
    @Override public void dispose() {
        try { if (created) invokeGame("a"); }
        finally { if (services != null) { try { services.dispose(); } catch (Exception e) { throw failure("save cleanup", e); } } }
    }
    private void flush() { try { services.flushPreferences(); } catch (Exception e) { throw failure("save flush", e); } }
    private Object invokeGame(String method, Object... args) {
        try { return callbacks.get(method).invoke(game, args); }
        catch (Exception e) { throw failure("game callback " + method, e); }
    }
    private static RuntimeException failure(String operation, Throwable error) {
        while (error instanceof InvocationTargetException) error = ((InvocationTargetException)error).getCause();
        if (error instanceof Error) throw (Error)error;
        return new IllegalStateException("Gunslugs " + operation + " failed", error);
    }
    private static void setGlobal(String name, Object value) throws Exception { Class.forName("i.h").getField(name).set(null, value); }
    private static void setBoolean(String type, String name, boolean value) throws Exception {
        Field field = Class.forName(type).getDeclaredField(name); field.setAccessible(true); field.setBoolean(null, value);
    }
    private static Object proxy(String type, InvocationHandler handler) throws ClassNotFoundException {
        Class<?> api = Class.forName(type);
        return Proxy.newProxyInstance(Main.class.getClassLoader(), new Class<?>[]{api}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("toString")) return "Gunslugs bridge " + type;
                if (method.getName().equals("hashCode")) return System.identityHashCode(self);
                if (method.getName().equals("equals")) return self == args[0];
            }
            return handler.invoke(self, method, args == null ? new Object[0] : args);
        });
    }
    private Object displayMode(Graphics.DisplayMode mode) throws Exception {
        Constructor<?> ctor = Class.forName("i.i$b").getDeclaredConstructor(int.class,int.class,int.class,int.class);
        ctor.setAccessible(true); return ctor.newInstance(
            lockDisplay ? layout.gameWidth : mode.width,
            lockDisplay ? layout.gameHeight : mode.height, mode.refreshRate,mode.bitsPerPixel);
    }
    private Object graphics() throws Exception {
        return proxy("i.i", (self, method, a) -> {
            switch (method.getName()) {
                case "b": return layout.gameWidth;
                case "c": return layout.gameHeight;
                case "e": return layout.gameWidth;
                case "n": return layout.gameHeight;
                case "f": return Gdx.graphics.supportsExtension((String)a[0]);
                case "g": return displayMode(Gdx.graphics.getDisplayMode());
                case "h": Gdx.graphics.requestRendering(); return null;
                case "i": return !lockDisplay && Gdx.graphics.supportsDisplayModeChange();
                case "j": return !lockDisplay && Gdx.graphics.setWindowedMode((Integer)a[0],(Integer)a[1]);
                case "k": return Gdx.graphics.isFullscreen();
                case "m": return false;
                case "l": {
                    Graphics.DisplayMode[] modes = lockDisplay
                        ? new Graphics.DisplayMode[]{Gdx.graphics.getDisplayMode()} : Gdx.graphics.getDisplayModes();
                    Object out = Array.newInstance(Class.forName("i.i$b"), modes.length);
                    for (int i=0;i<modes.length;i++) Array.set(out,i,displayMode(modes[i]));
                    return out;
                }
                case "d": {
                    if (lockDisplay) return false;
                    int width = a[0].getClass().getField("a").getInt(a[0]);
                    int height = a[0].getClass().getField("b").getInt(a[0]);
                    for (Graphics.DisplayMode mode:Gdx.graphics.getDisplayModes())
                        if (mode.width==width && mode.height==height) return Gdx.graphics.setFullscreenMode(mode);
                    return false;
                }
                default: throw new UnsupportedOperationException(method.toString());
            }
        });
    }
    private final Map<String, Method> inputCallbacks = new HashMap<>();
    private boolean inputEvent(String name, Object... args) {
        if (processor == null) return false;
        try { return (Boolean) inputCallbacks.get(name).invoke(processor,args); }
        catch (Exception e) { throw failure("input callback " + name,e); }
    }
    private Object input() throws Exception {
        for (Method m:Class.forName("i.k").getMethods()) inputCallbacks.put(m.getName(),m);
        return proxy("i.j", (self, method, a) -> {
            switch (method.getName()) {
                case "c": Gdx.input.setCursorPosition(layout.cursorX((Integer)a[0]),layout.cursorY((Integer)a[1])); return null;
                case "d": return Gdx.input.isKeyJustPressed((Integer)a[0]);
                case "e": return 0;
                case "g": Gdx.input.setCatchKey((Integer)a[0],(Boolean)a[1]); return null;
                case "i": return Gdx.input.isKeyPressed((Integer)a[0]);
                case "l": Gdx.input.setCursorCatched((Boolean)a[0]); return null;
                case "k":
                    processor = a[0];
                    Gdx.input.setInputProcessor(new InputAdapter() {
                        public boolean keyDown(int key) { return inputEvent("b",key); }
                        public boolean keyUp(int key) { return inputEvent("i",key); }
                        public boolean keyTyped(char c) { return inputEvent("a",c); }
                        public boolean touchDown(int x,int y,int p,int b) { return inputEvent("c",layout.inputX(x),layout.inputY(y),p,b); }
                        public boolean touchUp(int x,int y,int p,int b) { return inputEvent("f",layout.inputX(x),layout.inputY(y),p,b); }
                        public boolean touchCancelled(int x,int y,int p,int b) { return inputEvent("e",layout.inputX(x),layout.inputY(y),p,b); }
                        public boolean touchDragged(int x,int y,int p) { return inputEvent("h",layout.inputX(x),layout.inputY(y),p); }
                        public boolean mouseMoved(int x,int y) { return inputEvent("d",layout.inputX(x),layout.inputY(y)); }
                        public boolean scrolled(float x,float y) { return inputEvent("g",x,y); }
                    });
                    return null;
                default: throw new UnsupportedOperationException(method.toString());
            }
        });
    }
}
