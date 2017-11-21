package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;

/**
 * Gpgs Web REST Client
 * <p>
 * Do not forget to embed Googles's js lib in index.html for using this client, see
 * https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Google-Play-Games-(HTML)
 * <p>
 * see also https://developers.google.com/games/services/web/gettingstarted
 * <p>
 * Created by Benjamin Schulte on 03.11.2017.
 */

public class GpgsClient implements IGameServiceClient {
    public static final String GAMESERVICE_ID = IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;
    protected IGameServiceListener gsListener;
    protected IGameServiceIdMapper<Integer> statIdMapper;

    protected boolean initialized;
    protected boolean connectionPending;
    protected boolean enableDrive;
    protected String oAuthToken;
    private boolean isSilentConnect;
    private String clientId;
    private IGameServiceIdMapper<String> gpgsLeaderboardIdMapper;
    private IGameServiceIdMapper<String> gpgsAchievementIdMapper;
    private String displayName;

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

    @Override
    public String getGameServiceId() {
        return GAMESERVICE_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    public GpgsClient setStatIdMapper(IGameServiceIdMapper<Integer> statIds) {
        this.statIdMapper = statIds;
        return this;
    }

    /**
     * Initializes the GoogleApiClient. Give your main AndroidLauncher as context.
     * <p>
     * Don't forget to add onActivityResult method there with call to onGpgsActivityResult.
     *
     * @param clientId       OAuthID from API console
     * @param enableDriveAPI true if you activate save gamestate feature
     * @return this for method chunking
     */
    public GpgsClient initialize(String clientId, boolean enableDriveAPI) {

        if (initialized)
            throw new IllegalStateException("Already initialized.");

        this.clientId = clientId;
        this.enableDrive = enableDriveAPI;

        return this;
    }

    @Override
    public boolean resumeSession() {
        return connect(true);
    }

    @Override
    public boolean logIn() {
        return connect(false);
    }

    public boolean connect(boolean silent) {
        if (clientId == null)
            throw new IllegalStateException("Call initialize() and set client id before connecting to GPGS.");

        isSilentConnect = silent;
        if (!initialized && !connectionPending) {
            try {
                connectionPending = true;
                loadGApi(clientId, enableDrive);
                return true;
            } catch (Throwable t) {
                connectionPending = false;
                Gdx.app.error(GAMESERVICE_ID, "Could not initialize - Google api.js included in index.html? "
                        + t.getMessage());
                return false;
            }
        } else if (initialized && !silent && !isSessionActive()) {
            showGpgsLogin();
            return true;
        }
        return initialized;
    }

    protected void onInitialized() {
        initialized = true;
        connectionPending = false;
        // if Google API has initialized, check if user session is active
        displayName = "";
        boolean sessionActive = isSessionActive();
        if (sessionActive) {
            oAuthToken = getOAuthToken();
            sendNowPlayingEvent();
            refreshDisplayname();
        }

        if (gsListener != null) {
            if (sessionActive)
                gsListener.gsOnSessionActive();
            else
                gsListener.gsOnSessionInactive();
        }
    }

    private native String getOAuthToken() /*-{
        var user = $wnd.gapi.auth2.getAuthInstance().currentUser.get();
        return user.getAuthResponse().access_token;
    }-*/;

    private native void sendNowPlayingEvent() /*-{
        $wnd.gapi.client.request({
              path: 'games/v1/applications/played',
              method: 'POST',
              callback: function(response) {
                //nothing to do
              }
        });
    }-*/;

    private native void refreshDisplayname() /*-{
        var that = this;
        $wnd.gapi.client.request({
              path: 'games/v1/players/me',
              callback: function(response) {
                that.@de.golfgl.gdxgamesvcs.GpgsClient::onDisplayName(Ljava/lang/String;)(response.displayName);
              }
        });

    }-*/;

    protected void onDisplayName(String displayName) {
        this.displayName = displayName;
        if (gsListener != null)
            gsListener.gsOnSessionActive();
    }

    protected void onInitError(String msg) {
        initialized = false;
        connectionPending = false;
        if (gsListener != null) {
            gsListener.gsOnSessionInactive();
            if (!isSilentConnect)
                gsListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLoginFailed, msg, null);
        }
    }

