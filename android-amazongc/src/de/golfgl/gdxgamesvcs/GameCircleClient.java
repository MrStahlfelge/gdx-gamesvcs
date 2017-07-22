package de.golfgl.gdxgamesvcs;

import android.app.Activity;
import android.util.Log;

import com.amazon.ags.api.AGResponseCallback;
import com.amazon.ags.api.AmazonGamesCallback;
import com.amazon.ags.api.AmazonGamesClient;
import com.amazon.ags.api.AmazonGamesFeature;
import com.amazon.ags.api.AmazonGamesStatus;
import com.amazon.ags.api.overlay.PopUpLocation;
import com.amazon.ags.api.player.RequestPlayerResponse;
import com.badlogic.gdx.Gdx;

import java.util.EnumSet;

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
    protected boolean whistleSyncEnabled;
    protected boolean achievementsEnabled;
    protected boolean leaderboardsEnabled;
    protected boolean autoStartSignInFlow;
    protected EnumSet<AmazonGamesFeature> agsFeatures;
    protected IGameServiceListener gsListener;
    protected String cachedPlayerAlias;

    public GameCircleClient setWhistleSyncEnabled(boolean whistleSyncEnabled) {
        this.whistleSyncEnabled = whistleSyncEnabled;
        return this;
    }

    public GameCircleClient setAchievementsEnabled(boolean achievementsEnabled) {
        this.achievementsEnabled = achievementsEnabled;
        return this;
    }

    public GameCircleClient setLeaderboardsEnabled(boolean leaderboardsEnabled) {
        this.leaderboardsEnabled = leaderboardsEnabled;
        return this;
    }

    public GameCircleClient intialize(Activity context) {
        if (agsClient != null || myContext != null)
            throw new IllegalStateException("Already initialized");

        agsFeatures = EnumSet.noneOf(AmazonGamesFeature.class);
        this.myContext = context;

        if (whistleSyncEnabled)
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

        if (gsListener != null) {
            gsListener.gsDisconnected();
            if (!autoStartSignInFlow)
                gsListener.gsErrorMsg(IGameServiceListener.GsErrorType.errorLoginFailed, amazonGamesStatus.name());
        }
    }

    protected void agcServiceReady(AmazonGamesClient amazonGamesClient) {
        isConnectionPending = false;
        Gdx.app.log(GS_CLIENT_ID, "onServiceReady");
        agsClient = amazonGamesClient;
        agsClient.setPopUpLocation(PopUpLocation.TOP_CENTER);

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

        if (AmazonGamesClient.isInitialized())
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
            Log.i(GS_CLIENT_ID, "Disconnecting from GameCircle");

            AmazonGamesClient.release();
            if (gsListener != null)
                gsListener.gsDisconnected();
        }
    }

    @Override
    public void logOff() {
        if (isConnected()) {
            Log.i(GS_CLIENT_ID, "Shutting down GameCircle client");

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
        return agsClient != null && AmazonGamesClient.isInitialized();
    }

    @Override
    public boolean isConnectionPending() {
        return isConnectionPending;
    }

    @Override
    public boolean providesLeaderboardUI() {
        return true;
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
    public boolean providesAchievementsUI() {
        return true;
    }

    @Override
    public void showAchievements() throws GameServiceException {
        if (isConnected())
            agsClient.getAchievementsClient().showAchievementsOverlay();
        else
            throw new GameServiceException.NotConnectedException();
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        if (!leaderboardsEnabled || !isConnected())
            return false;

        agsClient.getLeaderboardsClient().submitScore(leaderboardId, score);
        return true;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        Gdx.app.log(GS_CLIENT_ID, "Event " + eventId + " not logged (not supported by this service)");
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
    public void saveGameState(String fileId, byte[] gameState, long progressValue) throws GameServiceException {
        //TODO
    }

    @Override
    public void loadGameState(String fileId) throws GameServiceException {
        //TODO
    }

    @Override
    public CloudSaveCapability supportsCloudGameState() {
        //TODO
        return CloudSaveCapability.NotSupported;
    }
}
