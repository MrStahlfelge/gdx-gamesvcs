package de.golfgl.gdxgamesvcs;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;

import androidx.annotation.NonNull;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidEventListener;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardScoreBuffer;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataBuffer;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * Client for Google Play Games
 * <p>
 * Refactored by Kari Vatjus-Anttila on 27.11.2021
 * Based on code made by Benjamin Schulte on 26.03.2017.
 */

public class GpgsClient implements IGameServiceClient, AndroidEventListener {

    public static final String GAMESERVICE_ID = IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;

    public static final int RC_GPGS_SIGNIN = 9001;
    public static final int RC_LEADERBOARD = 9002;
    public static final int RC_ACHIEVEMENTS = 9003;

    private static final int MAX_SNAPSHOT_RESOLVE_RETRIES = 10;

    protected Activity myContext;
    protected GoogleSignInClient mGoogleApiClient;

    protected boolean snapshotsEnabled;
    protected boolean forceReload;

    protected IGameServiceListener gameListener;
    protected IGameServiceIdMapper<String> gpgsLeaderboardIdMapper;
    protected IGameServiceIdMapper<String> gpgsAchievementIdMapper;

    // Play Games
    private GoogleSignInOptions mGoogleSignInOptions;
    private String mPlayerDisplayName;

    /**
     * sets up the mapper for leader board ids
     *
     * @param gpgsLeaderboardIdMapper Id mapper
     * @return this for method chaining
     */
    public GpgsClient setGpgsLeaderboardIdMapper(IGameServiceIdMapper<String> gpgsLeaderboardIdMapper) {
        this.gpgsLeaderboardIdMapper = gpgsLeaderboardIdMapper;
        return this;
    }

    /**
     * sets up the mapper for leader achievement ids
     *
     * @param gpgsAchievementIdMapper Id mapper
     * @return this for method chaining
     */
    public GpgsClient setGpgsAchievementIdMapper(IGameServiceIdMapper<String> gpgsAchievementIdMapper) {
        this.gpgsAchievementIdMapper = gpgsAchievementIdMapper;
        return this;
    }

    /**
     * set to true if you want to force refreshes when fetching data
     */
    public void setFetchForceRefresh(boolean forceRefresh) {
        this.forceReload = forceRefresh;
    }

    /**
     * Initializes the GoogleApiClient. Give your main AndroidLauncher as context.
     * <p>
     *
     * @param context         your AndroidLauncher class
     * @param enableSnapshots true if you want to activate save game state feature
     * @return this for method chaining
     */
    public GpgsClient initialize(AndroidApplication context, boolean enableSnapshots) {

        if (mGoogleApiClient != null)
            throw new IllegalStateException("Already initialized.");

        myContext = context;

        // We need to receive onActivityResult
        context.addAndroidEventListener(this);

        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);

        snapshotsEnabled = enableSnapshots;

        if (snapshotsEnabled) {
            builder.requestScopes(Games.SCOPE_GAMES_SNAPSHOTS);
        }

        mGoogleSignInOptions = builder.build();

        mGoogleApiClient = GoogleSignIn.getClient(context, mGoogleSignInOptions);

