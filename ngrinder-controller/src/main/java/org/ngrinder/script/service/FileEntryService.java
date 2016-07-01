/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ngrinder.script.service;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ngrinder.common.util.PathUtils;
import org.ngrinder.common.util.ThreadUtils;
import org.ngrinder.common.util.UrlUtils;
import org.ngrinder.infra.config.Config;
import org.ngrinder.model.User;
import org.ngrinder.script.handler.ProjectHandler;
import org.ngrinder.script.handler.ScriptHandler;
import org.ngrinder.script.handler.ScriptHandlerFactory;
import org.ngrinder.script.model.FileEntry;
import org.ngrinder.script.model.FileType;
import org.ngrinder.script.model.Request;
import org.ngrinder.script.repository.FileEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSHook;
import org.tmatesoft.svn.core.internal.io.fs.FSHookEvent;
import org.tmatesoft.svn.core.internal.io.fs.FSHooks;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.unmodifiableList;
import static org.ngrinder.common.util.CollectionUtils.buildMap;
import static org.ngrinder.common.util.CollectionUtils.newHashMap;
import static org.ngrinder.common.util.ExceptionUtils.processException;
import static org.ngrinder.common.util.Preconditions.checkNotEmpty;
import static org.ngrinder.common.util.Preconditions.checkNotNull;

/**
 * File entry service class.
 * <p>
 * This class is responsible for creating user svn repository whenever a user is
 * created and connect the user to the underlying svn.
 *
 * @author JunHo Yoon
 * @since 3.0
 */
@Service
public class FileEntryService {

	private static final Logger LOG = LoggerFactory.getLogger(FileEntryService.class);

	private SVNClientManager svnClientManager;

	@Autowired
	private Config config;


	@Autowired
	@Qualifier("cacheManager")
	private CacheManager cacheManager;

	@SuppressWarnings("SpringJavaAutowiringInspection")
	@Autowired
	private FileEntryRepository fileEntityRepository;

	@Autowired
	private ScriptHandlerFactory scriptHandlerFactory;

	private Cache fileEntryCache;

	/**
	 * Initialize {@link FileEntryService}.
	 */
	@PostConstruct
	public void init() {
		// Add cache invalidation hook.
		FSHooks.registerHook(new FSHook() {
			@Override
			public void onHook(FSHookEvent event) throws SVNException {
				if (event.getType().equals(FSHooks.SVN_REPOS_HOOK_POST_COMMIT)) {
					String name = event.getReposRootDir().getName();
					invalidateCache(name);
				}
			}
		});
		svnClientManager = fileEntityRepository.getSVNClientManager();
		fileEntryCache = cacheManager.getCache("file_entries");

	}

	/**
	 * invalidate the file_entry_search_cache.
	 *
	 * @param userId userId.
	 */
	public void invalidateCache(String userId) {
		fileEntryCache.evict(userId);
	}

	/**
	 * Create user svn repo.
	 * <p>
	 * This method is executed async way.
	 *
	 * @param user newly created user.
	 */
	@Async
	public void prepare(User user) {
		File newUserDirectory = getUserRepoDirectory(user);
		try {
			if (!newUserDirectory.exists()) {
				createUserRepo(user, newUserDirectory);
			}
		} catch (SVNException e) {
			LOG.error("Error while prepare user {}'s repo", user.getUserName(), e);
		}
	}

	private SVNURL createUserRepo(User user, File newUserDirectory) throws SVNException {
		return svnClientManager.getAdminClient().doCreateRepository(newUserDirectory, user.getUserId(), true, true);
	}

	private File getUserRepoDirectory(User user) {
		return new File(config.getHome().getRepoDirectoryRoot(), checkNotNull(user.getUserId()));
	}

	/**
	 * Get all {@link FileEntry} for the given user. This method is subject to
	 * be cached because it takes time.
	 *
	 * @param user user
	 * @return cached {@link FileEntry} list
	 */
	@Cacheable(value = "file_entries", key = "#user.userId")
	public List<FileEntry> getAll(User user) {
		prepare(user);
		List<FileEntry> allFileEntries;
		try {
			allFileEntries = fileEntityRepository.findAll(user);
		} catch (Exception e) {
			// Try once more for the case of the underlying file system fault.
			ThreadUtils.sleep(3000);
			allFileEntries = fileEntityRepository.findAll(user);
		}
		return unmodifiableList(allFileEntries);
	}

