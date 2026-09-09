package org.portmaster.gunslugs;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import java.lang.reflect.*;
import java.util.*;

/** Integration-only driver. Sends input through the same processor/polling APIs as keyboard input. */
public final class GameplaySmoke extends ApplicationAdapter {
    private final Main host=new Main();
    private final Set<Integer> held=new HashSet<>(), just=new HashSet<>();
    private int frame;
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(720,480); c.setInitialVisible(false); c.setForegroundFPS(60);
        c.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20,2,0);
        new Lwjgl3Application(new GameplaySmoke(),c);
    }
    public void create() {
        host.create();
        final Input backend=Gdx.input;
        Gdx.input=(Input)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Input.class},(self,m,a)->{
            if(m.getName().equals("isKeyPressed") && held.contains((Integer)a[0])) return true;
            if(m.getName().equals("isKeyJustPressed") && just.contains((Integer)a[0])) return true;
            try{return m.invoke(backend,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    void key(int key,boolean down) {
        if(down){held.add(key);just.add(key);Gdx.input.getInputProcessor().keyDown(key);}
        else{held.remove(key);Gdx.input.getInputProcessor().keyUp(key);}
    }
    public void render() {
        ++frame;
        if(frame==100||frame==180||frame==260||frame==340) key(Input.Keys.X,true);
        if(frame==102||frame==182||frame==262||frame==342) key(Input.Keys.X,false);
        if(frame==410){key(Input.Keys.D,true);key(Input.Keys.X,true);}
        if(frame==470)key(Input.Keys.W,true);
        if(frame==480)key(Input.Keys.W,false);
        if(frame==520){key(Input.Keys.D,false);key(Input.Keys.X,false);}
        host.render(); just.clear();
        if(frame==95||frame==170||frame==250||frame==400||frame==510||frame==550){
            String dir=System.getProperty("gunslugs.testOutput","build");
            Pixmap p=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
            try{PixmapIO.writePNG(Gdx.files.absolute(dir+"/gameplay-"+frame+".png"),p,-1,true);}finally{p.dispose();}
            System.out.println("CAPTURE_FRAME "+frame);
        }
        if(frame==560){System.out.println("GAMEPLAY_DRIVER_OK 560 rendered frames; inspect captures for stage coverage");Gdx.app.exit();}
    }
    public void resize(int w,int h){host.resize(w,h);}
    public void pause(){host.pause();}
    public void resume(){host.resume();}
    public void dispose(){host.dispose();}
}
