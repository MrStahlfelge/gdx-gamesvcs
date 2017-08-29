package de.golfgl.gdxgamesvcs;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.badlogic.gdx.Gdx;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.BaseGameUtils;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;

/**
 * Client for Google Play Games
 * <p>
 * Created by Benjamin Schulte on 26.03.2017.
 */

public class GpgsClient implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        IGameServiceClient {
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
    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInflow = true;
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
     * Initializes the GoogleApiClient. Give your main AndroidLauncher as context.
     * <p>
     * Don't forget to add onActivityResult method there with call to onGpgsActivityResult.
     *
     * @param context        your AndroidLauncher class
     * @param enableDriveAPI yes if you activate save gamestate feature
     * @return this for method chunking
     */
    public GpgsClient initialize(Activity context, boolean enableDriveAPI) {

        if (mGoogleApiClient != null)
            throw new IllegalStateException("Already initialized.");

        myContext = context;
        // retry some times when connect fails (needed when game state sync is enabled)
        firstConnectAttempt = MAX_CONNECTFAIL_RETRIES;

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(myContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES);

        driveApiEnabled = enableDriveAPI;
        if (driveApiEnabled)
            builder.addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER);

        // add other APIs and scopes here as needed

        mGoogleApiClient = builder.build();

        return this;
    }

    /**
     * Call this in the onActivityResult of the context you gave to initialize()
     *
     * @param requestCode requestCode
     * @param resultCode  resultCode
     * @param data        Intent
     * @return yes if this was a Gpgs activity
     */
    public boolean onGpgsActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_GPGS_SIGNIN) {
            signInResult(resultCode, data);
            return true;

            // check for "inconsistent state"
        } else if (resultCode == GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED &&
                (requestCode == RC_LEADERBOARD || requestCode == RC_ACHIEVEMENTS)) {
            // force a disconnect to sync up state, ensuring that mClient reports "not connected"
            disconnect(false);
            return true;
        }
        return false;
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

        Log.i(GAMESERVICE_ID, "Trying to connect with autostart " + autoStart);
        mAutoStartSignInflow = autoStart;
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
            Log.i(GAMESERVICE_ID, "Disconnecting with autoEnd " + autoEnd);
            if (!autoEnd)
                try {
                    Games.signOut(mGoogleApiClient);
                } catch (Throwable t) {
                    // eat security exceptions when already signed out via gpgs ui
                }
            mGoogleApiClient.disconnect();
            gameListener.gsOnSessionInactive();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // The player is signed in. Hide the sign-in button and allow the
        // player to proceed.
        Log.i(GAMESERVICE_ID, "Successfully signed in with player id " + getPlayerDisplayName());
        // reset counter for max connection retry apptemts. Important if app is not exited
        // and stays in memory, but is not used for a long time.
        firstConnectAttempt = MAX_CONNECTFAIL_RETRIES;
        isConnectionPending = false;
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
        Log.i(GAMESERVICE_ID, "Connection suspended, trying to reconnect");
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
        Log.w(GAMESERVICE_ID, "onConnectFailed: " + connectionResult.getErrorCode());

        boolean isPendingBefore = isConnectionPending;

        // if the sign-in button was clicked
        // launch the sign-in flow
        if (mSignInClicked) {
            mAutoStartSignInflow = false;
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
            Log.w(GAMESERVICE_ID, "Retrying to connect...");

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
            Log.w(GAMESERVICE_ID, "SignInResult - Unable to sign in: " + resultCode);

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

            if (errorMsg != null)
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
    public boolean fetchAchievements(IFetchAchievementsResponseListener callback) {
        //TODO supported by GPGS
        throw new UnsupportedOperationException();
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
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback) {
        //TODO supported by GPGS
        throw new UnsupportedOperationException();
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

        // Open the snapshot, creating if necessary
        Snapshots.OpenSnapshotResult open = Games.Snapshots.open(
                mGoogleApiClient, id, true).await();

        Snapshot snapshot = processSnapshotOpenResult(open, 0);

        if (snapshot == null) {
            Log.w(GAMESERVICE_ID, "Could not open Snapshot.");
            if (listener != null)
                listener.onGameStateSaved(false, "Could not open Snapshot.");
            return false;
        }

        if (progressValue < snapshot.getMetadata().getProgressValue()) {
            Log.e(GAMESERVICE_ID, "Progress of saved game state higher than current one. Did not save.");
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

        if (!commit.getStatus().isSuccess()) {
            Log.w(GAMESERVICE_ID, "Failed to commit Snapshot:" + commit.getStatus().getStatusMessage());
            if (listener != null)
                listener.onGameStateSaved(false, commit.getStatus().getStatusMessage());
            return false;
        }

        // No failures
        Log.i(GAMESERVICE_ID, "Successfully saved gamestate with " + gameState.length + "B");
        if (listener != null)
            listener.onGameStateSaved(true, null);
        return true;
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
    public boolean deleteGameState(String fileId, ISaveGameStateResponseListener success) {
        //TODO supported by GPGS
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
        //TODO supported by GPGS
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case GameStateMultipleFiles:
                return driveApiEnabled;
            case ShowAchievementsUI:
            case ShowAllLeaderboardsUI:
            case ShowLeaderboardUI:
            case SubmitEvents:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    public Boolean loadGameStateSync(String id, ILoadGameStateResponseListener listener) {
        if (!isSessionActive()) {
            listener.gsGameStateLoaded(null);
            return false;
        }

        // Open the snapshot, creating if necessary
        Snapshots.OpenSnapshotResult open = Games.Snapshots.open(
                mGoogleApiClient, id, true).await();

        Snapshot snapshot = processSnapshotOpenResult(open, 0);

        if (snapshot == null) {
            Log.w(GAMESERVICE_ID, "Could not open Snapshot.");
            listener.gsGameStateLoaded(null);
            return false;
        }

        // Read
        try {
            byte[] mSaveGameData = null;
            mSaveGameData = snapshot.getSnapshotContents().readFully();
            listener.gsGameStateLoaded(mSaveGameData);
            return true;
        } catch (Throwable t) {
            Log.e(GAMESERVICE_ID, "Error while reading Snapshot.", t);
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
        Log.i(GAMESERVICE_ID, "Open Snapshot Result status: " + result.getStatus().getStatusMessage());

        if (status == GamesStatusCodes.STATUS_OK) {
            return result.getSnapshot();
        } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONTENTS_UNAVAILABLE) {
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
