package org.jbpm.process.workitem.infinispan;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.BasicCacheContainer;
import org.jbpm.process.workitem.AbstractLogOrThrowWorkItemHandler;
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
public class InfinispanWorkItemHandler extends AbstractLogOrThrowWorkItemHandler {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanWorkItemHandler.class);

	private enum CacheOperation {
		//PUT("PUT"), GET("GET");
		PUT, GET

		//private final String cacheOperationName;

		
		/*
		private CacheOperation(final String cacheOperationName) {
			this.cacheOperationName = cacheOperationName;
		}
		*/

		/*
		public String getCacheOperationName() {
			return cacheOperationName;
		}
		*/
	}

	private static final String ENDPOINT_PARAM_NAME = "endpoint";

	private static final String CACHE_PARAM_NAME = "cache";

	private static final String OPERATION_PARAM_NAME = "operation";

	private static final String KEY_PARAM_NAME = "key";

	private static final String VALUE_PARAM_NAME = "value";

	private static final String RESULT_PARAM_NAME = "result";

	private Map<String, BasicCacheContainer> cacheContainers = new ConcurrentHashMap<String, BasicCacheContainer>();

	// private DataGridHelper jdgHelper = new DataGridHelper();

	@Override
	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		// TODO: Should we check for inconsistent parameter configuration? I.e. a GET operation and value don't really make sense.
		Map<String, Object> parameters = workItem.getParameters();

		String endpoint = (String) parameters.get(ENDPOINT_PARAM_NAME);
		String cacheName = (String) parameters.get(CACHE_PARAM_NAME);

		try {
			CacheOperation operation = CacheOperation.valueOf(((String) parameters.get(OPERATION_PARAM_NAME)).toUpperCase());
			String key = (String) parameters.get(KEY_PARAM_NAME);
			Object value = parameters.get(VALUE_PARAM_NAME);

			LOGGER.debug("Retrieving CacheContainer for endpoint: " + endpoint);
			BasicCacheContainer cacheContainer = getCacheContainer(endpoint);
			Map<String, Object> cache = null;
			if (cacheName == null || "".equals(cacheName)) {
				// Grab the default cache if no cache-name has been specified.
				cache = cacheContainer.getCache();
			} else {
				cache = cacheContainer.getCache(cacheName);
			}
			/*
			 * TODO: cache could be null if a cache with the given cacheName does not exist. Should we check that and throw an exception, or
			 * should we just have the WIH throw an NPE?
			 */
			Object result = null;
			switch (operation) {
			case GET:
				result = cache.get(key);
				break;
			case PUT:
				result = cache.put(key, value);
				break;
			}

			Map<String, Object> results = new HashMap<String, Object>();
			results.put(RESULT_PARAM_NAME, result);

			manager.completeWorkItem(workItem.getId(), results);
		} catch (Exception e) {
			handleException(e);
		}

	}

	/**
	 * Returns a {@link BasicCacheContainer} for the given endpoint.
	 * 
	 * @param endpoint
	 *            the Infinispan endpoint.
	 * @return the {@link BasicCacheContainer} for the given endpoint.
	 */
	private synchronized BasicCacheContainer getCacheContainer(final String endpoint) {
		BasicCacheContainer cacheContainer = cacheContainers.get(endpoint);

		if (cacheContainer == null) {
			// TODO: Refactor so we don't use deprecated APIs.
			cacheContainer = new RemoteCacheManager(endpoint);
			cacheContainers.put(endpoint, cacheContainer);
		}
		return cacheContainer;
	}

	@Override
	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		throw new UnsupportedOperationException();
	}

	/*
	 * private static class DataGridHelper {
	 * 
	 * private RemoteCacheManager cacheManager; private RemoteCache<String, Object> cache;
	 * 
	 * public DataGridHelper(final String endpoint) {
	 * 
	 * cacheManager = new RemoteCacheManager(endpoint); cache = cacheManager.getCache(); }
	 * 
	 * public void put(String key, Object value) { cache.put(key, value); }
	 * 
	 * public Object get(String key) { return cache.get(key); }
	 * 
	 * public void remove(String key) { cache.remove(key); }
	 * 
	 * }
	 */

}
