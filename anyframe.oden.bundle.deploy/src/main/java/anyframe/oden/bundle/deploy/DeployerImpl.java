/*
 * Copyright 2009 SAMSUNG SDS Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package anyframe.oden.bundle.deploy;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import anyframe.oden.bundle.common.FileInfo;
import anyframe.oden.bundle.common.FileUtil;
import anyframe.oden.bundle.common.Utils;

/**
 * all methods are throws Exception to catch
 * ROSGiException(RuntimeExcetpion) on the remote caller.
 * @see DeployerService
 * @ThreadSafe
 * 
 * @author joon1k
 *
 */
public class DeployerImpl implements DeployerService, Serializable {
	
	private static final long serialVersionUID = -2192884775487476693L;
	
	private Object lock = new Object();
	
	public String jvmStat(){
		return Utils.jvmStat();
	}
	
	/**
	 * this method is used to check if connection is available.
	 */
	public boolean alive(){
		return true;
	}
	
	public boolean exist(String parent, String child) throws Exception{
		return new File(parent, child).exists();
	}

	public boolean writable(String parent, String child) throws Exception{
		File f = new File(parent, child);
		return !f.exists() || f.canWrite();
	}
	
	public FileInfo fileInfo(String parent, String child) throws Exception{
		File f = new File(parent, child);
		if(f.exists())
			return new FileInfo(child, f.isDirectory(), f.lastModified(), f.length());
		return null;
	}
	
	public List<String> resolveFileRegex(String parent, List<String> includes, 
			List<String> excludes) throws Exception{
		boolean recursive = hasRecursive(includes);
		String common = FileUtil.commonParent(includes);
		if(common != null){
			List<String> _incs = new ArrayList<String>(includes);
			for(String inc : includes)
				_incs.add(FileUtil.getRelativePath(common, inc));
			return getMatchedFiles(parent, FileUtil.combinePath(parent, common), _incs, excludes, recursive);
		}
		return getMatchedFiles(parent, parent, includes, excludes, recursive);
	}
	
	private boolean hasRecursive(List<String> includes) {
		for(String s : includes)
			if(s.contains("**"))
				return true;
		return false;
	}
	
	private List<String> getMatchedFiles(final String root, String parent, 
			List<String> includes, List<String> excludes, boolean recursive) {
		
		List<String> matched = new ArrayList<String>();
		
		File[] files = new File(parent).listFiles();
		if(files != null){
			for(File file : files) {				
				String path = new File(parent, file.getName()).getPath(); 
				if(file == null || file.isHidden() || !file.canRead()) 
					continue;

				String rpath = FileUtil.getRelativePath(root, path);				
				if(file.isDirectory() && recursive)
					matched.addAll(getMatchedFiles(root, path, includes, excludes, recursive));
				else
					if(FileUtil.matched(rpath, includes, excludes))
						matched.add(rpath);				
			}
		}
		return matched;
	}
	
	/**
	 * @return date of the file (parentpath/path). -1 if there's no such file.
	 */
	public long getDate(String parentpath, String path) throws Exception{
		File f = new File(parentpath, path);
		if(!f.isAbsolute())
			return -1L;
		return f.lastModified();
	}
	
	
	private DeployOutputStream out = null;
	
	public void init(String parent, String path, long date) throws Exception{
		if(out != null)
			try{ close(null, null); }catch(IOException e){}
		out = new DeployOutputStream(parent, path, date);
	}
	
	public boolean write(byte[] buf) throws Exception{
		return out.write(buf, buf.length);
	}

	public boolean write(byte[] buf, int size) throws Exception{
		return out.write(buf, size);
	}
	
	/**
	 * @param updatefiles updated files when updating jar. null if you do not want to update jar.
	 * @param bakdir dir for backup. null if you don't want backup.
	 * @throws IOException 
	 */
	public DoneFileInfo close(List<String> updatefiles, String bakdir) throws Exception {
		if(out == null) return null;
		try{
			return out.close(updatefiles, bakdir);
		}finally{
			out = null;
		}
	}
	
	
	private StoppableJob job;
	
	public void stop(String id) throws Exception{
		if(job != null && job.isAlive() && job.id().equals(id)) {
			job.stop();
		}
	}
	
	public DoneFileInfo compress(String id, String srcdir, String destFile) throws Exception {
		synchronized (lock) {
			job = new CompressJob(id, srcdir, destFile);
			return (DoneFileInfo) job.start();
		}
	}

