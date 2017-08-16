package de.golfgl.gdxgamesvcs.achievement;

/**
 * IAchievement data. This is the most general IAchievement class
 *
 * @author mgsx
 */
public interface IAchievement {

    /**
     * Returns the achievementId for this achievement
     * Note that this might be an internal id from the game service. For game services where you defined a
     * mapping, use {@link #isAchievementId} which respects mappings
     *
     * @return achievementId
     */
    public String getAchievementId();

    /**
     * Returns if this achievement has the given id. This method respects a given id mapping
     *
     * @param achievementId achievement id
     * @return true or false
     */
    public boolean isAchievementId(String achievementId);

    /**
     * Returns the title for the achievement
     *
     * @return title
     */
    public String getTitle();

    /**
     * Returns the description for the achivement. If a description for locked and unlocked is available, it returns
     * the description for the locked achievement.
     *
     * @return description
     */
    public String getDescription();

    /**
     * Returns player progression
     *
     * @return Achievment progression rate in percent ranges from 0f to 1f. For non incremental achievements
     * (locked/unlocked), this value is 0 when locked, 1f when unlocked.
     */
    public float getCompletionPercentage();

    /**
     * Checks if achievmenet is unlocked
     *
     * @return true if unlocked
     */
    public boolean isUnlocked();

    /**
     * @return the achievement icon URL (may be null)
     */
    public String getIconUrl();
}