    private native void loadGApi(String clientId, boolean enableDrive) /*-{
        var that = this;
        var scopes = 'https://www.googleapis.com/auth/games';
        if (enableDrive)
            scopes = scopes + ' https://www.googleapis.com/auth/drive.appdata';

        $wnd.gapi.load('client:auth2', function(){
            $wnd.gapi.client.init({
                    clientId: clientId,
                    scope: scopes
            }).then(function () {
                // Listen for sign-in state changes.
                $wnd.gapi.auth2.getAuthInstance().isSignedIn.listen(function(){
                   that.@de.golfgl.gdxgamesvcs.GpgsClient::onInitialized()();
                });

                // Handle the initial sign-in state.
                that.@de.golfgl.gdxgamesvcs.GpgsClient::onInitialized()();
                }, function(error) {
                  that.@de.golfgl.gdxgamesvcs.GpgsClient::onInitError(Ljava/lang/String;)(error.details);
              });
        });

    }-*/;


    private native void showGpgsLogin() /*-{
       $wnd.gapi.auth2.getAuthInstance().signIn();
    }-*/;


    @Override
    public void pauseSession() {
        //nothing to do
    }

    @Override
    public native void logOff() /*-{
        $wnd.gapi.auth2.getAuthInstance().signOut();
    }-*/;

    @Override
    public String getPlayerDisplayName() {
        //TODO
        return isSessionActive() ? displayName : null;
    }

    @Override
    public boolean isSessionActive() {
        return initialized && isSignedIn();
    }

    private native boolean isSignedIn() /*-{
        return $wnd.gapi.auth2.getAuthInstance().isSignedIn.get();
    }-*/;

