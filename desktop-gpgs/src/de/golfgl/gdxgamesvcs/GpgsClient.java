package de.golfgl.gdxgamesvcs;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.games.model.Player;

import de.golfgl.gdxgamesvcs.GameServiceException.NotSupportedException;
import de.golfgl.gdxgamesvcs.IGameServiceListener.GsErrorType;

/**
 * TODO doc
 * 
 * As stated in {@link IGameServiceClient} all methods of this interface are thread safe, non blocking
 * and typically called from GLThread.
 * 
 * All *Sync methods are blocking and could be chained each others in a user thread.
 * TODO are they thread safe ???
 * 
 * TODO note about how to implements showAchievements ...
 * 
 * 
 * @author mgsx
 *
 */
public class GpgsClient implements IGameServiceClient
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
	
	/**
	 * TODO doc
	 * @author mgsx
	 *
	 */
	protected static interface SafeRunnable{
		void run() throws IOException;
	}
	
	/**
	 * TODO doc
	 * @param runnable
	 */
	protected void background(final SafeRunnable runnable){
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					runnable.run();
				} catch (IOException e) {
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
				Gdx.app.error(TAG, "Failed to retreive player name", e); // TODO silent ?
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
		// TODO should we dispatch this event ?
		// if(gameListener != null) gameListener.gsDisconnected();
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
		// TODO nope but could provides leaderboards data!
		return false;
	}

	@Override
	public void showLeaderboards(String leaderBoardId) throws GameServiceException {
		throw new NotSupportedException();
	}

	@Override
	public boolean providesAchievementsUI() {
		// TODO nope but could provides achievements data!
		return false;
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
	
	protected void submitToLeaderboardSync(String leaderboardId, long score, String tag) throws IOException {
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
	protected void submitEventSync(String eventId, int increment) {
		// TODO don't know the API ...
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
	protected void unlockAchievementSync(String achievementId) throws IOException {
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
	
	protected void incrementAchievementSync(String achievementId, int incNum, float completionPercentage) throws IOException {
		GAPIGateway.games.achievements().increment(achievementId, incNum).execute();
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
	protected void saveGameStateSync(String fileId, byte[] gameState, long progressValue) throws IOException {
			
		java.io.File file = java.io.File.createTempFile("games", "dat");
		new FileHandle(file).writeBytes(gameState, false);
		
		// no type since it is binary data
		FileContent mediaContent = new FileContent(null, file);
		
		// find file on server
		
		// TODO escape some chars (') see : https://developers.google.com/drive/v3/web/search-parameters#fn1
		List<File> files = GAPIGateway.drive.files().list().setSpaces("appDataFolder").setQ("name='" + fileId + "'").execute().getFiles();
		if(files.size() > 1){
			throw new GdxRuntimeException("multiple files with name " + fileId + " exists.");
		}
		
		// file exists then update it
		if(files.size() > 0){
			
			File remoteFile = files.get(0);
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
			
			File remoteFile = GAPIGateway.drive.files().create(fileMetadata, mediaContent)
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
	
	protected byte [] loadGameStateSync(String fileId) throws IOException {
		
		// TODO call gameListener.gsGameStateLoaded(null); if failed !!!
		
		InputStream stream = null;
		byte [] data = null;
		try {
			// TODO refactor
			List<File> files = GAPIGateway.drive.files().list().setSpaces("appDataFolder").setQ("name='" + fileId + "'").execute().getFiles();
			if(files.size() > 1){
				throw new GdxRuntimeException("multiple files with name " + fileId + " exists.");
			}
			if(files.size() < 1){
				
			}
			else
			{
				File remoteFile = files.get(0);
				// note that size metadata can be null ...
				
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
		return CloudSaveCapability.MultipleFilesSupported;
	}

}
