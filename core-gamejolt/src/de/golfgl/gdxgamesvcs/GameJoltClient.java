package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpParametersUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Timer;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * GameServiceClient for GameJolt API
 * <p>
 * See https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/GameJolt for documentation of this implementation.
 * <p>
 * See http://gamejolt.com/api/doc/game for GameJolt API documentation
 * <p>
 * Some code taken from smelc/gdx-gamejolt - thanks for providing it!
 * https://github.com/smelc/gdx-gamejolt &amp; http://www.schplaf.org/hgames/
 * <p>
 * Created by Benjamin Schulte on 17.06.2017.
 */

public class GameJoltClient implements IGameServiceClient {
    public static final String GAMESERVICE_ID = IGameServiceClient.GS_GAMEJOLT_ID;
    public static final String GJ_USERNAME_PARAM = "gjapi_username";
    public static final String GJ_USERTOKEN_PARAM = "gjapi_token";
    protected static final int GJ_PING_INTERVAL = 30;

    // This is not static and not final for overriding reasons
    public String GJ_GATEWAY = "https://gamejolt.com/api/game/v1/";
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
    protected Timer.Task pingTask;
    private String eventKeyPrefix;
    private String guestName;

    /**
     * You need to call this basic initialization in order to call GameJolts
     *
     * @param gjAppId         your GameJolt App Id
     * @param gjAppPrivateKey your apps private key
     * @return this for method chaining
     */
    public GameJoltClient initialize(String gjAppId, String gjAppPrivateKey) {
        this.gjAppId = gjAppId;
        this.gjAppPrivateKey = gjAppPrivateKey;
        initialized = true;

        return this;
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

    /**
     * Sets the GameJolt user token. Not possible when connected!
     *
     * @param userToken
     * @return
     */
    public GameJoltClient setUserToken(String userToken) {
        if (isSessionActive())
            throw new IllegalStateException();

        this.userToken = userToken;
        return this;
    }

    /**
     * Sets the GameJolt user name. Not possible when connected!
     *
     * @param userName
     * @return
     */
    public GameJoltClient setUserName(String userName) {
        if (isSessionActive())
            throw new IllegalStateException();

        this.userName = userName;
        return this;
    }

    /**
     * see {@link #setGuestName(String)}
     *
     * @return
     */
    public String getGuestName() {
        return guestName;
    }

    /**
     * GameJolt can post scores to scoreboards without an authenticated user. Set a guest name to enable this featuee.
     *
     * @param guestName
     */
    public GameJoltClient setGuestName(String guestName) {
        this.guestName = guestName;

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
    public boolean resumeSession() {
        return connect(true);
    }

    @Override
    public boolean logIn() {
        return connect(false);
    }

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

        final Net.HttpRequest http = buildJsonRequest("users/auth/", params);
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
                    Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt", t);
                }

                if (response == null) {
                    Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json);
                    authenticationFailed(silent, "Cannot authenticate. Response not in right format.");
                } else {
                    connected = response.getBoolean("success");

                    if (connected) {
                        // Open a session
                        sendOpenSessionEvent();

                        if (gsListener != null)
                            gsListener.gsOnSessionActive(null);
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

    protected void sendOpenSessionEvent() {
        if (!isSessionActive())
            return;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);

        final Net.HttpRequest http = buildJsonRequest("sessions/open/", params);

        if (http != null)
            Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

        pingTask = Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                sendKeepSessionOpenEvent();
            }
        }, GJ_PING_INTERVAL, GJ_PING_INTERVAL);

    }

    protected void sendKeepSessionOpenEvent() {
        if (!isSessionActive())
            return;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);

        final Net.HttpRequest http = buildJsonRequest("sessions/ping/", params);

        if (http != null)
            Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

    }

    protected void authenticationFailed(boolean silent, String msg) {
        connected = false;
        connectionPending = false;

        if (gsListener != null) {
            gsListener.gsOnSessionInactive(null);

            if (!silent)
                gsListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLoginFailed, msg, null);
        }
    }

    @Override
    public void pauseSession() {
        if (pingTask != null)
            pingTask.cancel();

        sendCloseSessionEvent();

        connected = false;

        if (gsListener != null)
            gsListener.gsOnSessionInactive(null);
    }

    protected void sendCloseSessionEvent() {
        if (!isSessionActive())
            return;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);

        final Net.HttpRequest http = buildJsonRequest("sessions/close/", params);

        if (http != null)
            Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

    }


    @Override
    public void logOff() {
        pauseSession();
        userName = null;
        userToken = null;
    }

    @Override
    public String getPlayerDisplayName() {
        return (connected ? userName : null);
    }

    @Override
    public boolean getPlayerData(IPlayerDataResponseListener callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSessionActive() {
        return connected;
    }

    @Override
    public boolean isConnectionPending() {
        return connectionPending && !connected;
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
    public boolean fetchAchievements(final IFetchAchievementsResponseListener callback) {
        if (!isSessionActive())
            return false;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);

        final Net.HttpRequest http = buildJsonRequest("trophies/", params);

        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {

                JsonValue response = null;
                String json = httpResponse.getResultAsString();
                try {
                    response = new JsonReader().parse(json).get("response");

                    if (response == null || !response.getBoolean("success")) {
                        Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json);
                        callback.onFetchAchievementsResponse(null);
                    } else {

                        JsonValue trophies = response.get("trophies");
                        Array<IAchievement> achs = new Array<IAchievement>();
                        for (JsonValue trophy = trophies.child; trophy != null; trophy = trophy.next) {
                            IAchievement ach = achievementJsonToObject(trophy);
                            if (ach != null)
                                achs.add(ach);
                        }
                        callback.onFetchAchievementsResponse(achs);
                    }
                } catch (Throwable t) {
                    Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt " + json, t);
                    callback.onFetchAchievementsResponse(null);
                }
            }

            @Override
            public void failed(Throwable t) {
                callback.onFetchAchievementsResponse(null);
            }

            @Override
            public void cancelled() {
                callback.onFetchAchievementsResponse(null);
            }
        });

        return true;
    }

    protected GjTrophy achievementJsonToObject(JsonValue trophy) {
        GjTrophy ach = GjTrophy.fromJson(trophy);
        ach.setTrophyMapper(trophyMapper);
        return ach;
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        //GameJolt allows submitting scores without an open session.
        //Enable it by setting guest name.
        //see http://gamejolt.com/api/doc/game/scores/add

        if (!initialized) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot post score: set app ID via initialize()");
            return false;
        }
        if (scoreTableMapper == null) {
            Gdx.app.log(GAMESERVICE_ID, "Cannot post score: No mapper for score table ids provided.");
            return false;
        }

        Integer boardId = scoreTableMapper.mapToGsId(leaderboardId);

        // no board available
        if (boardId == null)
            return false;

        Map<String, String> params = new HashMap<String, String>();

        if (isSessionActive())
            addGameIDUserNameUserToken(params);
        else if (guestName != null) {
            params.put("game_id", gjAppId);
            params.put("guest", guestName);
        } else {
            Gdx.app.log(GAMESERVICE_ID, "Cannot post to scoreboard. No guest name and no user given.");
            return false;
        }
        params.put("score", String.valueOf(score));
        params.put("sort", String.valueOf(score));
        if (tag != null)
            params.put("extra_data", tag);
        params.put("table_id", boardId.toString());

        final Net.HttpRequest http = buildJsonRequest("scores/add/", params);
        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

        return true;
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           final IFetchLeaderBoardEntriesResponseListener callback,
                                           int timespan, int collection) {
        if (!initialized) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot fetch leaderboard: set app ID via initialize() first");
            return false;
        }

        Map<String, String> params = new HashMap<String, String>();
        // http://gamejolt.com/api/doc/game/scores/fetch
        if (relatedToPlayer && isSessionActive())
            addGameIDUserNameUserToken(params);
        else
            params.put("game_id", gjAppId);

        params.put("limit", String.valueOf(limit));

        if (leaderBoardId != null) {
            Integer boardId = scoreTableMapper.mapToGsId(leaderBoardId);
            if (boardId != null)
                params.put("table_id", String.valueOf(boardId));
        }

        final Net.HttpRequest http = buildJsonRequest("scores/", params);
        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {

                JsonValue response = null;
                String json = httpResponse.getResultAsString();
                try {
                    response = new JsonReader().parse(json).get("response");

                    if (response == null || !response.getBoolean("success")) {
                        Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json);
                        callback.onLeaderBoardResponse(null);
                    } else {
                        JsonValue scores = response.get("scores");
                        int rank = 0;
                        Array<ILeaderBoardEntry> les = new Array<ILeaderBoardEntry>();
                        for (JsonValue score = scores.child; score != null; score = score.next) {
                            rank++;
                            ILeaderBoardEntry gje = scoreJsonToObject(rank, score);
                            if (gje != null)
                                les.add(gje);
                        }
                        callback.onLeaderBoardResponse(les);
                    }
                } catch (Throwable t) {
                    Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json, t);
                    callback.onLeaderBoardResponse(null);
                }
            }

            @Override
            public void failed(Throwable t) {
                callback.onLeaderBoardResponse(null);
            }

            @Override
            public void cancelled() {
                callback.onLeaderBoardResponse(null);
            }
        });

        return true;
    }

    /**
     * converts GameJolt's scoreboard return json to our own data type. This method is for overriding purposes
     */
    protected ILeaderBoardEntry scoreJsonToObject(int rank, JsonValue score) {
        return GjScoreboardEntry.fromJson(score, rank, getPlayerDisplayName());
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
    public GameJoltClient setEventKeyPrefix(String eventKeyPrefix) {
        this.eventKeyPrefix = eventKeyPrefix;
        return this;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {

        if (!initialized) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot submit event: set app ID via initialize() first");
            return false;
        }
        if (eventKeyPrefix == null) {
            Gdx.app.log(GAMESERVICE_ID, "No event logged - no event key prefix provided.");
            return false;
        }

        Map<String, String> params = new HashMap<String, String>();
        // no user name or token added! We want to use the global storage.
        // http://gamejolt.com/api/doc/game/data-store/update
        params.put("game_id", gjAppId);
        params.put("key", eventKeyPrefix + eventId);
        params.put("value", Integer.toString(increment));
        params.put("operation", "add");

        final Net.HttpRequest http = buildJsonRequest("data-store/update/", params);
        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

        return true;
    }

    /**
     * Use careful! It resets your event to 0. Needed for first time initialization.
     *
     * @param eventId
     */
    public void initializeOrResetEventKey(String eventId) {
        if (!initialized) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot submit event: set app ID via initialize() first");
            return;
        }
        if (eventKeyPrefix == null) {
            Gdx.app.log(GAMESERVICE_ID, "No event key prefix provided.");
            return;
        }

        // no user name or token added! We want to use the global storage.
        // http://gamejolt.com/api/doc/game/data-store/set
        Net.HttpRequest http = buildStoreDataRequest(eventKeyPrefix + eventId, true, "0");

        if (http != null)
            Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        if (trophyMapper == null) {
            Gdx.app.log(GAMESERVICE_ID, "Cannot unlock achievement: No mapper for trophy ids provided.");
            return false;
        }

        if (!isSessionActive())
            return false;

        Integer trophyId = trophyMapper.mapToGsId(achievementId);

        // no board available or not connected
        if (trophyId == null)
            return false;

        Map<String, String> params = new HashMap<String, String>();
        addGameIDUserNameUserToken(params);
        params.put("trophy_id", String.valueOf(trophyId));

        final Net.HttpRequest http = buildJsonRequest("trophies/add-achieved/", params);
        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new NoOpResponseListener());

        return true;
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        // not supported - fall back
        if (completionPercentage >= 1f)
            return unlockAchievement(achievementId);
        else
            return true;
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue,
                              final ISaveGameStateResponseListener listener) {
        if (!isSessionActive()) {
            if (listener != null)
                listener.onGameStateSaved(false, "NOT_CONNECTED");
            return;
        }

        //TODO progressValue is saved for future use, but should be checked before overwriting existing values

        Net.HttpRequest http = buildStoreDataRequest(fileId, false,
                Long.toString(progressValue) + "\n" + new String(Base64Coder.encode(gameState)));

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String json = httpResponse.getResultAsString();
                boolean success = parseSuccessFromResponse(json);

                if (!success)
                    Gdx.app.error(GAMESERVICE_ID, "Error saving gamestate: " + json);

                if (listener != null)
                    listener.onGameStateSaved(success, null);
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error(GAMESERVICE_ID, "Error saving gamestate", t);
                if (listener != null)
                    listener.onGameStateSaved(false, null);
            }

            @Override
            public void cancelled() {
                Gdx.app.error(GAMESERVICE_ID, "Error saving gamestate: Cancelled");
                if (listener != null)
                    listener.onGameStateSaved(false, null);
            }
        });
    }

    /**
     * Helper method when just interested if GameJolt request was successful
     */
    protected boolean parseSuccessFromResponse(String json) {
        JsonValue response = null;
        boolean success;
        try {
            response = new JsonReader().parse(json).get("response");
            success = response != null && response.getBoolean("success");
        } catch (Throwable t) {
            Gdx.app.error(GAMESERVICE_ID, "Cannot parse GameJolt response: " + json, t);
            success = false;
        }
        return success;
    }

    @Override
    public boolean deleteGameState(String fileId, final ISaveGameStateResponseListener successListener) {
        if (!isSessionActive())
            return false;

        Map<String, String> params = new HashMap<String, String>();

        addGameIDUserNameUserToken(params);
        params.put("key", fileId);

        final Net.HttpRequest http = buildJsonRequest("data-store/remove/", params);
        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String json = httpResponse.getResultAsString();
                boolean success = parseSuccessFromResponse(json);

                // just log, no error because deleting a nonexistant gamestate fails but is no error
                if (!success)
                    Gdx.app.log(GAMESERVICE_ID, "Failed to delete gamestate: " + json);

                if (successListener != null)
                    successListener.onGameStateSaved(success, null);
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error(GAMESERVICE_ID, "Error deleting gamestate", t);
                if (successListener != null)
                    successListener.onGameStateSaved(false, null);
            }

            @Override
            public void cancelled() {
                Gdx.app.error(GAMESERVICE_ID, "Error deleting gamestate: Cancelled");
                if (successListener != null)
                    successListener.onGameStateSaved(false, null);
            }
        });

        return true;
    }

    @Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        if (!isSessionActive())
            return false;

        Map<String, String> params = new HashMap<String, String>();

        addGameIDUserNameUserToken(params);

        final Net.HttpRequest http = buildJsonRequest("data-store/get-keys/", params);
        if (http == null)
            return false;

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                JsonValue response = null;
                String json = httpResponse.getResultAsString();
                try {
                    response = new JsonReader().parse(json).get("response");

                    if (response == null || !response.getBoolean("success")) {
                        Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json);
                        callback.onFetchGameStatesListResponse(null);
                    } else {

                        JsonValue keysObj = response.get("keys");
                        Array<String> keysArr = new Array<String>();
                        for (JsonValue keyObj = keysObj.child; keyObj != null; keyObj = keyObj.next) {
                            keysArr.add(keyObj.getString("key"));
                        }
                        callback.onFetchGameStatesListResponse(keysArr);
                    }
                } catch (Throwable t) {
                    Gdx.app.error(GAMESERVICE_ID, "Could not parse answer from GameJolt: " + json, t);
                    callback.onFetchGameStatesListResponse(null);
                }
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error(GAMESERVICE_ID, "Error fetching gamestates", t);
                callback.onFetchGameStatesListResponse(null);
            }

            @Override
            public void cancelled() {
                Gdx.app.error(GAMESERVICE_ID, "Error fetching gamestates: Cancelled");
                callback.onFetchGameStatesListResponse(null);
            }
        });

        return true;
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case GameStateMultipleFiles:
            case SubmitEvents:
            case FetchLeaderBoardEntries:
            case FetchAchievements:
            case GameStateDelete:
            case FetchGameStates:
            case PlayerLogOut:
                return true;
            default:
                return false;
        }
    }

    /**
     * content must be without special chars ampersand or question mark - use Base64 when not sure!
     */
    protected Net.HttpRequest buildStoreDataRequest(String dataKey, boolean globalKey, String content) {
        Map<String, String> params = new HashMap<String, String>();

        if (globalKey)
            params.put("game_id", gjAppId);
        else
            addGameIDUserNameUserToken(params);
        params.put("key", dataKey);

        final Net.HttpRequest http = buildJsonRequest("data-store/set/", params);
        if (http == null)
            return null;

        http.setMethod(Net.HttpMethods.POST);
        http.setHeader("Content-Type", "application/x-www-form-urlencoded");
        http.setContent("data=" + content);

        return http;
    }

    @Override
    public void loadGameState(String fileId, final ILoadGameStateResponseListener listener) {
        if (!isSessionActive()) {
            listener.gsGameStateLoaded(null);
            return;
        }

        Net.HttpRequest http = buildLoadDataRequest(fileId, false);

        Gdx.net.sendHttpRequest(http, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String response = httpResponse.getResultAsString();

                if (response == null || !response.startsWith("SUCCESS")) {
                    // just log, no error because loading a nonexistant gamestate fails but is no error
                    Gdx.app.log(GAMESERVICE_ID, "Gamestate load failed: " + response);
                    listener.gsGameStateLoaded(null);
                } else {
                    // indexOf is twice to cut first two lines. First one is success message,
                    // second one is progressValue
                    byte[] gs = Base64Coder.decode(response.substring(
                            response.indexOf('\n', response.indexOf('\n') + 1) + 1));
                    listener.gsGameStateLoaded(gs);
                }
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error(GAMESERVICE_ID, "Gamestate load failed", t);
                listener.gsGameStateLoaded(null);
            }

            @Override
            public void cancelled() {
                Gdx.app.error(GAMESERVICE_ID, "Gamestate load cancelled");

                listener.gsGameStateLoaded(null);
            }
        });
    }

    /**
     * Load data is done with dump format
     */
    protected Net.HttpRequest buildLoadDataRequest(String dataKey, boolean globalKey) {
        Map<String, String> params = new HashMap<String, String>();

        if (globalKey)
            params.put("game_id", gjAppId);
        else
            addGameIDUserNameUserToken(params);
        params.put("key", dataKey);

        final Net.HttpRequest http = buildRequest("data-store/?format=dump&", params);

        return http;

    }

    protected void addGameIDUserNameUserToken(Map<String, String> params) {
        params.put("game_id", String.valueOf(gjAppId));
        params.put("username", userName);
        params.put("user_token", userToken);
    }

    protected /* @Nullable */ Net.HttpRequest buildJsonRequest(String component, Map<String, String> params) {
        component = component + "?format=json&";
        return buildRequest(component, params);
    }

    protected Net.HttpRequest buildRequest(String component, Map<String, String> params) {
        String request = GJ_GATEWAY + component;
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
