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
    private JosAppsClient josAppsClient;
    private AchievementsClient achievementsClient;
    private RankingsClient leaderboardsClient;
    private EventsClient eventsClient;
    private ArchivesClient archivesClient;
    private IGameServiceListener gsListener;
    private Player currentPlayer;

    private boolean isSaveDataEnabled = false;
    private boolean isSessionActive = false;

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
        return true;
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
        Task<AuthHuaweiId> authHuaweiIdTask = HuaweiIdAuthManager
                .getService(this.activity, getHuaweiIdParams())
                .silentSignIn();

        authHuaweiIdTask.addOnSuccessListener(new OnSuccessListener<AuthHuaweiId>() {
            @Override
            public void onSuccess(AuthHuaweiId authHuaweiId) {
                loadPlayerInfo();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
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

    @Override
    public void pauseSession() {
    }

    @Override
    public void logOff() {
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

    @Override
    public String getPlayerDisplayName() {
        if (this.currentPlayer != null) {
            return this.currentPlayer.getDisplayName();
        }

        return null;
    }

    @Override
    public boolean isSessionActive() {
        return this.isSessionActive;
    }

    @Override
    public boolean isConnectionPending() {
        return this.isSessionActive;
    }

    @Override
    public void showLeaderboards(String leaderBoardId) throws GameServiceException {
        if (!TextUtils.isEmpty(leaderBoardId)) {
            Task<Intent> task = this.leaderboardsClient.getRankingIntent(leaderBoardId);
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

        this.leaderboardsClient.submitRankingScore(leaderboardId, score, scoreTips);

        return true;
    }

    @Override
    public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer, IFetchLeaderBoardEntriesResponseListener callback) {
        if (!this.isSessionActive) {
            return false;
        }

        if (relatedToPlayer) {
            this.fetchLeadeboardEntriesRelatedToPLayer(leaderBoardId, limit, callback);
        } else {
            this.fetchLeadeboardEntries(leaderBoardId, limit, callback);
        }

        return true;
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

        this.eventsClient.grow(eventId, increment);

        return true;
    }

    @Override
    public boolean unlockAchievement(String achievementId) {
        if (!isSessionActive) {
            return false;
        }

        this.achievementsClient.reach(achievementId);

        return true;
    }

    @Override
    public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
        if (!isSessionActive) {
            return false;
        }

        this.achievementsClient.grow(achievementId, incNum);

        return true;
    }

    @Override
    public void saveGameState(String fileId, final byte[] gameState, final long progressValue, final ISaveGameStateResponseListener success) {
        if (!this.isSaveDataEnabled) {
            throw new UnsupportedOperationException();
        }

        ArchiveDetails details = new ArchiveDetails.Builder().build();
        details.set(gameState);

        ArchiveSummaryUpdate archiveSummaryUpdate = new ArchiveSummaryUpdate.Builder()
                .setCurrentProgress(progressValue)
                .setDescInfo("Progress: " + progressValue)
                .build();

        if (TextUtils.isEmpty(fileId)) {
            saveArchive(details, archiveSummaryUpdate, success);
        } else {
            Task<OperationResult> addArchiveTask = this.archivesClient.updateArchive(fileId, archiveSummaryUpdate, details);
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

    private void saveArchive(ArchiveDetails details, ArchiveSummaryUpdate summary, final ISaveGameStateResponseListener success) {
        Task<ArchiveSummary> addArchiveTask = this.archivesClient.addArchive(details, summary, true);
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

    @Override
    public void loadGameState(String fileId, final ILoadGameStateResponseListener responseListener) {
        if (!this.isSaveDataEnabled) {
            throw new UnsupportedOperationException();
        }

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

        Task<OperationResult> task = this.archivesClient.loadArchiveDetails(fileId);
        task.addOnSuccessListener(new OnSuccessListener<OperationResult>() {
            @Override
            public void onSuccess(OperationResult operationResult) {
                Archive archive = operationResult.getArchive();
                deleteSaveGame(archive.getSummary(), successCallback);
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

    @Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        if (!this.isSaveDataEnabled) {
            throw new UnsupportedOperationException();
        }

        Task<List<ArchiveSummary>> task = this.archivesClient.getArchiveSummaryList(true);
        task.addOnSuccessListener(new OnSuccessListener<List<ArchiveSummary>>() {
            @Override
            public void onSuccess(List<ArchiveSummary> archiveSummaries) {
                Array<String> saveGames = new Array<>();

                for (ArchiveSummary summary : archiveSummaries) {
                    saveGames.add(summary.getId());
                }

                callback.onFetchGameStatesListResponse(saveGames);
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
    public boolean isFeatureSupported(GameServiceFeature feature) {
        switch (feature) {
            case GameStateStorage:
            case GameStateMultipleFiles:
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
                isSessionActive = true;
                currentPlayer = player;

                if (gsListener != null) {
                    gsListener.gsOnSessionActive();
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
