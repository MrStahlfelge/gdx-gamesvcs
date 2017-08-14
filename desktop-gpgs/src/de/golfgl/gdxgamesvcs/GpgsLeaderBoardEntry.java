package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.leaderboard.LeaderBoardEntry;

public class GpgsLeaderBoardEntry extends LeaderBoardEntry
{
	void setCurrentPlayer(boolean currentPlayer) {
		this.currentPlayer = currentPlayer;
	}
	void setAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}
    void setFormattedValue(String formattedValue) {
		this.formattedValue = formattedValue;
	}
    void setScoreRank(String scoreRank) {
		this.scoreRank = scoreRank;
	}
    void setScoreTag(String scoreTag) {
		this.scoreTag = scoreTag;
	}
    void setSortValue(long sortValue) {
		this.sortValue = sortValue;
	}
    void setUserDisplayName(String userDisplayName) {
		this.userDisplayName = userDisplayName;
	}
    void setUserId(String userId) {
		this.userId = userId;
	}
}
