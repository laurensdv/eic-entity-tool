
/**
 * @project trec-entity-tool
 * @author MMLab
 */
package org.sindice.siren.index;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Index a list of entities, creating incoming, outgoing triples fields, subject
 * and type fields. The type field is a grouping of the rdf:type objects for this
 * entity.<br>
 * Outgoing triples are stored as n-tuples where a predicate has all its related
 * values.
 * Incoming triples are also stored as n-tuples, the difference being that a
 * predicate possess its related subject URIs.
 */
public abstract class IndexingMMLab implements Iterator<Entity> {
  
  protected final Logger            logger            = LoggerFactory.getLogger(IndexingMMLab.class);
  
  /* Perform a commit by batch of COMMIT documents */
  public static int                 COMMIT            = 10000;
  public static boolean             STORE             = false;
  public static int                 SKIP_TO           = 0;
  public static boolean             CLEAR			  = false;
  
  /* FIELDS */
  final static public String        URL               = "url";
  final static public String        NTRIPLE           = "ntriple";
  final static public String        TYPE              = "type";
  final static public String        LABEL              = "label";
  final static public String        DESCRIPTION              = "description";
  
  /* The dataset files */
  protected final File[]            input;
  protected int                     inputPos          = 0;
  
  /* The current reader into the compressed archive */
  protected TarArchiveInputStream   reader            = null;
  
  /* A file entry in the archive */
  protected TarArchiveEntry         tarEntry;
  
  /* SIREn index */
  protected final String         	indexURL;
  private final SolrServer 			server;
  
  /*
   * Threading
   */
  protected int poolSize									= 256;
  protected int timeOut										= 1;
  protected BlockingQueue<Runnable> worksQueue = new ArrayBlockingQueue<Runnable>(COMMIT);
  protected RejectedExecutionHandler executionHandler = new MyRejectedExecutionHandelerImpl();
   
  // Create the ThreadPoolExecutor
  protected ThreadPoolExecutor executor = new ThreadPoolExecutor(this.poolSize, this.poolSize, this.timeOut, TimeUnit.MINUTES, worksQueue, executionHandler);
  
  protected AtomicLong counter = new AtomicLong(0);
  
  protected class MyRejectedExecutionHandelerImpl implements RejectedExecutionHandler
  {
      @Override
      public void rejectedExecution(Runnable runnable,
              ThreadPoolExecutor executor)
      {
          System.out.println(runnable.toString() + " : I've been rejected ! ");
      }
  }

  /**
   * Create a SIREn index at indexDir, taking the files at inputDir as input.
   * @param inputDir
   * @param dir
   * @throws IOException
   */
  public IndexingMMLab(final File inputDir, final String url)
	throws SolrServerException, IOException {
	this.executor.allowCoreThreadTimeOut(true);
	  
    this.input = inputDir.listFiles(new FilenameFilter() {
      
      @Override
      public boolean accept(File dir, String name) {
        if (name.matches(getPattern())) {
          final int dump = Integer.valueOf(name.substring(3, name.indexOf('.')));
          return dump >= SKIP_TO; // discards any dump files lower than #SKIP_TO
        }
        return false;
      }
      
    });
    
    /*
     *  Sort by filename: important because in the SIndice-ED dataset, two
     *  consecutive dumps can store a same entity
     */
    Arrays.sort(this.input);
    if (this.input.length == 0) {
      throw new RuntimeException("No archive files in the folder: " + inputDir.getAbsolutePath());
    }
    
    
    this.indexURL = url;
    //server = new CommonsHttpSolrServer(indexURL);
    server = new StreamingUpdateSolrServer(indexURL,10000,poolSize);
    // Clear the index
    if (CLEAR){
    	clear();
    }
    
    reader = getTarInputStream(this.input[0]);
    logger.info("Creating index from input located at {} ({} files)", inputDir.getAbsolutePath(), input.length);
    logger.info("Reading dump: {}", this.input[0]);
  }
  
  /**
   * The regular expression of the input files
   * @return
   */
  protected abstract String getPattern();

