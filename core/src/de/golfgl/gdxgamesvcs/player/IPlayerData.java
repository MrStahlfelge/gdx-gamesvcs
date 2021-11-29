package de.golfgl.gdxgamesvcs.player;

/**
 * Leaderboard entry data
 *
 * @author Kari Vatjus-Anttila
 */
public interface IPlayerData {

    /**
     * Player Id
     *
     * @return Player Id
     */
    public String getPlayerId();

    /**
     * Player Display Name
     *
     * @return Player Display Name
     */
    public String getDisplayName();

    /**
     * Player Name
     *
     * @return Player Name
     */
    public String getName();

    /**
     * Player Title
     *
     * @return Player Title
     */
    public String getTitle();

    /**
     * Player Retrieved Timestamp
     *
     * @return Player Retrieved Timestamp
     */
    public long getRetrievedTimestamp();
}
