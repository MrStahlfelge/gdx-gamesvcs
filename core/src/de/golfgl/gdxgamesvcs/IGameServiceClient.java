package de.golfgl.gdxgamesvcs;

/**
 * Created by Benjamin Schulte on 16.06.2017.
 */

public interface IGameServiceClient {
    /**
     * Connects to Gameservice
     *
     * @param silent if true, no error messages or log in prompts will be shown
     */
    void connect(boolean silent);

    /**
     * Disconnects from Gameservice
     *
     * @param silent if false, an expiclit signOut is performed
     */
    void disconnect(boolean silent);

    String getPlayerDisplayName();

    boolean isConnected();

    void showLeaderboards(String leaderBoardId) throws GameServiceException;

    void showAchievements() throws GameServiceException;

    void submitToLeaderboard(String leaderboardId, long score, String tag) throws GameServiceException;

    void submitEvent(String eventId, int increment);

    void unlockAchievement(String achievementId);

    void incrementAchievement(String achievementId, int incNum);

    boolean saveGameState(boolean sync, byte[] gameState, long progressValue);

    boolean loadGameState(boolean sync);
}
