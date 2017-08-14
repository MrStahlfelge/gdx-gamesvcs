package de.golfgl.gdxgamesvcs.leaderboard;

/**
 * Leaderboard entry data
 *
 * @author Benjamin Schulte
 */
public interface ILeaderBoardEntry {
    /**
     * Leaderboard entryformatted value
     *
     * @return formatted value
     */
    public String getFormattedValue();

    /**
     * Leaderboard entry sort value
     *
     * @return sort value
     */
    public long getSortValue();

    /**
     * Leaderboard entry score tag
     *
     * @return score tag
     */
    public String getScoreTag();

    /**
     * Leaderboard entry user's display name
     *
     * @return user display name
     */
    public String getUserDisplayName();

    /**
     * Leaderboard entry user's id
     *
     * @return user id. May be null if guest user
     */
    public String getUserId();

    /**
     * Leaderboard entry rank
     *
     * @return rank on leaderboard
     */
    public String getScoreRank();
    
    /**
     * @return Player's avatar URL (may be null)
     */
	public String getAvatarUrl();
	
	/**
	 * @return whether this entry is the current player score
	 */
	public boolean isCurrentPlayer();

}
