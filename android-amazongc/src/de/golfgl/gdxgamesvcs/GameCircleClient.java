package de.golfgl.gdxgamesvcs;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.SystemClock;

import com.amazon.ags.api.AGResponseCallback;
import com.amazon.ags.api.AmazonGamesCallback;
import com.amazon.ags.api.AmazonGamesClient;
import com.amazon.ags.api.AmazonGamesFeature;
import com.amazon.ags.api.AmazonGamesStatus;
import com.amazon.ags.api.overlay.PopUpLocation;
import com.amazon.ags.api.player.RequestPlayerResponse;
import com.amazon.ags.api.whispersync.FailReason;
import com.amazon.ags.api.whispersync.GameDataMap;
import com.amazon.ags.api.whispersync.WhispersyncEventListener;
import com.amazon.ags.api.whispersync.model.SyncableNumber;
import com.amazon.ags.api.whispersync.model.SyncableString;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Base64Coder;

import java.util.EnumSet;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;

/**
 * Client implementation for Amazon GameCircle
 * <p>
 * Created by Benjamin Schulte on 20.07.2017.
 */

public class GameCircleClient implements IGameServiceClient {
    public static final String GS_CLIENT_ID = IGameServiceClient.GS_AMAZONGC_ID;

    protected AmazonGamesClient agsClient;
    protected Activity myContext;
    protected boolean isConnectionPending;
    protected boolean whisperSyncEnabled;
    protected boolean achievementsEnabled;
    protected boolean leaderboardsEnabled;
    protected boolean autoStartSignInFlow;
    // GameCircle does not report if it is suspended, so keep it saved here
    protected boolean isConnected;
    // Whispersync syncs after connection is initialized, but reading the data immediately
    // will return the last local saved value. This member saves if a sync was already done
    // so that loadGameState will not return before that happened
    protected boolean loadedFromCloud;
    protected EnumSet<AmazonGamesFeature> agsFeatures;
    protected IGameServiceListener gsListener;
    protected String cachedPlayerAlias;

    public GameCircleClient setWhisperSyncEnabled(boolean whisperSyncEnabled) {
        if (agsFeatures != null)
            throw new IllegalStateException("Already initialized");

        this.whisperSyncEnabled = whisperSyncEnabled;
        return this;
    }

    public GameCircleClient setAchievementsEnabled(boolean achievementsEnabled) {
        if (agsFeatures != null)
            throw new IllegalStateException("Already initialized");

        this.achievementsEnabled = achievementsEnabled;
        return this;
    }

    public GameCircleClient setLeaderboardsEnabled(boolean leaderboardsEnabled) {
        if (agsFeatures != null)
            throw new IllegalStateException("Already initialized");

        this.leaderboardsEnabled = leaderboardsEnabled;
        return this;
    }

    public GameCircleClient intialize(Activity context) {
        if (agsClient != null || myContext != null)
            throw new IllegalStateException("Already initialized");

        agsFeatures = EnumSet.noneOf(AmazonGamesFeature.class);
        this.myContext = context;

        if (whisperSyncEnabled)
            agsFeatures.add(AmazonGamesFeature.Whispersync);
        if (achievementsEnabled)
            agsFeatures.add(AmazonGamesFeature.Achievements);
        if (leaderboardsEnabled)
            agsFeatures.add(AmazonGamesFeature.Leaderboards);

        return this;

    }

    protected void agcServiceNotReady(AmazonGamesStatus amazonGamesStatus) {
        Gdx.app.error(GS_CLIENT_ID, "onServiceNotReady - " + amazonGamesStatus.name());
        isConnectionPending = false;
        isConnected = false;
        if (gsListener != null) {
            gsListener.gsDisconnected();
            if (!autoStartSignInFlow)
                gsListener.gsErrorMsg(IGameServiceListener.GsErrorType.errorLoginFailed,
                        amazonGamesStatus.name(), null);
        }
    }

