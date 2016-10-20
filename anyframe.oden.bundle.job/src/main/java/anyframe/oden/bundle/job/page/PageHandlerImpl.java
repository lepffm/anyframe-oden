package anyframe.oden.bundle.job.page;

import java.io.File;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.ComponentContext;

import anyframe.oden.bundle.common.BundleUtil;
import anyframe.oden.bundle.common.FileUtil;
import anyframe.oden.bundle.common.Logger;
import anyframe.oden.bundle.common.OdenException;
import anyframe.oden.bundle.common.StringUtil;
import anyframe.oden.bundle.core.command.Cmd;
import anyframe.oden.bundle.core.command.Opt;
import anyframe.oden.bundle.job.log.BDBConnection;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class PageHandlerImpl implements PageHandler{
	private final String CACHE_LOC = "meta/pgcache";
	private final String DB = "PAGE_CACHE";
	private final String DATE_DB = "CACHED_DATE";
	private int CACHE_SZ = 20;
	
	private TupleBinding binding = new PageBinding();
	private JSONArrayFilter filter = null;
	int pgscale = 20;
	
	Object latch = new Object();
	
	protected void activate(ComponentContext context) {
		String cacheSz = context.getBundleContext().getProperty("page.cache.size");
		this.CACHE_SZ = StringUtil.empty(cacheSz) ? 20 : Integer.valueOf(cacheSz);
		
		String _scale = context.getBundleContext().getProperty("page.scale");
		this.pgscale = StringUtil.empty(_scale) ? 20 : Integer.valueOf(_scale);
		this.filter = new JSONArrayFilter();
		
		File cachedir = new File(BundleUtil.odenHome(), CACHE_LOC);
		FileUtil.removeDir(cachedir);
		cachedir.mkdirs();
		
		BDBConnection conn = null;
		Database db = null;
		synchronized (latch) {
			try{
				conn = new BDBConnection(CACHE_LOC, false);
				db = conn.openDB(DB);
			}catch(Exception e){
				Logger.error(e);
			}finally{
				try{ if(db != null) db.close(); } catch(Exception e){ }
				try{ if(conn != null) conn.close(); } catch(Exception e){ }	
			}
		}
	}
	
	public void setFilter(JSONArrayFilter filter){
		this.filter = filter;
	}
		
	/**
	 * 
	 * @param cmd
	 * @return null if no appropriate data
	 */
	public JSONObject getCachedData(Cmd cmd, int pgscale, PageHandlerOr or) 
			throws Exception{
		if(pgscale < 1)
			pgscale = this.pgscale;
		
		String _cmd = cmd.toString();
		String _page = cmd.getOptionArg(new String[]{"page"});
		if(StringUtil.empty(_page)){
			JSONArray arr = or.run();
			return makeJSONObject(arr, arr.length());
		}
		
		int page = Integer.valueOf(_page);
		if(page == 0)
			return runCachedOr(_cmd, page, pgscale, or);
		
		JSONObject ret = get(_cmd, page, pgscale);
		if(ret == null)		// cache hit fail
			return runCachedOr(_cmd, page, pgscale, or);
		return ret;
	}
	
	private JSONObject runCachedOr(String cmd, int page, 
			int pgscale, PageHandlerOr or) throws Exception {
		put(cmd, or.run());
		JSONObject ret = get(cmd, page, pgscale);
		return ret != null ? ret : new JSONObject().put("total", 0)
				.put("data", new JSONArray());
	}
	
	public JSONObject get(String cmd, int page, int pgscale) 
			throws Exception{
		if(pgscale < 1)
			pgscale = this.pgscale;
		
		BDBConnection conn = null;
		Database cache = null;
		synchronized (latch) {
			try{
				String cmdkey = removeRedundancy(cmd);
				
				conn = new BDBConnection(CACHE_LOC, true);
				cache = conn.openDB(DB);
				
				DatabaseEntry key = new DatabaseEntry(cmdkey.getBytes("utf-8"));
				DatabaseEntry data = new DatabaseEntry();
				if(cache.get(null, key, data, LockMode.DEFAULT) 
						!= OperationStatus.SUCCESS)
					return null;
				Object found = binding.entryToObject(data);
				if(found == null) return null;
				
				JSONArray arr = new JSONArray((String)found);
				return makeJSONObject(filter.run(arr, page, pgscale), arr.length());
			}finally{
				try{ if(cache != null) cache.close(); }catch(Exception e){ }
				try{ if(conn != null) conn.close(); }catch(Exception e){ }
			}
		}
	}
	
	public void put(String cmd, JSONArray data){
		BDBConnection conn = null;
		Transaction tx = null;
		Database cache = null;
		Database datedb = null;
		Cursor cur = null;
		synchronized (latch) {
			try{
				String cmdkey = removeRedundancy(cmd);
				
				conn = new BDBConnection(CACHE_LOC, false);
				cache = conn.openDB(DB);
				datedb = conn.openDB(DATE_DB);
				
				tx = conn.beginTransaction(null, null);
				
				DatabaseEntry key = new DatabaseEntry(cmdkey.getBytes("utf-8"));
				DatabaseEntry entrydata = new DatabaseEntry();
				binding.objectToEntry(data, entrydata);
				cache.put(tx, key, entrydata);
				
				DatabaseEntry date = new DatabaseEntry(
						String.valueOf(System.currentTimeMillis()).getBytes("utf-8"));
				datedb.put(tx, date, key);
	
				if(cache.count() < CACHE_SZ){
					tx.commit();
					return;
				}
				
				// search old
				try{
					cur = datedb.openCursor(tx, null);
					DatabaseEntry oldkey = new DatabaseEntry();
					for(int i=0; i<CACHE_SZ/3; i++){
						if(cur.getFirst(new DatabaseEntry(), oldkey, LockMode.DEFAULT) 
								!= OperationStatus.SUCCESS)
							break;
						
						// remove old
						cur.delete();
						cache.delete(tx, oldkey);
					}			
				}finally{
					// cursor must be closed before commit.
					try{ if(cur != null) cur.close(); }catch(Exception e){ }
				}
				tx.commit();
			}catch(Exception e){
				Logger.error(e);
				if(tx != null) tx.abort();
			}finally{
				try{ if(cache != null) cache.close(); }catch(Exception e){ }
				try{ if(conn != null) conn.close(); }catch(Exception e){ }
			}
		}
	}
	
	private String removeRedundancy(String s) throws OdenException{
		Cmd cmd = new Cmd(s);
		List<Opt> opts = cmd.getOptions();
		int i=0; 
		while(i<opts.size()){
			Opt opt = opts.get(i);
			if(!opt.getName().equals("json") &&
					!opt.getName().equals("_user") &&
					!opt.getName().equals("page")) 
				i++;
			opts.remove(i);
		}
		return cmd.toString();
	}
	
	private JSONObject makeJSONObject(JSONArray arr, int total) throws JSONException{
		return new JSONObject().put("total", total).put("data", arr);
	}
}

class PageBinding extends TupleBinding{
	public Object entryToObject(TupleInput in) {
		if(in.available() == 0) 
			return null;
		return in.readString();
	}

	public void objectToEntry(Object obj, TupleOutput out) {
		out.writeString(obj.toString());
	}
}
