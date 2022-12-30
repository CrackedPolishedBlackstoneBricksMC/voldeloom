package net.fabricmc.loom.util;

import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.zip.GZIPInputStream;

/**
 * Very largely based off of Loom's old DownloadUtil, but it's more builder-flavored now
 * Also allows to turn off gzip support because java's gzipinputstream is a bit funky x)
 */
public class DownloadSession {
	public DownloadSession() {}
	
	public DownloadSession(String url) {
		url(url);
	}
	
	public DownloadSession(String url, Logger logger) {
		url(url);
		this.logger = logger;
	}
	
	private URL url;
	private Path dest;
	private boolean useEtag = false, requestGzip = true;
	private Logger logger;
	
	public DownloadSession url(String url) {
		try {
			this.url = new URL(url);
		} catch (MalformedURLException pls) {
			throw new IllegalArgumentException("Skill issue", pls);
		}
		return this;
	}
	
	public DownloadSession dest(Path dest) {
		this.dest = dest;
		return this;
	}
	
	public DownloadSession etag(boolean etag) {
		this.useEtag = etag;
		return this;
	}
	
	public DownloadSession gzip(boolean gzip) {
		this.requestGzip = gzip;
		return this;
	}
	
	public DownloadSession logger(Logger logger) {
		this.logger = logger;
		return this;
	}
	
	public void download() throws IOException {
		Preconditions.checkNotNull(url, "url");
		Preconditions.checkNotNull(dest, "dest");
		
		Files.createDirectories(dest.getParent());
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		
		Path etagFile = dest.resolveSibling(dest.getFileName().toString() + ".etag");
		String knownEtag;
		if(useEtag && Files.exists(etagFile)) {
			knownEtag = new String(Files.readAllBytes(etagFile), StandardCharsets.UTF_8);
			conn.setRequestProperty("If-None-Match", knownEtag);
			if(Files.exists(dest)) conn.setIfModifiedSince(Files.getLastModifiedTime(dest).toMillis());
		}
		
		if(requestGzip) conn.setRequestProperty("Accept-Encoding", "gzip");
		
		lifecycle("Establishing connection to {} (using etag header: {}, requested gzip: {})", url.toString(), useEtag, requestGzip);
		conn.connect();
		
		int code = conn.getResponseCode();
		if((code < 200 || code >= 300) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
			//unexpected response code
			throw new IOException("Got " + code + " " + conn.getResponseMessage() + " from connection to " + url);
		}
		
		long srvModtime = conn.getHeaderFieldDate("Last-Modified", -1);
		if(code == HttpURLConnection.HTTP_NOT_MODIFIED) {
			info("Not Modified (etag match)");
			return;
		} else if (Files.exists(dest) && srvModtime > 0 && Files.getLastModifiedTime(dest).toMillis() >= srvModtime) {
			info("Not downloading more, our copy is new enough");
			return;
		}
		
		lifecycle("Saving to {} ", dest);
		try(InputStream in = "gzip".equals(conn.getContentEncoding()) ? new GZIPInputStream(conn.getInputStream()) : conn.getInputStream()) {
			Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			//not keeping this
			try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
			throw e;
		}
		
		if(srvModtime > 0) Files.setLastModifiedTime(dest, FileTime.fromMillis(srvModtime));
		
		String srvEtag = conn.getHeaderField("ETag");
		if(useEtag && srvEtag != null) {
			info("Saving etag to {} ", etagFile);
			Files.write(etagFile, srvEtag.getBytes(StandardCharsets.UTF_8));
		}
	}
	
	private void info(String x, Object... fmt) {
		if(logger != null) logger.info(x, fmt);
	}
	
	private void lifecycle(String x, Object... fmt) {
		if(logger != null) logger.lifecycle(x, fmt);
	}
}
