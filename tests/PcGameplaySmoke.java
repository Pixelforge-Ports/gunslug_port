import com.badlogic.gdx.*;
import org.portmaster.gunslugs.PcMain;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Exercise real PC game input, progression, persistence and pacing. */
public final class PcGameplaySmoke extends ApplicationAdapter {
    private final PcMain host = new PcMain();
    private Object canvas, pad;
    private Class<?> gameInput;
    private int frames;
    private long started;

    public static void main(String[] args) throws Exception { PcMain.launch(new PcGameplaySmoke()); }
    @Override public void create() {
        host.create();
        try {
            Field field = PcMain.class.getDeclaredField("game"); field.setAccessible(true); canvas = field.get(host);
            gameInput = Class.forName("com.orangepixel.controller.GameInput");
            pad = gameInput.getField("controller1").get(null);
            checkKey(Input.Keys.LEFT, "leftPressed"); checkKey(Input.Keys.RIGHT, "rightPressed");
            checkKey(Input.Keys.UP, "upPressed"); checkKey(Input.Keys.X, "BUTTON_X");
            checkKey(Input.Keys.ESCAPE, "backPressed"); checkKey(Input.Keys.O, "BUTTON_SPECIAL1");
            gameInput.getMethod("initControllers").invoke(null);
            require(gameInput.getField("controllersFound").getInt(null) == 0, "Native controller discovery must stay disabled");
            started = System.nanoTime();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void checkKey(int key, String field) throws ReflectiveOperationException {
        InputProcessor input = Gdx.input.getInputProcessor();
        input.keyDown(key); require(pad.getClass().getField(field).getBoolean(pad), "Key down " + field);
        input.keyUp(key); require(!pad.getClass().getField(field).getBoolean(pad), "Key release " + field);
    }
    @Override public void render() {
        host.render(); frames++;
        try {
            // The original menu finishes its splash, then accepts X to start.
            if (frames == 100) Gdx.input.getInputProcessor().keyDown(Input.Keys.X);
            if (frames == 101) Gdx.input.getInputProcessor().keyUp(Input.Keys.X);
            if (frames == 130) Gdx.input.getInputProcessor().keyDown(Input.Keys.X);
            if (frames == 131) Gdx.input.getInputProcessor().keyUp(Input.Keys.X);
            if (frames == 340) {
                Gdx.input.getInputProcessor().keyDown(Input.Keys.RIGHT);
                Gdx.input.getInputProcessor().keyDown(Input.Keys.UP);
                Gdx.input.getInputProcessor().keyDown(Input.Keys.X);
            }
            if (frames == 380) {
                Gdx.input.getInputProcessor().keyUp(Input.Keys.RIGHT);
                Gdx.input.getInputProcessor().keyUp(Input.Keys.UP);
                Gdx.input.getInputProcessor().keyUp(Input.Keys.X);
                require(!pad.getClass().getField("upPressed").getBoolean(pad), "Jump release");
            }
            if (frames == 500) {
                int state = canvas.getClass().getField("GameState").getInt(canvas);
                require(state == 43, "Game must reach gameplay; state=" + state);
                double seconds = (System.nanoTime() - started) / 1e9;
                require(seconds >= (frames - 2) / 30.0, "PC game must not exceed its original 30 FPS");
                if (System.getProperty("gunslugs.capture") != null) {
                    com.badlogic.gdx.graphics.Pixmap image = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0,0,
                        Integer.getInteger("gunslugs.width",640),Integer.getInteger("gunslugs.height",480));
                    try { com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("gunslugs.capture")),image,-1,true); }
                    finally { image.dispose(); }
                }
                host.pause(); host.resume();
                require(Files.isRegularFile(Paths.get(System.getProperty("gunslugs.saves"), "gunslugs")), "Preferences must be saved locally");
                System.out.println("PC_GAMEPLAY_OK input press/release, gameplay, save, resume, 30 FPS; seconds=" + seconds);
                Gdx.app.exit();
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
    @Override public void resize(int w, int h) { host.resize(w,h); }
    @Override public void pause() { host.pause(); }
    @Override public void resume() { host.resume(); }
    @Override public void dispose() { host.dispose(); }
}
