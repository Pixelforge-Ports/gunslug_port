package org.portmaster.gunslugs;

import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Calls the desktop JNI entry points from the relocated APK's native wrappers. */
public final class NativeCalls {
    private static final ConcurrentHashMap<String, Method> CACHE = new ConcurrentHashMap<>();

    public static Object invoke(String owner, String name, String descriptor, Object[] args) {
        String key = owner + "." + name + descriptor;
        try {
            Method method = CACHE.get(key);
            if (method == null) {
                ClassLoader loader = NativeCalls.class.getClassLoader();
                Class<?> type = Class.forName(owner.replace('/', '.'), true, loader);
                Class<?>[] params = MethodType.fromMethodDescriptorString(descriptor, loader).parameterArray();
                method = type.getDeclaredMethod(name, params);
                method.setAccessible(true);
                CACHE.put(key, method);
            }
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Desktop native call failed: " + key, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Desktop native signature is incompatible: " + key, e);
        }
    }
}