    protected void agcServiceReady(AmazonGamesClient amazonGamesClient) {
        isConnectionPending = false;
        Gdx.app.log(GS_CLIENT_ID, "onServiceReady");
        agsClient = amazonGamesClient;
        agsClient.setPopUpLocation(PopUpLocation.TOP_CENTER);
        isConnected = true;

        // Request Alias
        AmazonGamesClient.getInstance().getPlayerClient().getLocalPlayer().setCallback(
                new AGResponseCallback<RequestPlayerResponse>() {
                    @Override
                    public void onComplete(final RequestPlayerResponse response) {
                        if (!response.isError()) {
                            cachedPlayerAlias = response.getPlayer().getAlias();
                            if (gsListener != null)
                                gsListener.gsConnected();
                        }
                    }
                }
        );

        if (whisperSyncEnabled)
            AmazonGamesClient.getWhispersyncClient().setWhispersyncEventListener(new WhispersyncEventListener() {
                public void onNewCloudData() {
                    loadedFromCloud = true;
                    Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "Game data from cloud synced to local.");
                }

                public void onDataUploadedToCloud() {
                    Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "Local game data synced to cloud.");
                }

                @Override
                public void onAlreadySynchronized() {
                    loadedFromCloud = true;
                    Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "Game data is up to date.");
                }

