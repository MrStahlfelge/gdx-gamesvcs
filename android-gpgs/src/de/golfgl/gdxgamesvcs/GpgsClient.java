package de.golfgl.gdxgamesvcs;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidEventListener;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.achievement.Achievements;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardScoreBuffer;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataBuffer;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.BaseGameUtils;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

/**
 * Client for Google Play Games
 * <p>
 * Created by Benjamin Schulte on 26.03.2017.
 */

public class GpgsClient implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        IGameServiceClient, AndroidEventListener {
    public static final int RC_GPGS_SIGNIN = 9001;
    public static final int RC_LEADERBOARD = 9002;
    public static final int RC_ACHIEVEMENTS = 9003;

    public static final String GAMESERVICE_ID = IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;
    protected static final int MAX_CONNECTFAIL_RETRIES = 4;
    private static final int MAX_SNAPSHOT_RESOLVE_RETRIES = 3;
    protected Activity myContext;
    protected IGameServiceListener gameListener;
    // Play Games
    protected GoogleApiClient mGoogleApiClient;
    protected int firstConnectAttempt;
    protected boolean isConnectionPending;
    protected boolean driveApiEnabled;
    protected IGameServiceIdMapper<String> gpgsLeaderboardIdMapper;
    protected IGameServiceIdMapper<String> gpgsAchievementIdMapper;
    protected boolean forceRefresh;
    private boolean mResolvingConnectionFailure = false;
    private boolean mSignInClicked = false;

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
     * set to true if you want to force refreshes when fetching data
     */
    public void setFetchForceRefresh(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
    }

    /**
     * Initializes the GoogleApiClient. Give your main AndroidLauncher as context.
     * <p>
     *
     * @param context        your AndroidLauncher class
     * @param enableDriveAPI true if you activate save gamestate feature
     * @return this for method chunking
     */
    public GpgsClient initialize(AndroidApplication context, boolean enableDriveAPI) {

        if (mGoogleApiClient != null)
            throw new IllegalStateException("Already initialized.");

        myContext = context;

        // we need to receive onActivityResult
        context.addAndroidEventListener(this);

        // retry some times when connect fails (needed when game state sync is enabled)
        firstConnectAttempt = MAX_CONNECTFAIL_RETRIES;

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(myContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES);

        driveApiEnabled = enableDriveAPI;
        if (driveApiEnabled)
            builder.addScope(Drive.SCOPE_APPFOLDER);

        // add other APIs and scopes here as needed

        mGoogleApiClient = builder.build();

        return this;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_GPGS_SIGNIN) {
            signInResult(resultCode, data);

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
        return connect(true);
    }

    @Override
    public boolean logIn() {
        return connect(false);
    }

    public boolean connect(boolean autoStart) {
        if (mGoogleApiClient == null) {
            Gdx.app.error(GAMESERVICE_ID, "Call initialize first.");
            throw new IllegalStateException();
        }

        if (isSessionActive())
            return true;

        Gdx.app.log(GAMESERVICE_ID, "Trying to connect with autostart " + autoStart);
        mSignInClicked = !autoStart;
        isConnectionPending = true;
        mGoogleApiClient.connect();

        return true;
    }

    @Override
    public void logOff() {
        this.disconnect(false);
    }

    @Override
    public void pauseSession() {
        this.disconnect(true);
    }

