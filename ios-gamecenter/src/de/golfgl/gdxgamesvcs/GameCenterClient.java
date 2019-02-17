package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSData;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.gamekit.GKAchievement;
import org.robovm.apple.gamekit.GKGameCenterControllerDelegateAdapter;
import org.robovm.apple.gamekit.GKGameCenterViewController;
import org.robovm.apple.gamekit.GKGameCenterViewControllerState;
import org.robovm.apple.gamekit.GKLocalPlayer;
import org.robovm.apple.gamekit.GKSavedGame;
import org.robovm.apple.gamekit.GKScore;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

import java.util.ArrayList;

import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;

/**
 * Apple Game Center implementation
 */
public class GameCenterClient implements IGameServiceClient {
	public static final String GAMESERVICE_ID = IGameServiceClient.GS_GAMECENTER_ID;
	private final UIViewController viewController;
	private IGameServiceListener gsListener;
	private boolean connecting;
	private boolean handlerSet;
	private UIViewController lastGotLoginScreen;
	private boolean callLoginFromHandler;

	public GameCenterClient(UIViewController viewController) {
		this.viewController = viewController;
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
		if (!handlerSet) {
			connecting = true;
			handlerSet = true;
			final GKLocalPlayer localPlayer = GKLocalPlayer.getLocalPlayer();
			localPlayer.setAuthenticateHandler(new VoidBlock2<UIViewController, NSError>() {
				@Override
				public void invoke(UIViewController gkViewController, NSError nsError) {
					connecting = false;

					if (isSessionActive()) {
						lastGotLoginScreen = null;
						Gdx.app.debug(GAMESERVICE_ID, "Successfully logged into GameCenter");
						if (gsListener != null) {
							gsListener.gsOnSessionActive();
						}
					} else {
						if (gkViewController != null)
							lastGotLoginScreen = gkViewController;
						Gdx.app.debug(GAMESERVICE_ID, "Did not authenticate.");
						if (gsListener != null)
							gsListener.gsOnSessionInactive();
						if (callLoginFromHandler)
							logIn();
					}
				}
			});
		}
		return isSessionActive() || isConnectionPending();
	}