	/**
	 * Get file entries from underlying svn for given path.
	 *
	 * @param user     the user
	 * @param path     path in the repo
	 * @param revision revision number. -1 if HEAD.
	 * @return file entry list
	 */
	public List<FileEntry> getAll(User user, String path, Long revision) {
		// If it's not created, make one.
		prepare(user);
		return fileEntityRepository.findAll(user, path, revision);
	}

	/**
	 * Get file entity for the given revision.
	 *
	 * @param user     the user
	 * @param path     path in the repo
	 * @param revision revision. if -1, HEAD
	 * @return file entity
	 */
	public FileEntry getOne(User user, String path, Long revision) {
		SVNRevision svnRev = (revision == null || revision == -1) ? SVNRevision.HEAD : SVNRevision.create(revision);
		return fileEntityRepository.findOne(user, path, svnRev);
	}

	/**
	 * Get single file entity.
	 * <p>
	 * The return value has content byte.
	 *
	 * @param user the user
	 * @param path path in the svn repo
	 * @return single file entity
	 */
	public FileEntry getOne(User user, String path) {
		return getOne(user, path, -1L);
	}

	/**
	 * Check file existence.
	 *
	 * @param user user
	 * @param path path in user repo
	 * @return true if exists.
	 */
	public boolean hasFileEntry(User user, String path) {
		return fileEntityRepository.hasOne(user, path);
	}

	/**
	 * Add folder on the given path.
	 *
	 * @param user       user
	 * @param path       base path
	 * @param comment    comment
	 * @param folderName folder name
	 */
	public void addFolder(User user, String path, String folderName, String comment) {
		FileEntry entry = new FileEntry();
		entry.setPath(PathUtils.join(path, folderName));
		entry.setFileType(FileType.DIR);
		entry.setDescription(comment);
		fileEntityRepository.save(user, entry, null);
	}


	/**
	 * Save File entry.
	 *
	 * @param user      the user
	 * @param fileEntry fileEntry to be saved
	 */
	public void save(User user, FileEntry fileEntry) {
		prepare(user);
		checkNotEmpty(fileEntry.getPath());
		fileEntityRepository.save(user, fileEntry, fileEntry.getEncoding());
	}

	/**
	 * Delete file entries.
	 *
	 * @param user     the user
	 * @param basePath the base path
	 * @param files    files under base path
	 */
	public void delete(User user, String basePath, String[] files) {
		List<String> fullPathFiles = new ArrayList<String>();
		for (String each : files) {
			fullPathFiles.add(basePath + "/" + each);
		}
		fileEntityRepository.delete(user, fullPathFiles);
	}

	/**
	 * Delete file entry.
	 *
	 * @param user the user
	 * @param path the path
	 */
	public void delete(User user, String path) {
		fileEntityRepository.delete(user, newArrayList(path));
	}

	String getPathFromUrl(String urlString) {
		try {
			URL url = new URL(urlString);
			String urlPath = "/".equals(url.getPath()) ? "" : url.getPath();
			return (url.getHost() + urlPath).replaceAll("[;\\&\\?\\%\\$\\-\\#]", "_");
		} catch (MalformedURLException e) {
			throw processException("Error while translating " + urlString, e);
		}
	}

	String[] dividePathAndFile(String path) {
		int lastIndexOf = path.lastIndexOf("/");
		if (lastIndexOf == -1) {
			return new String[]{"", path};
		} else {
			return new String[]{path.substring(0, lastIndexOf), path.substring(lastIndexOf + 1)};
		}
	}

	/**
	 * Create new FileEntry.
	 *
	 * @param user           user
	 * @param path           base path path
	 * @param fileName       fileName
	 * @param name           name
	 * @param url            url
	 * @param scriptHandler  script handler
	 * @param libAndResource true if lib and resources should be created
	 * @return created file entry. main test file if it's the project creation.
	 */
	public FileEntry prepareNewEntry(User user, String path, String fileName, String name, String url,
	                                 ScriptHandler scriptHandler, boolean libAndResource, String options) {
		if (scriptHandler instanceof ProjectHandler) {
			scriptHandler.prepareScriptEnv(user, path, fileName, name, url, libAndResource,
				loadTemplate(user, getScriptHandler("groovy"), url, name, options));
			return null;
		}
		path = PathUtils.join(path, fileName);
		FileEntry fileEntry = new FileEntry();
		fileEntry.setPath(path);
		fileEntry.setContent(loadTemplate(user, scriptHandler, url, name, options));
		if (!"http://please_modify_this.com".equals(url)) {
			fileEntry.setProperties(buildMap("targetHosts", UrlUtils.getHost(url)));
		} else {
			fileEntry.setProperties(new HashMap<String, String>());
		}
		return fileEntry;
	}

