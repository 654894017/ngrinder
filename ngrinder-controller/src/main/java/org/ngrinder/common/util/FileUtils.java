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
package org.ngrinder.common.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Convenient File utilities.
 *
 * @author JunHo Yoon
 * @since 3.1
 */
public abstract class FileUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

	/**
	 * Copy the given resource to the given file.
	 *
	 * @param resourcePath resource path
	 * @param file         file to write
	 * @since 3.2
	 */
	public static void copyResourceToFile(String resourcePath, File file) {
		InputStream io = null;
		FileOutputStream fos = null;
		try {
			io = new ClassPathResource(resourcePath).getInputStream();
			fos = new FileOutputStream(file);
			IOUtils.copy(io, fos);
		} catch (IOException e) {
			LOGGER.error("error while writing {}", resourcePath, e);
		} finally {
			IOUtils.closeQuietly(io);
			IOUtils.closeQuietly(fos);
		}

	}

	/**
	 * convert File to String.
	 *
	 * @param file file
	 * @return String file to read
	 */
	public static String getStringToFile(MultipartFile file) throws IOException {
		String line;
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		InputStream is = file.getInputStream();
		br = new BufferedReader(new InputStreamReader(is));
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}
		return String.valueOf(sb);
	}
}