        return this;
    }

    private GoogleSignInAccount getSignInAccount() {
        return GoogleSignIn.getLastSignedInAccount(myContext);
    }

    private void showGPGSPopUp() {
        GamesClient gamesClient = Games.getGamesClient(myContext, getSignInAccount());
        gamesClient.setGravityForPopups(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        gamesClient.setViewForPopups(((AndroidGraphics) Gdx.graphics).getView());
    }

    @Override
    public void setListener(IGameServiceListener gameListener) {
        this.gameListener = gameListener;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_GPGS_SIGNIN) {
            Task<GoogleSignInAccount> result = GoogleSignIn.getSignedInAccountFromIntent(data);
            if (result.isSuccessful()) {
                Gdx.app.log(GAMESERVICE_ID, "Successfully signed in with player id " + result.getResult().getDisplayName());
                showGPGSPopUp();
                getPlayerDisplayName(); //Trigger player display name fetch and cache the result
                if (gameListener != null)
                    gameListener.gsOnSessionActive(GPGSReasonCodes.RESULT_OK);
            } else {
                // Sign in failed, show error to the user
                Gdx.app.log(GAMESERVICE_ID, "Unable to sign in: " + resultCode);

                // inform listener that connection attempt failed
                if (gameListener != null) {
                    if (resultCode == 0) { //User cancellation
                        gameListener.gsOnSessionInactive(GPGSReasonCodes.RESULT_EXPLICITLY_SIGNED_OUT);
                    } else {
                        gameListener.gsOnSessionInactive(resultCode);
                    }
                }

                // Bring up an error dialog to alert the user that sign-in
                // failed. The R.string.signin_failure should reference an error
                // string in your strings.xml file that tells the user they
                // could not be signed in, such as "Unable to sign in."

                String errorMsg;
                switch (resultCode) {
                    case GamesActivityResultCodes.RESULT_APP_MISCONFIGURED:
                        errorMsg = "The application is incorrectly configured. Check that the package name and signing " +
                                "certificate match the client ID created in Developer Console. Also, if the application " +
                                "is not yet published, check that the account you are trying to sign in with is listed as" +
                                " a tester account. See logs for more information.";
                        break;
                    case GamesActivityResultCodes.RESULT_SIGN_IN_FAILED:
                        errorMsg = "Failed to sign in. Please check your network connection and try again.";
                        break;
                    case GPGSReasonCodes.RESULT_EXPLICITLY_SIGNED_OUT:
                        errorMsg = "Sign in process cancelled by user.";
                        break;
                    default:
                        errorMsg = null;
                }

                if (errorMsg != null && gameListener != null)
                    gameListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLoginFailed,
                            "Google Play Games: " + errorMsg, null);
            }
            // check for "inconsistent state"
        } else if (resultCode == GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED &&
                (requestCode == RC_LEADERBOARD || requestCode == RC_ACHIEVEMENTS)) {
            // force a disconnect to sync up state, ensuring that mClient reports "not connected"
            disconnect(false);
        }
    }

    @Override
    public String getGameServiceId() {
        return GAMESERVICE_ID;
    }

    @Override
    public boolean resumeSession() {
        connect();
        return true;
    }

    @Override
    public boolean logIn() {
        connect();
        return true;
    }

    public void connect() {
        if (mGoogleApiClient == null) {
            Gdx.app.error(GAMESERVICE_ID, "Call initialize first");
            throw new IllegalStateException();
        }

        if (isSessionActive()) {
            return;
        }

        Gdx.app.log(GAMESERVICE_ID, "Trying to sign in silently to Google Play Services");

        //Attempt silent sign in
        mGoogleApiClient.silentSignIn().addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
            @Override
            public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                if (task.isSuccessful()) {
                    Gdx.app.log(GAMESERVICE_ID, "Silent sign in done successfully with player id " + task.getResult().getDisplayName());
                    showGPGSPopUp();
                    if (gameListener != null)
                        gameListener.gsOnSessionActive(GPGSReasonCodes.RESULT_OK);
                } else {
                    Gdx.app.log(GAMESERVICE_ID, "Unable to sign in silently. Triggering manual sign in flow");
                    myContext.startActivityForResult(mGoogleApiClient.getSignInIntent(), RC_GPGS_SIGNIN);
                }
            }
        });
    }

    @Override
    public void logOff() {
        this.disconnect(true);
    }

    @Override
    public void pauseSession() {
        //No need to do anything. Disconnection is done only when user explicitly request for it.
    }

    public void disconnect(final boolean explicit) {
        if (isSessionActive()) {
            Gdx.app.log(GAMESERVICE_ID, "Disconnecting");
            mGoogleApiClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        if (gameListener != null)
                            if (explicit) {
                                gameListener.gsOnSessionInactive(GPGSReasonCodes.RESULT_EXPLICITLY_SIGNED_OUT);
                            } else {
                                gameListener.gsOnSessionInactive(GPGSReasonCodes.RESULT_OK);
                            }
                    } else {
                        Gdx.app.error(GAMESERVICE_ID, "Error while trying to disconnect: " + task.getException().getMessage());
                    }
                }
            });
        }
    }

    /**
     * Fetches the player display name asynchronously from the Google API
     * <p>
     *
     * @return Cached result of the player display name. Display name fetching is done in the
     *         background and updated when / if data is fetched successfully.
     */
    @Override
    public String getPlayerDisplayName() {
        if (isSessionActive()) {
            Games.getPlayersClient(myContext, getSignInAccount()).getCurrentPlayer().addOnCompleteListener(new OnCompleteListener<Player>() {
                @Override
                public void onComplete(@NonNull Task<Player> task) {
                    if (task.isSuccessful()) {
                        mPlayerDisplayName = task.getResult().getDisplayName();
                    } else {
                        Gdx.app.error(GAMESERVICE_ID, "Failed to get player display name: " + task.getException().getMessage());
                    }
                }
            });
        }

        return mPlayerDisplayName;
    }

    @Override
    public boolean getPlayerData(final IPlayerDataResponseListener callback) {
        if (isSessionActive()) {
            Games.getPlayersClient(myContext, getSignInAccount()).getCurrentPlayer().addOnCompleteListener(new OnCompleteListener<Player>() {
                @Override
                public void onComplete(@NonNull Task<Player> task) {
                    if (task.isSuccessful()) {
                        GpgsPlayerData playerData = new GpgsPlayerData();
                        Player player = task.getResult();

                        playerData.playerId = player.getPlayerId();
                        playerData.displayName = player.getDisplayName();
                        playerData.title = player.getTitle();
                        playerData.name = player.getName();

                        if (callback != null)
                            callback.onPlayerDataResponse(playerData);
                    } else {
                        Gdx.app.error(GAMESERVICE_ID, "Failed to get player display name: " + task.getException().getMessage());
                    }
                }
            });
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isSessionActive() {
        return getSignInAccount() != null && GoogleSignIn.hasPermissions(getSignInAccount(), mGoogleSignInOptions.getScopeArray());
    }

    @Override
    public boolean isConnectionPending() {
        //Connections are handled behind the scenes. No need for this anymore. Return false by default.
        return false;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        if (isSessionActive()) {
            if (gpgsLeaderboardIdMapper != null)
                leaderBoardId = gpgsLeaderboardIdMapper.mapToGsId(leaderBoardId);

            LeaderboardsClient leaderboardsClient = Games.getLeaderboardsClient(myContext, getSignInAccount());

            if (leaderBoardId != null) {
                leaderboardsClient.getLeaderboardIntent(leaderBoardId).addOnCompleteListener(new OnCompleteListener<Intent>() {
                    @Override
                    public void onComplete(@NonNull Task<Intent> task) {
                        if (task.isSuccessful()) {
                            myContext.startActivityForResult(task.getResult(), RC_LEADERBOARD);
                        } else {
                            Gdx.app.error(GAMESERVICE_ID, "Failed to startup leaderboards activity");
                        }
                    }
                });
            } else {
                leaderboardsClient.getAllLeaderboardsIntent().addOnCompleteListener(new OnCompleteListener<Intent>() {
                    @Override
                    public void onComplete(@NonNull Task<Intent> task) {
                        if (task.isSuccessful()) {
                            myContext.startActivityForResult(task.getResult(), RC_LEADERBOARD);
                        } else {
                            Gdx.app.error(GAMESERVICE_ID, "Failed to startup leaderboards activity");
                        }
                    }
                });
            }
        } else {
            throw new GameServiceException.NoSessionException();
        }
    }

    @Override
    public void showAchievements() throws GameServiceException {
        if (isSessionActive()) {
            Games.getAchievementsClient(myContext, getSignInAccount()).getAchievementsIntent().addOnCompleteListener(new OnCompleteListener<Intent>() {
                @Override
                public void onComplete(@NonNull Task<Intent> task) {
                    if (task.isSuccessful()) {
                        myContext.startActivityForResult(task.getResult(), RC_ACHIEVEMENTS);
                    } else {
                        Gdx.app.error(GAMESERVICE_ID, "Failed to startup achievements activity");
                    }
                }
            });
        } else {
            throw new GameServiceException.NoSessionException();
        }
    }

    @Override
    public boolean fetchAchievements(final IFetchAchievementsResponseListener callback) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot fetch achievements. Session is not active");
            if (callback != null)
                callback.onFetchAchievementsResponse(null);
            return false;
        }

        Games.getAchievementsClient(myContext, getSignInAccount()).load(forceReload).addOnCompleteListener(new OnCompleteListener<AnnotatedData<AchievementBuffer>>() {
            @Override
            public void onComplete(@NonNull Task<AnnotatedData<AchievementBuffer>> task) {
                if (task.isSuccessful()) {
                    AchievementBuffer achievementsResult = task.getResult().get();

                    if (achievementsResult != null) {
                        Array<IAchievement> gpgsAchs = new Array<>(achievementsResult.getCount());

                        for (Achievement ach : achievementsResult) {
                            GpgsAchievement gpgsAchievement = new GpgsAchievement();
                            gpgsAchievement.achievementId = ach.getAchievementId();
                            gpgsAchievement.achievementMapper = gpgsAchievementIdMapper;
                            gpgsAchievement.description = ach.getDescription();
                            gpgsAchievement.title = ach.getName();

                            if (ach.getState() == Achievement.STATE_UNLOCKED)
                                gpgsAchievement.percCompl = 1f;
                            else if (ach.getType() == Achievement.TYPE_INCREMENTAL)
                                gpgsAchievement.percCompl = (float) ach.getCurrentSteps() / ach.getTotalSteps();

                            gpgsAchs.add(gpgsAchievement);
                        }

                        achievementsResult.release();
                        if (callback != null)
                            callback.onFetchAchievementsResponse(gpgsAchs);
                    }
                } else {
                    Gdx.app.log(GAMESERVICE_ID, "Failed to fetch achievements: " + (task.getException() == null ? "Unknown error" : task.getException().getMessage()));
                    if (callback != null)
                        callback.onFetchAchievementsResponse(null);
                }
            }
        });

        return true;
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not submit scores to leaderboard. Session is not active");
            return false;
        }

        if (gpgsLeaderboardIdMapper != null)
            leaderboardId = gpgsLeaderboardIdMapper.mapToGsId(leaderboardId);

        if (leaderboardId != null) {
            if (tag != null) {
                Games.getLeaderboardsClient(myContext, getSignInAccount()).submitScore(leaderboardId, score, tag);
            } else {
                Games.getLeaderboardsClient(myContext, getSignInAccount()).submitScore(leaderboardId, score);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit,
                                           boolean relatedToPlayer,
                                           final IFetchLeaderBoardEntriesResponseListener callback,
                                           int timespan, int collection) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not fetch scores from leaderboards. Session is not active");
            if (callback != null)
                callback.onLeaderBoardResponse(null);
            return false;
        }

        if (gpgsLeaderboardIdMapper != null)
            leaderBoardId = gpgsLeaderboardIdMapper.mapToGsId(leaderBoardId);

        LeaderboardsClient leaderboardsClient = Games.getLeaderboardsClient(myContext, getSignInAccount());

        //Validate timespan and collection
        timespan = timespan == 0 ? LeaderboardVariant.TIME_SPAN_DAILY : timespan == 1 ? LeaderboardVariant.TIME_SPAN_WEEKLY : LeaderboardVariant.TIME_SPAN_ALL_TIME;
        collection = collection == 0 ? LeaderboardVariant.COLLECTION_PUBLIC : LeaderboardVariant.COLLECTION_FRIENDS;

        if (relatedToPlayer) {
            leaderboardsClient.loadPlayerCenteredScores(leaderBoardId,
                    timespan,
                    collection,
                    MathUtils.clamp(limit, 1, 25), forceReload).addOnCompleteListener(new OnCompleteListener<AnnotatedData<LeaderboardsClient.LeaderboardScores>>() {
                @Override
                public void onComplete(@NonNull Task<AnnotatedData<LeaderboardsClient.LeaderboardScores>> task) {
                    if (task.isSuccessful()) {
                        Gdx.app.error(GAMESERVICE_ID, "Leaderboard entries retrieved successfully");
                        publishLeaderboardResults(task.getResult().get().getScores(), callback);
                    } else {
                        Gdx.app.error(GAMESERVICE_ID, "Failed to get leaderboard entries: " + task.getException().getMessage());
                        if (callback != null)
                            callback.onLeaderBoardResponse(null);
                    }
                }
            });
        } else {
            leaderboardsClient.loadTopScores(leaderBoardId,
                    timespan,
                    collection,
                    MathUtils.clamp(limit, 1, 25), forceReload).addOnCompleteListener(new OnCompleteListener<AnnotatedData<LeaderboardsClient.LeaderboardScores>>() {
                @Override
                public void onComplete(@NonNull Task<AnnotatedData<LeaderboardsClient.LeaderboardScores>> task) {
                    if (task.isSuccessful()) {
                        Gdx.app.error(GAMESERVICE_ID, "Leaderboard entries retrieved successfully");
                        publishLeaderboardResults(task.getResult().get().getScores(), callback);
                    } else {
                        Gdx.app.error(GAMESERVICE_ID, "Failed to get leaderboard entries: " + task.getException().getMessage());
                        if (callback != null)
                            callback.onLeaderBoardResponse(null);
                    }
                }
            });
        }

        return true;
    }

    private void publishLeaderboardResults(LeaderboardScoreBuffer scores, IFetchLeaderBoardEntriesResponseListener callback) {
        Array<ILeaderBoardEntry> gpgsLbEs = new Array<>(scores.getCount());
        String playerDisplayName = getPlayerDisplayName();

        Gdx.app.log(GAMESERVICE_ID, "Leaderboard entries size: " + scores.getCount());

        for (LeaderboardScore score : scores) {
            GpgsLeaderBoardEntry gpgsLbE = new GpgsLeaderBoardEntry();

            gpgsLbE.userDisplayName = score.getScoreHolderDisplayName();
            gpgsLbE.currentPlayer = gpgsLbE.userDisplayName.equalsIgnoreCase(playerDisplayName);
            gpgsLbE.formattedValue = score.getDisplayScore();
            gpgsLbE.scoreRank = score.getDisplayRank();
            gpgsLbE.userId = score.getScoreHolder().getPlayerId();
            gpgsLbE.sortValue = score.getRawScore();
            gpgsLbE.scoreTag = score.getScoreTag();

            gpgsLbEs.add(gpgsLbE);
        }

        scores.release();

        if (callback != null)
            callback.onLeaderBoardResponse(gpgsLbEs);
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not submit event. Session is not active");
            return false;
        }

        Games.getEventsClient(myContext, getSignInAccount()).increment(eventId, increment);

        return true;
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not unlock achievement. Session is not active");
            return false;
        }

        if (gpgsAchievementIdMapper != null)
            achievementId = gpgsAchievementIdMapper.mapToGsId(achievementId);

        if (achievementId != null) {
            Games.getAchievementsClient(myContext, getSignInAccount()).unlock(achievementId);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not increment achievement. Session is not active");
            return false;
        }

        if (gpgsAchievementIdMapper != null)
            achievementId = gpgsAchievementIdMapper.mapToGsId(achievementId);

        if (achievementId != null) {
            Games.getAchievementsClient(myContext, getSignInAccount()).increment(achievementId, incNum);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Override this method if you need to set some meta data, for example the description which is displayed
     * in the Play Games app
     *
     * @param metaDataBuilder builder for savegame metadata
     * @param id              snapshot id
     * @param gameState       gamestate data
     * @param progressValue   gamestate progress value
     * @return changed meta data builder
     */
    protected SnapshotMetadataChange.Builder setSaveGameMetaData(SnapshotMetadataChange.Builder metaDataBuilder,
                                                                 String id, byte[] gameState, long progressValue) {
        return metaDataBuilder.setProgressValue(progressValue);
    }

    @Override
    public void saveGameState(final String fileId, final byte[] gameState, final long progressValue,
                              final ISaveGameStateResponseListener callback) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not save game state. Session is not active");
            if (callback != null)
                callback.onGameStateSaved(false, "Session not active");
        }

        if (!snapshotsEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        final SnapshotsClient snapshotsClient = Games.getSnapshotsClient(myContext, getSignInAccount());

        // Open the snapshot, creating if necessary
        snapshotsClient.open(fileId, true).addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
            @Override
            public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) {
                if (task.isSuccessful()) {
                    try {
                        Snapshot snapshot = Tasks.await(processSnapshotOpenResult(task.getResult(), 0));
                        if (snapshot == null) {
                            Gdx.app.log(GAMESERVICE_ID, "Could not open snapshot due to conflicts");
                            if (callback != null)
                                callback.onGameStateSaved(false, "Could not open snapshot due to conflicts");
                        } else {
                            saveSnapshotProgress((Snapshot) snapshot, fileId, gameState, progressValue, callback);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Gdx.app.error(GAMESERVICE_ID, "Failed to open game state: " + task.getException().getMessage());
                }
            }
        });
    }

    public void saveSnapshotProgress(Snapshot snapshot, String id, final byte[] gameState, long progressValue, final ISaveGameStateResponseListener callback) {
        if (progressValue < snapshot.getMetadata().getProgressValue()) {
            Gdx.app.error(GAMESERVICE_ID, "Progress of saved game state higher than current one. Did not save.");
            if (callback != null)
                callback.onGameStateSaved(true, null);
        }

        // Write the new data to the snapshot
        snapshot.getSnapshotContents().writeBytes(gameState);

        // Change metadata
        SnapshotMetadataChange.Builder metaDataBuilder = new SnapshotMetadataChange.Builder()
                .fromMetadata(snapshot.getMetadata());
        metaDataBuilder = setSaveGameMetaData(metaDataBuilder, id, gameState, progressValue);
        SnapshotMetadataChange metadataChange = metaDataBuilder.build();

        Games.getSnapshotsClient(myContext, getSignInAccount()).commitAndClose(snapshot, metadataChange).addOnCompleteListener(new OnCompleteListener<SnapshotMetadata>() {
            @Override
            public void onComplete(@NonNull Task<SnapshotMetadata> task) {
                if (task.isSuccessful()) {
                    Gdx.app.log(GAMESERVICE_ID, "Successfully saved game state with " + gameState.length + "B");
                    if (callback != null)
                        callback.onGameStateSaved(true, null);
                } else {
                    Gdx.app.error(GAMESERVICE_ID, "Error while saving game state: " + task.getException().getMessage());
                    throw new RuntimeException(task.getException().getMessage());
                }
            }
        });
    }

    @Override
    public void loadGameState(final String id, final ILoadGameStateResponseListener callback) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not load game state. Session is not active");
            if (callback != null)
                callback.gsGameStateLoaded(null);
            return;
        }

        if (!snapshotsEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        final SnapshotsClient client = Games.getSnapshotsClient(myContext, getSignInAccount());

        // Open the snapshot, creating if necessary
        client.open(id, true).addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
            @Override
            public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> openTask) {
                if (openTask.isSuccessful()) {
                    try {
                        Snapshot snapshot = Tasks.await(processSnapshotOpenResult(openTask.getResult(), 0));
                        if (snapshot == null) {
                            Gdx.app.log(GAMESERVICE_ID, "Could not open snapshot due to conflicts!");
                            if (callback != null)
                                callback.gsGameStateLoaded(null);
                        } else {
                            byte[] mSaveGameData = snapshot.getSnapshotContents().readFully();
                            if(callback != null)
                                callback.gsGameStateLoaded(mSaveGameData);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Gdx.app.error(GAMESERVICE_ID, "Failed to load game state: " + openTask.getException().getMessage());
                    if(callback != null)
                        callback.gsGameStateLoaded(null);
                }
            }
        });
    }

    @Override
    public boolean deleteGameState(final String id, final ISaveGameStateResponseListener callback) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not delete game state. Session is not active");
            if (callback != null)
                callback.onGameStateSaved(false, "Session not active");
            return false;
        }

        if (!snapshotsEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        final SnapshotsClient client = Games.getSnapshotsClient(myContext, getSignInAccount());

        // Open the snapshot, creating if necessary
        client.open(id, false).addOnCompleteListener(new OnCompleteListener<SnapshotsClient.DataOrConflict<Snapshot>>() {
            @Override
            public void onComplete(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> openTask) {
                if (openTask.isSuccessful()) {
                    try {
                        Snapshot snapshot = Tasks.await(processSnapshotOpenResult(openTask.getResult(), 0));
                        if (snapshot == null) {
                            Gdx.app.log(GAMESERVICE_ID, "Could not open snapshot due to conflicts");
                            if (callback != null)
                                callback.onGameStateSaved(false, "Could not open snapshot due to conflicts");
                        } else {
                            client.delete(snapshot.getMetadata()).addOnCompleteListener(new OnCompleteListener<String>() {
                                @Override
                                public void onComplete(@NonNull Task<String> deleteTask) {
                                    if (deleteTask.isSuccessful()) {
                                        Gdx.app.error(GAMESERVICE_ID, "Snapshot deleted successfully: " + deleteTask.getException().getMessage());
                                        if(callback != null)
                                            callback.onGameStateSaved(true, "Snapshot deleted successfully");
                                    } else {
                                        Gdx.app.error(GAMESERVICE_ID, "Failed to delete the snapshot: " + deleteTask.getException().getMessage());
                                        if(callback != null)
                                            callback.onGameStateSaved(false,
                                                deleteTask.getException().getMessage());
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Gdx.app.log(GAMESERVICE_ID, "Could not delete game state " + id + ": " + openTask.getException().getMessage());
                    if (callback != null)
                        callback.onGameStateSaved(false, openTask.getException().getMessage());
                }
            }
        });

        return true;
    }

    @Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        if (!isSessionActive()) {
            Gdx.app.error(GAMESERVICE_ID, "Could not fetch game states. Session is not active");
            if (callback != null)
                callback.onFetchGameStatesListResponse(null);
            return false;
        }

        if (!snapshotsEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        Games.getSnapshotsClient(myContext, getSignInAccount()).load(forceReload).addOnCompleteListener(new OnCompleteListener<AnnotatedData<SnapshotMetadataBuffer>>() {
            @Override
            public void onComplete(@NonNull Task<AnnotatedData<SnapshotMetadataBuffer>> task) {
                if (task.isSuccessful()) {
                    SnapshotMetadataBuffer snapshots = task.getResult().get();
                    Array<String> gameStates = new Array<>(snapshots.getCount());

                    for (SnapshotMetadata snapshot : snapshots) {
                        gameStates.add(snapshot.getTitle());
                    }

                    snapshots.release();

                    if(callback != null)
                        callback.onFetchGameStatesListResponse(gameStates);
                } else {
                    Gdx.app.log(GAMESERVICE_ID, "Failed to fetch game states:" +
                            task.getException().getMessage());
                    if(callback != null)
                        callback.onFetchGameStatesListResponse(null);
                }
            }
        });

        return true;
    }

    /**
     * Conflict resolution for when Snapshots are opened. Returns a Task.
     */
    private Task<Snapshot> processSnapshotOpenResult(SnapshotsClient.DataOrConflict<Snapshot> result,
                                                     final int retryCount) {
        if (!result.isConflict()) {
            // There was no conflict, so return the result of the source.
            TaskCompletionSource<Snapshot> source = new TaskCompletionSource<>();
            source.setResult(result.getData());
            return source.getTask();
        }

        // There was a conflict.  Try resolving it by selecting the newest of the conflicting snapshots.
        // This is the same as using RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED as a conflict resolution
        // policy, but we are implementing it as an example of a manual resolution.
        // One option is to present a UI to the user to choose which snapshot to resolve.
        SnapshotsClient.SnapshotConflict conflict = result.getConflict();

        Snapshot snapshot = conflict.getSnapshot();
        Snapshot conflictSnapshot = conflict.getConflictingSnapshot();

        // Resolve between conflicts by selecting the newest of the conflicting snapshots.
        Snapshot resolvedSnapshot = snapshot;

        if (snapshot.getMetadata().getLastModifiedTimestamp() <
                conflictSnapshot.getMetadata().getLastModifiedTimestamp()) {
            resolvedSnapshot = conflictSnapshot;
        }

        return Games.getSnapshotsClient(myContext, GoogleSignIn.getLastSignedInAccount(myContext))
                .resolveConflict(conflict.getConflictId(), resolvedSnapshot)
                .continueWithTask(
                    new Continuation<SnapshotsClient.DataOrConflict<Snapshot>, Task<Snapshot>>() {
                        @Override
                        public Task<Snapshot> then(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) throws Exception {
                            // Resolving the conflict may cause another conflict,
                            // so recurse and try another resolution.
                            if (retryCount < MAX_SNAPSHOT_RESOLVE_RETRIES) {
                                return processSnapshotOpenResult(task.getResult(), retryCount + 1);
                            } else {
                                //Fail, return null;
                                return null;
                            }
                        }
                    });
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case GameStateMultipleFiles:
            case FetchGameStates:
            case GameStateDelete:
                return snapshotsEnabled;
            case ShowAchievementsUI:
            case ShowAllLeaderboardsUI:
            case ShowLeaderboardUI:
            case SubmitEvents:
            case FetchAchievements:
            case FetchLeaderBoardEntries:
            case PlayerLogOut:
                return true;
            default:
                return false;
        }
    }

    public static final class GPGSReasonCodes {
        public static final int RESULT_EXPLICITLY_SIGNED_OUT = 0;
        public static final int RESULT_OK = 1;

        private GPGSReasonCodes() {
        }
    }
}
