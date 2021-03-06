/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.anyframe.oden.bundle.hessiansvr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.anyframe.oden.bundle.common.FileInfo;
import org.anyframe.oden.bundle.common.FileUtil;
import org.anyframe.oden.bundle.deploy.StoppableJob;

/**
 * Get file list for agents sync.
 * 
 * @author Junghwan Hong
 */
public class ListFilesJob extends StoppableJob {
	String root;

	public ListFilesJob(String id, String path) {
		super(id);
		this.root = path;
	}

	@Override
	protected Object execute() throws IOException {
		File dir = new File(root);
		if (!dir.isAbsolute()) {
			throw new IOException("Absolute path is allowed only: " + root);
		}
		return listAllFiles(dir);
	}

	@SuppressWarnings("PMD")
	private List<FileInfo> listAllFiles(final File dir) {
		if (stop) {
			return Collections.EMPTY_LIST;
		}

		List<FileInfo> result = new ArrayList<FileInfo>();
		if (!dir.exists()) {
			return result;
		}
		File[] fs = dir.listFiles();
		for (File f : fs) {
			if (stop) {
				break;
			}
			if (f.isDirectory()) {
				result.addAll(listAllFiles(f));
			} else {
				result.add(new FileInfo(FileUtil.getRelativePath(root, f
						.getAbsolutePath()), false, f.lastModified(), f
						.length()));
			}
		}
		return result;
	}
}
