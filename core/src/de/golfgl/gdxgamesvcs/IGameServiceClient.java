package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * This is the main interface for gdx-gamesvcs. Use this in your game core.
 * See the readme file for further information how to use this interface: https://github.com/MrStahlfelge/gdx-gamesvcs
 * <p>
 * Game service client classes implementing this interface are not thread-safe, so you should use references to it
 * either in your UI render thread only or synchronize calls yourself. Using the methods on your render thread is
 * safe and will not block the responsiveness of your game: Blocking calls are done asynchronous.
 * <p>
 * Created by Benjamin Schulte on 16.06.2017.
 */

public interface IGameServiceClient {

    public static final String GS_GAMEJOLT_ID = "GS_GAMEJOLT";
    public static final String GS_GOOGLEPLAYGAMES_ID = "GPGS";
    public static final String GS_AMAZONGC_ID = "GS_AMAZONGC";
    public static final String GS_KONGREGATE_ID = "GS_KONG";
    public static final String GS_GAMECENTER_ID = "GS_APPLE";
    public static final String GS_HUAWEI_ID = "GS_HUAWEI";

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
     * Resumes connection to a user session, if possible
     * <p>
     * It depends from the game service implementation what the session connection state implies. See the
     * documentation of the specific service. This method should be called at application startup, and when resuming
     * in Android. No error messages or log in prompts will be shown in error case.
     * <p>
     * Note: Probably you have set up the GameService client with an initialize() method. It is not defined by
     * this interface because it depends on the service which parameters the method needs.
     *
     * @return true if connection is already established or connection is pending. false if no connect is tried due to
     * unfulfilled prejudices (normally credentials not given).
     * @see #isSessionActive()
     */
    boolean resumeSession();

    /**
     * Opens a user session for the game service if necessary. If possible, user session is resumed silently.
     * <p>
     * This method should only be called when user explicitly pushed a log in button. If supported, log in screens
     * may appear for the user to log in.

     * @return true if connection is already established or connection is pending. false if no connection attempt made.
     * @see #isSessionActive()
     */
    boolean logIn();

    /**
     * Disconnects from Gameservice user session by dropping an open connection or deactivating ping calls,
     * but does not close the user's session and so not invalidating a signin.
     * <p>
     * Use this in your main games pause() method on Android and when game is quit by the user.
     *
     * @see #isSessionActive()
     */
    void pauseSession();

    /**
     * Signs explicitely out and disconnects from Gameservice user session. Use only when explicitely wanted by user to
     * end his session.
     * <p>
     * Notes:
     * Some game services do not provide this feature, the user will stay logged in and connected to a user session.
     * Some game services will allow unauthenticated users to submit events or scores
     */
    void logOff();

    /**
     * Gets Players display name, if possible and user session is connected
     *
     * @return Display name, if available. May return null.
     */
    String getPlayerDisplayName();

    /**
     * Gets Player data if session is connected
     *
     * @param callback the listener that will be notified about the result
     *
     * @return Returns false if data retrieve failed. True if successful.
     */
    boolean getPlayerData(final IPlayerDataResponseListener callback);

    /**
     * Returns if a user session is active.
     * <p>
     * You can safely call game service methods without checking if a user session exists.
     * Implementations will check all prerequisites for you. Depending on the game service, some of its features may be
     * available without user context.
     *
     * @return true if a user session is available and active, false otherwise
     * @see #isConnectionPending()
     */
    boolean isSessionActive();

    /**
     * Checks if a user session connection attempt is running
     *
     * @return boolean which describes if the connection attempt is pending
     */
    boolean isConnectionPending();

    /**
     * Opens user interface for leader boards, if available.
     *
     * @param leaderBoardId if null, then overview is opened (when supported)
     * @throws GameServiceException if not connected to service or operation not supported by client
     */
    void showLeaderboards(String leaderBoardId) throws GameServiceException;

    /**
     * Opens user interface for achievements, if available.
     *
     * @throws GameServiceException if not connected to service or operation not supported by client
     */
    void showAchievements() throws GameServiceException;

    /**
     * Fetch current player's achievements.
     * <p>
     * Should only be called when {@link GameServiceFeature#FetchAchievements} is supported,
     * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
     *
     * @param callback the listener that will be notified about the result
     * @return false if fetch attempt could not be made. Response listener will not get called in that case.
     * @throws UnsupportedOperationException if not supported by game service client, so check
     *                                       {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
     */
    boolean fetchAchievements(final IFetchAchievementsResponseListener callback);

    /**
     * Submits to given leaderboard.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error - some game services allow submitting scores
     * without a user session.
     *
     * @param leaderboardId leaderboard id where the score is submitted
     * @param score         score that will be submitted
     * @param tag           an optional information to post on the leader board, if API supports it. May be null.
     * @return false if submission couldn't be made. true if submit request was made (regardless if it was successful)
     */
    boolean submitToLeaderboard(String leaderboardId, long score, String tag);

