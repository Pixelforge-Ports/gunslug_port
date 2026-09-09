package org.portmaster.gunslugs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Maps the APK's retained GL20 interface to the active desktop GL20 backend. */
public final class GlBridge {
    private GlBridge() { }

    public static Object create() throws ReflectiveOperationException {
        Class<?> apkInterface = Class.forName("p.e");
        Map<String, String> names = new HashMap<>();
        names.put("A", "glDeleteTexture");
        names.put("B", "glCompileShader");
        names.put("C", "glEnable");
        names.put("D", "glGenBuffer");
        names.put("E", "glBindFramebuffer");
        names.put("F", "glDrawArrays");
        names.put("G", "glUniformMatrix4fv");
        names.put("I", "glViewport");
        names.put("J", "glCheckFramebufferStatus");
        names.put("K", "glTexParameterf");
        names.put("L", "glBlendFunc");
        names.put("M", "glUseProgram");
        names.put("N", "glGetProgramiv");
        names.put("O", "glDeleteRenderbuffer");
        names.put("P", "glBufferSubData");
        names.put("Q", "glGenTexture");
        names.put("R", "glCreateProgram");
        names.put("S", "glDrawElements");
        names.put("T", "glGenFramebuffer");
        names.put("U", "glGetProgramInfoLog");
        names.put("V", "glEnableVertexAttribArray");
        names.put("W", "glGetString");
        names.put("X", "glClear");
        names.put("Y", "glUniform1i");
        names.put("Z", "glBindBuffer");
        names.put("b", "glVertexAttribPointer");
        names.put("b0", "glBufferData");
        names.put("c", "glGetFloatv");
        names.put("c0", "glCompressedTexImage2D");
        names.put("d", "glShaderSource");
        names.put("d0", "glGetUniformLocation");
        names.put("e", "glGenRenderbuffer");
        names.put("e0", "glDeleteFramebuffer");
        names.put("f", "glAttachShader");
        names.put("f0", "glPixelStorei");
        names.put("g", "glGetActiveUniform");
        names.put("g0", "glDepthMask");
        names.put("h", "glGenerateMipmap");
        names.put("h0", "glCreateShader");
        names.put("i", "glLinkProgram");
        names.put("j", "glGetShaderInfoLog");
        names.put("j0", "glGetIntegerv");
        names.put("k", "glDrawElements");
        names.put("k0", "glBindTexture");
        names.put("l", "glTexImage2D");
        names.put("l0", "glDeleteShader");
        names.put("m", "glBlendFuncSeparate");
        names.put("m0", "glDeleteProgram");
        names.put("n", "glBindRenderbuffer");
        names.put("n0", "glDeleteBuffer");
        names.put("o0", "glGetShaderiv");
        names.put("p", "glTexParameteri");
        names.put("q", "glDisable");
        names.put("r", "glVertexAttribPointer");
        names.put("s", "glGetAttribLocation");
        names.put("t", "glFramebufferTexture2D");
        names.put("u", "glFramebufferRenderbuffer");
        names.put("v", "glGetActiveAttrib");
        names.put("w", "glScissor");
        names.put("x", "glDisableVertexAttribArray");
        names.put("y", "glClearColor");
        names.put("z", "glRenderbufferStorage");
        Map<Method, Method> targets = new HashMap<>();
        for (Method method : apkInterface.getMethods()) {
            String targetName = names.get(method.getName());
            if (targetName == null) throw new NoSuchMethodException("Unmapped APK GL20 method: " + method);
            Method target = GL20.class.getMethod(targetName, method.getParameterTypes());
            if (target.getReturnType() != method.getReturnType())
                throw new NoSuchMethodException("GL20 return type mismatch: " + method);
            targets.put(method, target);
        }
        return Proxy.newProxyInstance(apkInterface.getClassLoader(), new Class<?>[] { apkInterface },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return "Gunslugs desktop GL20 bridge";
                }
                GL20 backend = Gdx.gl20;
                if (backend == null) throw new IllegalStateException("Desktop GL20 is not initialized");
                try {
                    return targets.get(method).invoke(backend, args);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
    }
}
