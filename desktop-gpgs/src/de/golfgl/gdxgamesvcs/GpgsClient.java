package de.golfgl.gdxgamesvcs;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.games.model.AchievementDefinition;
import com.google.api.services.games.model.Leaderboard;
import com.google.api.services.games.model.LeaderboardEntry;
import com.google.api.services.games.model.LeaderboardScores;
import com.google.api.services.games.model.Player;
import com.google.api.services.games.model.PlayerAchievement;

import de.golfgl.gdxgamesvcs.GameServiceException.NotSupportedException;
import de.golfgl.gdxgamesvcs.IGameServiceListener.GsErrorType;
import de.golfgl.gdxgamesvcs.LeaderBoard.Score;

/**
 * Google Play Games Services Desktop implementation based on REST API :
 * https://developers.google.com/games/services/web/api/
 * 
 * "Installed Application" authentication method is used as described here :
 * https://developers.google.com/identity/protocols/OAuth2#scenarios
 * 
 * Implementation based on Java API :
 * https://developers.google.com/api-client-library/java/google-api-java-client/oauth2
 * 
 * As stated in {@link IGameServiceClient} all methods of this interface are thread safe, non blocking
 * and typically called from GLThread.
 * 
 * All *Sync methods are blocking and could be chained each others in a user-defined thread
 * for advanced usage.
 * 
 * Service must be initialized prior to call other methods except features querying
 * {@link #isFeatureSupported(de.golfgl.gdxgamesvcs.IGameServiceClientEx.GameServiceFeature)}
 * It is recommended to call one of {@link #initialize(String, FileHandle)} or {@link #initialize(String, InputStream)}
 * at application startup.
 * 
 * Credential storage default behavior can be overridden by subclasses, see :
 * {@link #getDataStoreDirectory()} and {@link #getUserId()}
 * 
 * @author mgsx
 *
 */
public class GpgsClient implements IGameServiceClientEx
{
	private static final String TAG = "GpgsClient";
	
	/**
	 * Shortcut for current user as per Google API doc.
	 */
	private static final String ME = "me";

	/** current application name */
	protected String applicationName;
	
	private IGameServiceListener gameListener;
	
	private Thread authorizationThread;

	private volatile boolean connected;
	
	private volatile boolean connecting;
	
	private boolean initialized;

	private String playerName;
	
	private static interface SafeRunnable{
		void run() throws IOException;
	}
	
