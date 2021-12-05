package de.golfgl.gdxgamesvcs.player;

/**
 * Player data
 *
 * @author Kari Vatjus-Anttila
 */
public interface IPlayerData {

    /**
     * @return Player Id
     */
    public String getPlayerId();

    /**
     * @return Player Display Name
     */
    public String getDisplayName();

    /**
     * @return Player Name
     */
    public String getName();

    /**
     * @return Player Title
     */
    public String getTitle();

    /**
     * @return Player Retrieved Timestamp
     */
    public long getRetrievedTimestamp();
}
