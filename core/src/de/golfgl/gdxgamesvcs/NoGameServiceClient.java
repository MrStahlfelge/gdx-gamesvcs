package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

/**
 * NoGameServiceClient is an implementation for IGameServiceClient available on any platform.
 * <p>
 * It always performs no ops, but is logging its actions.
 * You can use it for testing purposes in your desktop project, but also in your productive game to avoid checking
 * for null pointers on every call. See {@link #setDebugConnectEnabled(boolean)} option.
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
    private boolean debugConnectEnabled = true;

    /**
     * Set to false if you don't want the NoGameServiceClient to emulate to be connected. While this can be useful
     * in development, it could be better for productive environments to ensure that isConnected() is always false.
     *
     * @param debugConnectEnabled
     * @return this, for method chaining
     */
    public NoGameServiceClient setDebugConnectEnabled(boolean debugConnectEnabled) {
        if (isConnected())
            throw new IllegalStateException();

        this.debugConnectEnabled = debugConnectEnabled;

        return this;
    }

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
        Gdx.app.log(GAMESERVICE_ID, "Connect called, silent: " + silent);

        if (connected)
            return true;

        if (!debugConnectEnabled)
            return false;

        connected = true;

        if (gsListener != null)
            gsListener.gsConnected();

        return true;
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
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        Gdx.app.log(GAMESERVICE_ID, "Submit to leaderboard " + leaderboardId + ", score " + score + ", tag " + tag);

        return isConnected();
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        Gdx.app.log(GAMESERVICE_ID, "Submit event " + eventId + ", value " + increment);

        return isConnected();
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        Gdx.app.log(GAMESERVICE_ID, "Unlock achievement " + achievementId);
        return isConnected();
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum) {
        Gdx.app.log(GAMESERVICE_ID, "Increment achievement " + incNum);
        return isConnected();
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue) {
        Gdx.app.log(GAMESERVICE_ID, "Called save game state " + fileId + " with progress " + progressValue);
    }

    @Override
    public void loadGameState(String fileId) {
        Gdx.app.log(GAMESERVICE_ID, "Called load game state " + fileId);
    }

    @Override
    public CloudSaveCapability supportsCloudGameState() {
        return CloudSaveCapability.NotSupported;
    }

}