	private void background(final SafeRunnable runnable){
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					runnable.run();
				} catch (Throwable e) {
					// Always log errors to be able to analize stacktrace.
					Gdx.app.error(TAG, "GpgsClient Error", e);
					if(gameListener != null) gameListener.gsErrorMsg(GsErrorType.errorUnknown, e.getMessage());
				}
			}
		}).start();
	}
	
	@Override
	public String getGameServiceId() {
		return IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;
	}

	@Override
	public void setListener(IGameServiceListener gsListener) {
		gameListener = gsListener;
	}
	
	/**
	 * Get the data store directory for your app.
	 * This is where users credential (token) will be stored.
	 * 
	 * Subclass may override this method in order to provide another location.
	 * 
	 * Default is <USER_HOME>/.store/<APPLICATION_NAME>
	 * 
	 * @param dataStoreDirectory where to store users credentials for this application
	 */
	protected java.io.File getDataStoreDirectory() {
		java.io.File dataStoresDirectory = new java.io.File(System.getProperty("user.home"), ".store");
		return new java.io.File(dataStoresDirectory, applicationName);
	}
	
	/**
	 * Provide a user identifier for the current user. It is only used to store/restore user
	 * credentials (API token) during authorization ({@link #connect(boolean)}.
	 * 
	 * Subclass may override this method in order to provide dynamically a user ID based on their own
	 * login system and want to store different credentials for different users.
	 * 
	 * Default is the OS user name.
	 * 
	 * @return a current user identifier, shouldn't be null.
	 */
	protected String getUserId(){
		return System.getProperty("user.name");
	}
	
	/**
	 * Initialize connector. Must be called at application startup.
	 * @param applicationName Application name registered in Google Play.
	 * @param clientSecret client/secret json data you get from Google Play.
	 * 
	 * Format is :
	 * <pre>
	 * {
	 *   "installed": {
	 *     "client_id": "xxxxxxx-yyyyyyyyyyyyyyyyy.apps.googleusercontent.com",
	 *     "client_secret": "zzzzzzzzzzzzzzzzzzzzzzzzz"
	 *   }
	 * }
	 * </pre>
	 * 
	 * @throws GdxRuntimeException if initialisation fails.
	 */
	public void initialize(String applicationName, InputStream clientSecret){
		this.applicationName = applicationName;
		try {
			GAPIGateway.init(applicationName, clientSecret, getDataStoreDirectory());
			initialized = true;
		} catch (GeneralSecurityException e) {
			throw new GdxRuntimeException(e);
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
	}
	
	/**
	 * Initialize with a clientSecretFile. 
	 * @see {@link #initialize(String, InputStream)}
	 * @param applicationName
	 * @param clientSecretFile
	 */
	public void initialize(String applicationName, FileHandle clientSecretFile){
		initialize(applicationName, clientSecretFile.read());
	}
	
	/**
	 * Try to authorize user. This method is blocking until user accept
	 * autorization.
	 */
	private void waitForUserAuthorization()
	{
		// load user token or open browser for user authorizations.
		boolean success = false;
		try {
			GAPIGateway.authorize(getUserId());
			success = true;
		} catch (IOException e) {
			Gdx.app.error(TAG, "failed to get authorization from user", e);
			if(gameListener != null) gameListener.gsErrorMsg(GsErrorType.errorUnknown, "failed to connect");
		}
		
		// try to retreive palyer name
		if(success){
			try {
				Player player = GAPIGateway.games.players().get(ME).execute();
				playerName = player.getDisplayName();
			} catch (IOException e) {
				Gdx.app.error(TAG, "Failed to retreive player name", e);
				if(gameListener != null) gameListener.gsErrorMsg(GsErrorType.errorUnknown, "Failed to retreive player name");
			}
		}
		
		connected = success;
		
		// dispatch status
		if(gameListener != null){
			if(connected){
				gameListener.gsConnected();
			}else{
				gameListener.gsDisconnected();
			}
		}
	}
	
	@Override
	public boolean connect(boolean silent) {
		if(!connected && !connecting){
			connecting = true;
			playerName = null;
			authorizationThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try{
						waitForUserAuthorization(); 
					}finally{
						connecting = false;
					}
				}
			}, "GpgsAuthorization");
			authorizationThread.start();
		}
		// return false only if client has not been properly initialized.
		return initialized;
	}

	@Override
	public void disconnect() {
		// nothing special to do here since there is no resources to freeup.
	}

	@Override
	public void logOff() {
		disconnect();
		connected = false;
		playerName = null;
		GAPIGateway.closeSession();
	}

	@Override
	public String getPlayerDisplayName() {
		return playerName;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public boolean isConnectionPending() {
		return connecting;
	}

	@Override
	public boolean providesLeaderboardUI() {
		// for backward compatibility.
		return isFeatureSupported(GameServiceFeature.leaderboardsUI);
	}

	@Override
	public void showLeaderboards(String leaderBoardId) throws GameServiceException {
		throw new NotSupportedException();
	}

	@Override
	public boolean providesAchievementsUI() {
		// for backward compatibility.
		return isFeatureSupported(GameServiceFeature.achievementsUI);
	}

	@Override
	public void showAchievements() throws GameServiceException {
		throw new NotSupportedException();
	}

	@Override
	public boolean submitToLeaderboard(final String leaderboardId, final long score, final String tag) {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				submitToLeaderboardSync(leaderboardId, score, tag);
			}
		});
		return initialized;
	}
	
	/**
	 * Blocking version of {@link #submitToLeaderboard(String, long, String)}
	 * @param leaderboardId
	 * @param score
	 * @param tag
	 * @throws IOException
	 */
	public void submitToLeaderboardSync(String leaderboardId, long score, String tag) throws IOException {
		GAPIGateway.games.scores().submit(leaderboardId, score).execute();
	}


	@Override
	public boolean submitEvent(final String eventId, final int increment) {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				submitEventSync(eventId, increment);
			}
		});
		return initialized;
	}
	
	/**
	 * Blocking version of {@link #submitEvent(String, int)}
	 * @param eventId
	 * @param increment
	 */
	public void submitEventSync(String eventId, int increment) {
		// TODO don't know the API for this use case
		throw new IllegalStateException("Not implemented");
	}
	

	@Override
	public boolean unlockAchievement(final String achievementId) {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				unlockAchievementSync(achievementId);
			}
		});
		return initialized;
	}
	
	/**
	 * Blocking version of {@link #unlockAchievement(String)}
	 * @param achievementId
	 * @throws IOException
	 */
	public void unlockAchievementSync(String achievementId) throws IOException {
		GAPIGateway.games.achievements().unlock(achievementId).execute();
	}

	@Override
	public boolean incrementAchievement(final String achievementId, final int incNum, final float completionPercentage) {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				incrementAchievementSync(achievementId, incNum, completionPercentage);
			}
		});
		return initialized;
	}
	
	/**
	 * Blocking version of {@link #incrementAchievement(String, int, float)}
	 * @param achievementId
	 * @param incNum
	 * @param completionPercentage
	 * @throws IOException
	 */
	public void incrementAchievementSync(String achievementId, int incNum, float completionPercentage) throws IOException {
		GAPIGateway.games.achievements().increment(achievementId, incNum).execute();
	}

	@Override
	public void fetchGameStates(final IGameStatesCallback callback) {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				Array<String> result = null;
				try{
					result = fetchGamesSync();
				}finally{
					final Array<String> fresult = result;
					Gdx.app.postRunnable(new Runnable() {
						@Override
						public void run() {
							callback.onGameStatesResponse(fresult);
						}
					});
				}
			}
		});
	}
	
	/**
	 * Blocking version of {@link #fetchGamesSync()}
	 * @return
	 * @throws IOException
	 */
	public Array<String> fetchGamesSync() throws IOException {
		
		Array<String> games = new Array<String>();

		FileList l = GAPIGateway.drive.files().list()
				.setSpaces("appDataFolder")
				.setFields("files(name)")
				.execute();
		
		for(File f : l.getFiles()){
			games.add(f.getName());
		}
		
		return games;
	}
	
	@Override
	public void deleteGameState(final String fileId) {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				deleteGameStateSync(fileId);
			}
		});
	}
	public void deleteGameStateSync(String fileId) throws IOException {
		File remoteFile = findFileByNameSync(fileId);
		if(remoteFile != null){
			GAPIGateway.drive.files().delete(remoteFile.getId()).execute();
		}
	}

	
	@Override
	public void saveGameState(final String fileId, final byte[] gameState, final long progressValue) throws GameServiceException {
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				saveGameStateSync(fileId, gameState, progressValue);
			}
		});
	}
	
	private File findFileByNameSync(String name) throws IOException{
		// escape some chars (') see : https://developers.google.com/drive/v3/web/search-parameters#fn1
		List<File> files = GAPIGateway.drive.files().list().setSpaces("appDataFolder").setQ("name='" + name + "'").execute().getFiles();
		if(files.size() > 1){
			throw new GdxRuntimeException("multiple files with name " + name + " exists.");
		} else if(files.size() < 1) {
			return null;
		} else {
			return files.get(0);
		}
	}
	
	/**
	 * Blocking version of {@link #saveGameState(String, byte[], long)}
	 * @param fileId
	 * @param gameState
	 * @param progressValue
	 * @throws IOException
	 */
	public void saveGameStateSync(String fileId, byte[] gameState, long progressValue) throws IOException {
			
		java.io.File file = java.io.File.createTempFile("games", "dat");
		new FileHandle(file).writeBytes(gameState, false);
		
		// no type since it is binary data
		FileContent mediaContent = new FileContent(null, file);
		
		// find file on server
		File remoteFile = findFileByNameSync(fileId);
		
		// file exists then update it
		if(remoteFile != null){
			
			// just update content, leave metadata intact.
			
			GAPIGateway.drive.files().update(remoteFile.getId(), null, mediaContent).execute();
			
			Gdx.app.log("GAPI", "File updated ID: " + remoteFile.getId());
		}
		// file doesn't exists then create it
		else{
			File fileMetadata = new File();
			fileMetadata.setName(fileId);
			
			// app folder is a reserved keyyword for current application private folder.
			fileMetadata.setParents(Collections.singletonList("appDataFolder"));
			
			remoteFile = GAPIGateway.drive.files().create(fileMetadata, mediaContent)
					.setFields("id")
					.execute();
			
			Gdx.app.log("GAPI", "File created ID: " + remoteFile.getId());
		}
			
	}

	@Override
	public void loadGameState(final String fileId) throws GameServiceException {
		background(new SafeRunnable() {
			
			@Override
			public void run() throws IOException {
				try{
					byte[] data = loadGameStateSync(fileId);
					if(gameListener != null) gameListener.gsGameStateLoaded(data);
				}catch(IOException e){
					if(gameListener != null) gameListener.gsGameStateLoaded(null);
					throw e;
				}
			}
		});
	}
	
	/**
	 * Blocking version of {@link #loadGameState(String)}
	 * @param fileId
	 * @return
	 * @throws IOException
	 */
	public byte [] loadGameStateSync(String fileId) throws IOException {
		
		InputStream stream = null;
		byte [] data = null;
		try {
			File remoteFile = findFileByNameSync(fileId);
			if(remoteFile != null){
				
				stream = GAPIGateway.drive.files().get(remoteFile.getId()).executeMediaAsInputStream();
			
				data = StreamUtils.copyStreamToByteArray(stream);
			}
		} finally {
			StreamUtils.closeQuietly(stream);
		}
		return data;
	}

	@Override
	public CloudSaveCapability supportsCloudGameState() {
		// for backward compatibility.
		return CloudSaveCapability.MultipleFilesSupported;
	}

	@Override
	public void fetchAchievements(final boolean fetchIcons, final IAchievementCallback callback)
	{
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				Array<Achievement> result = null;
				try{
					result = fetchAchievementsSync(fetchIcons);
				}finally{
					final Array<Achievement> fresult = result;
					Gdx.app.postRunnable(new Runnable() {
						@Override
						public void run() {
							callback.onAchievementsResponse(fresult);
						}
					});
				}
			}
		});
	}
	
	/**
	 * Blocking version of {@link #fetchAchievements(boolean, IAchievementCallback)}
	 * @param fetchIcons
	 * @return
	 * @throws IOException
	 */
	public Array<Achievement> fetchAchievementsSync(boolean fetchIcons) throws IOException {
		
		Array<Achievement> achievements = new Array<Achievement>();
		
		// fetch all definitions
		ObjectMap<String, AchievementDefinition> defs = new ObjectMap<String, AchievementDefinition>();
		for(AchievementDefinition def : GAPIGateway.games.achievementDefinitions().list().execute().getItems()){
			defs.put(def.getId(), def);
		}
		
		// Fetch player achievements
		for(PlayerAchievement p : GAPIGateway.games.achievements().list(ME).execute().getItems()){
			AchievementDefinition def = defs.get(p.getId());
			
			String state = p.getAchievementState();
			
			// filter hidden achievements : there is no reasons to display these
			// to the player. User code could unlock/increment it anyway and user code
			// can check if this achievement is hidden by its absence in the returned list.
			if("HIDDEN".equals(state)) continue;
			
			Achievement a = new Achievement();
			a.id = def.getId();
			a.name = def.getName();
			a.description = def.getDescription();
			
			boolean isIncremental = "INCREMENTAL".equals(def.getAchievementType());
			boolean unlocked = "UNLOCKED".equals(state);
			
			if(unlocked){
				a.progress = 100;
			}
			else if(isIncremental){
				int currentSteps = 0;
				if(p.getCurrentSteps() != null){
					currentSteps =  p.getCurrentSteps().intValue();
				}
				// total steps range between 2 and 10.000
				int totalSteps = def.getTotalSteps().intValue();
				a.progress = MathUtils.floorPositive((float)currentSteps * 100 / (float)totalSteps);
			}else{
				a.progress = 0;
			}
			a.iconUrl = unlocked ? def.getUnlockedIconUrl() : def.getRevealedIconUrl();
			
			if(fetchIcons && a.iconUrl != null){
				a.icon = downloadIconSyn(a.iconUrl);
			}
			
			achievements.add(a);
		}
		
		return achievements;
	}
	
	@Override
	public void fetchLeaderboard(final String leaderBoardId, final boolean aroundPlayer, final boolean friendsOnly, final boolean fetchIcons, final ILeaderBoardCallback callback)
	{
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				LeaderBoard result = null;
				try{
					result = fetchLeaderboardSync(leaderBoardId, aroundPlayer, friendsOnly, fetchIcons);
				}finally{
					final LeaderBoard fresult = result;
					Gdx.app.postRunnable(new Runnable() {
						@Override
						public void run() {
							callback.onLeaderBoardResponse(fresult);
						}
					});
				}
			}
		});
	}
	
	/**
	 * Blocking version of {@link #fetchLeaderboard(String, boolean, boolean, boolean, ILeaderBoardCallback)}
	 * @param leaderBoardId
	 * @throws IOException
	 */
	public LeaderBoard fetchLeaderboardSync(String leaderBoardId, boolean aroundPlayer, boolean friendsOnly, boolean fetchIcons) throws IOException
	{
		LeaderBoard result = new LeaderBoard();
		Leaderboard lb = GAPIGateway.games.leaderboards().get(leaderBoardId).execute();
		result.id = lb.getId();
		result.name = lb.getName();
		result.scores = new Array<LeaderBoard.Score>();
		result.iconUrl = lb.getIconUrl();
		if(fetchIcons && result.iconUrl != null){
			result.icon = downloadIconSyn(result.iconUrl);
		}
		
		LeaderboardScores r; 
		if(aroundPlayer){
			r = GAPIGateway.games.scores().listWindow(leaderBoardId, friendsOnly ? "SOCIAL" : "PUBLIC", "ALL_TIME").execute();
		}else{
			r = GAPIGateway.games.scores().list(leaderBoardId, friendsOnly ? "SOCIAL" : "PUBLIC", "ALL_TIME").execute();
		}
		LeaderboardEntry playerScore = r.getPlayerScore();
		// player may not have a score yet.
		if(playerScore != null){
			LeaderBoard.Score ps = mapPlayerScore(r.getPlayerScore(), fetchIcons);
			ps.currrentPlayer = true;
			result.scores.add(ps);
		}
		// r.getItems is null when no score has been submitted yet.
		if(r.getItems() != null){
			for(LeaderboardEntry score : r.getItems()){
				LeaderBoard.Score s = mapPlayerScore(score, fetchIcons);
				s.currrentPlayer = false;
				result.scores.add(s);
			}
		}
		
		// TODO maybe already sorted but API doesn't say anything : 
		// https://developers.google.com/games/services/web/api/scores/list
		// And test is required to se if it is OK ...
		// so we sort list depending of score meaning.
		final int order = "SMALLER_IS_BETTER".equals(lb.getOrder()) ? 1 : -1;
		result.scores.sort(new Comparator<LeaderBoard.Score>() {
			@Override
			public int compare(Score o1, Score o2) {
				return order * Long.compare(o1.sortKey, o2.sortKey);
			}
		});
		return result;
	}
	
	private LeaderBoard.Score mapPlayerScore(LeaderboardEntry score, boolean fetchAvatar) throws IOException{
		LeaderBoard.Score s = new LeaderBoard.Score();
		s.name = score.getPlayer().getDisplayName();
		s.rank = score.getFormattedScoreRank();
		s.score = score.getFormattedScore();
		s.avatarUrl = score.getPlayer().getAvatarImageUrl();
		s.sortKey = score.getScoreValue() != null ? score.getScoreValue().longValue() : 0;
		if(fetchAvatar && s.avatarUrl != null){
			s.avatar = downloadIconSyn(s.avatarUrl);
		}
		return s;
	}
	
	private Pixmap downloadIconSyn(String iconUrl) throws IOException{
		byte[] bytes = downloadSync(iconUrl);
		return new Pixmap(bytes, 0, bytes.length);
	}
	
	private static byte[] downloadSync(String url) throws IOException {
		InputStream in = null;
		try {
			HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
			conn.setDoInput(true);
			conn.setDoOutput(false);
			conn.setUseCaches(true);
			conn.connect();
			in = conn.getInputStream();
			return StreamUtils.copyStreamToByteArray(in);
		} catch (IOException ex) {
			throw ex;
		} finally {
			StreamUtils.closeQuietly(in);
		}
	}

	@Override
	public void showGameStates() {
		throw new IllegalStateException("not supported");
	}

	@Override
	public boolean isFeatureSupported(GameServiceFeature feature) {
		switch(feature){
		case achievementsList: 
		case gameStatesList: 
		case leaderBoardList: 
		case gameStateDelete:
		case gameStateMultiple:
		case gameStateStorage:
			return true;
		default:
			return false;
		}
	}
	
}
