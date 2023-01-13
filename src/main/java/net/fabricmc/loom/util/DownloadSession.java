package net.fabricmc.loom.util;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.Constants;
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
		
		boolean destExists = Files.exists(dest);
		
		//If we're offline, assume the file is up-to-date enough; and if we don't have the file, there's no way to get it.
		if(Constants.offline) {
			if(destExists) {
				info("Not connecting to {} because {} exists and we're in offline mode.", url, dest);
				return;
			} else throw new IllegalStateException("Need to download " + url + " to " + dest + ", but Gradle was started in offline mode. Aborting."); 
		}
		
		//More fine-grained up-to-dateness checks that we skip in refreshDependencies mode.
		if(destExists && !Constants.refreshDependencies) {
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
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection(); //doesnt actually open html connection yet
		
		//Read the locally known etag, if one exists, and set the etag header.
		String knownEtag = null;
		Path etagFile = dest.resolveSibling(dest.getFileName().toString() + ".etag");
		if(useEtag && destExists && Files.exists(etagFile) && !Constants.refreshDependencies) {
			knownEtag = new String(Files.readAllBytes(etagFile), StandardCharsets.UTF_8);
			conn.setRequestProperty("If-None-Match", knownEtag);
			conn.setIfModifiedSince(Files.getLastModifiedTime(dest).toMillis());
		}
		
		//Request a gzip header, if compression was requested.
		if(requestGzip) conn.setRequestProperty("Accept-Encoding", "gzip");
		
		//Actually connect.
		lifecycle("Establishing connection to {} (sending etag header: {}, gzip encoding: {})...", url, knownEtag != null, requestGzip);
		conn.connect();
		
		//We'll take a 304, or something in the OK section.
		int code = conn.getResponseCode();
		if(code == HttpURLConnection.HTTP_NOT_MODIFIED) {
			lifecycle("\\-> Not Modified (etag match)"); //The server *shouldn't* send a 304 if we didn't send an etag?
			return;
		} else if(code / 100 != 2) {
			throw new IOException("Got " + code + " " + conn.getResponseMessage() + " from connection to " + url);
		}
		
		//Download the entire file and save it to disk.
		lifecycle("\\-> Saving to {} ", dest);
		Files.createDirectories(dest.getParent());
		try(InputStream in = "gzip".equals(conn.getContentEncoding()) ? new GZIPInputStream(conn.getInputStream()) : conn.getInputStream()) {
			Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			//don't keep a half-downloaded file, if we can
			try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
			throw e;
		}
		
		//Save the etag to disk too, if one was sent alongside the file.
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
