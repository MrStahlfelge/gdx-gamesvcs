package de.golfgl.gdxgamesvcs;

import android.content.Intent;
import android.text.TextUtils;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidEventListener;
import com.badlogic.gdx.utils.Array;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.jos.JosApps;
import com.huawei.hms.jos.JosAppsClient;
import com.huawei.hms.jos.games.AchievementsClient;
import com.huawei.hms.jos.games.ArchivesClient;
import com.huawei.hms.jos.games.EventsClient;
import com.huawei.hms.jos.games.GameScopes;
import com.huawei.hms.jos.games.Games;
import com.huawei.hms.jos.games.PlayersClient;
import com.huawei.hms.jos.games.RankingsClient;
import com.huawei.hms.jos.games.achievement.Achievement;
import com.huawei.hms.jos.games.archive.Archive;
import com.huawei.hms.jos.games.archive.ArchiveDetails;
import com.huawei.hms.jos.games.archive.ArchiveSummary;
import com.huawei.hms.jos.games.archive.ArchiveSummaryUpdate;
import com.huawei.hms.jos.games.archive.OperationResult;
import com.huawei.hms.jos.games.player.Player;
import com.huawei.hms.support.api.entity.auth.Scope;
import com.huawei.hms.support.hwid.HuaweiIdAuthManager;
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams;
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper;
import com.huawei.hms.support.hwid.result.AuthHuaweiId;
import com.huawei.hms.support.hwid.result.HuaweiIdAuthResult;
import com.huawei.hms.support.hwid.service.HuaweiIdAuthService;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;
import de.golfgl.gdxgamesvcs.player.IPlayerDataResponseListener;

/**
 * Client for Huawei Game Services
 * <p>
 * Created by Francesco Stranieri on 24.07.2020.
 */
public class HuaweiGameServicesClient implements IGameServiceClient, AndroidEventListener {

    private final int HUAWEI_GAMESVCS_AUTH_REQUEST = 8971;
    private final int HUAWEI_GAMESVCS_ACHIEVEMENTS_REQUEST = 8972;
    private final int HUAWEI_GAMESVCS_LEADERBOARDS_REQUEST = 8973;

    private AndroidApplication activity;
    private IGameServiceIdMapper<String> huaweiLeaderboardIdMapper;
    private IGameServiceIdMapper<String> huaweiAchievementIdMapper;
    private IGameServiceIdMapper<String> huaweiGameEventIdMapper;
    protected JosAppsClient josAppsClient;
    protected AchievementsClient achievementsClient;
    protected RankingsClient leaderboardsClient;
    protected EventsClient eventsClient;
    protected ArchivesClient archivesClient;
    private IGameServiceListener gsListener;
    private Player currentPlayer;

    private boolean isSaveDataEnabled;
    private boolean isSessionActive = false;
    private boolean isSessionPending = false;

    private int currentLeaderboardsStatus = HuaweiGameServicesConstants.HUAWEI_GAMESVCS_LEADERBOARDS_DISABLED;

    public HuaweiGameServicesClient(AndroidApplication activity, boolean isSaveDataEnabled) {
        this.activity = activity;
        this.isSaveDataEnabled = isSaveDataEnabled;
        this.activity.addAndroidEventListener(this);
        this.josAppsClient = JosApps.getJosAppsClient(this.activity);
        this.josAppsClient.init();
        this.achievementsClient = Games.getAchievementsClient(this.activity);
        this.leaderboardsClient = Games.getRankingsClient(this.activity);
        this.eventsClient = Games.getEventsClient(this.activity);
        this.archivesClient = Games.getArchiveClient(this.activity);
    }

    /**
     * sets up the mapper for leaderboard ids
     *
     * @param huaweiLeaderboardIdMapper
     * @return this for method chaining
     */
    public HuaweiGameServicesClient setHuaweiLeaderboardIdMapper(IGameServiceIdMapper<String> huaweiLeaderboardIdMapper) {
        this.huaweiLeaderboardIdMapper = huaweiLeaderboardIdMapper;
        return this;
    }

