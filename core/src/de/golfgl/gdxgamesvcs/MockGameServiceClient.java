package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.achievement.IFetchAchievementsResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.IFetchGameStatesListResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ILoadGameStateResponseListener;
import de.golfgl.gdxgamesvcs.gamestate.ISaveGameStateResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.IFetchLeaderBoardEntriesResponseListener;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

/**
 * This is a mock implementation of {@link IGameServiceClient}. Useful during
 * development phase when you don't want to connect to an existing service but just
 * test your application behavior against service responses.
 *
 * It emulate network latencies with a fixed sleep time, helpful to test your wait
 * screen and your GUI behaviors.
 *
 * By default mock supports all feature, you may override {@link #isFeatureSupported(de.golfgl.gdxgamesvcs.IGameServiceClient.GameServiceFeature)}
 * in order to configure your mock.
 *
 * Subclass implements abstract methods in order to provide mock data (game state, achievements and leaderboard entries)
 *
 * @author mgsx
 *
 */
abstract public class MockGameServiceClient implements IGameServiceClient
{
	private float latency;
	private IGameServiceListener gsListener;
	private volatile boolean connected, connecting;

    private void sleep(final Runnable runnable) {
        if (runnable != null)
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    runnable.run();
                }
            }, latency);
    }

    /**
	 * Create mock service
	 * @param latency time of latency in seconds for each emulated remote call.
	 */
	public MockGameServiceClient(float latency) {
		this.latency = latency;
	}

	/**
	 * @return some fake leaderboard entries data
	 */
	abstract protected Array<ILeaderBoardEntry> getLeaderboardEntries();

	/**
	 * @return some fake game states fileNames
	 */
	abstract protected Array<String> getGameStates();

	/**
	 * @return fake saved game state data
	 */
	abstract protected byte[] getGameState();

	/**
	 * @return fake achievements list
	 */
	abstract protected Array<IAchievement> getAchievements();

	@Override
	public String getGameServiceId() {
		return "Mock";
	}

	@Override
	public void showAchievements() throws GameServiceException {
	}

	@Override
	public void showLeaderboards(String leaderBoardId) throws GameServiceException {
	}

	@Override
	public boolean isFeatureSupported(GameServiceFeature feature) {
		return true;
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

	public boolean connect(boolean silent) {
		if(!connected && !connecting){
			connecting = true;
			sleep(new Runnable() {

				@Override
				public void run() {
					connecting = false;
					connected = true;
					if(gsListener != null) gsListener.gsOnSessionActive();
				}
			});
		}
		return connected || connecting;
	}

	@Override
	public void pauseSession() {
		sleep(new Runnable() {

			@Override
			public void run() {
				if(gsListener != null) gsListener.gsOnSessionInactive();
			}
		});
	}

	@Override
	public void logOff() {
		connected = connecting = false;
		pauseSession();
	}

	@Override
	public String getPlayerDisplayName() {
		return connected ? getPlayerName() : null;
	}

	abstract protected String getPlayerName();

	@Override
	public boolean isSessionActive() {
		return connected;
	}

	@Override
	public boolean isConnectionPending() {
		return connecting;
	}

	@Override
	public boolean fetchAchievements(final IFetchAchievementsResponseListener callback) {
		sleep(new Runnable() {
			@Override
			public void run() {
				callback.onFetchAchievementsResponse(getAchievements());
			}
		});
		return true;
	}

	@Override
	public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
		sleep(null);
		return true;
	}

	@Override
	public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
										   final IFetchLeaderBoardEntriesResponseListener callback) {
		sleep(new Runnable() {
			@Override
			public void run() {
				callback.onLeaderBoardResponse(getLeaderboardEntries());
			}
		});
		return true;
	}

	@Override
	public boolean fetchLeaderboardEntries(String leaderBoardId, int limit, boolean relatedToPlayer,
			final IFetchLeaderBoardEntriesResponseListener callback, int timespan, int collection) {
		sleep(new Runnable() {
			@Override
			public void run() {
				callback.onLeaderBoardResponse(getLeaderboardEntries());
			}
		});
		return true;
	}

	@Override
	public boolean submitEvent(String eventId, int increment) {
		sleep(null);
		return true;
	}

	@Override
	public boolean unlockAchievement(String achievementId) {
		sleep(null);
		return true;
	}

	@Override
	public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
		sleep(null);
		return true;
	}

	@Override
	public void saveGameState(String fileId, byte[] gameState, long progressValue,
			final ISaveGameStateResponseListener listener) {
		sleep(new Runnable() {

			@Override
			public void run() {
				if(listener != null) listener.onGameStateSaved(true, null);
			}
		});
	}

	@Override
	public void loadGameState(String fileId, final ILoadGameStateResponseListener responseListener) {
		sleep(new Runnable() {
			@Override
			public void run() {
				responseListener.gsGameStateLoaded(getGameState());
			}
		});
	}

	@Override
    public boolean deleteGameState(String fileId, final ISaveGameStateResponseListener success) {
        sleep(new Runnable() {
            @Override
            public void run() {
                if (success != null)
                    success.onGameStateSaved(true, null);
            }
        });
        return true;
    }

    @Override
	public boolean fetchGameStates(final IFetchGameStatesListResponseListener callback) {
		sleep(new Runnable() {
			@Override
			public void run() {
				callback.onFetchGameStatesListResponse(getGameStates());
			}
		});
		return true;
	}
}
