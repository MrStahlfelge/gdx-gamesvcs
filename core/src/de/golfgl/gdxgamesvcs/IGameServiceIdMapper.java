package de.golfgl.gdxgamesvcs;

/**
 * Certain game services give you an own generated id for leaderboards and achievements. Use this mapper interface
 * to get independant from these ids and use your own independant string ids.
 *
 * Created by Benjamin Schulte on 18.06.2017.
 */

public interface IGameServiceIdMapper<A> {
    /**
     * method that maps your game service independant ids to Game Service dependant ids
     *
     * @param independantId independant ID to map from
     * @return game service dependant id, or null if none available.
     */
    A mapToGsId(String independantId);
}
