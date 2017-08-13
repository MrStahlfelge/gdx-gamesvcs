package de.golfgl.gdxgamesvcs;

/**
 * Incubating interface for new {@link IGameServiceClient} features.
 * 
 * Intented to be merged to {@link IGameServiceClient}.
 * 
 * @author mgsx
 *
 */
public interface IGameServiceClientEx extends IGameServiceClient {
	
	public static enum GameServiceFeature {
		gameStatesUI, 
		gameStatesList, 
		gameStateDelete,
		gameStateMultiple,
		gameStateStorage,
		
		achievementsList, 
		achievementsUI,
		
		leaderBoardList, 
		leaderboardsUI
	}
	
	/**
	 * @param feature
	 * @return true if feature is supported by implementation.
	 */
	boolean isFeatureSupported(GameServiceFeature feature);
	
	/**
	 * Fetch current player's achievements.
	 * Should only be called when {@link GameServiceFeature#achievementsList} is supported,
	 * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
	 * @param callback
	 */
	void fetchAchievements(final IAchievementCallback callback);
	
	/**
	 * Fetch a leader board.
	 * Should only be called when {@link GameServiceFeature#leaderBoardList} is supported,
	 * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
	 * @param leaderBoardId leaderboard to fetch
	 * @param aroundPlayer only fetch scores around current player score.
	 * @param friendsOnly only fetch player's friend.
	 * @param callback
	 */
	void fetchLeaderboard(String leaderBoardId, boolean aroundPlayer, boolean friendsOnly, ILeaderBoardCallback callback);

	/**
	 * Fetch current player's game states.
	 * Should only be called when {@link GameServiceFeature#gameStatesList} is supported,
	 * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
	 * @param callback
	 */
	void fetchGameStates(IGameStatesCallback callback);
	
	/**
	 * Show all player game states.
	 * Should only be called when {@link GameServiceFeature#gameStatesUI} is supported,
	 * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
	 */
	void showGameStates();
	
	/**
	 * Delete an existing game state.
	 * Should only be called when {@link GameServiceFeature#gameStateDelete} is supported,
	 * check {@link #isFeatureSupported(GameServiceFeature)} prior to call this method.
	 * @param fileId game state Id
	 */
	void deleteGameState(final String fileId);
}