                @Override
                public void onSyncFailed(FailReason reason) {
                    loadedFromCloud = true;
                    Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "Game data not synced: " + reason.name());
                }
            });


        if (gsListener != null)
            gsListener.gsConnected();

    }

    @Override
    public String getGameServiceId() {
        return GS_CLIENT_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    @Override
    public boolean connect(boolean silent) {
        if (myContext == null || agsFeatures == null)
            throw new IllegalStateException("Call initialize() before connecting");

        if (isConnected())
            return true;

        isConnectionPending = true;
        autoStartSignInFlow = silent;

        Gdx.app.log(GS_CLIENT_ID, "Trying to initialize AmazonGamesClient");

        AmazonGamesClient.initialize(myContext, new AmazonGamesCallback() {
            @Override
            public void onServiceReady(AmazonGamesClient amazonGamesClient) {
                agcServiceReady(amazonGamesClient);
            }

            @Override
            public void onServiceNotReady(AmazonGamesStatus amazonGamesStatus) {
                agcServiceNotReady(amazonGamesStatus);
            }
        }, agsFeatures);

        return true;
    }

    @Override
    public void disconnect() {
        if (isConnected()) {
            Gdx.app.log(GS_CLIENT_ID, "Disconnecting from GameCircle");

            AmazonGamesClient.release();
            isConnected = false;

            if (gsListener != null)
                gsListener.gsDisconnected();
        }
    }

    @Override
    public void logOff() {
        if (isConnected()) {
            Gdx.app.log(GS_CLIENT_ID, "Shutting down GameCircle client");

            AmazonGamesClient.shutdown();
            cachedPlayerAlias = null;
            if (gsListener != null)
                gsListener.gsDisconnected();
        }
    }

    @Override
    public String getPlayerDisplayName() {
        return (isConnected() ? cachedPlayerAlias : null);
    }

    @Override
    public boolean isConnected() {
        return agsClient != null && AmazonGamesClient.isInitialized() && isConnected && !isConnectionPending;
    }

    @Override
    public boolean isConnectionPending() {
        return isConnectionPending;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        if (isConnected()) {
            if (leaderBoardId != null)
                agsClient.getLeaderboardsClient().showLeaderboardOverlay(leaderBoardId);
            else
                agsClient.getLeaderboardsClient().showLeaderboardsOverlay();
        } else
            throw new GameServiceException.NotConnectedException();
    }

    @Override
    public void showAchievements() throws GameServiceException {
        if (isConnected())
            agsClient.getAchievementsClient().showAchievementsOverlay();
        else
            throw new GameServiceException.NotConnectedException();
    }

    @Override
    public boolean fetchAchievements(IFetchAchievementsResponseListener callback) {
        //TODO supported by GameCircle
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        if (!leaderboardsEnabled || !isConnected())
            return false;

        agsClient.getLeaderboardsClient().submitScore(leaderboardId, score);
        return true;
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback) {
        //TODO supported by GameCircle
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        Gdx.app.debug(GS_CLIENT_ID, "Event " + eventId + " not logged (not supported by this service)");
        return false;
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        return incrementAchievement(achievementId, 0, 1f);
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (!achievementsEnabled || !isConnected())
            return false;

        agsClient.getAchievementsClient().updateProgress(achievementId, completionPercentage * 100);
        return true;
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue,
                              ISaveGameStateResponseListener listener) {
        if (!whisperSyncEnabled)
            throw new UnsupportedOperationException();

        //Whispersync caches and is asynchronous anyway
        saveGameStateSync(fileId, gameState, progressValue, listener);
    }

    public Boolean saveGameStateSync(String id, byte[] gameState, long progressValue,
                                     ISaveGameStateResponseListener listener) {
        if (!isConnected() || !whisperSyncEnabled)
            return false;

        GameDataMap gameDataMap = AmazonGamesClient.getWhispersyncClient().getGameData();

        SyncableString savedData = gameDataMap.getLatestString(id);
        SyncableNumber savedProgress = gameDataMap.getLatestNumber(id + "progress");

        if (!savedProgress.isSet() || savedProgress.asLong() <= progressValue) {
            savedData.set(new String(Base64Coder.encode(gameState)));
            savedProgress.set(progressValue);
            if (listener != null)
                listener.onGameStateSaved(true, null);
            return true;
        } else {
            Gdx.app.error(GameCircleClient.GS_CLIENT_ID, "Progress of saved game state higher than current one. Did " +
                    "not save.");
            if (listener != null)
                listener.onGameStateSaved(true, null);
            return false;
        }
    }

    @Override
    public void loadGameState(final String fileId, final ILoadGameStateResponseListener listener) {
        if (!whisperSyncEnabled)
            throw new UnsupportedOperationException();

        if (!isConnected()) {
            listener.gsGameStateLoaded(null);
            return;
        }

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return loadGameStateSync(fileId, listener);
            }
        };

        task.execute();
    }

    @Override
    public boolean deleteGameState(String fileId, ISaveGameStateResponseListener success) {
        //TODO supported by GameCircle
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
        //TODO supported by GameCircle
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        return feature.equals(GameServiceFeature.GameStateStorage) && whisperSyncEnabled
                || feature.equals(GameServiceFeature.GameStateMultipleFiles) && whisperSyncEnabled
                || feature.equals(GameServiceFeature.ShowAchievementsUI)
                || feature.equals(GameServiceFeature.ShowAllLeaderboardsUI)
                || feature.equals(GameServiceFeature.ShowLeaderboardUI);
    }

    protected boolean loadGameStateSync(String fileId, ILoadGameStateResponseListener listener) {
        if (!isConnected() || !whisperSyncEnabled) {
            listener.gsGameStateLoaded(null);
            return false;
        }

        // wait some time to get data loaded from cloud
        int maxWaitTime = 5000;
        if (!loadedFromCloud)
            Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "Waiting for cloud data to get synced...");

        while (!loadedFromCloud && maxWaitTime > 0) {
            SystemClock.sleep(100);
            maxWaitTime -= 100;
        }

        GameDataMap gameDataMap = AmazonGamesClient.getWhispersyncClient().getGameData();
        SyncableString savedData = gameDataMap.getLatestString(fileId);
        if (!savedData.isSet()) {
            Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "No data in whispersync for " + fileId);
            listener.gsGameStateLoaded(null);
            return false;
        } else {
            Gdx.app.log(GameCircleClient.GS_CLIENT_ID, "Loaded " + fileId + "from whispersync successfully.");
            listener.gsGameStateLoaded(Base64Coder.decode(savedData.getValue()));
            return true;
        }
    }

}
