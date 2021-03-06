/*
 * Copyright 2011 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.jsonpath.internal;

import static com.jayway.jsonpath.JsonPath.compile;
import static com.jayway.jsonpath.internal.Utils.notEmpty;
import static com.jayway.jsonpath.internal.Utils.notNull;
import static java.util.Arrays.asList;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.EvaluationListener;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.Predicate;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.cache.Cache;
import com.jayway.jsonpath.spi.cache.CacheProvider;

public class JsonContext implements DocumentContext {

    private static final Logger logger = LoggerFactory.getLogger(JsonContext.class);

    private final Configuration configuration;
    private final Object json;

    JsonContext(Object json, Configuration configuration) {
        notNull(json, "json can not be null");
        notNull(configuration, "configuration can not be null");
        this.configuration = configuration;
        this.json = json;
    }


    @Override
    public Configuration configuration() {
        return configuration;
    }

    //------------------------------------------------
    //
    // ReadContext impl
    //
    //------------------------------------------------
    @Override
    public Object json() {
        return json;
    }

    @Override
    public String jsonString() {
        return configuration.jsonProvider().toJson(json);
    }

    @Override
    public <T> T read(String path, Predicate... filters) {
        notEmpty(path, "path can not be null or empty");

        // KR throw exception if config points to readRoot()
        if (configuration.getComputeRoot()) {
            throw new IllegalArgumentException("Invalid configuration, readRoot + read not supported.");
        }

        Cache cache = CacheProvider.getCache();

        path = path.trim();
        LinkedList filterStack = new LinkedList<Predicate>(asList(filters));
        String cacheKey = Utils.concat(path, filterStack.toString());

        JsonPath jsonPath = cache.get(cacheKey);
        if(jsonPath != null){
        	return read(jsonPath);
        } else {
		jsonPath = compile(path, filters);
		cache.put(cacheKey, jsonPath);
        	return read(jsonPath);
        }

    }

    /*
     *
     * @Override public Object readRoot(String path, Predicate... filters) { notEmpty(path, "path can not be null or empty"); Cache cache =
     * CacheProvider.getCache();
     *
     * path = path.trim(); LinkedList filterStack = new LinkedList<Predicate>(asList(filters)); String cacheKey = Utils.concat(path,
     * filterStack.toString());
     *
     * JsonPath jsonPath = cache.get(cacheKey); if (jsonPath != null) { return jsonPath.readRoot(json, configuration); } else { jsonPath =
     * compile(path, filters); cache.put(cacheKey, jsonPath); return jsonPath.readRoot(json, configuration); } }
     *
     */

    @Override
    public Object readRoot(String[] paths, Predicate... filters) {
        if (null == paths || paths.length == 0) {
            throw new IllegalArgumentException();
        }
        final Cache cache = CacheProvider.getCache();
        final List<Predicate> filterStack = new LinkedList<Predicate>(asList(filters));
        ;
        Object ret = null;
        Object rootObj = null;

        for (String path : paths) {
            path = path.trim();
            String cacheKey = Utils.concat(path, filterStack.toString());

            JsonPath jsonPath = cache.get(cacheKey);
            if (jsonPath == null) {
                jsonPath = compile(path, filters);
                cache.put(cacheKey, jsonPath);
            }
            try {
            	ret = jsonPath.readRoot(rootObj, json, configuration);
            	rootObj = ret;
            } catch (RuntimeException e) {
            	// support suppress exception
            	if (configuration.getOptions().contains(Option.SUPPRESS_EXCEPTIONS)) {
            		continue;
            	} else {
            		throw e;
            	}
            }
        }
        if (ret == null) {
        	// Return an empty map.
        	ret = configuration.jsonProvider().createMap();
        }
        return ret;
    }
    
    @Override
    public <T> T read(String path, Class<T> type, Predicate... filters) {
        return convert(read(path, filters), type, configuration);
    }

    @Override
    public <T> T read(JsonPath path) {
        notNull(path, "path can not be null");
        return path.read(json, configuration);
    }

    @Override
    public <T> T read(JsonPath path, Class<T> type) {
        return convert(read(path), type, configuration);
    }

    @Override
    public <T> T read(JsonPath path, TypeRef<T> type) {
        return convert(read(path), type, configuration);
    }

    @Override
    public <T> T read(String path, TypeRef<T> type) {
        return convert(read(path), type, configuration);
    }

    @Override
    public ReadContext limit(int maxResults){
        return withListeners(new LimitingEvaluationListener(maxResults));
    }

    @Override
    public ReadContext withListeners(EvaluationListener... listener){
        return new JsonContext(json, configuration.setEvaluationListeners(listener));
    }


    private <T> T convert(Object obj, Class<T> targetType, Configuration configuration){
        return configuration.mappingProvider().map(obj, targetType, configuration);
    }

    private <T> T convert(Object obj, TypeRef<T> targetType, Configuration configuration){
        return configuration.mappingProvider().map(obj, targetType, configuration);
    }

    @Override
    public DocumentContext set(String path, Object newValue, Predicate... filters) {
        return set(compile(path, filters), newValue);
    }

    @Override
    public DocumentContext set(JsonPath path, Object newValue){
        List<String> modified = path.set(json, newValue, configuration.addOptions(Option.AS_PATH_LIST));
        if(logger.isDebugEnabled()){
            for (String p : modified) {
                logger.debug("Set path {} new value {}", p, newValue);
            }
        }
        return this;
    }

    @Override
    public DocumentContext map(String path, MapFunction mapFunction, Predicate... filters) {
        map(compile(path, filters), mapFunction);
        return this;
    }

    @Override
    public DocumentContext map(JsonPath path, MapFunction mapFunction) {
        path.map(json, mapFunction, configuration);
        return this;
    }

    @Override
    public DocumentContext delete(String path, Predicate... filters) {
        return delete(compile(path, filters));
    }

    @Override
    public DocumentContext delete(JsonPath path) {
        List<String> modified = path.delete(json, configuration.addOptions(Option.AS_PATH_LIST));
        if(logger.isDebugEnabled()){
            for (String p : modified) {
                logger.debug("Delete path {}");
            }
        }
        return this;
    }

    @Override
    public DocumentContext add(String path, Object value, Predicate... filters){
        return add(compile(path, filters), value);
    }

    @Override
    public DocumentContext add(JsonPath path, Object value){
        List<String> modified =  path.add(json, value, configuration.addOptions(Option.AS_PATH_LIST));
        if(logger.isDebugEnabled()){
            for (String p : modified) {
                logger.debug("Add path {} new value {}", p, value);
            }
        }
        return this;
    }

    @Override
    public DocumentContext put(String path, String key, Object value, Predicate... filters){
        return put(compile(path, filters), key, value);
    }

    @Override
    public DocumentContext renameKey(String path, String oldKeyName, String newKeyName, Predicate... filters) {
        return renameKey(compile(path, filters), oldKeyName, newKeyName);
    }

    @Override
    public DocumentContext renameKey(JsonPath path, String oldKeyName, String newKeyName) {
        List<String> modified =  path.renameKey(json, oldKeyName, newKeyName, configuration.addOptions(Option.AS_PATH_LIST));
        if(logger.isDebugEnabled()){
            for (String p : modified) {
                logger.debug("Rename path {} new value {}", p, newKeyName);
            }
        }
        return this;
    }


    @Override
    public DocumentContext put(JsonPath path, String key, Object value){
        List<String> modified = path.put(json, key, value, configuration.addOptions(Option.AS_PATH_LIST));
        if(logger.isDebugEnabled()){
            for (String p : modified) {
                logger.debug("Put path {} key {} value {}", p, key, value);
            }
        }
        return this;
    }

    private final static class LimitingEvaluationListener implements EvaluationListener {
        final int limit;

        private LimitingEvaluationListener(int limit) {
            this.limit = limit;
        }

        @Override
        public EvaluationContinuation resultFound(FoundResult found) {
            if(found.index() == limit - 1){
                return EvaluationContinuation.ABORT;
            } else {
                return EvaluationContinuation.CONTINUE;
            }
        }
    }

}
