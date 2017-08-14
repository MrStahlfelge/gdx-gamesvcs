package de.golfgl.gdxgamesvcs;

import de.golfgl.gdxgamesvcs.achievement.Achievement;

class GpgsAchievement extends Achievement
{
	void setIconUrl(String iconUrl) {
		this.iconUrl = iconUrl;
	}
    void setAchievementId(String achievementId) {
		this.achievementId = achievementId;
	}
    void setDescription(String description) {
		this.description = description;
	}
    void setTitle(String title) {
		this.title = title;
	}
    void setCompletionPercentage(float completionPercentage) {
		this.completionPercentage = completionPercentage;
	}
}
