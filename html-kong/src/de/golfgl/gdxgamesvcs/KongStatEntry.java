package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

/**
 * Kong Statistics Entry for fetchLeaderboardEntries
 * Created by Benjamin Schulte on 21.08.2017.
 */

public class KongStatEntry implements ILeaderBoardEntry {
    protected long score;
    protected String username;
    protected String rank;
    protected String avatarUrl;
    protected boolean currentPlayer;

    @Override
    public String getFormattedValue() {
        return Long.toString(score);
    }

    @Override
    public long getSortValue() {
        return score;
    }

    @Override
    public String getScoreTag() {
        return null;
    }

    @Override
    public String getUserDisplayName() {
        return username;
    }

    @Override
    public String getUserId() {
        return username;
    }

    @Override
    public String getScoreRank() {
        return rank;
    }

    @Override
    public String getAvatarUrl() {
        return avatarUrl;
    }

    @Override
    public boolean isCurrentPlayer() {
        return currentPlayer;
    }
}
