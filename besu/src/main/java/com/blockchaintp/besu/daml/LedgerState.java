/*
 * Copyright 2020 Blockchain Technology Partners.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blockchaintp.besu.daml;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.blockchaintp.besu.daml.protobuf.TimeKeeperUpdate;
import com.daml.ledger.validator.LedgerStateAccess;
import com.daml.ledger.validator.LedgerStateOperations;
import com.daml.lf.data.Time.Timestamp;
import com.google.protobuf.ByteString;

/**
 * An interface to keep the coupling to the context implementation loose.
 *
 * @author scealiontach
 */
public interface LedgerState<T> extends LedgerStateOperations<T>, LedgerStateAccess<T> {
  /**
   * Fetch a single DamlStateValue from the ledger state.
   *
   * @param key DamlStateKey identifying the operation
   * @return the DamlStateValue for this key or null
   */
  ByteString getDamlState(ByteString key);

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
   *
   * @param keys a collection DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   */
  Map<ByteString, ByteString> getDamlStates(Collection<ByteString> keys);

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
   *
   * @param keys one or more DamlStateKeys identifying the values to be fetches
   * @return a map of DamlStateKeys to DamlStateValues
   */
  Map<ByteString, ByteString> getDamlStates(ByteString... keys);

  /**
   * @param key   The key identifying this DamlStateValue
   * @param value the DamlStateValue
   * @throws InternalError when there is an unexpected back end error.
   */
  void setDamlState(ByteString key, ByteString value) throws InternalError;

  /**
   * Store a collection of DamlStateValues at the logical keys provided.
   *
   * @param entries a collection of tuples of DamlStateKey to DamlStateValue mappings
   * @throws InternalError when there is an unexpected back end error.
   */
  void setDamlStates(Collection<Entry<ByteString, ByteString>> entries) throws InternalError;

  /**
   * Record an event containing the provided log info.z
   *
   * @param entryId the id of this log entry
   * @param entry   the entry itself
   * @throws InternalError when there is an unexpected back end error
   */
  DamlLogEvent sendLogEvent(ByteString entryId, ByteString entry) throws InternalError;

  DamlLogEvent sendTimeEvent(com.google.protobuf.Timestamp timeUpdate) throws InternalError;

  /**
   * Fetch the current global record time.
   *
   * @return a Timestamp
   * @throws InternalError when there is an unexpected back end error.
   */
  Timestamp getRecordTime() throws InternalError;

  com.google.protobuf.Timestamp updateTime(String participantId, TimeKeeperUpdate update);
}