	public List<DoneFileInfo> extract(String id, String srcdir, String zipname, String destdir) throws Exception {
		synchronized (lock) {
			job = new ExtractJob(id, srcdir, zipname, destdir);
			return (List<DoneFileInfo>) job.start();
		}
	}

	public void removeFile(String dir, String filename) throws Exception{
		synchronized (lock) {
			File s = new File(dir, filename);
			if(!s.isAbsolute())
				throw new IOException("Absolute path is allowed only: " + s);
			if(s.exists() && !s.delete())
				throw new IOException("Fail to remove file: " + s);	
		}
	}

	public List<DoneFileInfo> backupNRemoveDir(String id, String dir, String bak) throws Exception {
		synchronized (lock) {
			job = new RemoveDirJob(id, dir, bak);
			return (List<DoneFileInfo>) job.start();
		}
	}
	
	public List<FileInfo> listAllFiles(String id, String path) throws Exception{
		job = new ListFilesJob(id, path);
		return (List<FileInfo>) job.start();
	}
	
	
	
	/**
	 * used before restoring snapshots.
	 * 
	 * @param infos
	 * @param dir
	 * @param root
	 * @param bak
	 * @throws IOException
	 */
	private void backupRemoveDir(List<DoneFileInfo> infos, File dir, final String root, final String bak) throws IOException {
		if(dir != null && dir.exists() && dir.isDirectory()) {
			File[] files = dir.listFiles();
			for(int i=0; i<files.length; i++) {
				if(files[i].isDirectory()) {
					backupRemoveDir(infos, files[i], root, bak);
				}else {
					DoneFileInfo info = new DoneFileInfo(
							FileUtil.getRelativePath(root, files[i].getPath()), 
							false, 
							files[i].lastModified(), 
							files[i].length(), false, false);
					infos.add(info);
					
					info.setUpdate(DeployerUtils.undoBackup(root, info.getPath(), bak));
					info.setSuccess(files[i].delete());
				}
			}
	        dir.delete();
        }
	}
		
	/**
	 * make destination's backup & copy src to dest
	 * 
	 * @param parentPath
	 * @param filePath
	 * @param destPath
	 * @param bakPath null if no backup
	 * @return fileinfo copied
	 * @throws IOException 
	 */
	public DoneFileInfo backupNCopy(String srcPath, String filePath, 
			String destPath, String bakPath) throws Exception {
		synchronized (lock) {
			if(!new File(srcPath).isAbsolute() || 
					!new File(destPath).isAbsolute() ||
					!new File(srcPath, filePath).exists())
				throw new IOException("Wrong path: " + srcPath + " or " + destPath);
			
			boolean success = true;
			boolean srcExist = false;
			
			//backup
			if(bakPath != null ){
				if(!new File(bakPath).isAbsolute())
					throw new IOException("Absolute path is allowed only: " + bakPath); 
				srcExist = DeployerUtils.undoBackup(destPath, filePath, bakPath);
			}
				
			//copy
			DeployerUtils.copy(srcPath, filePath, destPath);
			
			File copied = new File(destPath, filePath); 
			return new DoneFileInfo(FileUtil.combinePath(srcPath, filePath), false, copied.lastModified(), copied.length(), srcExist, success);
		}
	}
	
	/**
	 * make source's backup & remove
	 * 
	 * @param srcPath
	 * @param filePath
	 * @param bakPath
	 * @return fileinfo removed
	 * @throws IOException 
	 */
	public DoneFileInfo backupNRemove(String srcPath, String filePath, String bakPath) throws Exception {
		synchronized (lock) {
			if(!new File(srcPath).isAbsolute() || (bakPath != null &&
					!new File(bakPath).isAbsolute()) )
				throw new IOException("Wrong path: " + srcPath + " or " + bakPath);
			
			boolean success = true;
			boolean srcExist = false;
			
			srcExist = DeployerUtils.undoBackup(srcPath, filePath, bakPath);
			
			//remove
			File f = new File(srcPath, filePath);
			long srcdate = f.lastModified();
			long srcsz = f.length();
			if(f.exists() && !f.delete())
				throw new IOException("Fail to remove file: " + new File(srcPath, filePath));
			
			return new DoneFileInfo(FileUtil.combinePath(srcPath, filePath), false, srcdate, srcsz, srcExist, success);
		}
	}
	
}