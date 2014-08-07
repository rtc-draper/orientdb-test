/**
 * 
 */
package com.draper.orientdbtest;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Random;
import joptsimple.OptionSet;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

/**
 * @author rick
 * 
 */
public class RecordWriter extends Thread {
  private OrientGraphFactory DBConn;
  private int FailureLimit;
  private int FailCount;
  private HashMap<Integer, OrientVertex> VertexMap;
  private OptionSet Opts;

  public RecordWriter(OptionSet CLOpts, OrientGraphFactory DBC) {
    this(CLOpts, DBC, 10);
  }

  public RecordWriter(OptionSet CLOpts, OrientGraphFactory DBC, 
    int FailLimit) {
    FailureLimit = FailLimit;
    FailCount = 0;
    Opts = CLOpts;
    DBConn = DBC;
    VertexMap = new HashMap<Integer, OrientVertex>();
  }

  /**
   * Attempts to add to the DB. 
   * 
   * @return if the commit was successful.
   */
  private boolean addVertsToDB() {
    OrientGraph DB = DBConn.getTx();
    Integer Seed = (Integer) Opts.valueOf("s");
    Random RNG = new Random(Seed);
    Integer NumVerts = Math.max((int) Opts.valueOf("minv"), 
                                RNG.nextInt((int) Opts.valueOf("maxv")));
    Double EdgeFactor = (Double) Opts.valueOf("ef");
    try {
      for (Integer i = 0; i < NumVerts; ++i) {
        VertexMap.put(i, DB.addVertex("class:Testing", "vertid", i));
      }
      for (Integer i = 0; i < (int) (EdgeFactor*NumVerts); ++i) {
        DB.addEdge(null,
                   VertexMap.get(RNG.nextInt(NumVerts)),
                   VertexMap.get(RNG.nextInt(NumVerts)),
                   "points");
      }
      DB.commit();
      return true;
    } catch (Exception E) {
      DB.rollback();
      VertexMap.clear();
      System.err.println("Error when committing to DB: " + E.getMessage());
      //E.printStackTrace();
    } finally {
      DB.shutdown();
    }
    return false;
  }

  /**
   * Sleep, then check if we exceeded the failure count.
   * 
   * @return true if we exceed the failure count, false otherwise.
   */
  private int Timeout = 50;

  private boolean exceedsFailureLimit() {
    try {
      System.out.println(Thread.currentThread().getId() + " sleeping " +
                Timeout + "ms...");
      Thread.sleep(Timeout);
      Timeout = (int)(Math.random()*Timeout) + Timeout; // random backoff algorithm.
    } catch (InterruptedException E) {
      E.printStackTrace();
    }

    if (++FailCount >= FailureLimit) {
      return true;
    }
    return false;
  }
  /**
   * Resets the fail counter.
   */
  private void resetFailureCount() {
    FailCount = 0;
  }

  @Override
  public void run() {
    try {
      System.err.println("BEGIN: " + Thread.currentThread().getId());
  
      resetFailureCount();
      while (!addVertsToDB()) {
        System.err.println("Error: " + Thread.currentThread().getId()
            + " failed to commit to database!");
        if (exceedsFailureLimit()) {
          System.err.println("Exceeded failure limit, cancelling operation!");
          return;
        }
      }
      System.out.println("ADDED: " + Thread.currentThread().getId());
    } finally {
      ThreadMXBean B = ManagementFactory.getThreadMXBean();
      ThreadInfo TI = B.getThreadInfo(Thread.currentThread().getId());
      if (TI.getLockedMonitors().length != 0 
          || TI.getLockedSynchronizers().length != 0) {
        System.err.println("SEVERE: DID NOT RELEASE LOCK!!!!");
        for (MonitorInfo MI : TI.getLockedMonitors()) {
          System.err.println("Depth: " + MI.getLockedStackDepth() + ", "
              + "Frame: " + MI.getLockedStackFrame().toString());
        }
        for (LockInfo MI : TI.getLockedSynchronizers()) {
          System.err.println(MI.toString());
        }
      }
      
    }
  }
}
