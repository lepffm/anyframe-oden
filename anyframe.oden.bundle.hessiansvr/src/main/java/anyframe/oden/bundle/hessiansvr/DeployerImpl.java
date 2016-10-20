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
package anyframe.oden.bundle.hessiansvr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import anyframe.oden.bundle.common.BundleUtil;
import anyframe.oden.bundle.common.FileInfo;
import anyframe.oden.bundle.common.FileUtil;
import anyframe.oden.bundle.common.Utils;
import anyframe.oden.bundle.deploy.ByteArray;
import anyframe.oden.bundle.deploy.DeployerService;
import anyframe.oden.bundle.deploy.DoneFileInfo;
import anyframe.oden.bundle.deploy.StoppableJob;

/**
 * all methods are throws Exception to catch
 * ROSGiException(RuntimeExcetpion) on the remote caller.
 * @see DeployerService
 * @ThreadSafe
 * 
 * @author joon1k
 *
 */
public class DeployerImpl implements DeployerService {
	private final String HOME = BundleUtil.odenHome().getPath();
	
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
		return new File(toAbsPath(parent), child).exists();
	}

	public boolean writable(String parent, String child) throws Exception{
		File f = new File(toAbsPath(parent), child);
		return !f.exists() || f.canWrite();
	}
	
	public FileInfo fileInfo(String parent, String child) throws Exception{
		File f = new File(toAbsPath(parent), child);
		if(f.exists())
			return new FileInfo(child, f.isDirectory(), f.lastModified(), f.length());
		return null;
	}
	
	public List<String> resolveFileRegex(String parent, List<String> includes, 
			List<String> excludes) throws Exception{
		parent = toAbsPath(parent);		
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

				if(file.isDirectory() && recursive){
					matched.addAll(getMatchedFiles(root, path, includes, excludes, recursive));
				}else{
					String rpath = FileUtil.getRelativePath(root, path);
					if(FileUtil.matched(rpath, includes, excludes))
						matched.add(rpath);
				}
			}
		}
		return matched;
	}
	
	/**
	 * @return date of the file (parentpath/path). -1 if there's no such file.
	 */
	public long getDate(String parent, String path) throws Exception{
		File f = new File(toAbsPath(parent), path);
		if(!f.exists() || !f.isAbsolute())
			return -1L;
		return f.lastModified();
	}
	
	public void setDate(String parent, String path, long date) 
			throws Exception{
		File f = new File(toAbsPath(parent), path);
		if(!f.exists() || !f.isAbsolute())
			throw new FileNotFoundException(f.getPath());
		
		synchronized (lock) {
			if(!f.setLastModified(date))
				throw new IOException("Fail to set date: " 
						+ f.getPath() + ", " + date);	
		}
	}
	
	private Map<String, DeployOutputStream> fileStreams =
		new ConcurrentHashMap<String, DeployOutputStream>();
	
	private MODE mode = MODE.INIT;
	
	enum MODE {INIT, WRITE, CLOSE}
	
