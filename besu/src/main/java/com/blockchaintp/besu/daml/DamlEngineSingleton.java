package com.blockchaintp.besu.daml;

import com.daml.lf.engine.Engine;

/**
 * Create a singleton instance of the DAML engine in a thread-safe way using the Bill Pugh pattern.
 */
public class DamlEngineSingleton {
  public static Engine getInstance() {
    return SingletonHelper.INSTANCE;
  }

  private static class SingletonHelper {
    private static final Engine INSTANCE = Engine.StableEngine();
  }

  private DamlEngineSingleton() {
  }
}
