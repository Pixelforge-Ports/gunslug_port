package org.portmaster.gunslugs;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.graphics.GL20;
import java.io.File;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Standalone checks against the real converted APK; no window or audio device required. */
public final class VerifyBridge {
    private static int checks;
    private static final List<LifecycleListener> listeners = new ArrayList<>();

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static Object call(Object target, String owner, String method, Class<?>[] types, Object... args)
            throws Exception {
        return Class.forName(owner).getMethod(method, types).invoke(target, args);
    }

    private static Object proxy(String name) throws Exception {
        Class<?> type = Class.forName(name);
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("toString")) return "verification " + name;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == int.class) return 0;
            return null;
        });
    }

    private static void verifyRelocation(Path gameJar) throws Exception {
        try (JarFile jar = new JarFile(gameJar.toFile())) {
            require(jar.stream().noneMatch(e -> e.getName().startsWith("com/badlogic/gdx/")),
                "APK libGDX classes still conflict with desktop runtime");
            require(jar.getEntry("B/j.class") != null, "Actual Gunslugs game class missing");
            require(jar.getEntry("org/portmaster/gunslugs/apk/com/badlogic/gdx/math/Matrix4.class") != null,
                "APK native helper was not relocated");
        }
        require(Class.forName("i.d").isAssignableFrom(Class.forName("B.j", false,
            VerifyBridge.class.getClassLoader())), "Game does not implement the expected lifecycle");
        String[][] natives = {
            {"graphics/g2d/Gdx2DPixmap", "clear", "(JI)V"},
            {"graphics/g2d/Gdx2DPixmap", "drawPixmap", "(JJIIIIIIII)V"},
            {"graphics/g2d/Gdx2DPixmap", "free", "(J)V"},
            {"graphics/g2d/Gdx2DPixmap", "getFailureReason", "()Ljava/lang/String;"},
            {"graphics/g2d/Gdx2DPixmap", "load", "([J[BII)Ljava/nio/ByteBuffer;"},
            {"graphics/g2d/Gdx2DPixmap", "newPixmap", "([JIII)Ljava/nio/ByteBuffer;"},
            {"graphics/g2d/Gdx2DPixmap", "setBlend", "(JI)V"},
            {"graphics/glutils/ETC1", "decodeImage", "(Ljava/nio/ByteBuffer;ILjava/nio/ByteBuffer;IIII)V"},
            {"graphics/glutils/ETC1", "getHeightPKM", "(Ljava/nio/ByteBuffer;I)I"},
            {"graphics/glutils/ETC1", "getWidthPKM", "(Ljava/nio/ByteBuffer;I)I"},
            {"graphics/glutils/ETC1", "isValidPKM", "(Ljava/nio/ByteBuffer;I)Z"},
            {"math/Matrix4", "prj", "([F[FIII)V"},
            {"utils/BufferUtils", "copyJni", "([FLjava/nio/Buffer;II)V"},
            {"utils/BufferUtils", "freeMemory", "(Ljava/nio/ByteBuffer;)V"},
            {"utils/BufferUtils", "newDisposableByteBuffer", "(I)Ljava/nio/ByteBuffer;"}
        };
        ClassLoader loader = VerifyBridge.class.getClassLoader();
        for (String[] item : natives) {
            String original = "com.badlogic.gdx." + item[0].replace('/', '.');
            Class<?>[] args = MethodType.fromMethodDescriptorString(item[2], loader).parameterArray();
            Method desktop = Class.forName(original, false, loader).getDeclaredMethod(item[1], args);
            Method apk = Class.forName("org.portmaster.gunslugs.apk." + original, false, loader)
                .getDeclaredMethod(item[1], args);
            require(Modifier.isNative(desktop.getModifiers()), "Desktop JNI target missing: " + desktop);
            require(!Modifier.isNative(apk.getModifiers()) && Modifier.isStatic(apk.getModifiers()),
                "APK native method was not replaced: " + apk);
            require(desktop.getReturnType() == apk.getReturnType(), "Native return type changed: " + apk);
        }
    }

    private static void verifyGl() throws Exception {
        List<String> invoked = new ArrayList<>();
        List<Object[]> received = new ArrayList<>();
        Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (p, m, a) -> {
                invoked.add(m.getName()); received.add(a);
                if (m.getReturnType() == String.class) return "bridge-test-renderer";
                if (m.getReturnType() == int.class) return 23;
                return null;
            });
        Object gl = GlBridge.create();
        require(Class.forName("p.e").isInstance(gl), "GL adapter has incorrect APK interface");
        Object renderer = call(gl, "p.e", "W", new Class<?>[]{int.class}, 7938);
        require("bridge-test-renderer".equals(renderer), "glGetString return value was lost");
        call(gl, "p.e", "y", new Class<?>[]{float.class,float.class,float.class,float.class}, .1f,.2f,.3f,1f);
        IntBuffer integers = IntBuffer.allocate(4);
        call(gl, "p.e", "j0", new Class<?>[]{int.class,IntBuffer.class}, 36006, integers);
        Object generated = call(gl, "p.e", "Q", new Class<?>[]{});
        require(Integer.valueOf(23).equals(generated), "glGenTexture return value was lost");
        require(invoked.equals(Arrays.asList("glGetString","glClearColor","glGetIntegerv","glGenTexture")),
            "GL dispatch method mapping mismatch: " + invoked);
        require(received.get(2)[1] == integers, "GL buffer argument identity was lost");
        require(received.get(1)[2].equals(.3f), "GL primitive argument forwarding failed");
        Gdx.gl20 = null;
    }

    private static void verifyServices(Path assets, Path saves) throws Exception {
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (p, m, a) -> {
                if (m.getName().equals("addLifecycleListener")) listeners.add((LifecycleListener) a[0]);
                if (m.getName().equals("removeLifecycleListener")) listeners.remove(a[0]);
                if (m.getName().equals("postRunnable")) ((Runnable) a[0]).run();
                return null;
            });
        Object game = proxy("i.d"), input = proxy("i.j"), graphics = proxy("i.i");
        Class<?>[] keyInt = {String.class,int.class};
        Class<?>[] keyBool = {String.class,boolean.class};
        try (Services services = new Services(assets, saves)) {
            Object app = services.application(game, input, graphics);
            Object prefs = call(app, "i.c", "l", new Class<?>[]{String.class}, "bridge-verification");
            call(prefs, "i.n", "clear", new Class<?>[]{});
            require(Integer.valueOf(11).equals(call(prefs,"i.n","b",keyInt,"missing",11)), "Preference default failed");
            require(call(prefs,"i.n","d",keyInt,"score",12345) == prefs, "putInteger chaining failed");
            call(prefs,"i.n","a",keyBool,"music",false);
            call(prefs,"i.n","flush",new Class<?>[]{});
            require(Files.isRegularFile(saves.resolve("bridge-verification.properties")), "Preferences not written");
            require(call(app,"i.c","n",new Class<?>[]{}) == game, "Game identity forwarding failed");
            require(call(app,"i.c","f",new Class<?>[]{}) == input, "Input forwarding failed");
            require(call(app,"i.c","m",new Class<?>[]{}) == graphics, "Graphics forwarding failed");
            require(((Enum<?>)call(app,"i.c","e",new Class<?>[]{})).name().equals("Desktop"), "Application type is not Desktop");
            Path asset;
            try (Stream<Path> files = Files.walk(assets)) {
                asset = files.filter(Files::isRegularFile).sorted().findFirst().orElseThrow(
                    () -> new AssertionError("No APK asset exists to verify"));
            }
            String relative = assets.relativize(asset).toString();
            Object handle = call(services.files(),"i.g","a",new Class<?>[]{String.class},relative);
            byte[] actual = (byte[])call(handle,"o.a","h",new Class<?>[]{});
            require(Arrays.equals(Files.readAllBytes(asset),actual), "APK FileHandle asset read changed bytes");
            File location = (File)call(handle,"o.a","c",new Class<?>[]{});
            require(location.toPath().toRealPath().equals(asset.toRealPath()), "Asset path resolution failed");
            Class.forName("i.h").getField("a").set(null,app);
            Class.forName("n.f").getField("b").set(null,"n.e");
            Object controllers = Class.forName("n.f").getMethod("b").invoke(null);
            require(Class.forName("x.b").getField("b").getInt(controllers)==0, "No-op controller manager isn't empty");
            Class.forName("n.f").getMethod("a").invoke(null);
            require(listeners.size()==1, "Controller lifecycle listener was not registered");
            listeners.get(0).pause(); listeners.get(0).resume(); listeners.get(0).dispose();
        }
        require(listeners.isEmpty(), "Services retained controller lifecycle listeners after close");
        try (Services services = new Services(assets,saves)) {
            Object app=services.application(game,input,graphics);
            Object prefs=call(app,"i.c","l",new Class<?>[]{String.class},"bridge-verification");
            require(Integer.valueOf(12345).equals(call(prefs,"i.n","b",keyInt,"score",0)), "Saved integer failed reload");
            require(Boolean.FALSE.equals(call(prefs,"i.n","c",keyBool,"music",true)), "Saved boolean failed reload");
            call(prefs,"i.n","clear",new Class<?>[]{});
        }
        try (Services services=new Services(assets,saves)) {
            Object prefs=call(services.application(game,input,graphics),"i.c","l",new Class<?>[]{String.class},"bridge-verification");
            require(Integer.valueOf(7).equals(call(prefs,"i.n","b",keyInt,"score",7)), "clear was not saved on close");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length!=3) throw new IllegalArgumentException("Usage: VerifyBridge game.jar assets-directory test-save-directory");
        Path jar=Paths.get(args[0]).toAbsolutePath(), assets=Paths.get(args[1]).toRealPath(), saves=Paths.get(args[2]).toAbsolutePath();
        verifyRelocation(jar);
        verifyGl();
        verifyServices(assets,saves);
        System.out.println("BRIDGE_VERIFICATION_OK checks="+checks+" nativeMethods=15 glMethods=62");
    }
}
