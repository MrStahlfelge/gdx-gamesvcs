package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;

/**
 * Kongegrate Client
 * <p>
 * see http://docs.kongregate.com/docs/javascript-api
 * <p>
 * Created by Benjamin Schulte on 25.06.2017.
 */

public class KongClient implements IGameServiceClient {
    public static final String GAMESERVICE_ID = IGameServiceClient.GS_KONGREGATE_ID;
    protected IGameServiceListener gsListener;

    boolean initialized;

    @Override
    public String getGameServiceId() {
        return GAMESERVICE_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    @Override
    public boolean connect(boolean silent) {
        if (!initialized) {
            try {
                loadKongApi();
                initialized = true;
                // if Kong API has initialized, it also is connected
                if (gsListener != null)
                    gsListener.gsConnected();
            } catch (Throwable t) {
                Gdx.app.error(GAMESERVICE_ID, "Could not initialize - kongregate_api.js included in index.html?");
            }
        }
        return true;
    }

    private native void loadKongApi() /*-{
        $wnd.kongregateAPI.loadAPI(function(){
            $wnd.kongregate = $wnd.kongregateAPI.getAPI();
        });
    }-*/;

    @Override
    public void disconnect() {
        //nothing to do
    }

    @Override
    public void logOff() {
        //nothing to do
    }

    @Override
    public String getPlayerDisplayName() {
        if (isKongGuest())
            return null;
        else
            return getKongPlayerName();
    }

    private native boolean isKongGuest() /*-{
        return kongregate.services.isGuest();
    }-*/;

    private native String getKongPlayerName() /*-{
        return $wnd.kongregate.services.getUsername();
    }-*/;

    @Override
    public boolean isConnected() {
        return initialized;
    }

    @Override
    public boolean isConnectionPending() {
        return false;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public void showAchievements() throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public boolean fetchAchievements(IFetchAchievementsResponseListener callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        try {
            if (initialized)
                submitKongStat(leaderboardId, (int) score);

            return initialized;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback) {
        //TODO Supported by Kong
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        if (initialized)
            submitKongStat(eventId, increment);

        return initialized;
    }

    private native void submitKongStat(String id, int num) /*-{
        $wnd.kongregate.stats.submit(id, num);
    }-*/;

    @Override
    public boolean unlockAchievement(String achievementId) {
        return incrementAchievement(achievementId, 1, 1f);
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (initialized)
            submitKongStat(achievementId, incNum);

        return initialized;
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue, ISaveGameStateResponseListener
            listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadGameState(String fileId, ILoadGameStateResponseListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteGameState(String fileId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteGameState(String fileId, ISaveGameStateResponseListener success) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        return feature.equals(GameServiceFeature.SubmitEvents);
    }
}
