package com.cache.panpan.common.guava;

import com.cache.panpan.base.CacheClearManager;
import com.cache.panpan.base.notice.IClearNotice;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.guava.GuavaCache;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.data.redis.cache.DefaultRedisCachePrefix;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @ClassName: GuavaRedisCacheManager
 * @Description:
 * @Author: panxia
 * @Date: Create in 2019/8/19 3:18 PM
 * @Version:1.0
 */
public class GuavaRedisCacheManager extends AbstractCacheManager implements CacheClearManager {
    private  final Logger logger ;
    private  DefaultRedisCachePrefix cachePrefix;
    private  RedisTemplate template;
    private boolean dynamic;
    private GuavaRedisCacheConfig guavaRedisConfig;
    private IClearNotice notice;

    public GuavaRedisCacheManager(GuavaRedisCacheConfig guavaRedisConfig) {
        this.logger = LoggerFactory.getLogger(GuavaRedisCacheManager.class);
        this.cachePrefix = new DefaultRedisCachePrefix();
        this.dynamic = true;

        if(Objects.isNull(guavaRedisConfig)){
            this.guavaRedisConfig=new GuavaRedisCacheConfig();
        }else {
            this.guavaRedisConfig=guavaRedisConfig;
        }
        this.template = guavaRedisConfig.getRedisTemplate();
        if(CollectionUtils.isEmpty(guavaRedisConfig.getLocalGuavaConfigs())){
            guavaRedisConfig.setLocalGuavaConfigs(new ArrayList<>());
        }
        this.notice=guavaRedisConfig.getNotice();
        this.setCacheNames(guavaRedisConfig.getLocalGuavaConfigs().stream().map(guavaConfig ->
                guavaConfig.getCacheName()).collect(Collectors.toList()));
    }

    public void clearLocal(String cacheName, Object key) {
        logger.info("清理本地缓存key:{},cacheName:{} ",key,cacheName);
        Cache cache = getCache(cacheName);
        if(cache == null) {
            return ;
        }
        GuavaRedisCache guaveRedisCache = (GuavaRedisCache) cache;
        guaveRedisCache.clearLocal(key);
    }


    @Override
    protected Collection<? extends Cache> loadCaches() {
        return  Collections.emptyList();
    }


    @Override
    public Cache getCache(String name) {
        Assert.notNull(this.guavaRedisConfig, "guavaRedisConfig 配置为空");
        Assert.notNull(name, "cacheName 为空");
        Cache cache = super.getCache(name);
        return cache == null && this.dynamic ? this.createAndAddCache(name) : cache;
    }

    protected Cache createAndAddCache(String cacheName) {
        this.addCache(this.createCache(cacheName));
        return super.getCache(cacheName);
    }

    protected Cache createCache(String cacheName) {
        Optional<GuavaConfig> guavaRedisConfigOptional= guavaRedisConfig.getLocalGuavaConfigs().stream().
                filter(c -> cacheName.equals(c.getCacheName())).findFirst();
        GuavaConfig guavaConfig=new GuavaConfig(cacheName);
        if(guavaRedisConfigOptional.isPresent()){
            guavaConfig =guavaRedisConfigOptional.get();
        }
        long expiration = this.computeExpiration(guavaRedisConfig);

        //没有配置redis;
        if(this.template==null){
            return this.guavaCache(guavaConfig);
        }
        return new GuavaRedisCache(cacheName, this.cachePrefix.prefix(cacheName),this.template,this.guavaCache(guavaConfig),expiration,notice);
    }

    protected long computeExpiration(GuavaRedisCacheConfig guavaRedisConfig) {
        Long expiration = null;
            if (guavaRedisConfig.getRedisExpires() != null) {
                expiration = guavaRedisConfig.getRedisExpires();
            }
        return expiration != null ? expiration : guavaRedisConfig.getRedisDefaultExpiration();
    }



    public void setCacheNames(Collection<String> cacheNames) {
        Set<String> newCacheNames = CollectionUtils.isEmpty(cacheNames) ? Collections.emptySet() : new HashSet(cacheNames);
        this.dynamic = ((Set)newCacheNames).isEmpty();
    }


    public GuavaCache guavaCache(GuavaConfig guavaConfig){
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        if(guavaConfig.getGuavaExpireAfterAccess() > 0) {
            cacheBuilder.expireAfterAccess(guavaConfig.getGuavaExpireAfterAccess(), TimeUnit.SECONDS);
        }
        if(guavaConfig.getGuavaExpireAfterWrite() > 0) {
            cacheBuilder.expireAfterWrite(guavaConfig.getGuavaExpireAfterWrite(), TimeUnit.SECONDS);
        }
        if(guavaConfig.getGuavaInitialCapacity() > 0) {
            cacheBuilder.initialCapacity(guavaConfig.getGuavaInitialCapacity());
        }
        if(guavaConfig.getGuavaMaximumSize() > 0) {
            cacheBuilder.maximumSize(guavaConfig.getGuavaMaximumSize());
        }
        if(guavaConfig.getGuavaRefreshAfterWrite() > 0) {
            cacheBuilder.refreshAfterWrite(guavaConfig.getGuavaRefreshAfterWrite(), TimeUnit.SECONDS);
        }
        return new GuavaCache(guavaConfig.getCacheName(),cacheBuilder.build());
    }

    @Override
    public void clearLocal(Object key,String cacheName){
        Cache cache =getCache(cacheName);
        if(cache!=null){
            logger.info("本地缓存开始 cacheName:{} key;{}  ",cacheName,key);
            GuavaRedisCache guavaRedisCache= (GuavaRedisCache) cache;
            guavaRedisCache.clearLocal(key);
            logger.info("本地缓存结束 cacheName:{} key;{}  ",cacheName,key);
        }
    }


    public void setNotice(IClearNotice notice) {
        this.notice = notice;
    }

    public RedisTemplate getTemplate() {
        return template;
    }

    public void setTemplate(RedisTemplate template) {
        this.template = template;
    }




}
