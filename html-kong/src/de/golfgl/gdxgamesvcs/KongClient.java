package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * Kongegrate Client
 * <p>
 * Do not forget to embed Kongregate's js lib in index.html for using this client, see
 * https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Kongregate
 * <p>
 * see also http://docs.kongregate.com/docs/javascript-api and
 * http://developers.kongregate.com/docs/api-overview/client-api
 * <p>
 * Created by Benjamin Schulte on 25.06.2017.
 */

public class KongClient implements IGameServiceClient {
    public static final String GAMESERVICE_ID = IGameServiceClient.GS_KONGREGATE_ID;
    protected IGameServiceListener gsListener;
    protected IGameServiceIdMapper<Integer> statIdMapper;

    protected boolean initialized;
    protected boolean connectionPending;

    @Override
    public String getGameServiceId() {
        return GAMESERVICE_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    public KongClient setStatIdMapper(IGameServiceIdMapper<Integer> statIds) {
        this.statIdMapper = statIds;
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
        if (!initialized && !connectionPending) {
            try {
                connectionPending = true;
                loadKongApi();
            } catch (Throwable t) {
                connectionPending = false;
                Gdx.app.error(GAMESERVICE_ID, "Could not initialize - kongregate_api.js included in index.html? "
                        + t.getMessage());
            }
        } else if (initialized && !silent && isKongGuest())
            showKongLogin();

        return initialized;
    }

    protected void onInitialized() {
        initialized = true;
        connectionPending = false;
        // if Kong API has initialized, check if user session is active
        if (gsListener != null) {
            if (!isKongGuest())
                gsListener.gsOnSessionActive(null);
            else
                gsListener.gsOnSessionInactive(null);
        }
    }

    private native void loadKongApi() /*-{
        var that = this;
        $wnd.kongregateAPI.loadAPI(function(){
            $wnd.kongregate = $wnd.kongregateAPI.getAPI();
            that.@de.golfgl.gdxgamesvcs.KongClient::onInitialized()();

            $wnd.kongregate.services.addEventListener('login', function(){
               that.@de.golfgl.gdxgamesvcs.KongClient::onInitialized()();
            });
        });
    }-*/;

    private native void showKongLogin() /*-{
       $wnd.kongregate.services.showRegistrationBox()
    }-*/;

    @Override
    public void pauseSession() {
        //nothing to do
    }

    @Override
    public void logOff() {
        //nothing to do, inform the user to log out via web interface
        if (gsListener != null)
            gsListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLogoutFailed,
                    "Please logout via Kongregate's interface", null);
    }

    @Override
    public String getPlayerDisplayName() {
        if (!initialized || isKongGuest())
            return null;
        else
            return getKongPlayerName();
    }

    @Override
    public boolean getPlayerData(IPlayerDataResponseListener callback) {
        throw new UnsupportedOperationException();
    }

    private native boolean isKongGuest() /*-{
        return $wnd.kongregate.services.isGuest();
    }-*/;

    private native String getKongPlayerName() /*-{
        return $wnd.kongregate.services.getUsername();
    }-*/;

    public int getUserId() {
        if (!initialized || isKongGuest())
            return 0;
        else
            return getKongPlayerId();
    }

    private native int getKongPlayerId() /*-{
        return $wnd.kongregate.services.getUserId();
    }-*/;

    @Override
    public boolean isSessionActive() {
        return initialized && !isKongGuest();
    }

    @Override
    public boolean isConnectionPending() {
        return connectionPending && !initialized;
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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
        try {
            if (initialized)
                submitKongStat(leaderboardId, (int) score);

            return initialized;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, final int limit, boolean relatedToPlayer,
                                           final IFetchLeaderBoardEntriesResponseListener callback,
                                           int timespan, int collection) {
        //this does not work without hosting an own webservice, thus isFeatureSupported does not report it as supported
        //See issue #13 https://github.com/MrStahlfelge/gdx-gamesvcs/issues/13 for more information

        if (statIdMapper == null)
            throw new IllegalStateException("Call setStatIdMapper before querying stats");

        Integer statId = statIdMapper.mapToGsId(leaderBoardId);

        if (statId == null)
            return false;

        Gdx.net.sendHttpRequest(buildQueryStatRequest(statId, relatedToPlayer),
                new Net.HttpResponseListener() {
                    @Override
                    public void handleHttpResponse(Net.HttpResponse httpResponse) {
                        JsonValue response = null;
                        String json = httpResponse.getResultAsString();
                        // looks like a CORS error when fetching :-(
                        try {
                            response = new JsonReader().parse(json).get(0);
                            int rank = 0;
                            Array<ILeaderBoardEntry> le = new Array<ILeaderBoardEntry>();
                            for (JsonValue statEntry = response.child; statEntry != null && rank < limit;
                                 statEntry = statEntry.next) {
                                rank++;
                                KongStatEntry kse = new KongStatEntry();
                                kse.username = statEntry.getString("username");
                                kse.currentPlayer = kse.username.equalsIgnoreCase(getPlayerDisplayName());
                                kse.avatarUrl = statEntry.getString("avatar_url");
                                kse.score = statEntry.getLong("score");
                                kse.rank = Integer.toString(rank);

                                le.add(kse);
                            }

                            callback.onLeaderBoardResponse(le);

                        } catch (Throwable t) {
                            Gdx.app.error(GAMESERVICE_ID, "Error querying stats " + json, t);
                            callback.onLeaderBoardResponse(null);
                        }
                    }

                    @Override
                    public void failed(Throwable t) {
                        Gdx.app.error(GAMESERVICE_ID, "Query stat failed", t);
                        callback.onLeaderBoardResponse(null);
                    }

                    @Override
                    public void cancelled() {
                        Gdx.app.error(GAMESERVICE_ID, "Query stat cancelled");
                        callback.onLeaderBoardResponse(null);
                    }
                });

        return true;
    }

    /**
     * Override this method for tunneling through own server or other needs
     *
     * @param statId the stat id
     * @param playerRelated is the requested results related to the player
     *
     * @return the resulting HTTP request
     */
    protected Net.HttpRequest buildQueryStatRequest(Integer statId, boolean playerRelated) {
        String url = "https://api.kongregate.com/api/high_scores/" +
                (playerRelated && isSessionActive() ? "friends/" + statId.toString() + "/" + Integer.toString(getUserId())
                        : "https://api.kongregate.com/api/high_scores/lifetime/" + statId.toString())
                + ".json";

        Net.HttpRequest http = new Net.HttpRequest();
        http.setMethod(Net.HttpMethods.GET);
        http.setUrl(url);

        return http;
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        if (initialized)
            submitKongStat(eventId, increment);

        return initialized;
    }

    private native void submitKongStat(String id, int num) /*-{
        $wnd.kongregate.stats.submit(id, num);
    }-*/;

    @Override
    public boolean unlockAchievement(String achievementId) {
        return incrementAchievement(achievementId, 1, 1f);
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (initialized)
            submitKongStat(achievementId, incNum);

        return initialized;
    }

    @Override
    public void saveGameState(String fileId, byte[] gameState, long progressValue, ISaveGameStateResponseListener
            listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadGameState(String fileId, ILoadGameStateResponseListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteGameState(String fileId, ISaveGameStateResponseListener success) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        //this does not list FetchLeaderBoardEntries because it does not work out of the box
        //See issue #13 https://github.com/MrStahlfelge/gdx-gamesvcs/issues/13 for more information
        return feature.equals(GameServiceFeature.SubmitEvents);
    }
}
