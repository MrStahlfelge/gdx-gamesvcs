package de.golfgl.gdxgamesvcs;

/**
 * Created by Benjamin Schulte on 16.06.2017.
 */

public interface IGameServiceClient {

    /**
     * Gets an id for this game service for identifiying or showing labels
     *
     * @return Game Service ID
     */
    String getGameServiceId();

    /**
     * Set this listener to get callbacks from the game service client
     * <p>
     * This is not mandatory.
     *
     * @param gsListener your listening class, normally your Main Game class
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
     * @return true if connection is already established or connection is pending. false if no connect is tried due to
     * unfulfilled prejudices (normally credentials not given).
     */
    boolean connect(boolean silent);

    /**
     * Disconnects from Gameservice by dropping an open connection or deactivating ping calls,
     * but does not close the user's session and so not invalidating a signin.
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
     * @throws GameServiceException if not connected to service or operation not supported by client
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
     * @throws GameServiceException if not connected to service or operation not supported by client
     */
    void showAchievements() throws GameServiceException;

    /**
     * Submits to given leaderboard.
     * <p>
     * Besides the thrown error when no connection is open, this API is of type
     * fire and forget. Every possible error is checked by the API and not thrown, but logged on info level.
     *
     * @param leaderboardId
     * @param score
     * @param tag
     * @throws GameServiceException.NotConnectedException when no connection is open
     */
    void submitToLeaderboard(String leaderboardId, long score, String tag) throws GameServiceException;

    /**
     * Posts an event to the API.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error.
     *
     * @param eventId   event to post to
     * @param increment value the event is incremented. This parameter is ignored when not supported by the API
     */
    void submitEvent(String eventId, int increment);

    /**
     * Unlocks an achievement.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error.
     *
     * @param achievementId achievement to unlock
     */
    void unlockAchievement(String achievementId);

    /**
     * Increments an achievement. If incrementing achievements is not supported by the API, it unlocks the achievement.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error.
     *
     * @param achievementId achievement to increment
     * @param incNum        value to increment
     */
    void incrementAchievement(String achievementId, int incNum);

    /**
     * Saves game state to the cloud.
     * <p>
     * This method may throw an UnsupportedOperationException when cloud save is not supported. Check with
     * {@link #supportsCloudGameState()} before calling.
     *
     * @param gameState     State to save
     * @param progressValue A value indicating player's progress. Used for conflict handling
     */
    void saveGameState(byte[] gameState, long progressValue);

    /**
     * Loads game state from the cloud and calls gsGameStateLoaded method of the listener set.
     * <p>
     * This method may throw an UnsupportedOperationException when cloud save is not supported. Check with
     * {@link #supportsCloudGameState()} before calling.
     */
    void loadGameState();

    /**
     * use this to check if your game service - or the API client - supports cloud save feature
     *
     * @return enum CloudSaveCapability
     */
    CloudSaveCapability supportsCloudGameState();

    enum CloudSaveCapability {NotSupported, SingleFileSupported, MultipleFilesSupported}

    ;
}