	/**
	 * Create new FileEntry for the given URL.
	 *
	 * @param user          user
	 * @param url           URL to be tested.
	 * @param scriptHandler scriptHandler
	 * @return created new {@link FileEntry}
	 */
	public FileEntry prepareNewEntryForQuickTest(User user, String url,
	                                             ScriptHandler scriptHandler) {
		String path = getPathFromUrl(url);
		String host = UrlUtils.getHost(url);
		FileEntry quickTestFile = scriptHandler.getDefaultQuickTestFilePath(path);
		String nullOptions = null;
		if (scriptHandler instanceof ProjectHandler) {
			String[] pathPart = dividePathAndFile(path);
			prepareNewEntry(user, pathPart[0], pathPart[1], host, url, scriptHandler, false, nullOptions);
		} else {
			FileEntry fileEntry = prepareNewEntry(user, path, quickTestFile.getFileName(), host, url, scriptHandler,
				false, nullOptions);
			fileEntry.setDescription("Quick test for " + url);
			save(user, fileEntry);
		}
		return quickTestFile;
	}

	/**
	 * Load freemarker template for quick test.
	 *
	 * @param user    user
	 * @param handler handler
	 * @param url     url
	 * @param name    name
	 * @return generated test script
	 */
	public String loadTemplate(User user, ScriptHandler handler, String url, String name,
	                           String options) {
		Map<String, Object> map = newHashMap();
		map.put("url", url);
		map.put("userName", user.getUserName());
		map.put("name", name);
		map.put("options", options);
		return handler.getScriptTemplate(map);
	}

	/**
	 * Get a SVN content into given dir.
	 *
	 * @param user     user
	 * @param fromPath path in svn subPath
	 * @param toDir    to directory
	 */
	public void writeContentTo(User user, String fromPath, File toDir) {
		fileEntityRepository.writeContentTo(user, fromPath, toDir);
	}

	/**
	 * Get the appropriate {@link ScriptHandler} subclass for the given
	 * {@link FileEntry}.
	 *
	 * @param scriptEntry script entry
	 * @return scriptHandler
	 */
	public ScriptHandler getScriptHandler(FileEntry scriptEntry) {
		return scriptHandlerFactory.getHandler(scriptEntry);
	}

	/**
	 * Get the appropriate {@link ScriptHandler} subclass for the given
	 * ScriptHandler key.
	 *
	 * @param key script entry
	 * @return scriptHandler
	 */
	public ScriptHandler getScriptHandler(String key) {
		return scriptHandlerFactory.getHandler(key);
	}

