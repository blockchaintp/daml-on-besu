package com.blockchaintp.besu.daml.rpc;

/**
 * A Submitter monitors a queue and does whatever is necessary to
 * commit the operation of type T.
 *
 * @param <T>
 */
public interface Submitter<T> extends Runnable {

  /**
   * Set whether or not to continue.
   *
   * @param running true to keep running, false to stop at the next chance
   */
  void setKeepRunning(final boolean running);

  /**
   * Place operation on to the submitter queue
   *
   * @param operation operation to submit
   * @throws InterruptedException
   */
  void put(T operation) throws InterruptedException;
}
