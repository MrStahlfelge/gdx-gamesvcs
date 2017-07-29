package de.golfgl.gdxgamesvcs;

/**
 * Callback for {@link IGameServiceClientEx#fetchLeaderboard(String, boolean, boolean, boolean, ILeaderBoardCallback)}
 * 
 * @author mgsx
 *
 */
public interface ILeaderBoardCallback {
	/**
	 * Called in GLThread when leaderBoard received.
	 * @param achievements null if leaderBoard couldn't be fetched.
	 */
	void onLeaderBoardResponse(LeaderBoard leaderBoard);
}