    public void disconnect(boolean autoEnd) {

        if (isSessionActive()) {
            Gdx.app.log(GAMESERVICE_ID, "Disconnecting with autoEnd " + autoEnd);
            if (!autoEnd)
                try {
                    Games.signOut(mGoogleApiClient);
                } catch (Throwable t) {
                    // eat security exceptions when already signed out via gpgs ui
                }
            mGoogleApiClient.disconnect();
            if (gameListener != null)
                gameListener.gsOnSessionInactive();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // The player is signed in. Hide the sign-in button and allow the
        // player to proceed.
        Gdx.app.log(GAMESERVICE_ID, "Successfully signed in with player id " + getPlayerDisplayName());
        // reset counter for max connection retry apptemts. Important if app is not exited
        // and stays in memory, but is not used for a long time.
        firstConnectAttempt = MAX_CONNECTFAIL_RETRIES;
        isConnectionPending = false;
        if (gameListener != null)
            gameListener.gsOnSessionActive();
    }

    @Override
    public String getPlayerDisplayName() {
        if (isSessionActive())
            return Games.Players.getCurrentPlayer(mGoogleApiClient)
                    .getDisplayName();
        else
            return null;
    }

    @Override
    public boolean isSessionActive() {
        return mGoogleApiClient != null && mGoogleApiClient.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return isConnectionPending;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Gdx.app.log(GAMESERVICE_ID, "Connection suspended, trying to reconnect");
        // Attempt to reconnect
        isConnectionPending = true;
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (mResolvingConnectionFailure) {
            // already resolving
            return;
        }
        Gdx.app.log(GAMESERVICE_ID, "onConnectFailed: " + connectionResult.getErrorCode());

        boolean isPendingBefore = isConnectionPending;

        // if the sign-in button was clicked
        // launch the sign-in flow
        if (mSignInClicked) {
            mSignInClicked = false;
            mResolvingConnectionFailure = true;

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."
            if (!BaseGameUtils.resolveConnectionFailure(myContext,
                    mGoogleApiClient, connectionResult,
                    RC_GPGS_SIGNIN, "Unable to sign in.")) {
                mResolvingConnectionFailure = false;
                isConnectionPending = false;
            }
        }
        // Error code 4 is thrown sometimes on first attempt when game state feature is enabled.
        // Just retry some times solves the problem.
        else if (firstConnectAttempt > 0 && connectionResult.getErrorCode() == 4) {
            firstConnectAttempt -= 1;
            Gdx.app.log(GAMESERVICE_ID, "Retrying to connect...");

            AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        // wait some time before next try
                        Thread.sleep(200);
                        if (!mGoogleApiClient.isConnected())
                            mGoogleApiClient.connect();
                    } catch (InterruptedException e) {
                        //eat
                    }
                    return null;
                }
            };