    /**
     * Fetches leaderboard entries
     *
     * @param leaderBoardId   leaderboard to fetch
     * @param limit           limit how many entries to retrieve
     * @param relatedToPlayer only fetch scores around current player score or by current player (depending on Game
     *                        Service). If this is not possible, player-unrelated leaderboard entries are returned.
     * @param callback        the listener that will be notified about the result
     * @param timespan        filter the scores based on a specific timespan (depending on Game Service). If this is not
     *                        possible, no timespan scope is used.
     * @param collection      filter the results based on a collection e.g. Social or Public. Depends on the Game Service
     *                        if this is possible. If this is not possible, then public scores are returned.
     * @return false if fetch attempt could not be made. Response listener will not get called in that case.
     * @throws UnsupportedOperationException if not supported by game service client, so check
     *                                       {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
     */
    boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                    IFetchLeaderBoardEntriesResponseListener callback, int timespan, int collection);

    /**
     * Posts an event to the API.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error - some game services allow submitting events
     * without a user session. Also, if game service does not support events, no error is thrown.
     *
     * @param eventId   event to post to
     * @param increment value the event is incremented. This parameter is ignored when not supported by the API
     * @return false if submission couldn't be made. true if submit request was made (regardless if it was successful)
     */
    boolean submitEvent(String eventId, int increment);

    /**
     * Unlocks an achievement.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error.
     *
     * @param achievementId achievement to unlock
     * @return false if submission couldn't be made. true if submit request was made (regardless if it was successful)
     */
    boolean unlockAchievement(String achievementId);

    /**
     * Increments an achievement. If API supports incrementing by a value, parameter incNum is passed to the API.
     * If API supports only passing a percentage value for completion, the parameter completionPercentage is passed
     * to the API.
     * If incrementing achievements is not supported by the API at all, the achievement will be unlocked if
     * completionPercentage is at least 1.
     * <p>
     * This API is of type fire and forget. Every possible error is checked by the API and not thrown, but logged on
     * info level. If the connection is not open, this is no error.
     *
     * @param achievementId        achievement to increment
     * @param incNum               value to increment (if supported by API)
     * @param completionPercentage completion percentage, should be between 0f and 1f
     * @return false if submission couldn't be made. true if submit request was made (regardless if it was successful)
     */
    boolean incrementAchievement(String achievementId, int incNum, float completionPercentage);

    /**
     * Saves game state to the cloud.
     * <p>
     * This method may throw an UnsupportedOperationException when cloud save is not supported. Check with
     * {@link GameServiceFeature#GameStateStorage} before calling.
     *
     * @param fileId        file id to save to when multiple files are supported. Ignored otherwise
     * @param gameState     State to save
     * @param progressValue A value indicating player's progress. Used for conflict handling: if game state already
     *                      saved is higher than this value, the gameState is not saved
     * @param success       response listener, can be null
     */
    void saveGameState(String fileId, byte[] gameState, long progressValue, ISaveGameStateResponseListener success);

    /**
     * Loads game state from the cloud and calls gsGameStateLoaded method of the listener set.
     * <p>
     * This method may throw an UnsupportedOperationException when cloud save is not supported. Check with
     * {@link GameServiceFeature#GameStateStorage} ()} before calling.
     *
     * @param fileId file id to load from when multiple files are supported. Ignored otherwise
     * @param responseListener the listener that will be notified about the result
     */
    void loadGameState(String fileId, ILoadGameStateResponseListener responseListener);

    /**
     * Delete an existing game state.
     * Should only be called when {@link GameServiceFeature#GameStateDelete} is supported,
     * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
     *
     * @param fileId  game state Id
     * @param success response listener, can be null. Called when a delete attempt succeeded or failed
     * @return true if delete attempt could be made, false otherwise
     */
    boolean deleteGameState(final String fileId, ISaveGameStateResponseListener success);

    /**
     * Fetch current player's game states.
     *
     * @param callback the listener that will be notified about the result
     * @return false if fetch attempt could not be made. Response listener will not get called in that case.
     * @throws UnsupportedOperationException if not supported by game service client, so check
     *                                       {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
     */
    boolean fetchGameStates(IFetchGameStatesListResponseListener callback);

    /**
     * Queries if a certain feature is available for this Game Service
     *
     * @param feature feature to query if it is supported
     * @return true if feature is supported by implementation and game service
     */
    boolean isFeatureSupported(GameServiceFeature feature);

    public static enum GameServiceFeature {
        FetchGameStates,
        GameStateStorage,
        GameStateDelete,
        GameStateMultipleFiles,

        FetchAchievements,
        ShowAchievementsUI,

        SubmitEvents,

        FetchLeaderBoardEntries,

		PlayerLogOut,

        ShowLeaderboardUI,
        ShowAllLeaderboardsUI
    }
}
