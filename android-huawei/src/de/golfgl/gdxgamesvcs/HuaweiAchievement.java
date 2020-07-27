package de.golfgl.gdxgamesvcs;

import com.huawei.hms.jos.games.achievement.Achievement;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;

public class HuaweiAchievement implements IAchievement {

    public static final int UNLOCKED_STATUS = 3;

    private String achievementId;
    private String title;
    private String description;
    private String iconUrl;
    private float percCompl = 0f;

    public HuaweiAchievement(Achievement achievement) {
        this.achievementId = achievement.getId();
        this.title = achievement.getDisplayName();
        this.description = achievement.getDescInfo();
        this.iconUrl = achievement.getVisualizedThumbnailUri().toString();

        if (achievement.getState() == UNLOCKED_STATUS) {
            this.percCompl = 1f;
        } else if (achievement.getType() == Achievement.TYPE_GROW_ABLE)
            this.percCompl = (float) achievement.getReachedSteps() / achievement.getAllSteps();
    }

    @Override
    public String getAchievementId() {
        return this.achievementId;
    }

    @Override
    public boolean isAchievementId(String achievementId) {
        return this.achievementId.equals(achievementId);
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public float getCompletionPercentage() {
        return this.percCompl;
    }

    @Override
    public boolean isUnlocked() {
        return this.percCompl >= 1f;
    }

    @Override
    public String getIconUrl() {
        return this.iconUrl;
    }
}
