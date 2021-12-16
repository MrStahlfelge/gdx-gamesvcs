package de.golfgl.gdxgamesvcs.player;

/**
 * Class representing a player
 * <p>
 * Created by Kari Vatjus-Anttila on 27.11.2021
 */

public class PlayerData implements IPlayerData {
    public String playerId;
    public String displayName;
    public String name;
    public String title;
    public long retrievedTimeStamp;

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