    @Override
    public boolean isConnectionPending() {
        //TODO
        return false;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public void showAchievements() throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public boolean fetchAchievements(IFetchAchievementsResponseListener callback) {
        //TODO
        return false;
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        //TODO
        return false;
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback) {
        //TODO
        return false;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        //TODO
        return false;
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        //TODO
        return false;
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        //TODO
        return false;
    }

    @Override
    public void saveGameState(final String fileId, final byte[] gameState, long progressValue, final ISaveGameStateResponseListener success) {
        if (!enableDrive)
            throw new UnsupportedOperationException();

        if (!isSessionActive()) {
            if (success != null)
                success.onGameStateSaved(false, "NOT_CONNECTED");
            return;
        }

        findDriveFileId(fileId, new IDoWithDriveFileId() {
            @Override
            public void doWithDriveFileId(String driveFileId) {
                saveFileToDrive(fileId, driveFileId, gameState, success);
            }
        });
    }

    protected void saveFileToDrive(String fileName, String driveFileId, byte[] gameState, final ISaveGameStateResponseListener success) {
        String request = "--foo_bar_baz\n" +
                "Content-Type: application/json; charset=UTF-8\n" +
                "\n" +
                "{\"name\": \"" + fileName + "\", \"parents\": [\"appDataFolder\"]}\n" +
                "\n" +
                "--foo_bar_baz\n" +
                "Content-Type: application/octet-stream\n" +
                "\n" + new String(gameState) +
                "\n--foo_bar_baz--";

        Net.HttpRequest httpRequest;
        if (driveFileId == null) {
            // create new file
            httpRequest = new Net.HttpRequest(Net.HttpMethods.POST);
            httpRequest.setUrl("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart");
        } else {
            //v3 needs PATCH and PATCH is not supported by libgdx, so use v2 and PUT
            httpRequest = new Net.HttpRequest(Net.HttpMethods.PUT);
            httpRequest.setUrl("https://www.googleapis.com/upload/drive/v2/files/" + driveFileId + "?uploadType=multipart");
        }
        httpRequest.setHeader("Authorization", "Bearer " + oAuthToken);
        httpRequest.setHeader("Content-Type", "multipart/related; boundary=foo_bar_baz");
        //httpRequest.setHeader("Content-Length", String.valueOf(request.length()));
        httpRequest.setContent(request);
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                if (success != null)
                    success.onGameStateSaved(true, null);
            }

            @Override
            public void failed(Throwable t) {
                if (success != null)
                    success.onGameStateSaved(false, t.getMessage());
            }

            @Override
            public void cancelled() {
                if (success != null)
                    success.onGameStateSaved(true, "CANCELLED");
            }
        });
    }

    @Override
    public void loadGameState(String fileId, final ILoadGameStateResponseListener responseListener) {
        if (!enableDrive)
            throw new UnsupportedOperationException();

        if (!isSessionActive()) {
            responseListener.gsGameStateLoaded(null);
            return;
        }

        findDriveFileId(fileId, new IDoWithDriveFileId() {
            @Override
            public void doWithDriveFileId(String driveFileId) {
                if (driveFileId == null)
                    responseListener.gsGameStateLoaded(null);
                else
                    loadFileFromDrive(driveFileId, responseListener);
            }
        });
    }

    /**
     * finds a file with the given id (name) and calls dowithDriveFileId with the internal drive id, or null
     *
     * @param fileId            the file name
     * @param doWithDriveFileId callback interface
     */
    protected native void findDriveFileId(String fileId, IDoWithDriveFileId doWithDriveFileId) /*-{
        $wnd.gapi.client.request({
              path: 'drive/v3/files',
              params: {spaces: 'appDataFolder'},
              callback: function(response) {
                var driveFileId = null;
                if (response.files) {
                    response.files.forEach(function (file) {
                       if (file.name == fileId)
                          driveFileId = file.id;
                    });
                }
                doWithDriveFileId.@de.golfgl.gdxgamesvcs.GpgsClient.IDoWithDriveFileId::doWithDriveFileId(Ljava/lang/String;)(driveFileId);
              }
        });

    }-*/;

    protected void loadFileFromDrive(String driveFileId, final ILoadGameStateResponseListener responseListener) {
        Net.HttpRequest httpRequest = new Net.HttpRequest(Net.HttpMethods.GET);
        httpRequest.setUrl("https://content.googleapis.com/drive/v3/files/" + driveFileId + "?alt=media");
        httpRequest.setHeader("Authorization", "Bearer " + oAuthToken);
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                byte[] result = httpResponse.getResultAsString().getBytes();
                responseListener.gsGameStateLoaded(result);
            }

            @Override
            public void failed(Throwable t) {
                responseListener.gsGameStateLoaded(null);
            }

            @Override
            public void cancelled() {
                responseListener.gsGameStateLoaded(null);
            }
        });
    }


    @Override
    public boolean deleteGameState(String fileId, final ISaveGameStateResponseListener success) {
        if (!enableDrive)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        if (!isSessionActive())
            return false;

        findDriveFileId(fileId, new IDoWithDriveFileId() {
            @Override
            public void doWithDriveFileId(String driveFileId) {
                if (driveFileId == null)
                    success.onGameStateSaved(true, null);
                else {
                    Net.HttpRequest httpRequest = new Net.HttpRequest(Net.HttpMethods.DELETE);
                    httpRequest.setUrl("https://www.googleapis.com/drive/v3/files/" + driveFileId);
                    httpRequest.setHeader("Authorization", "Bearer " + oAuthToken);
                    Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
                        @Override
                        public void handleHttpResponse(Net.HttpResponse httpResponse) {
                            if (success != null)
                                success.onGameStateSaved(true, "");
                        }

                        @Override
                        public void failed(Throwable t) {
                            if (success != null)
                                success.onGameStateSaved(false, t.getMessage());
                        }

                        @Override
                        public void cancelled() {
                            if (success != null)
                                success.onGameStateSaved(true, "CANCELLED");
                        }
                    });

                }
            }
        });

        return true;
    }

    @Override
    public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
        if (!enableDrive)
            throw new UnsupportedOperationException("To use game states, enable Drive API when initializing");

        if (!isSessionActive())
            return false;

        try {
            nativeFetchGameStates(callback);
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    private native void nativeFetchGameStates(IFetchGameStatesListResponseListener callback) /*-{
        $wnd.gapi.client.request({
              path: 'drive/v3/files',
              params: {spaces: 'appDataFolder'},
              callback: function(response) {
                var stringarray;
                if (response.files) {
                    stringarray = @com.badlogic.gdx.utils.Array::new()();
                    response.files.forEach(function (file) {
                       stringarray.@com.badlogic.gdx.utils.Array::add(Ljava/lang/Object;)(file.name);
                    });
                } else
                    stringarray = null;
                callback.@de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener::onFetchGameStatesListResponse(Lcom/badlogic/gdx/utils/Array;)(stringarray);
              }
        });
    }-*/;

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case GameStateMultipleFiles:
            case GameStateDelete:
            case FetchGameStates:
                return enableDrive;
            default:
                return false;
        }
    }

    protected interface IDoWithDriveFileId {
        void doWithDriveFileId(String driveFileId);
    }
}