  /**
   * Create a buffered tar inputstream from the file in
   * @param in
   * @return
   * @throws FileNotFoundException
   * @throws IOException
   */
  private TarArchiveInputStream getTarInputStream(final File in)
  throws FileNotFoundException, IOException {
    return new TarArchiveInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(in))));
  }
  
  @Override
  public boolean hasNext() {
    return this.hasNext(null);
  }
  
  /**
   * Move to the next tar entry.
   * @param rootDir an entry path
   * @return true if a next tar entry can be read, or if this entry name is a sub-folder of rootDir
   */
  protected synchronized boolean hasNext(final String rootDir) {
    try {
      /*
       * if reader.available() is not equal to 0, then it means that this entry
       * has been loaded, but not read.
       */
      while (reader.available() == 0 && (tarEntry = reader.getNextTarEntry()) == null) { // Next tar entry
        if (++inputPos >= input.length) {
          reader.close();
          return false;
        }
        // Next archive file
        reader.close();
        logger.info("Reading dump: {}", this.input[inputPos]);
        reader = getTarInputStream(input[inputPos]);
      }
    } catch (IOException e) {
      logger.error("Error while reading the input: {}\n{}", input[inputPos], e);
    }
    /*
     *  When returning from this method, the inputstream is positionned at a regular file,
     *  i.e., metadata, outgoing-triples.nt or incoming-triples.nt.
     */
    if (tarEntry.isDirectory()) {
      return hasNext(rootDir);
    }
    return rootDir == null || tarEntry.getName().startsWith(rootDir) ? true : false;
  }
  

  /**
   * Creates an entity index
   * @throws IOException
   * @throws SolrServerException 
   */
  public void indexIt()
  throws IOException, SolrServerException {
	// Starting the monitor thread as a daemon
	Thread monitor = new Thread(new MyMonitorThread(this.executor));
	monitor.setDaemon(true);
	monitor.start();
    Entity entity = null;
    
    while (hasNext()) { // for each entity
    	if (this.worksQueue.remainingCapacity() > 0) {
    		entity = next();
    		//createDocument(entity);
      
    		this.executor.execute(new MyDocumentCreation(entity, this));
    	}
    	else {
    		try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				logger.error("could not sleep while waiting for queue spot",e);
			}
    	}
    }
    commit(false, this.counter, entity.subject); // Commit what is left
  }

  protected void createDocument(Entity entity) {
  		final SolrInputDocument document = new SolrInputDocument();
        document.addField(URL, StringUtils.strip(entity.subject, "<>"));
        document.addField(NTRIPLE, cleanup(entity.getTriples(true)));
        document.addField(TYPE, Utils.toString(entity.type));
        document.addField(LABEL, Utils.toString(entity.label));
        document.addField(DESCRIPTION, Utils.toString(entity.description));
        try {
      	  add(document);
      	  this.counter = commit(true, this.counter, entity.subject);
        } catch (Exception e) {
      	  logger.error("Error while processing the document: {}", e);
        }
  }

  
/**
 * My {@link Runnable} class. Represents a task which need to be executed.
 */
private class MyDocumentCreation implements Runnable
{
	Entity entity;
	IndexingMMLab parent;
	
    public MyDocumentCreation(Entity entity, IndexingMMLab parent) {
    	this.entity = entity;
    	this.parent = parent;
    }
 
    @Override
    public void run()
    { 
        this.parent.createDocument(this.entity);
    }
    

}

/**
 * My monitor thread. To monitor the status of {@link ThreadPoolExecutor}
 * and its status.
 */
public class MyMonitorThread implements Runnable
{
    ThreadPoolExecutor executor;
 
    public MyMonitorThread(ThreadPoolExecutor executor)
    {
        this.executor = executor;
    }
 
    @Override
    public void run()
    {
        try
        {
            do
            {
                System.out.println(
                    String.format("[monitor] [%d/%d] Active: %d, Completed: %d, Task: %d, isShutdown: %s, isTerminated: %s",
                        this.executor.getPoolSize(),
                        this.executor.getCorePoolSize(),
                        this.executor.getActiveCount(),
                        this.executor.getCompletedTaskCount(),
                        this.executor.getTaskCount(),
                        this.executor.isShutdown(),
                        this.executor.isTerminated()));
                Thread.sleep(3000);
            }
            while (true);
        }
        catch (Exception e)
        {
        }
    }
}

/**
 * @param triples
 * @return
 */
private Object cleanup(String triples) {
	
    //if triples.contains();
	
	return triples;
}

/**
   * Commits the documents by batch
   * @param indexing
   * @param counter
   * @param subject
   * @return
   * @throws CorruptIndexException
   * @throws IOException
   */
  private AtomicLong commit(boolean indexing, AtomicLong counter, String subject)
  throws SolrServerException, IOException {
	long cnt = counter.incrementAndGet();
    if (!indexing || (cnt % COMMIT) == 0) { // Index by batch
    	server.commit();
    	logger.info("Commited {} entities. Last entity: {}", (indexing ? COMMIT : counter.get()), subject);
    }
    return counter;
  }
  

  @Override
  public void remove() {
  }
  
  /**
   * Add a {@link SolrInputDocument}.
   */
  public void add(final SolrInputDocument doc)
  throws SolrServerException, IOException {
    final UpdateRequest request = new UpdateRequest();
    request.add(doc);
    request.process(server);
  }
  
  /**
   * Commit all documents that have been submitted
   */
  public synchronized void commit()
  throws SolrServerException, IOException {
    server.commit();
  }
  
  /**
   * Delete all the documents
   */
  public synchronized void clear() throws SolrServerException, IOException {
    server.deleteByQuery("*:*");
    server.commit();
  }
  
}
