package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

/**
 * Kongegrate Client
 *
 * Created by Benjamin Schulte on 25.06.2017.
 */

public class KongClient implements IGameServiceClient {
    public static final String GAMESERVICE_ID = "GS_KONG";
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
        //TODO
    }

    @Override
    public void logOff() {
        //TODO
    }

    @Override
    public String getPlayerDisplayName() {
        //TODO var username = kongregate.services.getUsername();
        return null;
    }

    @Override
    public boolean isConnected() {
        //TODO
        return false;
    }

    @Override
    public boolean isConnectionPending() {
        //TODO
        return false;
    }

    @Override
    public boolean providesLeaderboardUI() {
        return false;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public boolean providesAchievementsUI() {
        return false;
    }

    @Override
    public void showAchievements() throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        //TODO Abfang für Long
        submitKongStat(leaderboardId, (int) score);
        return false;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        submitKongStat(eventId, increment);
        return true;
    }

    private native void submitKongStat(String id, int num) /*-{
        $wnd.kongregate.stats.submit(id, num);
    }-*/;

    @Override
    public boolean unlockAchievement(String achievementId) {
        //TODO not supported
        return false;
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum) {
        //TODO not supported
        return false;
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue) throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public void loadGameState(String fileId) throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public CloudSaveCapability supportsCloudGameState() {
        return CloudSaveCapability.NotSupported;
    }
}
