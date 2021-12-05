package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.games.model.AchievementDefinition;
import com.google.api.services.games.model.Leaderboard;
import com.google.api.services.games.model.LeaderboardEntry;
import com.google.api.services.games.model.LeaderboardScores;
import com.google.api.services.games.model.Player;
import com.google.api.services.games.model.PlayerAchievement;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.golfgl.gdxgamesvcs.GameServiceException.NotSupportedException;
import de.golfgl.gdxgamesvcs.IGameServiceListener.GsErrorType;
import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * Google Play Games Services Desktop implementation based on REST API :
 * https://developers.google.com/games/services/web/api/
 * <p>
 * "Installed Application" authentication method is used as described here :
 * https://developers.google.com/identity/protocols/OAuth2#scenarios
 * <p>
 * Implementation based on Java API :
 * https://developers.google.com/api-client-library/java/google-api-java-client/oauth2
 * <p>
 * As stated in {@link IGameServiceClient} all methods of this interface are thread safe, non blocking
 * and typically called from GLThread.
 * <p>
 * All *Sync methods are blocking and could be chained each others in a user-defined thread
 * for advanced usage.
 * <p>
 * Service must be initialized prior to call other methods except features querying
 * {@link #isFeatureSupported(de.golfgl.gdxgamesvcs.IGameServiceClient.GameServiceFeature)}
 * It is recommended to call one of {@link #initialize(String, FileHandle, boolean)} or
 * {@link #initialize(String, InputStream, boolean)}
 * at application startup.
 * <p>
 * Credential storage default behavior can be overridden by subclasses, see :
 * {@link #getDataStoreDirectory()} and {@link #getUserId()}
 *
 * @author mgsx
 */
public class GpgsClient implements IGameServiceClient {
    private static final String TAG = IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;
    protected boolean driveApiEnabled;

    /**
     * Shortcut for current user as per Google API doc.
     */
    private static final String ME = "me";

    /**
     * current application name
     */
    protected String applicationName;

    protected IGameServiceIdMapper<String> gpgsLeaderboardIdMapper;
    protected IGameServiceIdMapper<String> gpgsAchievementIdMapper;

    private IGameServiceListener gameListener;

    private Thread authorizationThread;

    private volatile boolean connected;

    private volatile boolean connecting;

    private boolean initialized;

    private String playerName;

    /**
     * Gdx to Log4j log level mapping
     */
    private static Level getLogLevel(int logLevel) {
        switch (logLevel) {
            case Application.LOG_NONE:
                return Level.OFF;
            case Application.LOG_ERROR:
                return Level.SEVERE;
            case Application.LOG_INFO:
                return Level.INFO;
            case Application.LOG_DEBUG:
                return Level.FINEST;
            default:
                return Level.ALL;
        }
    }

    private void background(final SafeRunnable runnable) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Throwable e) {
                    Gdx.app.error(TAG, "Gpgs Error", e);
                    if (gameListener != null) gameListener.gsShowErrorToUser(GsErrorType.errorUnknown, e.getMessage(), e);
                }
            }
        }).start();
    }

    /**
     * Configure underlying service log level
     *
     * @param logLevel log level as defined in {@link Application} LOG_* constants.
     */
    public void setLogLevel(int logLevel) {
        // configure root java logger to gdx log level.
        Logger.getLogger("").setLevel(getLogLevel(logLevel));
    }

    @Override
    public String getGameServiceId() {
        return IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        gameListener = gsListener;
    }

    /**
     * Get the data store directory for your app.
     * This is where users credential (token) will be stored.
     * <p>
     * Subclass may override this method in order to provide another location.
     * <p>
     * Default is USER_HOME/.store/APPLICATION_NAME
     *
     * @return directory to store users credentials for this application
     */
    protected java.io.File getDataStoreDirectory() {
        java.io.File dataStoresDirectory = new java.io.File(System.getProperty("user.home"), ".store");
        return new java.io.File(dataStoresDirectory, applicationName);
    }

    /**
     * Provide a user identifier for the current user. It is only used to store/restore user
     * credentials (API token) during authorization ({@link #connect(boolean)}.
     * <p>
     * Subclass may override this method in order to provide dynamically a user ID based on their own
     * login system and want to store different credentials for different users.
     * <p>
     * Default is the OS user name.
     *
     * @return a current user identifier, shouldn't be null.
     */
    protected String getUserId() {
        return System.getProperty("user.name");
    }

    /**
     * Initialize connector. Must be called at application startup.
     *
     * @param applicationName Application name registered in Google Play.
     * @param clientSecret    client/secret json data you get from Google Play.
     *                        <p>
     *                        Format is :
     *                        <pre>
     *                        {
     *                          "installed": {
     *                            "client_id": "xxxxxxx-yyyyyyyyyyyyyyyyy.apps.googleusercontent.com",
     *                            "client_secret": "zzzzzzzzzzzzzzzzzzzzzzzzz"
     *                          }
     *                        }
     *                        </pre>
     * @param enableDriveAPI true if you want to use game state sync
     * @return method chaining
     * @throws GdxRuntimeException if initialisation fails.
     */
    public GpgsClient initialize(String applicationName, InputStream clientSecret, boolean enableDriveAPI) {
        this.applicationName = applicationName;
        this.driveApiEnabled = enableDriveAPI;
        try {
            GApiGateway.init(applicationName, clientSecret, getDataStoreDirectory());
            initialized = true;
        } catch (GeneralSecurityException e) {
            throw new GdxRuntimeException(e);
        } catch (IOException e) {
            throw new GdxRuntimeException(e);
        }

        return this;
    }

    /**
     * Initialize with a clientSecretFile.
     * see {@link #initialize(String, InputStream, boolean)}
     *
     * @param applicationName
     * @param clientSecretFile
     * @return method chaining
     */
    public GpgsClient initialize(String applicationName, FileHandle clientSecretFile, boolean enableDriveAPI) {
        initialize(applicationName, clientSecretFile.read(), enableDriveAPI);
        return this;
    }

    /**
     * sets up the mapper for leader board ids
     *
     * @param gpgsLeaderboardIdMapper
     * @return this for method chaining
     */
    public GpgsClient setGpgsLeaderboardIdMapper(IGameServiceIdMapper<String> gpgsLeaderboardIdMapper) {
        this.gpgsLeaderboardIdMapper = gpgsLeaderboardIdMapper;
        return this;
    }

    /**
     * sets up the mapper for leader achievement ids
     *
     * @param gpgsAchievementIdMapper
     * @return this for method chaining
     */
    public GpgsClient setGpgsAchievementIdMapper(IGameServiceIdMapper<String> gpgsAchievementIdMapper) {
        this.gpgsAchievementIdMapper = gpgsAchievementIdMapper;
        return this;
    }

    /**
     * Try to authorize user. This method is blocking until user accept
     * autorization.
     */
    private void waitForUserAuthorization(boolean silent) {
        // load user token or open browser for user authorizations.
        boolean success = false;
        try {
            GApiGateway.authorize(getUserId(), driveApiEnabled);
            success = true;
        } catch (IOException e) {
            if (gameListener != null && !silent)
                gameListener.gsShowErrorToUser(GsErrorType.errorLoginFailed, "failed to get authorization from user", e);
        }

        // try to retreive palyer name
        if (success) {
            try {
                Player player = GApiGateway.games.players().get(ME).execute();
                playerName = player.getDisplayName();
            } catch (IOException e) {
                // if that does not work, connection is not possible
                success = false;
                if (gameListener != null && !silent)
                    gameListener.gsShowErrorToUser(GsErrorType.errorLoginFailed, "Request to Google API failed.", e);
            }
        }

        connected = success;

        // dispatch status
        if (gameListener != null) {
            if (connected) {
                gameListener.gsOnSessionActive();
            } else {
                gameListener.gsOnSessionInactive();
            }
        }
    }

    @Override
    public boolean resumeSession() {
        return connect(true);
    }

    @Override
    public boolean logIn() {
        return connect(false);
    }

    public boolean connect(final boolean silent) {
        if (initialized && !connected && !connecting) {
            connecting = true;
            playerName = null;
            authorizationThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        waitForUserAuthorization(silent);
                    } finally {
                        connecting = false;
                    }
                }
            }, "GpgsAuthorization");
            authorizationThread.start();
        }
        // return false only if client has not been properly initialized.
        return initialized;
    }

    @Override
    public void pauseSession() {
        // nothing special to do here since there is no resources to freeup.
        if (gameListener != null) gameListener.gsOnSessionInactive();
    }

    @Override
    public void logOff() {
        connected = false;
        playerName = null;
        GApiGateway.closeSession();
        pauseSession();
    }

    @Override
    public String getPlayerDisplayName() {
        return playerName;
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
        return connecting;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        throw new NotSupportedException();
    }

    @Override
    public void showAchievements() throws GameServiceException {
        throw new NotSupportedException();
    }

    @Override
    public boolean submitToLeaderboard(final String leaderboardId, final long score, final String tag) {
        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    submitToLeaderboardSync(leaderboardId, score, tag);
                }
            });
        }
        return connected;
    }

    /**
     * Blocking version of {@link #submitToLeaderboard(String, long, String)}
     *
     * @param leaderboardId
     * @param score
     * @param tag
     * @throws IOException
     */
    public void submitToLeaderboardSync(String leaderboardId, long score, String tag) throws IOException {
        if (gpgsLeaderboardIdMapper != null)
            leaderboardId = gpgsLeaderboardIdMapper.mapToGsId(leaderboardId);

        if (leaderboardId != null)
            GApiGateway.games.scores().submit(leaderboardId, score).execute();
    }

    @Override
    public boolean submitEvent(final String eventId, final int increment) {
        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    submitEventSync(eventId, increment);
                }
            });
        }
        return connected;
    }

    /**
     * Blocking version of {@link #submitEvent(String, int)}
     *
     * @param eventId
     * @param increment
     */
    public void submitEventSync(String eventId, int increment) {
        // TODO don't know the API for this use case
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean unlockAchievement(final String achievementId) {
        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    unlockAchievementSync(achievementId);
                }
            });
        }
        return connected;
    }

    /**
     * Blocking version of {@link #unlockAchievement(String)}
     *
     * @param achievementId
     * @throws IOException
     */
    public void unlockAchievementSync(String achievementId) throws IOException {
        if (gpgsAchievementIdMapper != null)
            achievementId = gpgsAchievementIdMapper.mapToGsId(achievementId);

        if (achievementId != null)
            GApiGateway.games.achievements().unlock(achievementId).execute();
    }

    @Override
    public boolean incrementAchievement(final String achievementId, final int incNum, final float
            completionPercentage) {
        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    incrementAchievementSync(achievementId, incNum, completionPercentage);
                }
            });
        }
        return connected;
    }

    /**
     * Blocking version of {@link #incrementAchievement(String, int, float)}
     *
     * @param achievementId
     * @param incNum
     * @param completionPercentage
     * @throws IOException
     */
    public void incrementAchievementSync(String achievementId, int incNum, float completionPercentage) throws
            IOException {
        if (gpgsAchievementIdMapper != null)
            achievementId = gpgsAchievementIdMapper.mapToGsId(achievementId);

        if (achievementId != null)
            GApiGateway.games.achievements().increment(achievementId, incNum).execute();
    }

    @Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    Array<String> result = null;
                    try {
                        result = fetchGameStatesSync();
                    } finally {
                        callback.onFetchGameStatesListResponse(result);
                    }
                }
            });
        }
        return connected;
    }

    /**
     * Blocking version of {@link #fetchGameStatesSync()}
     *
     * @return game states
     * @throws IOException
     */
    public Array<String> fetchGameStatesSync() throws IOException {

        if (!driveApiEnabled)
            throw new UnsupportedOperationException();

        Array<String> games = new Array<String>();

        FileList l = GApiGateway.drive.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(name)")
                .execute();

        for (File f : l.getFiles()) {
            games.add(f.getName());
        }

        return games;
    }

    @Override
    public boolean deleteGameState(final String fileId, final ISaveGameStateResponseListener listener) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    try {
                        deleteGameStateSync(fileId);
                        if (listener != null) listener.onGameStateSaved(true, null);
                    } catch (IOException e) {
                        if (listener != null) listener.onGameStateSaved(false, "Cannot delete game");
                        throw e;
                    }
                }
            });
        }
        return connected;
    }

    public void deleteGameStateSync(String fileId) throws IOException {
        File remoteFile = findFileByNameSync(fileId);
        if (remoteFile != null) {
            GApiGateway.drive.files().delete(remoteFile.getId()).execute();
        }
    }

    @Override
    public void saveGameState(final String fileId, final byte[] gameState, final long progressValue, final
	ISaveGameStateResponseListener listener) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException();

        if (!isSessionActive()) {
            if (listener != null)
                listener.onGameStateSaved(false, "NOT_CONNECTED");
            return;
        }

        background(new SafeRunnable() {
            @Override
            public void run() throws IOException {
                try {
                    saveGameStateSync(fileId, gameState, progressValue);
                    if (listener != null) listener.onGameStateSaved(true, null);
                } catch (IOException e) {
                    if (listener != null) listener.onGameStateSaved(false, "Cannot save game");
                    throw e;
                }
            }
        });
    }

    private File findFileByNameSync(String name) throws IOException {
        // escape some chars (') see : https://developers.google.com/drive/v3/web/search-parameters#fn1
        List<File> files = GApiGateway.drive.files().list().setSpaces("appDataFolder").setQ("name='" + name + "'")
                .execute().getFiles();
        if (files.size() > 1) {
            File snapshotFile = null;
            for (File file : files) {
                if (file.getMimeType().equals("application/vnd.google-play-games.snapshot")) {
                    // Search for snapshot files and choose the first found one
                    snapshotFile = file;
                    break;
                }
            }
            if (snapshotFile == null)
                Gdx.app.error(TAG, "Multiple files with name " + name + " exists. No snapshot file found");
            else
                Gdx.app.log(TAG, "Multiple files with name " + name + " exists. Choose snapshot file with ID:  " + snapshotFile.getId());
            return snapshotFile;
        } else if (files.size() < 1) {
            return null;
        } else {
            return files.get(0);
        }
    }

    /**
     * Blocking version of {@link #saveGameState(String, byte[], long, ISaveGameStateResponseListener)}
     *
     * @param fileId
     * @param gameState
     * @param progressValue
     * @throws IOException
     */
    public void saveGameStateSync(String fileId, byte[] gameState, long progressValue) throws IOException {

        java.io.File file = java.io.File.createTempFile("games", "dat");
        new FileHandle(file).writeBytes(gameState, false);

        // no type since it is binary data
        FileContent mediaContent = new FileContent(null, file);

        // find file on server
        File remoteFile = findFileByNameSync(fileId);

        // file exists then update it
        if (remoteFile != null) {

            // just update content, leave metadata intact.

            GApiGateway.drive.files().update(remoteFile.getId(), null, mediaContent).execute();

            Gdx.app.log(TAG, "File updated ID: " + remoteFile.getId());
        }
        // file doesn't exists then create it
        else {
            File fileMetadata = new File();
            fileMetadata.setName(fileId);

            // app folder is a reserved keyyword for current application private folder.
            fileMetadata.setParents(Collections.singletonList("appDataFolder"));

            remoteFile = GApiGateway.drive.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            Gdx.app.log(TAG, "File created ID: " + remoteFile.getId());
        }

    }

    @Override
    public void loadGameState(final String fileId, final ILoadGameStateResponseListener listener) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException();

        if (!isSessionActive()) {
            listener.gsGameStateLoaded(null);
            return;
        }

        background(new SafeRunnable() {

            @Override
            public void run() throws IOException {
                try {
                    byte[] data = loadGameStateSync(fileId);
                    if (listener != null) listener.gsGameStateLoaded(data);
                } catch (IOException e) {
                    if (listener != null) listener.gsGameStateLoaded(null);
                    throw e;
                }
            }
        });
    }

    /**
     * Blocking version of {@link #loadGameState(String, ILoadGameStateResponseListener)}
     *
     * @param fileId
     * @return game state data
     * @throws IOException
     */
    public byte[] loadGameStateSync(String fileId) throws IOException {

        InputStream stream = null;
        byte[] data = null;
        try {
            File remoteFile = findFileByNameSync(fileId);
            if (remoteFile != null) {

                stream = GApiGateway.drive.files().get(remoteFile.getId()).executeMediaAsInputStream();

                data = StreamUtils.copyStreamToByteArray(stream);
            }
        } finally {
            StreamUtils.closeQuietly(stream);
        }
        return data;
    }

    @Override
    public boolean fetchAchievements(final IFetchAchievementsResponseListener callback) {
        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    Array<IAchievement> result = null;
                    try {
                        result = fetchAchievementsSync();
                    } finally {
                        callback.onFetchAchievementsResponse(result);
                    }
                }
            });
        }
        return connected;
    }

    /**
     * Blocking version of {@link #fetchAchievements(IFetchAchievementsResponseListener)}
     *
     * @return the achievement list
     * @throws IOException
     */
    public Array<IAchievement> fetchAchievementsSync() throws IOException {

        Array<IAchievement> achievements = new Array<IAchievement>();

        // fetch all definitions
        ObjectMap<String, AchievementDefinition> defs = new ObjectMap<String, AchievementDefinition>();
        for (AchievementDefinition def : GApiGateway.games.achievementDefinitions().list().execute().getItems()) {
            defs.put(def.getId(), def);
        }

        // Fetch player achievements
        for (PlayerAchievement p : GApiGateway.games.achievements().list(ME).execute().getItems()) {
            AchievementDefinition def = defs.get(p.getId());

            String state = p.getAchievementState();

            // filter hidden achievements : there is no reasons to display these
            // to the player. User code could unlock/increment it anyway and user code
            // can check if this achievement is hidden by its absence in the returned list.
            if ("HIDDEN".equals(state)) continue;

            GpgsAchievement a = new GpgsAchievement();
            a.setAchievementId(def.getId());
            a.setTitle(def.getName());
            a.setDescription(def.getDescription());

            boolean isIncremental = "INCREMENTAL".equals(def.getAchievementType());
            boolean unlocked = "UNLOCKED".equals(state);

            if (unlocked) {
                a.setCompletionPercentage(1f);
            } else if (isIncremental) {
                int currentSteps = 0;
                if (p.getCurrentSteps() != null) {
                    currentSteps = p.getCurrentSteps().intValue();
                }
                // total steps range between 2 and 10.000
                int totalSteps = def.getTotalSteps().intValue();
                a.setCompletionPercentage(MathUtils.floorPositive((float) currentSteps / (float) totalSteps));
            } else {
                a.setCompletionPercentage(0f);
            }
            a.setIconUrl(unlocked ? def.getUnlockedIconUrl() : def.getRevealedIconUrl());
            a.achievementIdMapper = gpgsAchievementIdMapper;

            achievements.add(a);
        }

        return achievements;
    }

    @Override
    public boolean fetchLeaderboardEntries(final String leaderBoardId, final int limit,
                                           final boolean relatedToPlayer,
                                           final IFetchLeaderBoardEntriesResponseListener callback) {
        if (connected) {
            background(new SafeRunnable() {
                @Override
                public void run() throws IOException {
                    Array<ILeaderBoardEntry> result = null;
                    try {
                        result = fetchLeaderboardSync(leaderBoardId, limit, relatedToPlayer, false);
                    } finally {
                        callback.onLeaderBoardResponse(result);
                    }
                }
            });
        }
        return connected;
    }

    @Override
    public boolean fetchLeaderboardEntries(final String leaderBoardId, final int limit,
                                           final boolean relatedToPlayer,
                                           final IFetchLeaderBoardEntriesResponseListener callback,
                                           final int timespan, final int collection) {
        throw new UnsupportedOperationException();
    }

    /**
     * Blocking version of
     * {@link #fetchLeaderboardEntries(String, int, boolean, IFetchLeaderBoardEntriesResponseListener, int, int)}
     *
     * @param leaderBoardId leaderboard id used for data fetching
     * @param limit how many results to return
     * @param aroundPlayer are the results related to the player
     * @param friendsOnly should the results contain friends only
     *
     * @return array of leaderboard entries
     * @throws IOException on error cases throws an IOException
     */
    public Array<ILeaderBoardEntry> fetchLeaderboardSync(String leaderBoardId, int limit, boolean aroundPlayer,
                                                         boolean friendsOnly) throws IOException {
        // TODO implement limit

        if (gpgsLeaderboardIdMapper != null)
            leaderBoardId = gpgsLeaderboardIdMapper.mapToGsId(leaderBoardId);

        if (leaderBoardId == null)
            return null;

        Array<ILeaderBoardEntry> result = new Array<ILeaderBoardEntry>();
        Leaderboard lb = GApiGateway.games.leaderboards().get(leaderBoardId).execute();

        // XXX no longer LB info ...
//		result.id = lb.getId();
//		result.name = lb.getName();
//		result.scores = new Array<LeaderBoard.Score>();
//		result.iconUrl = lb.getIconUrl();

        LeaderboardScores r;
        if (aroundPlayer) {
            r = GApiGateway.games.scores().listWindow(leaderBoardId, friendsOnly ? "SOCIAL" : "PUBLIC", "ALL_TIME")
                    .execute();
        } else {
            r = GApiGateway.games.scores().list(leaderBoardId, friendsOnly ? "SOCIAL" : "PUBLIC", "ALL_TIME").execute();
        }
        LeaderboardEntry playerScore = r.getPlayerScore();
        // player is null when not having a score yet.
        // we add it to the list because non-public profile won't appear in
        // the full list.
        if (playerScore != null) {
            GpgsLeaderBoardEntry ps = mapPlayerScore(r.getPlayerScore());
            ps.setCurrentPlayer(true);
            result.add(ps);
        }
        // r.getItems is null when no score has been submitted yet.
        if (r.getItems() != null) {
            for (LeaderboardEntry score : r.getItems()) {
                // when player is public it appear in this list as well, so we filter it.
                if (playerScore == null || !score.getPlayer().getPlayerId().equals(playerScore.getPlayer()
                        .getPlayerId())) {
                    GpgsLeaderBoardEntry s = mapPlayerScore(score);
                    s.setCurrentPlayer(false);
                    result.add(s);
                }
            }
        }

        // maybe already sorted but API doesn't say anything about it :
        // https://developers.google.com/games/services/web/api/scores/list
        // so we sort list depending of score meaning.
        final int order = "SMALLER_IS_BETTER".equals(lb.getOrder()) ? 1 : -1;
        result.sort(new Comparator<ILeaderBoardEntry>() {
            @Override
            public int compare(ILeaderBoardEntry o1, ILeaderBoardEntry o2) {
                return order * Long.compare(o1.getSortValue(), o2.getSortValue());
            }
        });
        return result;
    }

    protected GpgsLeaderBoardEntry mapPlayerScore(LeaderboardEntry score) throws IOException {
        GpgsLeaderBoardEntry s = new GpgsLeaderBoardEntry();
        s.setUserDisplayName(score.getPlayer().getDisplayName());
        s.setScoreRank(score.getFormattedScoreRank());
        s.setFormattedValue(score.getFormattedScore());
        s.setSortValue(score.getScoreValue() != null ? score.getScoreValue().longValue() : 0);
        s.setAvatarUrl(score.getPlayer().getAvatarImageUrl());
        s.setUserId(score.getPlayer().getPlayerId());
        s.setScoreTag(score.getScoreTag());
        return s;
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case FetchGameStates:
            case GameStateDelete:
            case GameStateMultipleFiles:
            case GameStateStorage:
                return driveApiEnabled;
            case FetchAchievements:
            case FetchLeaderBoardEntries:
            case PlayerLogOut:
                return true;
            default:
                return false;
        }
    }

    private static interface SafeRunnable {
        void run() throws IOException;
    }
}
