package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.Array;

/**
 * Leaderboard data
 * 
 * @author mgsx
 *
 */
public class LeaderBoard {

	/**
	 * Individual player score.
	 * 
	 * Note that fields could be null (even player name)
	 */
	public static class Score 
	{
		/** if this score is the current logged player score*/
		public boolean currrentPlayer;
		
		public String name, rank;
		public String score;
		public String avatarUrl;
		
		long sortKey;
	}
	
	public String id, name;
	public String iconUrl;
	
	/**
	 * The list of all scores ordered by score from the best one to the worse one.
	 * Note that some scores can be defined as "smaller is better". In this case 
	 * first item in this list has the lowest score value (the best one).
	 *  
	 * Empty when leader board doesn't have scores yet.
	 */
	public Array<Score> scores;
}
