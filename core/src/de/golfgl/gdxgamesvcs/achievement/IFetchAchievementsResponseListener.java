package de.golfgl.gdxgamesvcs.achievement;

import com.badlogic.gdx.utils.Array;

/**
 * Response listener for {@link IGameServiceClient#fetchAchievements}
 * <p>
 * This listener may not be called on UI thread. Use Gdx.app.postRunnable or convinience class
 * FetchAchievementsResponseRenderThreadListener
 *
 * @author mgsx
 */
public interface IFetchAchievementsResponseListener {

    /**
     * Response with achievement list
     *
     * @param achievements null if achievements couldn't be fetched.
     */
    void onFetchAchievementsResponse(Array<Achievement> achievements);

}
