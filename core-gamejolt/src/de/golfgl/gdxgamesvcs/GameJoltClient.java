package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpParametersUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * GameServiceClient for GameJolt API
 * <p>
 * See https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/GameJolt for documentation of this implementation.
 * <p>
 * See http://gamejolt.com/api/doc/game for GameJolt API documentation
 * <p>
 * Created by Benjamin Schulte on 17.06.2017.
 */

public class GameJoltClient implements IGameServiceClient {
    public static final String GAMESERVICE_ID = "GS_GAMEJOLT";
    public static final String GJ_GATEWAY = "http://gamejolt.com/api/game/v1/";
    public static final String GJ_USERNAME_PARAM = "gjapi_username";
    public static final String GJ_USERTOKEN_PARAM = "gjapi_token";

    //TODO: Sessions support with open/close/ping - http://gamejolt.com/api/doc/game/sessions

    protected IGameServiceListener gsListener;
    protected String userName;
    protected String userToken;
    protected String gjAppId;
    protected String gjAppPrivateKey;
    protected boolean connected;
    protected boolean connectionPending;
    protected boolean initialized;
    protected IGameServiceIdMapper<Integer> scoreTableMapper;
    protected IGameServiceIdMapper<Integer> trophyMapper;
    private String eventKeyPrefix;

    public void initialize(String gjAppId, String gjAppPrivateKey) {
        this.gjAppId = gjAppId;
        this.gjAppPrivateKey = gjAppPrivateKey;
        initialized = true;
    }

    /**
     * sets up the mapper for score table calls
     *
     * @param scoreTableMapper
     * @return this for method chaining
     */
    public GameJoltClient setGjScoreTableMapper(IGameServiceIdMapper<Integer> scoreTableMapper) {
        this.scoreTableMapper = scoreTableMapper;
        return this;
    }

    /**
     * sets up the mapper for trophy calls
     *
     * @param trophyMapper
     * @return this for method chaining
     */
    public GameJoltClient setGjTrophyMapper(IGameServiceIdMapper<Integer> trophyMapper) {
        this.trophyMapper = trophyMapper;
        return this;
    }


    public String getUserToken() {
        return userToken;
    }

    public GameJoltClient setUserToken(String userToken) {
        this.userToken = userToken;
        return this;
    }

    public GameJoltClient setUserName(String userName) {
        this.userName = userName;
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

    @Override
    public boolean connect(final boolean silent) {
        if (!initialized) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot connect before app ID is set via initialize()");
            return false;
        }

        if (connected)
            return true;

        if (userName == null || userToken == null) {
            //show UI via Gdx.input.getTextInput not possible in GWT w/o gdx-dialog.
            //to avoid a dependency and keep this simple, nothing is done here but
            //see the sample apps extension at https://github.com/MrStahlfelge/gdx-gamesvcs-app
            //GameJolt branch
            Gdx.app.log(GAMESERVICE_ID, "Cannot connect without user name and user's token.");

            return false;
        }

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);

        final Net.HttpRequest http = buildRequest("users/auth/", params);
        if (http == null)
            return false;

        connectionPending = true;

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                connectionPending = false;
                JsonValue response = null;
                String json = httpResponse.getResultAsString();
                try {
                    response = new JsonReader().parse(json).get("response");
                } catch (Throwable t) {
                    // eat
                }