	/**
	 * jsonFormat String To template.
	 *
	 * @param harContent har Content
	 * @param removeIncludeStaticCall remove Include Static Call
	 * @return paramMap result Map
	 * @throws JSONException
	 */
	private Map<String, Object> getHARContentToTemplate(String harContent, boolean removeIncludeStaticCall) throws JSONException {
		Map<String, Object> paramMap = newHashMap();
		Map<String, Object> commonHeaders = newHashMap();
		ArrayList<Request> harList = new ArrayList<Request>();
		JSONObject log = new JSONObject(harContent).getJSONObject("log");
		JSONArray entries = log.getJSONArray("entries");

		// Set commonHeaders.
		for (int i = 0; i < entries.length(); i++) {
			Map<String, Object> header = newHashMap();
			JSONObject obj = (JSONObject) entries.get(i);
			JSONObject req = obj.getJSONObject("request");
			if (removeIncludeStaticCall && !isCheckContentType(obj.getJSONObject("response"))) {
				continue;
			}

			if (i > 0) {
				header = convertMap(req, "headers", i);
				commonHeaders.entrySet().retainAll(header.entrySet());
			} else {
				commonHeaders = convertMap(req, "headers", i);
			}
		}

		for (int i = 0; i < entries.length(); i++) {
			Request request = new Request();
			Map<String, Object> perRequestHeaders = newHashMap();
			try {
				JSONObject obj = (JSONObject) entries.get(i);
				JSONObject res = obj.getJSONObject("response");
				JSONObject req = obj.getJSONObject("request");

				if (removeIncludeStaticCall) {
					if (!isCheckContentType(obj.getJSONObject("response"))) {
						continue;
					}
				}
				perRequestHeaders = convertMap(req, "headers", i);
				perRequestHeaders.entrySet().removeAll(commonHeaders.entrySet());

				request.setUrl(req.getString("url"));
				request.setMethod(req.getString("method"));
				request.setResponseState(res.getString("status"));
				request.setQueryString(convertMap(req, "queryString", i));
				request.setPerRequestHeaders(perRequestHeaders);
				harList.add(request);
			} catch (JSONException e) {
				LOG.error("ERROR HAR2Script getHARContentToTemplate JSONException", e);
				throw e;
			}
		}
		paramMap.put("request", harList);
		paramMap.put("commonHeaders", commonHeaders);
		return paramMap;
	}

	/**
	 * append map
	 *
	 * @param appendMap
	 * @param map
	 */
	private void appendMap(Map<String, Object> appendMap, Map<String, Object> map) {
		Iterator<String> keys = map.keySet().iterator();
		while(keys.hasNext()){
			String key = keys.next();
			String value = String.valueOf(map.get(key));
			if ((!appendMap.containsKey(key)) && (!appendMap.containsValue(value))) {
				appendMap.put(key, value);
			}
		}
	}

	/**
	 * convert map to jsonarray
	 *
	 * @param map map
	 * @return JSONArray json array
	 * @throws JSONException
	 */
	private JSONArray convertToJsonArray(Map<String, Object> map) throws JSONException {
		JSONArray array = new JSONArray();
		Set key = map.keySet();
		for (Iterator iterator = key.iterator(); iterator.hasNext();) {
			String keyName = (String) iterator.next();
			String valueName = (String) map.get(keyName);
			JSONObject jsonHeaderObj = new JSONObject();
			jsonHeaderObj.put("name", keyName);
			jsonHeaderObj.put("value", valueName);
			array.put(jsonHeaderObj);
		}
		return array;
	}

	/**
	 * convert json map.
	 *
	 * @param request
	 * @param name
	 * @param seq
	 * @return
	 * @throws JSONException
	 */
	private Map<String, Object> convertMap(JSONObject request, String name, int seq) throws JSONException {
		JSONArray array = request.getJSONArray(name);
		Map<String, Object> map = newHashMap();
		for (int j = 0; j < array.length(); j++){
			JSONObject obj = (JSONObject) array.get(j);
			map.put(obj.getString("name"), obj.getString("value"));
		}
		return map;
	}

	/**
	 * convert HARContent to NGrinder template.
	 *
	 * @param harContent HAR content String
	 * @return resultMap result map
	 * @throws JSONException
	 */
	public Map<String, Object> convertToTemplate(String harContent, boolean removeIncludeStaticCall) throws JSONException {
		Map<String, Object> resultMap = newHashMap();
		Map<String, Object> paramMap = newHashMap();
		paramMap = getHARContentToTemplate(harContent, removeIncludeStaticCall);
		resultMap.put("groovy", getScriptHandler("groovy").getScriptTemplate(paramMap));
		resultMap.put("jython", getScriptHandler("jython").getScriptTemplate(paramMap));
		return resultMap;
	}

	/**
	 * contentType check.
	 *
	 * @param obj
	 * @return boolean
	 * @throws JSONException
	 */
	private boolean isCheckContentType(JSONObject obj) throws JSONException {
		JSONArray array = obj.getJSONArray("headers");
		for (int a = 0; a < array.length(); a++) {
			JSONObject header = (JSONObject) array.get(a);
			if ("Content-Type".equals(header.getString("name"))) {
				if (StringUtils.startsWith(header.getString("value"),"text") ||
					StringUtils.startsWith(header.getString("value"),"application")) {
					return true;
				}
			}
		}
		return false;
	}

}
