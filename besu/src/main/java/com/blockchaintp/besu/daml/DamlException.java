/*
 * Copyright © 2023 Paravela Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blockchaintp.besu.daml;

/**
 * Base Exception for exceptions that included opaque extended data that must be returned on the
 * response.
 */
public class DamlException extends Exception {
  private static final long serialVersionUID = 1L;

  /** Optional extra data for this error. */
  private final byte[] extendedData;

  /**
   * Creates an exception.
   *
   * @param message the message to return on the response
   */
  public DamlException(final String message) {
    super(message);
    this.extendedData = null;
  }

  /**
   * Creates an exception with extended data.
   *
   * @param message the message to returned on the response
   * @param myExtendedData opaque, application-specific encoded data to be returned on the response
   */
  public DamlException(final String message, final byte[] myExtendedData) {
    super(message);
    this.extendedData = myExtendedData;
  }

  /**
   * The extended data associated with this exception.
   *
   * @return opaque, application-specific encoded data
   */
  public final byte[] getExtendedData() {
    return extendedData;
  }
}
