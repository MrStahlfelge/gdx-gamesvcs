package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.Array;
import com.huawei.hms.jos.games.RankingsClient;
import com.huawei.hms.jos.games.achievement.Achievement;
import com.huawei.hms.jos.games.ranking.RankingScore;

import java.util.List;

import de.golfgl.gdxgamesvcs.achievement.IAchievement;
import de.golfgl.gdxgamesvcs.leaderboard.ILeaderBoardEntry;

public class HuaweiGameServicesUtils {
    public static Array<IAchievement> getIAchievementsList(List<Achievement> achievements) {
        Array<IAchievement> list = new Array<>();

        for (Achievement achievement: achievements) {
            list.add(new HuaweiAchievement(achievement));
        }

        return list;
    }

    public static Array<ILeaderBoardEntry> getILeaderboardsEntriesList(RankingsClient.RankingScores rankingScores,
                                                                       String currentPlayerId) {
        Array<ILeaderBoardEntry> list = new Array<>();
        List<RankingScore> scores = rankingScores.getRankingScores();

        for (int i = 0; i < scores.size(); i++) {
            list.add(new HuaweiLeadeboardScore(scores.get(i), currentPlayerId, i));
        }

        return list;
    }
}
