package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;

/**
 * Gpgs Achievement
 * <p>
 * Created by Benjamin Schulte on 30.08.2017.
 */

public class GpgsAchievement implements IAchievement {
    protected String achievementId;
    protected String title;
    protected String description;
    protected IGameServiceIdMapper<String> achievementMapper;
    protected float percCompl;

    @Override
    public String getAchievementId() {
        return achievementId;
    }

    @Override
    public boolean isAchievementId(String achievementId) {
        return (achievementMapper == null ? this.achievementId.equals(achievementId) :
                this.achievementId.equals(achievementMapper.mapToGsId(achievementId)));
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public float getCompletionPercentage() {
        return percCompl;
    }

    @Override
    public boolean isUnlocked() {
        return percCompl >= 1f;
    }

    @Override
    public String getIconUrl() {
        return null;
    }
}
