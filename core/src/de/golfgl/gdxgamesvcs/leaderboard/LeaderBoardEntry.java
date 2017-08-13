package de.golfgl.gdxgamesvcs.leaderboard;

/**
 * Leaderboard entry data
 *
 * @author Benjamin Schulte
 */
public class LeaderBoardEntry {
    protected String formattedValue;
    protected long sortValue;
    protected String scoreTag;
    protected String userDisplayName;
    protected String userId;
    protected String scoreRank;
	protected String avatarUrl;
	protected boolean currentPlayer;

    /**
     * Leaderboard entryformatted value
     *
     * @return formatted value
     */
    public String getFormattedValue() {
        return formattedValue;
    }

    /**
     * Leaderboard entry sort value
     *
     * @return sort value
     */
    public long getSortValue() {
        return sortValue;
    }

    /**
     * Leaderboard entry score tag
     *
     * @return score tag
     */
    public String getScoreTag() {
        return scoreTag;
    }

    /**
     * Leaderboard entry user's display name
     *
     * @return user display name
     */
    public String getUserDisplayName() {
        return userDisplayName;
    }

    /**
     * Leaderboard entry user's id
     *
     * @return user id. May be null if guest user
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Leaderboard entry rank
     *
     * @return rank on leaderboard
     */
    public String getScoreRank() {
        return scoreRank;
    }
    
    /**
     * @return Player's avatar URL (may be null)
     */
	public String getAvatarUrl() {
		return avatarUrl;
	}
	
	/**
	 * @return whether this entry is the current player score
	 */
	public boolean isCurrentPlayer() {
		return currentPlayer;
	}
	

}
