package org.jbpm.process.workitem.infinispan;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.infinispan.commons.util.Util;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides Infinispan/JBoss Data Grid utilities that can be used when testing Infinispan/JBoss Data Grid integrations. 
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class InfinispanTestUtil {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanTestUtil.class);
	
	public static EmbeddedCacheManager getCacheManager(final String infinispanConfigFileName) {
		EmbeddedCacheManager cacheContainer;
		// Retrieve Infinispan config file.
		InputStream infinispanConfigStream = InfinispanTestUtil.class.getClassLoader().getResourceAsStream(infinispanConfigFileName);
		try {
			try {
				LOGGER.info("Creating CacheContainers using config: " + infinispanConfigFileName);
				cacheContainer = new DefaultCacheManager(infinispanConfigStream);
			} catch (IOException ioe) {
				throw new RuntimeException("Error loading Infinispan CacheManager.", ioe);
			}
		} finally {
			// Use Infinispan Util class to flush and close stream.
			Util.close(infinispanConfigStream);
		}
		return cacheContainer;
	}

	public static void disposeCacheManager(EmbeddedCacheManager cacheContainer) {
		LOGGER.info("Stopping Caches and CacheContainer.");
		Set<String> cacheNames = cacheContainer.getCacheNames();
		for (String nextCacheName : cacheNames) {
			LOGGER.info("Stopping cache: " + nextCacheName);
			cacheContainer.getCache(nextCacheName).stop();
		}
		LOGGER.info("Stopping default cache.");
		cacheContainer.getCache().stop();
		LOGGER.info("Stopping CacheContainer.");
		cacheContainer.stop();
	}


}
