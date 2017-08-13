package de.golfgl.gdxgamesvcs.gamestate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

/**
 * Convinience wrapper if response calls are only needed on render thread
 * <p>
 * Created by Benjamin Schulte on 12.08.2017.
 */

public class FetchGameStatesListResponseRenderThreadListener implements IFetchGameStatesListResponseListener {

    IFetchGameStatesListResponseListener realListener;

    public FetchGameStatesListResponseRenderThreadListener(IFetchGameStatesListResponseListener listener) {
        realListener = listener;
    }

    @Override
    public void onFetchGameStatesListResponse(final Array<String> gameStates) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.onFetchGameStatesListResponse(gameStates);
            }
        });
    }
}
