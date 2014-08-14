/**
 * This is a test for orientdb insertion
 */
package com.draper.orientdbtest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class Main {

  /**
   * Sets up command line arguments.
   * 
   * @param Args
   *          - String array of args passed to program
   * @return An OptionSet object of the parsed arguments
   */
  public static final OptionSet parseCLOpts(String[] Args) {
    // Setup and parse command line options.
    OptionParser CLOptsParser = new OptionParser() {
      {
        accepts("db", "database").withRequiredArg().ofType(String.class)
            .describedAs("uri");        
        accepts("j", "Maximum number of threads to run.")
            .withRequiredArg().ofType(Integer.class).defaultsTo(16);
        accepts("minv", "Minimum number of vertices")
            .withRequiredArg().ofType(Integer.class).defaultsTo(2000);
        accepts("maxv", "Maximum number of vertices.")
            .withRequiredArg().ofType(Integer.class).defaultsTo(10000);
        accepts("ef", "Edge factor multiplier.")
            .withRequiredArg().ofType(Double.class).defaultsTo(1.3);
        accepts("s", "Random number generator seed.")
            .withRequiredArg().ofType(Integer.class).defaultsTo(0);
        accepts("c", "Total number of threads to run")
            .withRequiredArg().ofType(Integer.class).defaultsTo(10000);
        accepts("cmin", "Minimum client pool")
            .withRequiredArg().ofType(Integer.class).defaultsTo(100);
        accepts("cmax", "Maximum client pool")
          .withRequiredArg().ofType(Integer.class).defaultsTo(200);
        acceptsAll(Arrays.asList("h", "?"), "show help").forHelp();
      }
    };

    OptionSet CLOpts = CLOptsParser.parse(Args);
    // Print help and exit
    if (CLOpts.has("h")) {
      try {
        CLOptsParser.printHelpOn(System.out);
      } catch (IOException E) {
        System.err.println("Cannot print help!");
        E.printStackTrace();
        System.exit(74);
      }
      System.exit(0);
    }

    return CLOpts;
  }



  /**
   * Opens the db connection, and calls a thread to import random data into
   * orientdb.
   * 
   * @param Args
   *          - cmd line args
   * @throws InterruptedException
   */
  public static final void main(String[] Args) throws InterruptedException {
    ExecutorService ThreadPool;
    // Parse Command Line Options
    OptionSet CLOpts = parseCLOpts(Args);

    Integer NumThreads = (Integer) CLOpts.valueOf("j");
    // Various bits of orient to toggle
//    OGlobalConfiguration.CLIENT_CONNECT_POOL_WAIT_TIMEOUT.setValue(5000);
//    OGlobalConfiguration.CLIENT_DB_RELEASE_WAIT_TIMEOUT.setValue(5000);
    OGlobalConfiguration.CLIENT_CHANNEL_MAX_POOL.setValue(
        (Integer)CLOpts.valueOf("cmin"));
    OGlobalConfiguration.CLIENT_CHANNEL_MIN_POOL.setValue(
        (Integer)CLOpts.valueOf("cmax"));
//    OGlobalConfiguration.NETWORK_LOCK_TIMEOUT.setValue(30000);
//    OGlobalConfiguration.NETWORK_SOCKET_TIMEOUT.setValue(30000);
//    OGlobalConfiguration.CACHE_LEVEL1_ENABLED.setValue(false);
//    OGlobalConfiguration.CACHE_LEVEL2_ENABLED.setValue(false);
//    OGlobalConfiguration.WAL_COMMIT_TIMEOUT.setValue(100);
//    OGlobalConfiguration.WAL_SHUTDOWN_TIMEOUT.setValue(10000);
    System.setProperty("log.console.level", "FINEST");
    OLogManager.installCustomFormatter();
    ThreadPool = Executors.newFixedThreadPool(NumThreads);
    OrientGraphFactory DBC =
        new OrientGraphFactory((String)CLOpts.valueOf("db"));
    DBC.setupPool(NumThreads, NumThreads*2);

    // Add a class with some clusters
    OrientGraphNoTx DB = DBC.getNoTx();
    try {
      addClass(DB, "Testing", "V", 500);
    } finally {
      DB.shutdown();
    }

    LinkedTransferQueue<ThreadEntry> Thds =
        new LinkedTransferQueue<ThreadEntry>();
    Main d = new Main();
    int ThreadTimeout = 120000;
    Vector<Future<?>> CancelList = new Vector<Future<?>>();
    for (int i = 0; i < (int) CLOpts.valueOf("c"); ++i) {
      Thds.add(d.new ThreadEntry(ThreadPool.submit(new RecordWriter(CLOpts, DBC))));
      
      // Start timing out when we get too many threads in the queue. 
      while (Thds.size() > NumThreads*2 || (Thds.size() > 0 
          && System.currentTimeMillis()-Thds.peek().startTime > ThreadTimeout)) {
        while (Thds.size() > NumThreads || (Thds.size() > 0 
            && System.currentTimeMillis()-Thds.peek().startTime > ThreadTimeout)) {
          ThreadEntry Thd = Thds.poll();
          if (Thd != null && !Thd.ThdId.isDone()) {
            try {
              System.out.println("Waiting on " + Thd.ThdId.toString());
              Thd.ThdId.get(Math.max(ThreadTimeout, 1), 
                            TimeUnit.MILLISECONDS);
              System.out.println("done!");
            } catch (Exception E) {
              System.out.println("adding to kill queue");
              CancelList.add(Thd.ThdId);
            }
          }
        }
      }
    }
    ThreadPool.shutdown();
    ThreadEntry Thd = Thds.poll();
    while (Thd != null) {
      try {
        Thd.ThdId.get(Math.max(ThreadTimeout
            -(System.currentTimeMillis()-Thd.startTime), 1), 
          TimeUnit.MILLISECONDS);
      } catch (Exception E) {
        System.out.println("Killing: " + Thd.ThdId.toString());
        Thd.ThdId.cancel(true);
      }
      Thd = Thds.poll();
    }
    for (Future<?> T : CancelList) {
      System.out.println("Killing: " + T.toString());
      T.cancel(true);
    }
    DBC.close();
  }
  private static void addClass(OrientGraphNoTx DB, String ClassName,
      String ParentClassName, int NumClusters) {
    OCommandSQL SetNumQry, CreateClassQry;
    SetNumQry = new OCommandSQL("alter database minimumclusters "
      + NumClusters);
    CreateClassQry = new OCommandSQL("create class " + ClassName + " extends "
      + ParentClassName);
    for (int i = 0; i < 100; ++i) {
      try {
        OSchema Schema = DB.getRawGraph().getMetadata().getSchema();
        if (!Schema.existsClass(ParentClassName)) {
          System.err.println(ParentClassName + " does not exist!");
          return;
        }
        if (Schema.existsClass(ClassName)) {
          System.err.println(ClassName + " already exists.");
          return;
        }
        DB.command(SetNumQry).execute();
        DB.command(CreateClassQry).execute();
        return;
      } catch (Exception E) {
        System.err.println("Error adding class " + ClassName
            + ":" + E.getMessage());
      }
      DB.getRawGraph().getMetadata().getSchema().reload();
    }
  }
  public class ThreadEntry {
    Future<?> ThdId;
    long startTime;
    public ThreadEntry(Future<?> T) {
      ThdId = T;
      startTime = System.currentTimeMillis();
    }
  }
}
