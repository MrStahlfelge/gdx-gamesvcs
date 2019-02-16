package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.gamekit.GKAchievement;
import org.robovm.apple.gamekit.GKGameCenterControllerDelegateAdapter;
import org.robovm.apple.gamekit.GKGameCenterViewController;
import org.robovm.apple.gamekit.GKGameCenterViewControllerState;
import org.robovm.apple.gamekit.GKLocalPlayer;
import org.robovm.apple.gamekit.GKScore;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

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
		if (isSessionActive()) {
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
		if (isSessionActive()) {
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
	public void saveGameState(String fileId, byte[] gameState, long progressValue, ISaveGameStateResponseListener success) {

	}

	@Override
	public void loadGameState(String fileId, ILoadGameStateResponseListener responseListener) {

	}

	@Override
	public boolean deleteGameState(String fileId, ISaveGameStateResponseListener success) {
		return false;
	}

	@Override
	public boolean fetchGameStates(IFetchGameStatesListResponseListener callback) {
		return false;
	}

	@Override
	public boolean isFeatureSupported(GameServiceFeature feature) {
		switch (feature) {
			case FetchGameStates:
			case GameStateStorage:
			case GameStateDelete:
			case GameStateMultipleFiles:
				// TODO
				return false;

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