	@Override
	public boolean logIn() {
		callLoginFromHandler = false;

		if (!isSessionActive()) {
			if (!handlerSet) {
				callLoginFromHandler = true;
				return resumeSession();
			}

			if (lastGotLoginScreen != null)
			    // unfortunately, the login window will never call back
			    viewController.presentViewController(lastGotLoginScreen, true, null);
			else if (gsListener != null)
			    gsListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLoginFailed,
                        "Please use Game Center settings to log in", null);
		}
		return isSessionActive() || lastGotLoginScreen != null;
	}

	@Override
	public void pauseSession() {
		// GameCenter handles this stuff
	}

	@Override
	public void logOff() {
		//nothing to do, inform the user to log out via GameCenter app
		if (gsListener != null)
			gsListener.gsShowErrorToUser(IGameServiceListener.GsErrorType.errorLogoutFailed,
					"Please logout via GameCenter's interface", null);
	}

	@Override
	public String getPlayerDisplayName() {
		return isSessionActive() ? GKLocalPlayer.getLocalPlayer().getAlias() : null;
	}

	@Override
	public boolean isSessionActive() {
		return GKLocalPlayer.getLocalPlayer().isAuthenticated();
	}

	@Override
	public boolean isConnectionPending() {
		return connecting;
	}

	@Override
	public void showLeaderboards(String leaderBoardId) throws GameServiceException {
		if (!isSessionActive())
			throw new GameServiceException.NoSessionException();

		GKGameCenterViewController gameCenterView = new GKGameCenterViewController();
		gameCenterView.setViewState(GKGameCenterViewControllerState.Leaderboards);
		if (leaderBoardId != null)
			gameCenterView.setLeaderboardIdentifier(leaderBoardId);
		gameCenterView.setGameCenterDelegate(new GKGameCenterControllerDelegateAdapter() {
			@Override
			public void didFinish(GKGameCenterViewController gameCenterViewController) {
				gameCenterViewController.dismissViewController(true, null);
			}
		});
		viewController.presentViewController(gameCenterView, true, null);
	}

	@Override
	public void showAchievements() throws GameServiceException {
		if (!isSessionActive())
			throw new GameServiceException.NoSessionException();

		GKGameCenterViewController gameCenterView = new GKGameCenterViewController();
		gameCenterView.setViewState(GKGameCenterViewControllerState.Achievements);
		gameCenterView.setGameCenterDelegate(new GKGameCenterControllerDelegateAdapter() {
			@Override
			public void didFinish(GKGameCenterViewController gameCenterViewController) {
				gameCenterViewController.dismissViewController(true, null);
			}
		});
		viewController.presentViewController(gameCenterView, true, null);
	}

	@Override
	public boolean fetchAchievements(IFetchAchievementsResponseListener callback) {
		return false;
	}

	@Override
	public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
		if (isSessionActive() && leaderboardId != null) {
			GKScore scoreReporter = new GKScore();
			scoreReporter.setValue(score);
			scoreReporter.setLeaderboardIdentifier(leaderboardId);
			NSArray<GKScore> scores = new NSArray<>(scoreReporter);
			GKScore.reportScores(scores, new VoidBlock1<NSError>() {
				@Override
				public void invoke (NSError error) {
					// ignore errors
				}
			});
			return true;
		}

		return false;
	}

	@Override
	public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer, IFetchLeaderBoardEntriesResponseListener callback) {
		return false;
	}

	@Override
	public boolean submitEvent(String eventId, int increment) {
		return false;
	}

	@Override
	public boolean unlockAchievement(String achievementId) {
		return incrementAchievement(achievementId, 0, 1f);
	}

	@Override
	public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
		if (isSessionActive() && achievementId != null) {
			GKAchievement achievement = new GKAchievement(achievementId);
			achievement.setPercentComplete(completionPercentage * 100);
			achievement.setShowsCompletionBanner(true);
			// Create an array with the achievement
			NSArray<GKAchievement> achievements = new NSArray<>(achievement);
			GKAchievement.reportAchievements(achievements, new VoidBlock1<NSError>() {
				@Override
				public void invoke (NSError error) {
					// do nothing
				}
			});

			return true;
		}

		return false;
	}

	@Override
	public void saveGameState(String fileId, byte[] gameState, long progressValue, final ISaveGameStateResponseListener success) {
        if (!isSessionActive()) {
            if (success != null)
                success.onGameStateSaved(false, "NOT_CONNECTED");
            return;
        }

        NSData nsData = new NSData(gameState);

        GKLocalPlayer.getLocalPlayer().saveGameData(nsData, fileId, new VoidBlock2<GKSavedGame, NSError>() {
            @Override
            public void invoke(GKSavedGame savedGame, NSError error) {
                if (success == null)
                    return;

                if (error == null)
                    success.onGameStateSaved(true, null);
                else
                    success.onGameStateSaved(false, String.valueOf(error.getCode()));
            }
        });
	}

	@Override
	public void loadGameState(final String fileId, final ILoadGameStateResponseListener responseListener) {
        if (!isSessionActive()) {
            responseListener.gsGameStateLoaded(null);
            return;
        }

        GKLocalPlayer.getLocalPlayer().fetchSavedGames(new VoidBlock2<NSArray<GKSavedGame>, NSError>() {
            @Override
            public void invoke(NSArray<GKSavedGame> savedGames, NSError error) {
                if (error == null && savedGames != null) {
                    final ArrayList<GKSavedGame> snapshots = new ArrayList<>();
                    for (GKSavedGame snapshot : savedGames) {
                        if (snapshot.getName().equals(fileId))
                            snapshots.add(snapshot);
                    }

                    GKSavedGame mySnapshot = null;
                    final NSArray<GKSavedGame> resolved = new NSArray<>();
                    if (snapshots.isEmpty()) {
                        responseListener.gsGameStateLoaded(null);
                        return;
                    } else if (snapshots.size() == 1) {
                        mySnapshot = snapshots.get(0);
                    } else {
                        // look for the newest one
                        mySnapshot = snapshots.get(0);
                        for (int i = 1; i < snapshots.size(); i++) {
                            if (snapshots.get(i).getModificationDate().getTimeIntervalSinceReferenceDate()
                                    > mySnapshot.getModificationDate().getTimeIntervalSinceReferenceDate())
                                mySnapshot = snapshots.get(i);
                        }
                        for (GKSavedGame snapshot : snapshots)
                            resolved.add(snapshot);
                    }

                    mySnapshot.loadData(new VoidBlock2<NSData, NSError>() {
                        @Override
                        public void invoke(NSData data, NSError error) {
                            if (error == null) {
                                // if there were conflicts, resolve them now
                                if (snapshots.size() > 1)
                                    GKLocalPlayer.getLocalPlayer().resolveConflictingSavedGames(resolved, data, null);
                                responseListener.gsGameStateLoaded(data.getBytes());
                            } else {
                                responseListener.gsGameStateLoaded(null);
                            }
                        }
                    });
                }
            }
        });
	}

	@Override
    public boolean deleteGameState(String fileId, final ISaveGameStateResponseListener success) {
        if (!isSessionActive())
            return false;

        GKLocalPlayer.getLocalPlayer().deleteSavedGames(fileId, new VoidBlock1<NSError>() {
            @Override
            public void invoke(NSError error) {
                if (success == null)
                    return;

                if (error == null)
                    success.onGameStateSaved(true, null);
                else
                    success.onGameStateSaved(false, String.valueOf(error.getCode()));
            }
        });
        return true;
    }

	@Override
    public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
        if (!isSessionActive())
            return false;

        GKLocalPlayer.getLocalPlayer().fetchSavedGames(new VoidBlock2<NSArray<GKSavedGame>, NSError>() {
            @Override
            public void invoke(NSArray<GKSavedGame> savedGames, NSError error) {
                if (error == null && savedGames != null) {
                    Array<String> gameStates = new Array<String>(savedGames.size());

                    for (GKSavedGame snapshot : savedGames) {
                        gameStates.add(snapshot.getName());
                    }

                    callback.onFetchGameStatesListResponse(gameStates);
                } else {
                    callback.onFetchGameStatesListResponse(null);
                }
            }
        });
        return true;
    }

    @Override
	public boolean isFeatureSupported(GameServiceFeature feature) {
		switch (feature) {
			case FetchGameStates:
			case GameStateStorage:
			case GameStateDelete:
			case GameStateMultipleFiles:
				return true;

			case FetchAchievements:
			case FetchLeaderBoardEntries:
				// TODO :)
				return false;

			case ShowAchievementsUI:
			case SubmitEvents:
			case ShowLeaderboardUI:
			case ShowAllLeaderboardsUI:
			case PlayerLogOut:
				return true;

			default:
				return false;
		}
	}
}
