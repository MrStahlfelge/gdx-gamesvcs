package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

/**
 * NoGameServiceClient is an implementation for IGameServiceClient available on any platform.
 * <p>
 * It always performs no ops, but is logging its actions.
 * You can use it for testing purposes in your desktop project, but also in your productive game to avoid checking
 * for null pointers on every call.
 * <p>
 * And of course you can extend it for your own implementation of a game service - contributions are very welcome!
 * <p>
 * Created by Benjamin Schulte on 17.06.2017.
 */

public class NoGameServiceClient implements IGameServiceClient {

    public static final String GAMESERVICE_ID = "GS_NOOP";

    protected IGameServiceListener gsListener;

    protected boolean connected;
    private boolean providesLeaderboardUI;
    private boolean providesAchievementsUI;

    @Override
    public String getGameServiceId() {
        return GAMESERVICE_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    @Override
    public void connect(boolean silent) {
        Gdx.app.log(GAMESERVICE_ID, "Connect called, silent: " + silent);

        connected = true;

        if (gsListener != null)
            gsListener.gsConnected();
    }

    @Override
    public void disconnect() {
        Gdx.app.log(GAMESERVICE_ID, "Disconnect called.");
        connected = false;

        if (gsListener != null)
            gsListener.gsDisconnected();
    }

    @Override
    public void logOff() {
        Gdx.app.log(GAMESERVICE_ID, "Log off called.");

        disconnect();
    }

    @Override
    public String getPlayerDisplayName() {
        Gdx.app.log(GAMESERVICE_ID, "No player name to return.");
        return null;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isConnectionPending() {
        return false;
    }

    /**
     * for testing purposes
     */
    public NoGameServiceClient setProvidesLeaderboardUI(boolean providesLeaderboardUI) {
        this.providesLeaderboardUI = providesLeaderboardUI;
        return this;
    }

    @Override
    public boolean providesLeaderboardUI() {
        return this.providesLeaderboardUI;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        Gdx.app.log(GAMESERVICE_ID, "Show leaderboards called: " + leaderBoardId);

        if (!this.providesLeaderboardUI)
            throw new GameServiceException.NotSupportedException();
    }

    public NoGameServiceClient setProvidesAchievementsUI(boolean providesAchievementsUI) {
        this.providesAchievementsUI = providesAchievementsUI;

        return this;
    }

    @Override
    public boolean providesAchievementsUI() {
        return providesAchievementsUI;
    }

    @Override
    public void showAchievements() throws GameServiceException {
        Gdx.app.log(GAMESERVICE_ID, "Show achievements called.");

        if (!this.providesAchievementsUI)
            throw new GameServiceException.NotSupportedException();
    }

    @Override
    public void submitToLeaderboard(String leaderboardId, long score, String tag) throws GameServiceException {
        Gdx.app.log(GAMESERVICE_ID, "Submit to leaderboard " + leaderboardId + ", score " + score + ", tag " + tag);

        if (!isConnected())
            throw new GameServiceException.NotConnectedException();
    }

    @Override
    public void submitEvent(String eventId, int increment) {
        Gdx.app.log(GAMESERVICE_ID, "Submit event " + eventId + ", value " + increment);
    }

    @Override
    public void unlockAchievement(String achievementId) {
        Gdx.app.log(GAMESERVICE_ID, "Unlock achievement " + achievementId);
    }

    @Override
    public void incrementAchievement(String achievementId, int incNum) {
        Gdx.app.log(GAMESERVICE_ID, "Increment achievement " + incNum);
    }

    @Override
    public void saveGameState(byte[] gameState, long progressValue) {
        Gdx.app.log(GAMESERVICE_ID, "Called save game state with progress " + progressValue);
    }

    @Override
    public void loadGameState() {
        Gdx.app.log(GAMESERVICE_ID, "Called load game state.");
    }

    @Override
    public CloudSaveCapability supportsCloudGameState() {
        return CloudSaveCapability.NotSupported;
    }

}
