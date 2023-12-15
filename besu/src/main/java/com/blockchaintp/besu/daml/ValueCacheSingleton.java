/*
 * Copyright © 2023 Paravela Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.blockchaintp.besu.daml;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.protobuf.ByteString;

public class ValueCacheSingleton {
  private static final String VALUE_CACHE_SIZE_ENV = "VALUE_CACHE_SIZE";
  private static final String DEFAULT_CACHE_SIZE = "100";
  private static ValueCacheSingleton instance;
  private Cache<ByteString, ByteString> cache;

  public Cache<ByteString, ByteString> getCache() {
    return cache;
  }

  private ValueCacheSingleton() {
    var env = System.getenv();
    long cacheSize = Long.parseLong(env.getOrDefault(VALUE_CACHE_SIZE_ENV, DEFAULT_CACHE_SIZE));
    cache = Caffeine.newBuilder().maximumSize(cacheSize).build();
  }

  public static synchronized ValueCacheSingleton getInstance() {
    if (null == instance) {
      instance = new ValueCacheSingleton();
    }

    return instance;
  }
}
