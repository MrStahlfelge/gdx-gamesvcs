package de.golfgl.gdxgamesvcs;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.StreamUtils;

public class DownloadUtil {

	public static Pixmap downloadImage(String imageUrl) throws IOException{
		byte[] bytes = download(imageUrl);
		return new Pixmap(bytes, 0, bytes.length);
	}
	
	public static byte[] download(String url) throws IOException {
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

}