//	public void init(String parent, String path, long date) throws Exception{
//		init(parent, path, date, true);
//	}
	
	public void init(String parent, String path, 
			long date, boolean useTmp) throws Exception{
		parent = toAbsPath(parent);
		synchronized (lock) {
			if(mode != MODE.INIT){
				// close all open streams.
				for(String f : fileStreams.keySet()){
					try{
						fileStreams.remove(f).close(null, null);
					}catch(IOException e){
					}
				}
				mode = MODE.INIT;
			}
			
			final String key = parent + path;
			if(fileStreams.containsKey(key))
				throw new IOException("Couldn't open the file again: " + 
						FileUtil.combinePath(parent, path));
			fileStreams.put(key, new DeployOutputStream(
					parent, path, date, useTmp));
		}
	}
	
	public boolean write(String parent, String path, 
			ByteArray buf) throws Exception {
		parent = toAbsPath(parent);
		synchronized (lock) {
			if(mode == MODE.CLOSE)
				throw new IOException("Writing any bytes is not allowed while closing mode.");
			mode = MODE.WRITE;
			
			DeployOutputStream out = fileStreams.get(parent + path);
			if(out == null)
				throw new IOException("No open stream: " + 
						FileUtil.combinePath(parent, path));
			return out.write(buf.getBytes());	
		}
	}
	
	public DoneFileInfo close(String parent, String path, 
			List<String> updatefiles, String bakdir) throws Exception {
		parent = toAbsPath(parent);
		bakdir = toAbsPath(bakdir);
		synchronized (lock) {
			mode = MODE.CLOSE;
			
			final String key = parent + path;
			
			DeployOutputStream stream = fileStreams.remove(key);
			if(stream == null) 
				return null;
			return stream.close(updatefiles, bakdir);
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
			job = new CompressJob(id, toAbsPath(srcdir), toAbsPath(destFile));
			return (DoneFileInfo) job.start();
		}
	}

	public List<DoneFileInfo> extract(String id, String srcdir, String zipname, String destdir) throws Exception {
		synchronized (lock) {
			job = new ExtractJob(id, toAbsPath(srcdir), zipname, toAbsPath(destdir));
			return (List<DoneFileInfo>) job.start();
		}
	}

	public void removeFile(String dir, String filename) throws Exception{
		synchronized (lock) {
			File s = new File(toAbsPath(dir), filename);
			if(s.exists() && !s.delete())
				throw new IOException("Fail to remove file: " + s);	
		}
	}

	public List<DoneFileInfo> backupNRemoveDir(String id, String dir, String bak) throws Exception {
		synchronized (lock) {
			job = new RemoveDirJob(id, toAbsPath(dir), toAbsPath(bak));
			return (List<DoneFileInfo>) job.start();
		}
	}
	
	public List<FileInfo> listAllFilesAsJob(String id, String path) throws Exception{
		synchronized (lock) {
			job = new ListFilesJob(id, toAbsPath(path));
			return (List<FileInfo>) job.start();
		}
	}
	
	public List<FileInfo> listAllFiles(String dir) throws Exception{
		dir = toAbsPath(dir);
		File d = new File(dir);
		if(!d.isDirectory())
			return Collections.EMPTY_LIST;
		
		List<FileInfo> ret = new ArrayList<FileInfo>();
		FileUtil.listAllFiles(ret, dir, d);
		return ret;
	}
	
	public Set<FileInfo> listAllFilesAsSet(String dir) throws Exception{
		dir = toAbsPath(dir);
		File d = new File(dir);
		if(!d.isDirectory())
			return Collections.EMPTY_SET;
		
		Set<FileInfo> ret = new HashSet<FileInfo>();
		FileUtil.listAllFiles(ret, dir, d);
		return ret;
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
		srcPath = toAbsPath(srcPath);
		destPath = toAbsPath(destPath);
		bakPath = toAbsPath(bakPath);
		synchronized (lock) {
			if(!new File(srcPath, filePath).exists())
				throw new IOException("File not found: " + new File(srcPath, filePath).getPath());
			
			boolean success = true;
			boolean srcExist = false;
			
			//backup
			if(bakPath != null ){ 
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
		srcPath = toAbsPath(srcPath);
		bakPath = toAbsPath(bakPath);
		synchronized (lock) {
			boolean success = true;
			boolean srcExist = false;
			
			srcExist = DeployerUtils.undoBackup(srcPath, filePath, bakPath);
			
			//remove
			File f = new File(srcPath, filePath);
			long srcdate = f.lastModified();
			long srcsz = f.length();
			if(f.exists() && !f.delete())
				throw new IOException("Fail to remove file: " + new File(srcPath, filePath));
			
			// remove parent directory if empty
			removeEmptyParent(f);
			
			return new DoneFileInfo(FileUtil.combinePath(srcPath, filePath), false, srcdate, srcsz, srcExist, success);
		}
	}
	
	private void removeEmptyParent(File f) {
		File parent = f.getParentFile();
		if(parent == null)
			return;
		
		String[] items = parent.list();
		if(items == null || items.length > 0)
			return;
		
		try{
			if(parent.delete());
				removeEmptyParent(parent);
		}catch(Exception e){
		}
	}

	public boolean touchAvailable(){
		return true;
	}
	
	public String execShellCommand(String command, String dir, long timeout) throws Exception{
		return new Launcher(new Proc(command, 
				toAbsPath(dir), timeout), timeout).start();
	}

	public String odenHome(){
		return HOME;
	}
	
	private String toAbsPath(String path){
		if(path == null || FileUtil.isAbsolutePath(path))
			return path;
		return FileUtil.resolveDotNatationPath(
				FileUtil.combinePath(HOME, path));
	}
}