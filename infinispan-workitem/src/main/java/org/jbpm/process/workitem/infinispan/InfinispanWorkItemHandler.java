package org.jbpm.process.workitem.infinispan;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jBPM Infinispan {@link WorkItemHandler}.
 * 
 * @author <a href="mailto:ddoyle@redhat.com">Duncan Doyle</a>
 */
public class InfinispanWorkItemHandler implements WorkItemHandler {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanWorkItemHandler.class);
	
	private static final String KEY_PARAM_NAME = "key";
	
	private static final String VALUE_PARAM_NAME = "value";
	
	private DataGridHelper jdgHelper = new DataGridHelper();

	@Override
	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		Map<String, Object> parameters = workItem.getParameters();
		String key = (String) parameters.get(KEY_PARAM_NAME);
		Object value = parameters.get(VALUE_PARAM_NAME);
		
		LOGGER.debug("Storing data in JDG.");
		jdgHelper.put(key, value);
		LOGGER.debug("Data stored in JDG. Completing WorkItem");
		manager.completeWorkItem(workItem.getId(), new HashMap<String, Object>());
	}

	@Override
	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		throw new UnsupportedOperationException();
	}

	private static class DataGridHelper {

		private RemoteCacheManager cacheManager;
		private RemoteCache<String, Object> cache;
		
		public DataGridHelper() {
			// TODO: Refactor so we don't use deprecated APIs.
			cacheManager = new RemoteCacheManager("datagrid:11222");
			cache = cacheManager.getCache();
		}

		public void put(String key, Object value) {
			cache.put(key, value);
		}

		public Object get(String key) {
			return cache.get(key);
		}

		public void remove(String key) {
			cache.remove(key);
		}

	}

}
