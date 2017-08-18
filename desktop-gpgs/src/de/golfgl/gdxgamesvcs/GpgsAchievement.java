package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;

class GpgsAchievement implements IAchievement {
    protected String achievementId;
    protected String title;
    protected String description;
    protected float completionPercentage;
    protected String iconUrl;
    protected IGameServiceIdMapper<String> achievementIdMapper;

    public String getAchievementId() {
        return achievementId;
    }

    @Override
    public boolean isAchievementId(String achievementId) {
        if (achievementIdMapper != null)
            achievementId = achievementIdMapper.mapToGsId(achievementId);

        return achievementId != null && achievementId.equalsIgnoreCase(this.achievementId);
    }

    void setAchievementId(String achievementId) {
        this.achievementId = achievementId;
    }

    @Override
    public String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    @Override
    public float getCompletionPercentage() {
        return completionPercentage;
    }

    void setCompletionPercentage(float completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    @Override
    public boolean isUnlocked() {
        return completionPercentage >= 1f;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
}
