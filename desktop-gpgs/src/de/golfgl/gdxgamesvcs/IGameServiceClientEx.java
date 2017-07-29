package de.golfgl.gdxgamesvcs;

/**
 * Incubating interface for new {@link IGameServiceClient} features.
 * 
 * Intented to be merged to {@link IGameServiceClient}.
 * 
 * TODO move to core package
 * 
 * @author mgsx
 *
 */
public interface IGameServiceClientEx extends IGameServiceClient {
	
	public static enum GameServiceFeature {
		gameStatesUI, gameStatesList, gameStateDelete,
		achievementsList,
		leaderBoardList
	}
	
	boolean isFeatureSupported(GameServiceFeature feature);
	
	/**
	 * @param fetchIcons
	 * @param callback
	 */
	void fetchAchievements(final boolean fetchIcons, final IAchievementCallback callback);
	
	/**
	 * 
	 * @param leaderBoardId
	 * @param aroundPlayer
	 * @param friendsOnly
	 * @param fetchIcons
	 * @param callback
	 */
	void fetchLeaderboard(String leaderBoardId, boolean aroundPlayer, boolean friendsOnly, boolean fetchIcons, ILeaderBoardCallback callback);

	void listGameStates(IGameStatesCallback callback);
	
	/**
	 * Show all player game states.
	 * Should only be called when {@link GameServiceFeature#gameStatesUI} is supported.
	 * Check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
	 */
	void showGameStates();
	
	
	void deleteGameState(final String fileId);
}
