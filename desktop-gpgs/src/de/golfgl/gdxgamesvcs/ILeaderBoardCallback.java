package de.golfgl.gdxgamesvcs;

public interface ILeaderBoardCallback {
	/**
	 * Called when leaderBoard received.
	 * @param achievements null if leaderBoard couldn't be fetched.
	 */
	void onLeaderBoardResponse(LeaderBoard leaderBoard);
}
