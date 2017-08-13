package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

/**
 * Convinience wrapper for {@link IGameServiceListener} implementations: all calls are made on UI main render thread
 * when using this wrapper
 * <p>
 * Created by Benjamin Schulte on 12.08.2017.
 */

public class GameServiceRenderThreadListener implements IGameServiceListener {

    IGameServiceListener realListener;

    public GameServiceRenderThreadListener(IGameServiceListener listener) {
        realListener = listener;
    }

    @Override
    public void gsConnected() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.gsConnected();
            }
        });
    }

    @Override
    public void gsDisconnected() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.gsDisconnected();
            }
        });
    }

    @Override
    public void gsErrorMsg(final GsErrorType et, final String msg) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.gsErrorMsg(et, msg);
            }
        });
    }
}
