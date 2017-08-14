package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

public class GpgsLeaderBoardEntry implements ILeaderBoardEntry {
    protected String formattedValue;
    protected long sortValue;
    protected String scoreTag;
    protected String userDisplayName;
    protected String userId;
    protected String scoreRank;
    protected String avatarUrl;
    protected boolean currentPlayer;

    @Override
    public String getFormattedValue() {
        return formattedValue;
    }

    void setFormattedValue(String formattedValue) {
        this.formattedValue = formattedValue;
    }

    @Override
    public long getSortValue() {
        return sortValue;
    }

    void setSortValue(long sortValue) {
        this.sortValue = sortValue;
    }

    @Override
    public String getScoreTag() {
        return scoreTag;
    }

    void setScoreTag(String scoreTag) {
        this.scoreTag = scoreTag;
    }

    @Override
    public String getUserDisplayName() {
        return userDisplayName;
    }

    void setUserDisplayName(String userDisplayName) {
        this.userDisplayName = userDisplayName;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String getScoreRank() {
        return scoreRank;
    }

    void setScoreRank(String scoreRank) {
        this.scoreRank = scoreRank;
    }

    @Override
    public String getAvatarUrl() {
        return avatarUrl;
    }

    void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Override
    public boolean isCurrentPlayer() {
        return currentPlayer;
    }

    void setCurrentPlayer(boolean currentPlayer) {
        this.currentPlayer = currentPlayer;
    }
}
