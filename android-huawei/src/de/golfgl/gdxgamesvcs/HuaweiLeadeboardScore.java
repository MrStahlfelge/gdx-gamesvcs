package de.golfgl.gdxgamesvcs;

import com.huawei.hms.jos.games.ranking.RankingScore;

import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

public class HuaweiLeadeboardScore implements ILeaderBoardEntry {
    private String formattedValue;
    private String scoreTag;
    private String userDisplayName;
    private String userId;
    private String scoreRank;
    private String avatarUrl;
    private long sortValue;
    private boolean isCurrentPlayer;

    public HuaweiLeadeboardScore(RankingScore score, String currentPlayerId, int sortValue) {
        this.userDisplayName = score.getScoreOwnerDisplayName();
        this.userId = score.getScoreOwnerPlayer().getPlayerId();
        this.isCurrentPlayer = userId.equals(currentPlayerId);
        this.scoreRank = score.getDisplayRank();
        this.avatarUrl = score.getScoreOwnerHiIconUri().toString();
        this.sortValue = sortValue;
        this.scoreTag = score.getScoreTips();
        this.formattedValue = score.getRankingDisplayScore();
    }

    @Override
    public String getFormattedValue() {
        return this.formattedValue;
    }

    @Override
    public long getSortValue() {
        return this.sortValue;
    }

    @Override
    public String getScoreTag() {
        return this.scoreTag;
    }

    @Override
    public String getUserDisplayName() {
        return this.userDisplayName;
    }

    @Override
    public String getUserId() {
        return this.userId;
    }

    @Override
    public String getScoreRank() {
        return this.scoreRank;
    }

    @Override
    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    @Override
    public boolean isCurrentPlayer() {
        return this.isCurrentPlayer;
    }
}