                if (response == null) {
                    Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json);
                    authenticationFailed(silent, "Cannot authenticate. Response not in right format.");
                } else {
                    connected = response.getBoolean("success");

                    if (connected) {
                        //TODO Sessions: session could be opened here

                        if (gsListener != null)
                            gsListener.gsConnected();
                    } else {
                        Gdx.app.log(GAMESERVICE_ID, "Authentification from GameJolt failed. Check username, token, " +
                                "app id and private key.");
                        authenticationFailed(silent, "GameJolt authentication failed.");
                    }
                }
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.log(GAMESERVICE_ID, "Auth HTTP Request failed");
                authenticationFailed(silent, "Cannot connect to GameJolt due to network problems.");
            }

            @Override
            public void cancelled() {
                Gdx.app.log(GAMESERVICE_ID, "Auth HTTP Request cancelled.");
                authenticationFailed(silent, "Cannot connect to GameJolt. Request cancelled.");
            }
        });

        return true;
    }

    protected void authenticationFailed(boolean silent, String msg) {
        connected = false;
        connectionPending = false;

        if (gsListener != null) {
            gsListener.gsDisconnected();

            if (!silent)
                gsListener.gsErrorMsg(IGameServiceListener.GsErrorType.errorLoginFailed, msg);
        }
    }

    @Override
    public void disconnect() {
        //TODO sessions could be closed here (when opened)

        connected = false;

        if (gsListener != null)
            gsListener.gsDisconnected();
    }

    @Override
    public void logOff() {
        userName = null;
        userToken = null;
        disconnect();
    }

    @Override
    public String getPlayerDisplayName() {
        return (connected ? userName : null);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isConnectionPending() {
        return connectionPending && !connected;
    }

    @Override
    public boolean providesLeaderboardUI() {
        return false;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public boolean providesAchievementsUI() {
        return false;
    }

    @Override
    public void showAchievements() throws GameServiceException {
        throw new GameServiceException.NotSupportedException();
    }

    @Override
    public void submitToLeaderboard(String leaderboardId, long score, String tag) {
        //Interesting detail: GameJolt allows submitting scores without an open session.
        //This client does not implement it, but you can submit scores without giving username and token,
        //but with "guest" parameter instead
        //see http://gamejolt.com/api/doc/game/scores/add

        if (scoreTableMapper == null) {
            Gdx.app.log(GAMESERVICE_ID, "Cannot post score: No mapper for score table ids provided.");
            return;
        }

        Integer boardId = scoreTableMapper.mapToGsId(leaderboardId);

        // no board available or not connected
        if (boardId == null || !isConnected())
            return;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);
        params.put("score", String.valueOf(score));
        params.put("sort", String.valueOf(score));
        if (tag != null)
            params.put("extra_data", tag);
        params.put("table_id", boardId.toString());

        final Net.HttpRequest http = buildRequest("scores/add/", params);
        if (http == null)
            return;

        Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

    }

    /**
     * see {@link #getEventKeyPrefix()}
     *
     * @return
     */
    public String getEventKeyPrefix() {
        return eventKeyPrefix;
    }


    /**
     * GameJolt does not have a dedicated API for events, but it is encouraged by documentation to use the
     * global data store. Use this method to set a prefix to use for event keys for no conflicts with other
     * keys you are using.
     * <p>
     * Please note: GameJolt does not provide a user interface for reading the event stats. You have to write
     * your own program to read the event stats regularly.
     * <p>
     * You have to set the key yourself the first time. submitEvents performs an add operation on the key, which
     * fails when the key is not already created.
     *
     * @param eventKeyPrefix Your prefix for event keys, or null to deactivate using global data storage for events.
     *                       Default is null.
     */
    public void setEventKeyPrefix(String eventKeyPrefix) {
        this.eventKeyPrefix = eventKeyPrefix;
    }

    @Override
    public void submitEvent(String eventId, int increment) {

        if (!isConnected())
            return;

        if (eventKeyPrefix == null) {
            Gdx.app.log(GAMESERVICE_ID, "No event logged - no event key prefix provided.");
            return;
        }

        Map<String, String> params = new HashMap<String, String>();
        // no user name or token added! We want to use the global storage.
        // http://gamejolt.com/api/doc/game/data-store/update
        params.put("game_id", gjAppId);
        params.put("key", eventKeyPrefix + eventId);
        params.put("value", Integer.toString(increment));
        params.put("operation", "add");

        final Net.HttpRequest http = buildRequest("data-store/update/", params);
        if (http == null)
            return;

        Gdx.net.sendHttpRequest(http, new NoOpResponseListener());
    }

    @Override
    public void unlockAchievement(String achievementId) {
        if (trophyMapper == null) {
            Gdx.app.log(GAMESERVICE_ID, "Cannot unlock achievement: No mapper for trophy ids provided.");
            return;
        }

        if (!isConnected())
            return;

        Integer trophyId = trophyMapper.mapToGsId(achievementId);

        // no board available or not connected
        if (trophyId == null)
            return;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);
        params.put("trophy_id", String.valueOf(trophyId));

        final Net.HttpRequest http = buildRequest("trophies/add-achieved/", params);
        if (http == null)
            return;

        Gdx.net.sendHttpRequest(http, new NoOpResponseListener());
    }

    @Override
    public void incrementAchievement(String achievementId, int incNum) {
        // not supported - fall back
        unlockAchievement(achievementId);
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue) {
        //TODO - it is supported by Gamejolt, but not by this client

        throw new UnsupportedOperationException();
    }

    @Override
    public void loadGameState(String fileId) {
        //TODO - it is supported by Gamejolt, but not by this client

        throw new UnsupportedOperationException();
    }

    @Override
    public CloudSaveCapability supportsCloudGameState() {
        //TODO - it is supported by Gamejolt, but not by this client
        return CloudSaveCapability.NotSupported;
    }

    protected void addGameIDUserNameUserToken(Map<String, String> params) {
        params.put("game_id", String.valueOf(gjAppId));
        params.put("username", userName);
        params.put("user_token", userToken);
    }

    protected /* @Nullable */ Net.HttpRequest buildRequest(String component, Map<String, String> params) {
        String request = GJ_GATEWAY + component + "?format=json&";
        request += HttpParametersUtils.convertHttpParameters(params);

        /* Generate signature */
        final String signature;
        try {
            signature = md5(request + gjAppPrivateKey);
        } catch (Exception e) {
            /* Do not leak 'gamePrivateKey' in log */
            Gdx.app.error(GAMESERVICE_ID, "Cannot honor request: " + request, e);
            return null;
        }
        /* Append signature */
        String complete = request;
        complete += "&";
        complete += "signature";
        complete += "=";
        complete += signature;

        final Net.HttpRequest http = new Net.HttpRequest();
        http.setMethod(Net.HttpMethods.GET);
        http.setUrl(complete);

        return http;
    }

    protected String md5(String s) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        // Thanks to smelc/gdx-gamejolt for providing this code!
        // https://github.com/smelc/gdx-gamejolt & http://www.schplaf.org/hgames/

        final MessageDigest md = MessageDigest.getInstance("MD5");
        final byte[] bytes = s.getBytes("UTF-8");
        final byte[] digest = md.digest(bytes);
        /**
         * Magic to convert it into an String: an answer at the bottom of:
         *
         * <pre>
         * http://stackoverflow.com/questions/415953/how-can-i-generate-an-md5-hash
         * </pre>
         */
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < digest.length; ++i) {
            sb.append(Integer.toHexString((digest[i] & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString();
    }

    protected static class NoOpResponseListener implements Net.HttpResponseListener {
        @Override
        public void handleHttpResponse(Net.HttpResponse httpResponse) {
            Gdx.app.debug(GAMESERVICE_ID, httpResponse.getResultAsString());
        }

        @Override
        public void failed(Throwable t) {
            Gdx.app.log(GAMESERVICE_ID, t.getMessage(), t);
        }

        @Override
        public void cancelled() {

        }
    }
}
