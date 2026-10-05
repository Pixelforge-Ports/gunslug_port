package org.portmaster.gunslugs;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import java.lang.reflect.*;
import java.nio.file.*;

/** PC adapter; game code is loaded only from the owner's prepared gunslugs.dat. */
public final class PcMain extends ApplicationAdapter {
    private ApplicationListener game;
    private Graphics physicalGraphics;
    private GL20 physicalGl, bridgeGl;
    private Graphics bridgeGraphics;
    private final DisplayLayout layout = new DisplayLayout();
    private boolean ready;
    private int frames;
    private long start;

    public static Lwjgl3ApplicationConfiguration configuration() throws Exception {
        int width = Integer.getInteger("gunslugs.width", 640), height = Integer.getInteger("gunslugs.height", 480);
        if (width < 160 || height < 160 || width > 8192 || height > 8192)
            throw new IllegalArgumentException("Display dimensions must be 160..8192 pixels");
        Path saves = Paths.get(System.getProperty("gunslugs.saves", "saves/pc")).toAbsolutePath();
        java.nio.file.Files.createDirectories(saves);
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Gunslugs 3.3.0"); cfg.setWindowedMode(width, height);
        cfg.setHdpiMode(HdpiMode.Pixels); cfg.setResizable(false);
        cfg.setForegroundFPS(30); cfg.setIdleFPS(30); cfg.useVsync(false);
        cfg.setPreferencesConfig(saves.toString(), com.badlogic.gdx.Files.FileType.Absolute);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0);
        cfg.setInitialVisible(!Boolean.getBoolean("gunslugs.hidden"));
        cfg.disableAudio(Boolean.getBoolean("gunslugs.noAudio"));
        if (Boolean.getBoolean("gunslugs.fullscreen")) cfg.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        return cfg;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Gunslugs PC 3.3.0 | 30 updates/s | " + System.getProperty("os.arch"));
        launch(new PcMain());
    }

    public static void launch(ApplicationListener listener) throws Exception {
        new Lwjgl3Application(listener, configuration()) {
            @Override public Graphics getGraphics() { return Gdx.graphics == null ? super.getGraphics() : Gdx.graphics; }
        };
    }

    @Override public void create() {
        try {
            physicalGraphics = Gdx.graphics; physicalGl = Gdx.gl20;
            layout.resize(physicalGraphics.getWidth(), physicalGraphics.getHeight());
            installDisplayBridge();
            game = (ApplicationListener)Class.forName("com.orangepixel.gunslugs.myCanvas").getDeclaredConstructor().newInstance();
            game.getClass().getField("argument_noController").setBoolean(game, true);
            game.create();
            InputProcessor input = Gdx.input.getInputProcessor();
            Gdx.input.setInputProcessor(new InputAdapter() {
                // This PC build's legacy Escape default is 131; its explicit
                // Back handler remains stable across libGDX keyboard versions.
                @Override public boolean keyDown(int key) { return input.keyDown(key == Input.Keys.ESCAPE ? Input.Keys.BACK : key); }
                @Override public boolean keyUp(int key) { return input.keyUp(key == Input.Keys.ESCAPE ? Input.Keys.BACK : key); }
                @Override public boolean keyTyped(char c) { return input.keyTyped(c); }
                @Override public boolean touchDown(int x, int y, int p, int b) { return input.touchDown(layout.inputX(x), layout.inputY(y), p, b); }
                @Override public boolean touchUp(int x, int y, int p, int b) { return input.touchUp(layout.inputX(x), layout.inputY(y), p, b); }
                @Override public boolean touchDragged(int x, int y, int p) { return input.touchDragged(layout.inputX(x), layout.inputY(y), p); }
                @Override public boolean mouseMoved(int x, int y) { return input.mouseMoved(layout.inputX(x), layout.inputY(y)); }
                @Override public boolean scrolled(float x, float y) {
                    try { return (Boolean)input.getClass().getMethod("scrolled", int.class).invoke(input, (int)y); }
                    catch (ReflectiveOperationException e) { return false; }
                }
            });
            ready = true; resize(physicalGraphics.getWidth(), physicalGraphics.getHeight());
            start = System.nanoTime();
            System.out.println("PC_GAME_CREATE_OK offline; gptokeyb2 keyboard input; saves=" + System.getProperty("gunslugs.saves"));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("PC game initialization failed", e); }
    }

    @Override public void resize(int width, int height) {
        if (!ready || width < 160 || height < 160) return;
        layout.resize(width, height); restoreDisplayBridge();
        game.resize(layout.gameWidth, layout.gameHeight);
        System.out.println("PC_GAME_RESIZE_OK " + width + "x" + height + " view=" + layout.gameWidth + "x" + layout.gameHeight);
    }
    @Override public void render() {
        restoreDisplayBridge();
        physicalGl.glDisable(GL20.GL_SCISSOR_TEST);
        physicalGl.glClearColor(0, 0, 0, 1); physicalGl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.render();
        frames++;
        int smoke = Integer.getInteger("gunslugs.smokeFrames", 0);
        if (smoke > 0 && frames >= smoke) {
            String capture = System.getProperty("gunslugs.capture");
            if (capture != null) {
                Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, physicalGraphics.getBackBufferWidth(), physicalGraphics.getBackBufferHeight());
                try { PixmapIO.writePNG(Gdx.files.absolute(capture), pixels, -1, true); }
                finally { pixels.dispose(); }
            }
            System.out.println("PC_SMOKE_OK frames=" + frames + " seconds=" + (System.nanoTime() - start) / 1e9);
            Gdx.app.exit();
        }
    }
    private void save() {
        if (!ready) return;
        try { game.getClass().getMethod("saveSettings").invoke(game); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("PC save failed", e); }
    }
    @Override public void pause() { save(); if (ready) game.pause(); }
    @Override public void resume() { if (ready) game.resume(); }
    @Override public void dispose() {
        try { save(); if (ready) game.dispose(); }
        finally { if (physicalGraphics != null) { Gdx.graphics = physicalGraphics; Gdx.gl = physicalGl; Gdx.gl20 = physicalGl; } }
    }
    private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
    private void restoreDisplayBridge() { Gdx.graphics = bridgeGraphics; Gdx.gl = bridgeGl; Gdx.gl20 = bridgeGl; }
    private void installDisplayBridge() {
        final int[] framebuffer = {0};
        bridgeGl = (GL20)Proxy.newProxyInstance(PcMain.class.getClassLoader(), new Class<?>[]{GL20.class}, (self, method, args) -> {
            String name = method.getName();
            if (name.equals("glBindFramebuffer")) framebuffer[0] = (Integer)args[1];
            if (framebuffer[0] == 0 && (name.equals("glViewport") || name.equals("glScissor")))
                args = new Object[]{layout.viewportX((Integer)args[0]), layout.viewportY((Integer)args[1]),
                    layout.viewportWidth((Integer)args[2]), layout.viewportHeight((Integer)args[3])};
            return delegate(physicalGl, method, args);
        });
        bridgeGraphics = (Graphics)Proxy.newProxyInstance(PcMain.class.getClassLoader(), new Class<?>[]{Graphics.class}, (self, method, args) -> {
            switch (method.getName()) {
                case "getWidth": case "getBackBufferWidth": return layout.gameWidth;
                case "getHeight": case "getBackBufferHeight": return layout.gameHeight;
                case "getGL20": return bridgeGl;
                case "setWindowedMode": case "setFullscreenMode": case "supportsDisplayModeChange": return false;
                case "setVSync": return null;
                default: return delegate(physicalGraphics, method, args);
            }
        });
        restoreDisplayBridge();
    }
}
