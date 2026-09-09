package org.portmaster.gunslugs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Verify fitting, pointer mapping, resize safety, and offscreen GL isolation. */
public final class VerifyDisplay {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        DisplayLayout layout = new DisplayLayout();
        for (int[] size : new int[][]{{640,480},{720,480},{720,720},{1024,768},{1280,720}}) {
            layout.resize(size[0], size[1]);
            require(layout.gameWidth >= 720 && layout.gameHeight == 480, "Minimum game view");
            require(layout.x >= 0 && layout.y >= 0 && layout.x + layout.width <= size[0]
                && layout.y + layout.height <= size[1], "Viewport exceeds screen");
            require(Math.abs((double)layout.width / layout.height - (double)layout.gameWidth / layout.gameHeight) < .005,
                "Aspect ratio distorted");
            int cx = layout.cursorX(layout.gameWidth / 2), cy = layout.cursorY(240);
            require(Math.abs(layout.inputX(cx) - layout.gameWidth / 2) <= 1
                && Math.abs(layout.inputY(cy) - 240) <= 1, "Pointer mapping");
        }
        layout.resize(720,720);
        require(layout.width == 720 && layout.height == 480 && layout.y == 120, "Square fitting");
        layout.resize(0,0);
        require(layout.y == 120, "Minimizing changed layout");
        final Object[][] call = {null};
        GL20 previous = Gdx.gl20;
        try {
            Gdx.gl20 = (GL20)Proxy.newProxyInstance(VerifyDisplay.class.getClassLoader(), new Class<?>[]{GL20.class},
                (proxy, method, values) -> { if (method.getName().equals("glViewport") || method.getName().equals("glScissor")) call[0] = values; return null; });
            Object bridge = GlBridge.create(layout);
            Class<?> api = Class.forName("p.e");
            api.getMethod("I",int.class,int.class,int.class,int.class).invoke(bridge,0,0,720,480);
            require(Arrays.equals(call[0],new Object[]{0,120,720,480}), "Screen viewport not fitted");
            api.getMethod("E",int.class,int.class).invoke(bridge,GL20.GL_FRAMEBUFFER,7);
            api.getMethod("I",int.class,int.class,int.class,int.class).invoke(bridge,0,0,256,256);
            require(Arrays.equals(call[0],new Object[]{0,0,256,256}), "Offscreen framebuffer was scaled");
            api.getMethod("E",int.class,int.class).invoke(bridge,GL20.GL_FRAMEBUFFER,0);
            api.getMethod("w",int.class,int.class,int.class,int.class).invoke(bridge,0,0,720,480);
            require(Arrays.equals(call[0],new Object[]{0,120,720,480}), "Screen scissor not fitted");
        } finally { Gdx.gl20 = previous; }
        System.out.println("DISPLAY_LAYOUT_OK: five ratios, pointers, zero-size resize, default/offscreen viewport and scissor");
    }
}
