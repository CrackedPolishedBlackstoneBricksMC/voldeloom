package net.fabricmc.loom.util;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.zip.GZIPInputStream;

/**
 * Utilities for downloading files from HTTP servers.
 * 
 * (Very largely based off of Loom's old DownloadUtil, but it's more builder-flavored now
 * Also allows to turn off gzip support because java's gzipinputstream is a bit funky x))
 */
public class DownloadSession {
	public DownloadSession(Project project) {
		this.project = project;
	}
	
	public DownloadSession(String url, Project project) {
		this(project);
		url(url);
	}
	
	private URL url;
	private Path dest;
	//If the server sends an ETag, save it in an auxillary file and send it back to the server when rerequesting the file
	private boolean useEtag = false;
	//Send an Accept-Encoding: gzip header and decompress the file on the client
	private boolean requestGzip = true;
	
	private boolean skipIfExists = false;
	private @Nullable String skipIfSha1 = null;
	private @Nullable TemporalAmount skipIfNewerThan = null;
	
	private final Project project;
	private boolean quiet;
	
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
	
	public DownloadSession skipIfExists() {
		this.skipIfExists = true;
		return this;
	}
	
	public DownloadSession skipIfSha1Equals(@Nullable String skipIfSha1) {
		this.skipIfSha1 = skipIfSha1;
		return this;
	}
	
	public DownloadSession skipIfNewerThan(@Nullable TemporalAmount skipIfNewerThan) { //e.g. "Period.ofDays()"
		this.skipIfNewerThan = skipIfNewerThan;
		return this;
	}
	
	public DownloadSession quiet() {
		this.quiet = true;
		return this;
	}
	
	public void download() throws IOException {
		Preconditions.checkNotNull(url, "url");
		Preconditions.checkNotNull(dest, "dest");
		
		Path etagFile = dest.resolveSibling(dest.getFileName().toString() + ".etag");
		boolean destExists = Files.exists(dest);
		boolean etagExists = Files.exists(etagFile);
		
		if(destExists) {
			if(skipIfExists) {
				info("Not connecting to {} because {} exists", url, dest);
				return;
			}
			if(skipIfSha1 != null && Checksum.compareSha1(dest, skipIfSha1)) {
				info("Not connecting to {} because {} exists and has correct SHA-1 hash ({})", url, dest, skipIfSha1);
				return;
			}
			if(skipIfNewerThan != null && Files.getLastModifiedTime(dest).toInstant().isAfter(Instant.now().minus(skipIfNewerThan))) {
				info("Not connecting to {} because {} exists and was downloaded within {}", url, dest, skipIfNewerThan);
				return;
			}
		}
		
		Files.createDirectories(dest.getParent());
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection(); //doesnt actually open html connection yet
		
		String knownEtag = null;
		if(useEtag && destExists && etagExists) {
			knownEtag = new String(Files.readAllBytes(etagFile), StandardCharsets.UTF_8);
			conn.setRequestProperty("If-None-Match", knownEtag);
			conn.setIfModifiedSince(Files.getLastModifiedTime(dest).toMillis());
		}
		
		if(requestGzip) conn.setRequestProperty("Accept-Encoding", "gzip");
		
		lifecycle("Establishing connection to {} (sending etag header: {}, gzip encoding: {})...", url.toString(), knownEtag != null, requestGzip);
		conn.connect();
		
		int code = conn.getResponseCode();
		if((code < 200 || code >= 300) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
			//unexpected response code
			throw new IOException("Got " + code + " " + conn.getResponseMessage() + " from connection to " + url);
		}
		
		if(code == HttpURLConnection.HTTP_NOT_MODIFIED) {
			info("\\-> Not Modified (etag match)");
			return;
		}
		
		lifecycle("\\-> Saving to {} ", dest);
		try(InputStream in = "gzip".equals(conn.getContentEncoding()) ? new GZIPInputStream(conn.getInputStream()) : conn.getInputStream()) {
			Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			//not keeping this
			try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
			throw e;
		}
		
		String srvEtag = conn.getHeaderField("ETag");
		if(useEtag && srvEtag != null) {
			info("\\-> Saving etag to {} ", etagFile);
			Files.write(etagFile, srvEtag.getBytes(StandardCharsets.UTF_8));
		}
	}
	
	private void info(String x, Object... fmt) {
		if(!quiet) project.getLogger().info(x, fmt);
	}
	
	private void lifecycle(String x, Object... fmt) {
		if(!quiet) project.getLogger().lifecycle(x, fmt);
	}
}
