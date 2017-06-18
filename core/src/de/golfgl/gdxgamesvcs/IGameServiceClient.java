package de.golfgl.gdxgamesvcs;

/**
 * Created by Benjamin Schulte on 16.06.2017.
 */

public interface IGameServiceClient {

    /**
     * Gets an id for this game service.
     *
     * @return Game Service ID
     */
    String getGameServiceId();

    /**
     * set this listener to get callbacks from the game service client
     *
     * @param gsListener
     */
    void setListener(IGameServiceListener gsListener);

    /**
     * Connects to Gameservice and tries to get a user session.
     * <p>
     * Note: Probably you have set up the GameService client with an initialize() method. It is not defined by
     * this interface because it depends on the service which parameters the method needs.
     *
     * @param silent if true, no error messages or log in prompts will be shown for. Use this at application start
     *               or after resuming the application in Android. If false, log in screens may appear for letting
     *               the user enter his credentials.
     */
    void connect(boolean silent);

    /**
     * Disconnects from Gameservice by dropping an open connection or deactivating ping calls,
     * but does not close the session.
     * <p>
     * Use this in your main games pause() method on Android and when game is quit by the user.
     */
    void disconnect();

    /**
     * Signs explicitely out and disconnects from Gameservice. Use only when explicitely wanted by user to end his
     * session.
     */
    void logOff();

    /**
     * Gets Players display name
     *
     * @return Display name
     */
    String getPlayerDisplayName();

    /**
     * Checks the connection status of the game service. See also isConnectionPending.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Checks if a connection attempt is running
     *
     * @return
     */
    boolean isConnectionPending();

    /**
     * Some game service libraries provide an own UI for leaderboards so you don't have to implement one.
     * Use this to check before calling showLeaderboards() to prevent UnsupportedOperationExceptions.
     *
     * @return true, if UI for leaderboard is provided. false otherwise
     */
    boolean providesLeaderboardUI();

    /**
     * Opens user interface for leader boards, if available.
     *
     * @param leaderBoardId if null, then overview is opened (when supported)
     * @throws GameServiceException
     */
    void showLeaderboards(String leaderBoardId) throws GameServiceException;

    /**
     * Some game service libraries provide an own UI for achievements so you don't have to implement one.
     * Use this to check before calling showAchievements() to prevent UnsupportedOperationExceptions.
     *
     * @return true, if UI for achievements is provided. false otherwise
     */
    boolean providesAchievementsUI();

    /**
     * Opens user interface for achievements, if available.
     *
     * @throws GameServiceException
     */
    void showAchievements() throws GameServiceException;

    /**
     * Submits to given leaderboard
     *
     * @param leaderboardId
     * @param score
     * @param tag
     * @throws GameServiceException.NotConnectedException when no connection is open
     */
    void submitToLeaderboard(String leaderboardId, long score, String tag) throws GameServiceException;

    void submitEvent(String eventId, int increment);

    void unlockAchievement(String achievementId);

    void incrementAchievement(String achievementId, int incNum);

    /**
     * Saves game state to the cloud.
     *
     * @param gameState     State to save
     * @param progressValue A value indicating player's progress. Used for conflict handling
     */
    void saveGameState(byte[] gameState, long progressValue);

    void loadGameState();

}
