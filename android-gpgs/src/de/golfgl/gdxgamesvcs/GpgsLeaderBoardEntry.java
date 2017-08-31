package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

/**
 * Gpgs Leaderboard Entry
 * <p>
 * Created by Benjamin Schulte on 31.08.2017.
 */
public class GpgsLeaderBoardEntry implements ILeaderBoardEntry {
    protected String formattedValue;
    protected long sortValue;
    protected String scoreTag;
    protected String userDisplayName;
    protected String userId;
    protected String scoreRank;
    protected boolean currentPlayer;

    @Override
    public String getFormattedValue() {
        return formattedValue;
    }

    @Override
    public long getSortValue() {
        return sortValue;
    }

    @Override
    public String getScoreTag() {
        return scoreTag;
    }

    @Override
    public String getUserDisplayName() {
        return userDisplayName;
    }

    @Override
    public String getUserId() {
        // do not return null as a user with a null id is a guest. But there are no guest users in GPGS
        if (userId != null)
            return userId;
        else
            return "";
    }

    @Override
    public String getScoreRank() {
        return scoreRank;
    }

    @Override
    public String getAvatarUrl() {
        return null;
    }

    @Override
    public boolean isCurrentPlayer() {
        return currentPlayer;
    }
}