            task.execute();

        } else
            isConnectionPending = false;

        // inform listener that connection attempt failed
        if (gameListener != null && isPendingBefore && !isConnectionPending)
            gameListener.gsOnSessionInactive();
    }

    public void signInResult(int resultCode, Intent data) {
        mSignInClicked = false;
        mResolvingConnectionFailure = false;
        if (resultCode == Activity.RESULT_OK) {
            isConnectionPending = true;
            mGoogleApiClient.connect();
        } else {
            Gdx.app.log(GAMESERVICE_ID, "SignInResult - Unable to sign in: " + resultCode);

            boolean isPendingBefore = isConnectionPending;
            isConnectionPending = false;

            // inform listener that connection attempt failed
            if (gameListener != null && isPendingBefore)
                gameListener.gsOnSessionInactive();

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
                default:
                    errorMsg = null;
            }

            if (errorMsg != null && gameListener != null)
                gameListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLoginFailed,
                        "Google Play Games: " + errorMsg, null);

        }
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        if (isSessionActive()) {
            if (gpgsLeaderboardIdMapper != null)
                leaderBoardId = gpgsLeaderboardIdMapper.mapToGsId(leaderBoardId);

            myContext.startActivityForResult(leaderBoardId != null ?
                    Games.Leaderboards.getLeaderboardIntent(mGoogleApiClient, leaderBoardId) :
                    Games.Leaderboards.getAllLeaderboardsIntent(mGoogleApiClient), RC_LEADERBOARD);
        } else
            throw new GameServiceException.NoSessionException();
    }

    @Override
    public void showAchievements() throws GameServiceException {
        if (isSessionActive())
            myContext.startActivityForResult(Games.Achievements.getAchievementsIntent(mGoogleApiClient),
                    RC_ACHIEVEMENTS);
        else
            throw new GameServiceException.NoSessionException();

    }

    @Override
    public boolean fetchAchievements(final IFetchAchievementsResponseListener callback) {
        if (!isSessionActive())
            return false;

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return fetchAchievementsSync(callback);
            }
        };

        task.execute();

        return true;

    }

    public boolean fetchAchievementsSync(IFetchAchievementsResponseListener callback) {
        if (!isSessionActive())
            return false;

        Achievements.LoadAchievementsResult achievementsResult = Games.Achievements.load(
                mGoogleApiClient, forceRefresh).await();

        if (!achievementsResult.getStatus().isSuccess()) {
            Gdx.app.log(GAMESERVICE_ID, "Failed to fetch achievements:" +
                    achievementsResult.getStatus().getStatusMessage());
            callback.onFetchAchievementsResponse(null);
            return false;
        }

        AchievementBuffer achievements = achievementsResult.getAchievements();

        Array<IAchievement> gpgsAchs = new Array<IAchievement>(achievements.getCount());

        for (Achievement ach : achievements) {
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

        achievements.release();

        callback.onFetchAchievementsResponse(gpgsAchs);

        return true;
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        if (gpgsLeaderboardIdMapper != null)
            leaderboardId = gpgsLeaderboardIdMapper.mapToGsId(leaderboardId);

        if (leaderboardId != null && isSessionActive()) {
            if (tag != null)
                Games.Leaderboards.submitScore(mGoogleApiClient, leaderboardId, score, tag);
            else
                Games.Leaderboards.submitScore(mGoogleApiClient, leaderboardId, score);
            return true;
        } else
            return false;
    }

    @Override
    public boolean fetchLeaderboardEntries(final String leaderBoardId, final int limit,
                                           final boolean relatedToPlayer,
                                           final IFetchLeaderBoardEntriesResponseListener callback) {
        if (!isSessionActive())
            return false;

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return fetchLeaderboardEntriesSync(leaderBoardId, limit, relatedToPlayer, callback);
            }
        };

        task.execute();

        return true;
    }

    private boolean fetchLeaderboardEntriesSync(String leaderBoardId, int limit, boolean relatedToPlayer,
                                                IFetchLeaderBoardEntriesResponseListener callback) {
        if (!isSessionActive())
            return false;

        if (gpgsLeaderboardIdMapper != null)
            leaderBoardId = gpgsLeaderboardIdMapper.mapToGsId(leaderBoardId);

        Leaderboards.LoadScoresResult scoresResult =
                (relatedToPlayer ?
                        Games.Leaderboards.loadPlayerCenteredScores(mGoogleApiClient, leaderBoardId,
                                LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC,
                                MathUtils.clamp(limit, 1, 25), forceRefresh).await()
                        :
                        Games.Leaderboards.loadTopScores(mGoogleApiClient, leaderBoardId,
                                LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC,
                                MathUtils.clamp(limit, 1, 25), forceRefresh).await());

        if (!scoresResult.getStatus().isSuccess()) {
            Gdx.app.log(GAMESERVICE_ID, "Failed to fetch leaderboard entries:" +
                    scoresResult.getStatus().getStatusMessage());
            callback.onLeaderBoardResponse(null);
            return false;
        }

        LeaderboardScoreBuffer scores = scoresResult.getScores();

        Array<ILeaderBoardEntry> gpgsLbEs = new Array<ILeaderBoardEntry>(scores.getCount());
        String playerDisplayName = getPlayerDisplayName();

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

        callback.onLeaderBoardResponse(gpgsLbEs);

        return true;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        // No exception, if not online events are dismissed
        if (!isSessionActive())
            return false;

        Games.Events.increment(mGoogleApiClient, eventId, increment);

        return true;
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        if (gpgsAchievementIdMapper != null)
            achievementId = gpgsAchievementIdMapper.mapToGsId(achievementId);

        if (achievementId != null && isSessionActive()) {
            Games.Achievements.unlock(mGoogleApiClient, achievementId);
            return true;
        } else
            return false;
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (gpgsAchievementIdMapper != null)
            achievementId = gpgsAchievementIdMapper.mapToGsId(achievementId);

        if (achievementId != null && isSessionActive()) {
            // GPGS supports passing a value for incrementation, no need to use completionPercentage
            Games.Achievements.increment(mGoogleApiClient, achievementId, incNum);
            return true;
        } else
            return false;
    }

    @Override
    public void setListener(IGameServiceListener gameListener) {
        this.gameListener = gameListener;
    }

    @Override
    public void saveGameState(final String fileId, final byte[] gameState, final long progressValue,
                              final ISaveGameStateResponseListener listener) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException();

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return saveGameStateSync(fileId, gameState, progressValue, listener);
            }
        };

        task.execute();
    }

    @NonNull
    public Boolean saveGameStateSync(String id, byte[] gameState, long progressValue,
                                     ISaveGameStateResponseListener listener) {
        if (!isSessionActive()) {
            if (listener != null)
                listener.onGameStateSaved(false, "NOT_CONNECTED");
            return false;
        }

        try {
            // Open the snapshot, creating if necessary
            Snapshots.OpenSnapshotResult open = Games.Snapshots.open(
                    mGoogleApiClient, id, true).await();

            Snapshot snapshot = processSnapshotOpenResult(open, 0);

            if (snapshot == null) {
                Gdx.app.log(GAMESERVICE_ID, "Could not open Snapshot.");
                if (listener != null)
                    listener.onGameStateSaved(false, "Could not open Snapshot.");
                return false;
            }

            if (progressValue < snapshot.getMetadata().getProgressValue()) {
                Gdx.app.error(GAMESERVICE_ID, "Progress of saved game state higher than current one. Did not save.");
                if (listener != null)
                    listener.onGameStateSaved(true, null);
                return false;
            }

            // Write the new data to the snapshot
            snapshot.getSnapshotContents().writeBytes(gameState);

            // Change metadata
            SnapshotMetadataChange.Builder metaDataBuilder = new SnapshotMetadataChange.Builder()
                    .fromMetadata(snapshot.getMetadata());
            metaDataBuilder = setSaveGameMetaData(metaDataBuilder, id, gameState, progressValue);
            SnapshotMetadataChange metadataChange = metaDataBuilder.build();

            Snapshots.CommitSnapshotResult commit = Games.Snapshots.commitAndClose(
                    mGoogleApiClient, snapshot, metadataChange).await();

            if (!commit.getStatus().isSuccess())
                throw new RuntimeException(commit.getStatus().getStatusMessage());

            // No failures
            Gdx.app.log(GAMESERVICE_ID, "Successfully saved gamestate with " + gameState.length + "B");
            if (listener != null)
                listener.onGameStateSaved(true, null);
            return true;

        } catch (Throwable t) {
            Gdx.app.error(GAMESERVICE_ID, "Failed to commit snapshot:" + t.getMessage());
            if (listener != null)
                listener.onGameStateSaved(false, t.getMessage());
            return false;
        }
    }

    /**
     * override this method if you need to set some meta data, for example the description which is displayed
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
    public void loadGameState(final String id, final ILoadGameStateResponseListener listener) {

        if (!driveApiEnabled)
            throw new UnsupportedOperationException();

        if (!isSessionActive()) {
            listener.gsGameStateLoaded(null);
            return;
        }

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return loadGameStateSync(id, listener);
            }
        };

        task.execute();
    }

    @Override
    public boolean deleteGameState(final String fileId, final ISaveGameStateResponseListener success) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        if (!isSessionActive())
            return false;

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return deleteGameStateSync(fileId, success);
            }
        };

        task.execute();

        return true;
    }

    public boolean deleteGameStateSync(String fileId, ISaveGameStateResponseListener success) {
        if (!isSessionActive()) {
            if (success != null)
                success.onGameStateSaved(false, "NO_CONNECTION");
            return false;
        }

        // Open the snapshot, creating if necessary
        Snapshots.OpenSnapshotResult open = Games.Snapshots.open(
                mGoogleApiClient, fileId, false).await();

        Snapshot snapshot = processSnapshotOpenResult(open, 0);

        if (snapshot == null) {
            Gdx.app.log(GAMESERVICE_ID, "Could not delete game state " + fileId + ": " +
                    open.getStatus().getStatusMessage());
            if (success != null)
                success.onGameStateSaved(false, open.getStatus().getStatusMessage());
            return false;
        }

        Snapshots.DeleteSnapshotResult deleteResult = Games.Snapshots.delete(mGoogleApiClient,
                snapshot.getMetadata()).await();

        boolean deletionDone = deleteResult.getStatus().isSuccess();

        Gdx.app.log(GAMESERVICE_ID, "Delete game state " + fileId + ": " + deletionDone +
                " - " + open.getStatus().getStatusMessage());

        if (success != null) {

            success.onGameStateSaved(deletionDone,
                    deleteResult.getStatus().getStatusMessage());
        }

        return deletionDone;
    }

    @Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        if (!driveApiEnabled)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        if (!isSessionActive())
            return false;

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return fetchGameStatesSync(callback);
            }
        };

        task.execute();

        return true;
    }

    private boolean fetchGameStatesSync(IFetchGameStatesListResponseListener callback) {
        if (!isSessionActive())
            return false;

        if (!driveApiEnabled)
            throw new UnsupportedOperationException();

        Snapshots.LoadSnapshotsResult loadResult = Games.Snapshots.load(mGoogleApiClient, forceRefresh).await();

        if (!loadResult.getStatus().isSuccess()) {
            Gdx.app.log(GAMESERVICE_ID, "Failed to fetch game states:" +
                    loadResult.getStatus().getStatusMessage());
            callback.onFetchGameStatesListResponse(null);
            return false;
        }

        SnapshotMetadataBuffer snapshots = loadResult.getSnapshots();

        Array<String> gameStates = new Array<String>(snapshots.getCount());

        for (SnapshotMetadata snapshot : snapshots) {
            gameStates.add(snapshot.getTitle());
        }

        snapshots.release();

        callback.onFetchGameStatesListResponse(gameStates);

        return true;
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case GameStateMultipleFiles:
            case FetchGameStates:
            case GameStateDelete:
                return driveApiEnabled;
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

    public boolean loadGameStateSync(String id, ILoadGameStateResponseListener listener) {
        if (!isSessionActive()) {
            listener.gsGameStateLoaded(null);
            return false;
        }

        try {
            // Open the snapshot, creating if necessary
            Snapshots.OpenSnapshotResult open = Games.Snapshots.open(
                    mGoogleApiClient, id, true).await();

            Snapshot snapshot = processSnapshotOpenResult(open, 0);

            if (snapshot == null) {
                Gdx.app.log(GAMESERVICE_ID, "Could not open Snapshot.");
                listener.gsGameStateLoaded(null);
                return false;
            }

            // Read
            byte[] mSaveGameData = snapshot.getSnapshotContents().readFully();
            listener.gsGameStateLoaded(mSaveGameData);
            return true;
        } catch (Throwable t) {
            Gdx.app.error(GAMESERVICE_ID, "Error while reading Snapshot.", t);
            listener.gsGameStateLoaded(null);
            return false;
        }

    }

    /**
     * Conflict resolution for when Snapshots are opened.  Must be run in an AsyncTask or in a
     * background thread,
     */
    public Snapshot processSnapshotOpenResult(Snapshots.OpenSnapshotResult result, int retryCount) {
        Snapshot mResolvedSnapshot = null;
        retryCount++;

        int status = result.getStatus().getStatusCode();
        Gdx.app.log(GAMESERVICE_ID, "Open Snapshot Result status: " + result.getStatus().getStatusMessage());

        if (status == GamesStatusCodes.STATUS_OK) {
            return result.getSnapshot();
        } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT) {
            Snapshot snapshot = result.getSnapshot();
            Snapshot conflictSnapshot = result.getConflictingSnapshot();

            // Resolve between conflicts by selecting the highest progress or, if equal, newest of the conflicting
            // snapshots.
            mResolvedSnapshot = snapshot;

            if (snapshot.getMetadata().getProgressValue() < conflictSnapshot.getMetadata().getProgressValue()
                    || snapshot.getMetadata().getProgressValue() == conflictSnapshot.getMetadata().getProgressValue()
                    && snapshot.getMetadata().getLastModifiedTimestamp() <
                    conflictSnapshot.getMetadata().getLastModifiedTimestamp()) {
                mResolvedSnapshot = conflictSnapshot;
            }

            Snapshots.OpenSnapshotResult resolveResult = Games.Snapshots.resolveConflict(
                    mGoogleApiClient, result.getConflictId(), mResolvedSnapshot).await();

            if (retryCount < MAX_SNAPSHOT_RESOLVE_RETRIES) {
                // Recursively attempt again
                return processSnapshotOpenResult(resolveResult, retryCount);
            }

        }

        // Fail, return null.
        return null;
    }
}
