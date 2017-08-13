package de.golfgl.gdxgamesvcs.achievement;

/**
 * Achievement data. This is the most general Achievement class
 *
 * @author mgsx
 */
public class Achievement {
    protected String achievementId;
    protected String title;
    protected String description;
    protected float completionPercentage;

    /**
     * Returns the achievementId for this achievement
     *
     * @return achievementId
     */
    public String getAchievementId() {
        return achievementId;
    }

    /**
     * Returns the title for the achievement
     *
     * @return title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the description for the achivement. If a description for locked and unlocked is available, it returns
     * the description for the locked achievement.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns player progression
     *
     * @return Achievment progression rate in percent ranges from 0f to 1f. For non incremental achievements
     * (locked/unlocked), this value is 0 when locked, 1f when unlocked.
     */
    public float getCompletionPercentage() {
        return completionPercentage;
    }

    /**
     * Checks if achievmenet is unlocked
     *
     * @return true if unlocked
     */
    public boolean isUnlocked() {
        return completionPercentage >= 1f;
    }
}