    /**
     * sets up the mapper for achievement ids
     *
     * @param huaweiAchievementIdMapper
     * @return this for method chaining
     */
    public HuaweiGameServicesClient setHuaweiAchievementIdMapper(IGameServiceIdMapper<String> huaweiAchievementIdMapper) {
        this.huaweiAchievementIdMapper = huaweiAchievementIdMapper;
        return this;
    }

    /**
     * sets up the mapper for game event ids
     *
     * @param huaweiGameEventIdMapper
     * @return this for method chaining
     */
    public HuaweiGameServicesClient setHuaweiGameEventIdMapper(IGameServiceIdMapper<String> huaweiGameEventIdMapper) {
        this.huaweiGameEventIdMapper = huaweiGameEventIdMapper;
        return this;
    }

    @Override
    public String getGameServiceId() {
        return IGameServiceClient.GS_HUAWEI_ID;
    }

    @Override
    public void setListener(IGameServiceListener gsListener) {
        this.gsListener = gsListener;
    }

    @Override
    public boolean resumeSession() {
        return logIn();
    }

    private void sendError(IGameServiceListener.GsErrorType type, String msg, Throwable t) {
        if (gsListener != null) {
            gsListener.gsShowErrorToUser(type, msg, t);
        }
    }

    private HuaweiIdAuthParams getHuaweiIdParams() {
        List<Scope> scopes = new ArrayList<>();

        if (this.isSaveDataEnabled) {
            scopes.add(GameScopes.DRIVE_APP_DATA);
        }

        return new HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM_GAME)
                .setScopeList(scopes)
                .createParams();
    }

    @Override
    public boolean logIn() {
        if (!this.isSessionActive && !this.isSessionPending) {
            this.isSessionPending = true;

            //try with silentSignIn. If It fails for missing account/verification, It will forward to the Huawei signIn/signUp page.
            Task<AuthHuaweiId> authHuaweiIdTask = HuaweiIdAuthManager
                    .getService(this.activity, getHuaweiIdParams())
                    .silentSignIn();

            authHuaweiIdTask.addOnSuccessListener(new OnSuccessListener<AuthHuaweiId>() {
                @Override
                public void onSuccess(AuthHuaweiId authHuaweiId) {
                    loadPlayerInfo();
                    switchLeaderboardsStatus(HuaweiGameServicesConstants.HUAWEI_GAMESVCS_LEADERBOARDS_ENABLED);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    isSessionPending = false;
                    if (e instanceof ApiException) {
                        //Sign in explicitly. The sign-in result is obtained in onActivityResult.
                        HuaweiIdAuthService service = HuaweiIdAuthManager.getService(activity, getHuaweiIdParams());
                        activity.startActivityForResult(service.getSignInIntent(), HUAWEI_GAMESVCS_AUTH_REQUEST);
                    } else {
                        sendError(IGameServiceListener.GsErrorType.errorLoginFailed, e.getMessage(), e);
                    }
                }
            });

            return true;
        }

        return false;
    }

    @Override
    public void pauseSession() {
    }

    @Override
    public void logOff() {
        if (this.isSessionActive) {
            switchLeaderboardsStatus(HuaweiGameServicesConstants.HUAWEI_GAMESVCS_LEADERBOARDS_DISABLED);

            Task<Void> authHuaweiIdTask = HuaweiIdAuthManager
                    .getService(this.activity, getHuaweiIdParams())
                    .signOut();

            authHuaweiIdTask.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void v) {
                    isSessionActive = false;
                    currentPlayer = null;
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    sendError(IGameServiceListener.GsErrorType.errorLogoutFailed, e.getMessage(), e);
                }
            });
        }
    }

    @Override
    public String getPlayerDisplayName() {
        if (this.currentPlayer != null) {
            return this.currentPlayer.getDisplayName();
        }

        return null;
    }

    @Override
    public boolean getPlayerData(IPlayerDataResponseListener callback) {
        return false;
    }

    @Override
    public boolean isSessionActive() {
        return this.isSessionActive;
    }

    @Override
    public boolean isConnectionPending() {
        return this.isSessionPending;
    }

    private void switchLeaderboardsStatus(int newStatus) {
        if (newStatus != this.currentLeaderboardsStatus) {
            Task<Integer> task = this.leaderboardsClient.setRankingSwitchStatus(newStatus);
            task.addOnSuccessListener(new OnSuccessListener<Integer>() {
                @Override
                public void onSuccess(Integer statusValue) {
                    currentLeaderboardsStatus = statusValue;
                }
            });
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
                }
            });
        }
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        Task<Intent> task;

        if (!TextUtils.isEmpty(leaderBoardId)) {
            if (this.huaweiLeaderboardIdMapper != null) {
                leaderBoardId = huaweiLeaderboardIdMapper.mapToGsId(leaderBoardId);
            }

            task = this.leaderboardsClient.getRankingIntent(leaderBoardId);
        } else {
            task = this.leaderboardsClient.getTotalRankingsIntent();
        }

        task.addOnSuccessListener(new OnSuccessListener<Intent>() {
            @Override
            public void onSuccess(Intent intent) {
                try {
                    activity.startActivityForResult(intent, HUAWEI_GAMESVCS_LEADERBOARDS_REQUEST);
                } catch (Exception e) {
                    sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    @Override
    public void showAchievements() throws GameServiceException {
        if (this.isSessionActive) {
            Task<Intent> task = this.achievementsClient.getShowAchievementListIntent();
            task.addOnSuccessListener(new OnSuccessListener<Intent>() {
                @Override
                public void onSuccess(Intent intent) {
                    try {
                        activity.startActivityForResult(intent, HUAWEI_GAMESVCS_ACHIEVEMENTS_REQUEST);
                    } catch (Exception e) {
                        sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
                }
            });
        } else {
            throw new GameServiceException.NoSessionException();
        }
    }

    @Override
    public boolean fetchAchievements(final IFetchAchievementsResponseListener callback) {
        if (!this.isSessionActive) {
            return false;
        }

        Task<List<Achievement>> task = this.achievementsClient.getAchievementList(true);
        task.addOnSuccessListener(new OnSuccessListener<List<Achievement>>() {
            @Override
            public void onSuccess(List<Achievement> data) {
                if (data == null) {
                    sendError(IGameServiceListener.GsErrorType.errorUnknown,
                            "data is null", new NullPointerException());
                    return;
                }
                Array<IAchievement> achievements = HuaweiGameServicesUtils.getIAchievementsList(data);
                callback.onFetchAchievementsResponse(achievements);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
        return true;
    }

    @Override
    public boolean submitToLeaderboard(String leaderboardId, long score, String scoreTips) {
        if (!this.isSessionActive) {
            return false;
        }

        if (this.huaweiLeaderboardIdMapper != null) {
            leaderboardId = huaweiLeaderboardIdMapper.mapToGsId(leaderboardId);
        }

        this.leaderboardsClient.submitRankingScore(leaderboardId, score, scoreTips);

        return true;
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback) {
        if (!this.isSessionActive) {
            return false;
        }

        if (this.huaweiLeaderboardIdMapper != null) {
            leaderBoardId = huaweiLeaderboardIdMapper.mapToGsId(leaderBoardId);
        }

        if (relatedToPlayer) {
            this.fetchLeadeboardEntriesRelatedToPLayer(leaderBoardId, limit, callback);
        } else {
            this.fetchLeadeboardEntries(leaderBoardId, limit, callback);
        }

        return true;
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
                                           IFetchLeaderBoardEntriesResponseListener callback,
                                           int timespan, int collection) {
        throw new UnsupportedOperationException();
    }



    private void fetchLeadeboardEntriesRelatedToPLayer(String leaderBoardId, int limit, final IFetchLeaderBoardEntriesResponseListener callback) {
        Task<RankingsClient.RankingScores> task = this.leaderboardsClient.getPlayerCenteredRankingScores(leaderBoardId, 2, limit, true);
        task.addOnSuccessListener(new OnSuccessListener<RankingsClient.RankingScores>() {
            @Override
            public void onSuccess(RankingsClient.RankingScores rankingScores) {
                Array<ILeaderBoardEntry> list = HuaweiGameServicesUtils.getILeaderboardsEntriesList(rankingScores, currentPlayer.getPlayerId());
                callback.onLeaderBoardResponse(list);
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    private void fetchLeadeboardEntries(String leaderBoardId, int limit, final IFetchLeaderBoardEntriesResponseListener callback) {
        Task<RankingsClient.RankingScores> task = this.leaderboardsClient.getRankingTopScores(leaderBoardId, 2, limit, true);
        task.addOnSuccessListener(new OnSuccessListener<RankingsClient.RankingScores>() {
            @Override
            public void onSuccess(RankingsClient.RankingScores rankingScores) {
                Array<ILeaderBoardEntry> list = HuaweiGameServicesUtils.getILeaderboardsEntriesList(rankingScores, currentPlayer.getPlayerId());
                callback.onLeaderBoardResponse(list);
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean submitEvent(String eventId, int increment) {
        if (!isSessionActive) {
            return false;
        }

        if (this.huaweiGameEventIdMapper != null) {
            eventId = huaweiGameEventIdMapper.mapToGsId(eventId);
        }

        this.eventsClient.grow(eventId, increment);

        return true;
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        if (!isSessionActive) {
            return false;
        }

        if (this.huaweiAchievementIdMapper != null) {
            achievementId = huaweiAchievementIdMapper.mapToGsId(achievementId);
        }

        this.achievementsClient.reach(achievementId);

        return true;
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (!isSessionActive) {
            return false;
        }

        if (this.huaweiAchievementIdMapper != null) {
            achievementId = huaweiAchievementIdMapper.mapToGsId(achievementId);
        }

        this.achievementsClient.grow(achievementId, incNum);

        return true;
    }

    @Override
    public void saveGameState(String fileId, final byte[] gameState, final long progressValue, final ISaveGameStateResponseListener success) {
        if (!this.isSaveDataEnabled) {
            throw new UnsupportedOperationException();
        }

        final ArchiveDetails details = new ArchiveDetails.Builder().build();
        details.set(gameState);

        final ArchiveSummaryUpdate archiveSummaryUpdate = new ArchiveSummaryUpdate.Builder()
                .setCurrentProgress(progressValue)
                .setDescInfo("Progress: " + progressValue)
                .build();

        Task<List<ArchiveSummary>> task = this.archivesClient.getArchiveSummaryList(true);
        task.addOnSuccessListener(new OnSuccessListener<List<ArchiveSummary>>() {
            @Override
            public void onSuccess(List<ArchiveSummary> archiveSummaries) {
                String firstSaveDataId = archiveSummaries != null && !archiveSummaries.isEmpty() ? archiveSummaries.get(0).getId() : null;
                if (TextUtils.isEmpty(firstSaveDataId)) {
                    saveArchive(details, archiveSummaryUpdate, success);
                } else {
                    updateArchive(firstSaveDataId, details, archiveSummaryUpdate, success);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    private void saveArchive(ArchiveDetails details, ArchiveSummaryUpdate summary, final ISaveGameStateResponseListener success) {
        Task<ArchiveSummary> addArchiveTask = this.archivesClient.addArchive(details, summary, true);

        if (success != null) {
            addArchiveTask.addOnSuccessListener(new OnSuccessListener<ArchiveSummary>() {
                @Override
                public void onSuccess(ArchiveSummary archiveSummary) {
                    if (archiveSummary != null) {
                        success.onGameStateSaved(true, null);
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    success.onGameStateSaved(false, e.getMessage());
                }
            });
        }
    }

    private void updateArchive(String fileId, ArchiveDetails details, ArchiveSummaryUpdate summary, final ISaveGameStateResponseListener success) {
        Task<OperationResult> addArchiveTask = archivesClient.updateArchive(fileId, summary, details);

        if (success != null) {
            addArchiveTask.addOnSuccessListener(new OnSuccessListener<OperationResult>() {
                @Override
                public void onSuccess(OperationResult result) {
                    success.onGameStateSaved(true, null);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    success.onGameStateSaved(false, e.getMessage());
                }
            });
        }
    }

    @Override
    public void loadGameState(String fileId, final ILoadGameStateResponseListener responseListener) {
        if (!this.isSaveDataEnabled) {
            throw new UnsupportedOperationException();
        }

        Task<List<ArchiveSummary>> task = this.archivesClient.getArchiveSummaryList(true);
        task.addOnSuccessListener(new OnSuccessListener<List<ArchiveSummary>>() {
            @Override
            public void onSuccess(List<ArchiveSummary> archiveSummaries) {
                String firstSaveDataId = archiveSummaries != null && !archiveSummaries.isEmpty() ? archiveSummaries.get(0).getId() : null;

                if (TextUtils.isEmpty(firstSaveDataId)) {
                    responseListener.gsGameStateLoaded(null);
                } else {
                    loadGameData(firstSaveDataId, responseListener);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    private void loadGameData(String fileId, final ILoadGameStateResponseListener responseListener) {
        Task<OperationResult> addArchiveTask = this.archivesClient.loadArchiveDetails(fileId);
        addArchiveTask.addOnSuccessListener(new OnSuccessListener<OperationResult>() {
            @Override
            public void onSuccess(OperationResult result) {
                Archive archive = result.getArchive();

                try {
                    byte[] data = archive.getDetails().get();
                    responseListener.gsGameStateLoaded(data);
                } catch (IOException e) {
                    sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean deleteGameState(String fileId, final ISaveGameStateResponseListener successCallback) {
        if (!this.isSaveDataEnabled) {
            return false;
        }

        Task<List<ArchiveSummary>> task = this.archivesClient.getArchiveSummaryList(true);
        task.addOnSuccessListener(new OnSuccessListener<List<ArchiveSummary>>() {
            @Override
            public void onSuccess(List<ArchiveSummary> archiveSummaries) {
                ArchiveSummary firstSaveData = archiveSummaries != null && !archiveSummaries.isEmpty() ? archiveSummaries.get(0) : null;

                if (firstSaveData != null) {
                    deleteSaveGame(firstSaveData, successCallback);
                } else if (successCallback != null){
                    successCallback.onGameStateSaved(true, null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });

        return true;
    }

    private void deleteSaveGame(ArchiveSummary archiveSummary, final ISaveGameStateResponseListener successCallback) {
        Task<String> task = this.archivesClient.removeArchive(archiveSummary);

        if (successCallback != null) {
            task.addOnSuccessListener(new OnSuccessListener<String>() {
                @Override
                public void onSuccess(String result) {
                    successCallback.onGameStateSaved(true, null);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    successCallback.onGameStateSaved(false, e.getMessage());
                }
            });
        }
    }

    @Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        //only single savegame is supported
        return false;
    }

    @Override
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case FetchGameStates:
            case GameStateDelete:
                return this.isSaveDataEnabled;
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

    private void loadPlayerInfo() {
        PlayersClient playersClient = Games.getPlayersClient(this.activity);
        Task<Player> playerTask = playersClient.getCurrentPlayer();
        playerTask.addOnSuccessListener(new OnSuccessListener<Player>() {
            @Override
            public void onSuccess(Player player) {
                isSessionPending = false;
                isSessionActive = true;
                currentPlayer = player;

                if (gsListener != null) {
                    gsListener.gsOnSessionActive();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                isSessionPending = false;
                sendError(IGameServiceListener.GsErrorType.errorUnknown, e.getMessage(), e);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case HUAWEI_GAMESVCS_AUTH_REQUEST:
                if (data == null) {
                    sendError(IGameServiceListener.GsErrorType.errorLoginFailed,
                            "data is null",
                            new NullPointerException());
                    return;
                }
                String jsonSignInResult = data.getStringExtra("HUAWEIID_SIGNIN_RESULT");
                if (TextUtils.isEmpty(jsonSignInResult)) {
                    sendError(IGameServiceListener.GsErrorType.errorLoginFailed,
                            "empty result",
                            new IllegalStateException());
                    return;
                }
                try {
                    HuaweiIdAuthResult signInResult = new HuaweiIdAuthResult().fromJson(jsonSignInResult);
                    if (signInResult.getStatus().getStatusCode() == 0) {
                        loadPlayerInfo();
                    } else {
                        sendError(IGameServiceListener.GsErrorType.errorLoginFailed,
                                "" + signInResult.getStatus().getStatusCode(),
                                new IllegalStateException());
                    }
                } catch (JSONException je) {
                    sendError(IGameServiceListener.GsErrorType.errorLoginFailed, je.getMessage(), je);
                }
                break;
            case HUAWEI_GAMESVCS_ACHIEVEMENTS_REQUEST:
            case HUAWEI_GAMESVCS_LEADERBOARDS_REQUEST:
                break;
        }
    }

}
