/*
 * Copyright 2022 Blockchain Technology Partners
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

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.protobuf.ByteString;

public class KeyCacheSingleton {
  private static final long MAX_CACHE_SIZE = 100;
  private static KeyCacheSingleton instance;
  private Cache<ByteString, DamlStateKey> cache;

  public Cache<ByteString, DamlStateKey> getCache() {
    return cache;
  }

  private KeyCacheSingleton() {
    cache = Caffeine.newBuilder().maximumSize(MAX_CACHE_SIZE).build();
  }

  synchronized public static KeyCacheSingleton getInstance() {
    if (null == instance) {
      instance = new KeyCacheSingleton();
    }

    return instance;
  }
}
