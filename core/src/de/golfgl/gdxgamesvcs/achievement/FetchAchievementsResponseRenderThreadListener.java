package de.golfgl.gdxgamesvcs.achievement;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

/**
 * Convinience wrapper if response calls are only needed on render thread
 * <p>
 * Created by Benjamin Schulte on 12.08.2017.
 */

public class FetchAchievementsResponseRenderThreadListener implements IFetchAchievementsResponseListener {

    private IFetchAchievementsResponseListener realListener;

    public FetchAchievementsResponseRenderThreadListener(IFetchAchievementsResponseListener listener) {
        realListener = listener;
    }

    @Override
    public void onFetchAchievementsResponse(final Array<Achievement> achievements) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                realListener.onFetchAchievementsResponse(achievements);
            }
        });
    }

}