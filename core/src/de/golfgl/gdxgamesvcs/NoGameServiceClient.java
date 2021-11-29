package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * NoGameServiceClient is an implementation for IGameServiceClient available on any platform.
 * <p>
 * It always performs no ops, but is logging its actions.
 * You can use it in your productive game to avoid checking for null pointers on every call.
 * <p>
 * For testing purposes in your desktop project, it's recommended to use {@link MockGameServiceClient} instead
 * which provides more testing features.
 * <p>
 * And of course you can extend it for your own implementation of a game service - contributions are very welcome!
 * <p>
 * Created by Benjamin Schulte on 17.06.2017.
 */

public class NoGameServiceClient implements IGameServiceClient {

    public static final String GAMESERVICE_ID = "GS_NOOP";

    protected IGameServiceListener gsListener;

    protected boolean connected;

    @Override
    public String getGameServiceId() {
        return GAMESERVICE_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    @Override
    public boolean resumeSession() {
        return connect(true);
    }

    @Override
    public boolean logIn() {
        return connect(false);
    }

    public boolean connect(boolean silent) {
        Gdx.app.log(GAMESERVICE_ID, "Connect called, silent: " + silent);

        if (connected)
            return true;

        connected = true;

        if (gsListener != null)
            gsListener.gsOnSessionActive(null);

        return true;
    }

    @Override
    public void pauseSession() {
        Gdx.app.log(GAMESERVICE_ID, "Disconnect called.");
        connected = false;

        if (gsListener != null)
            gsListener.gsOnSessionInactive(null);
    }

    @Override
    public void logOff() {
        Gdx.app.log(GAMESERVICE_ID, "Log off called.");

        pauseSession();
    }

    @Override
    public String getPlayerDisplayName() {
        Gdx.app.log(GAMESERVICE_ID, "No player name to return.");
        return null;
    }

    @Override
    public boolean getPlayerData(IPlayerDataResponseListener callback) {
        return false;
    }

    @Override
    public boolean isSessionActive() {
        return connected;
    }

    @Override
    public boolean isConnectionPending() {
        return false;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        Gdx.app.log(GAMESERVICE_ID, "Show leaderboards called: " + leaderBoardId);
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public void showAchievements() throws GameServiceException {
        Gdx.app.log(GAMESERVICE_ID, "Show achievements called.");
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public boolean fetchAchievements(IFetchAchievementsResponseListener callback) {
        return false;
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        Gdx.app.log(GAMESERVICE_ID, "Submit to leaderboard " + leaderboardId + ", score " + score + ", tag " + tag);

        return isSessionActive();
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback, int timespan, int collection) {
        return false;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        Gdx.app.log(GAMESERVICE_ID, "Submit event " + eventId + ", value " + increment);

        return isSessionActive();
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        Gdx.app.log(GAMESERVICE_ID, "Unlock achievement " + achievementId);
        return isSessionActive();
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        Gdx.app.log(GAMESERVICE_ID, "Increment achievement "+ achievementId
                + " by " + incNum + " (" + completionPercentage + "%)");
        return isSessionActive();
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue, ISaveGameStateResponseListener
            listener) {
        Gdx.app.log(GAMESERVICE_ID, "Called save game state " + fileId + " with progress " + progressValue);
    }

    @Override
    public void loadGameState(String fileId, ILoadGameStateResponseListener listener) {
        Gdx.app.log(GAMESERVICE_ID, "Called load game state " + fileId);
    }

    @Override
    public boolean deleteGameState(String fileId, ISaveGameStateResponseListener success) {
        return false;
    }

    @Override
    public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
        return false;
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        return false;
    }

}
