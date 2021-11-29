package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.player.IPlayerData;

/**
 * Gpgs Player
 * <p>
 * Created by Kari Vatjus-Anttila on 27.11.2021
 */

public class GpgsPlayerData implements IPlayerData {
    protected String playerId;
    protected String displayName;
    protected String name;
    protected String title;
    protected long retrievedTimeStamp;

    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public long getRetrievedTimestamp() {
        return retrievedTimeStamp;
    }
}
