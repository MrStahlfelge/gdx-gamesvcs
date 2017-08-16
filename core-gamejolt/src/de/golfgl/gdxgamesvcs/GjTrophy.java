package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.JsonValue;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;

/**
 * GameJolt Trophy
 * <p>
 * Created by Benjamin Schulte on 16.08.2017.
 */

public class GjTrophy implements IAchievement {
    protected String trophyId;
    protected String trophyTitle;
    protected String trophyDesc;
    protected String iconUrl;
    protected String trophyAchieved;
    protected String difficulty;

    protected IGameServiceIdMapper<Integer> trophyMapper;

    protected static GjTrophy fromJson(JsonValue json) {
        GjTrophy trophy = new GjTrophy();
        trophy.difficulty = json.getString("difficulty");
        trophy.trophyAchieved = json.getString("achieved");
        trophy.iconUrl = json.getString("image_url");
        trophy.trophyDesc = json.getString("description");
        trophy.trophyTitle = json.getString("title");
        trophy.trophyId = json.getString("id");

        return trophy;
    }

    public IGameServiceIdMapper<Integer> getTrophyMapper() {
        return trophyMapper;
    }

    protected void setTrophyMapper(IGameServiceIdMapper<Integer> trophyMapper) {
        this.trophyMapper = trophyMapper;
    }

    @Override
    public String getAchievementId() {
        return trophyId;
    }

    @Override
    public boolean isAchievementId(String achievementId) {
        if (trophyMapper == null)
            throw new IllegalStateException("No trophy mapper given");

        Integer mappedId = trophyMapper.mapToGsId(achievementId);

        return mappedId != null && mappedId.toString().equals(getAchievementId());
    }

    @Override
    public String getTitle() {
        return trophyTitle;
    }

    @Override
    public String getDescription() {
        return trophyDesc;
    }

    @Override
    public float getCompletionPercentage() {
        return (isUnlocked() ? 1f : 0);
    }

    @Override
    public boolean isUnlocked() {
        return (trophyAchieved != null && !trophyAchieved.equalsIgnoreCase("false"));
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

}
