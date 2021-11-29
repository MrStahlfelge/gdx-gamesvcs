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
    public void gsOnSessionActive(final Integer resultCode) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.gsOnSessionActive(resultCode);
            }
        });
    }

    @Override
    public void gsOnSessionInactive(final Integer resultCode) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.gsOnSessionInactive(resultCode);
            }
        });
    }

    @Override
    public void gsShowErrorToUser(final GsErrorType et, final String msg, final Throwable t) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.gsShowErrorToUser(et, msg, t);
            }
        });
    }
}
