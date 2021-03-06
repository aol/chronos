package com.huffingtonpost.chronos.model;

import com.huffingtonpost.chronos.agent.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TestJobDao {

  int limit = AgentConsumer.LIMIT_JOB_RUNS;
  JobDao dao;

  @Before
  public void setUp() throws Exception {
    dao = new H2TestJobDaoImpl();
    dao.init();
  }

  @After
  public void cleanup() throws Exception {
    dao.close();
  }

  @Test
  public void testBasic() {
    JobSpec expected = TestAgent.getTestJob("William Faulkner", dao);
    try {
      dao.createJob(expected);
    } catch (Exception ex) { ex.printStackTrace(); }

    JobSpec actual = dao.getJob(expected.getId());
    assertEquals(expected, actual);
  }

  @Test
  public void updateJob() {
    JobSpec expected = TestAgent.getTestJob("Thomas Bernhard", dao);
    try {
      dao.createJob(expected);
      expected.setDescription("The Woodcutters");
      Thread.sleep(10);
      dao.updateJob(expected);
    } catch (Exception ex) { ex.printStackTrace(); }

    JobSpec actual = dao.getJob(expected.getId());
    assertEquals(expected, actual);
  }

  @Test
  public void deleteJob() {
    JobSpec expected = TestAgent.getTestJob("Jane Austen", dao);
    try {
      dao.createJob(expected);
      expected.setName("Virginia Woolf");
      Thread.sleep(10);
      dao.updateJob(expected);
      dao.deleteJob(expected.getId());
    } catch (Exception ex) { ex.printStackTrace(); }
    
    JobSpec actual = dao.getJob(expected.getId());
    assertEquals(null, actual);
  }

  @Test
  public void testGetJobs() {
    List<JobSpec> expected = new ArrayList<>();
    for (String aName : new String[]{ "Allen Ginsberg", "Louis Gluck"}) {
      JobSpec aJob = TestAgent.getTestJob(aName, dao);
      try {
        dao.createJob(aJob);
      } catch (Exception ex) { ex.printStackTrace(); }
      expected.add(aJob);
    }

    List<JobSpec> actual = dao.getJobs();
    assertEquals(expected, actual);
  }

  @Test
  public void testBasicDependent() {
    JobSpec expected = TestAgent.getTestJob("Mrs Dalloway", dao);
    JobSpec childJob = TestAgent.getTestJob("A child", dao);
    try {
      dao.createJob(expected);
      childJob.setParent(expected.getId());
      dao.createJob(childJob);
    } catch (Exception ex) { ex.printStackTrace(); }

    JobSpec actual = dao.getJob(expected.getId());
    assertEquals(expected, actual);
  }

  @Test
  public void testQueueJob() throws Exception {
    JobSpec job = TestAgent.getTestJob("blah", dao);
    long id = dao.createJob(job);
    job = dao.getJob(id);
    PlannedJob aJob =
      new PlannedJob(job, Utils.getCurrentTime());
    List<PlannedJob> expected = new ArrayList<>();
    expected.add(aJob);

    try {
      dao.addToQueue(aJob);
    } catch (Exception ex) { ex.printStackTrace(); }

    List<PlannedJob> actual = dao.getQueue(aJob.getJobSpec().getId());
    assertEquals(expected, actual);

    PlannedJob actualPJ = null;
    try {
      actualPJ = dao.removeFromQueue();
    } catch (Exception ex) { ex.printStackTrace(); }
    assertEquals(aJob, actualPJ);

    expected = new ArrayList<>();
    actual = dao.getQueue(null);
    assertEquals(expected, actual);
  }

  @Test(timeout=3000)
  public void testQueueSynchronization() throws Exception {
    final List<PlannedJob> expected = new CopyOnWriteArrayList<>();
    final int size = 10;
    Runnable t1 = new Thread() {
      public void run() {
        for (int i = 0; i < size; i++) {
          JobSpec job = TestAgent.getTestJob(UUID.randomUUID().toString(), dao);
          long id = dao.createJob(job);
          job = dao.getJob(id);
          PlannedJob aJob =
            new PlannedJob(job, Utils.getCurrentTime());
          try {
            dao.addToQueue(aJob);
          } catch (Exception ex) { ex.printStackTrace(); }
          expected.add(aJob);
        }
      }
    };
    Thread t2 = new Thread(t1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    executor.submit(t1);
    executor.submit(t2);
    executor.awaitTermination(2500, TimeUnit.MILLISECONDS);

    List<PlannedJob> sorted = new ArrayList<>(expected); // so we can sort
    Collections.sort(sorted);
    List<PlannedJob> actual = dao.getQueue(null);
    Collections.sort(actual);
    assertEquals(size*2, actual.size());
    assertEquals(sorted, actual);
  }

  @Test
  public void testGetJobVersions() {
    JobSpec expected = TestAgent.getTestJob("Franz Kafka", dao);
    try {
      dao.createJob(expected);
      expected = dao.getJob(expected.getId());
      expected.setDescription("Metamorphosis");
      dao.updateJob(expected);
    } catch (Exception ex) { ex.printStackTrace(); }

    JobSpec actual = dao.getJob(expected.getId());
    assertEquals(expected, actual);
    List<JobSpec> versions = dao.getJobVersions(actual.getId());
    assertEquals(2, versions.size());
  }

  @Test
  public void testQueueLarge() {
    int count = 1000;
    for (int i = 1; i <= count; i++) {
      JobSpec aJob = TestAgent.getTestJob("Vincent Van Gogh", dao);
      long id = dao.createJob(aJob);
      aJob = dao.getJob(id);
      PlannedJob pj = new PlannedJob(aJob, Utils.getCurrentTime());
      dao.addToQueue(pj);
    }
    assertEquals(count, dao.getQueue(null).size());
  }

  @Test
  public void testGetJobRuns() {
    List<Long> ids = new ArrayList<>();
    Map<Long, CallableJob> expected = new HashMap<>();
    for (String name : new String[] { "Wifredo Lam", "Rene Magritte" }) {
      JobSpec aJob = TestAgent.getTestJob(name, dao);
      try {
        dao.createJob(aJob);
        aJob = dao.getJob(aJob.getId());
        PlannedJob pj = new PlannedJob(aJob, Utils.getCurrentTime());
        CallableJob cj = new CallableQuery(pj, dao, null,
          "example.com", null, null, null, null, 1);
        dao.createJobRun(cj);
        ids.add(aJob.getId());
        expected.put(aJob.getId(), cj);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    for (Long id : ids) {
      Map<Long, CallableJob> got = dao.getJobRuns(id, limit);
      assertEquals(1, got.size());
      assertEquals(expected.get(id), got.get(id));
    }
    Map<Long, CallableJob> got = dao.getJobRuns(null, limit);
    assertEquals(expected, got);
  }

  @Test
  public void testGetTree() {
    JobSpec parent = TestAgent.getTestJob("A", dao);
    JobSpec childJob = TestAgent.getTestJob("B", dao);
    JobSpec childJob2 = TestAgent.getTestJob("C", dao);
    JobSpec childChildJob = TestAgent.getTestJob("D", dao);
    try {
      dao.createJob(parent);
      childJob.setParent(parent.getId());
      dao.createJob(childJob);
      childJob2.setParent(parent.getId());
      dao.createJob(childJob2);
      childChildJob.setParent(childJob.getId());
      dao.createJob(childChildJob);
    } catch (Exception ex) { ex.printStackTrace(); }

    JobNode expected = new JobNode(parent.getName(), null);
    JobNode eChild = new JobNode(childJob.getName(), parent.getName());
    expected.getChildren().add(eChild);
    JobNode eChild2 = new JobNode(childJob2.getName(), parent.getName());
    expected.getChildren().add(eChild2);
    JobNode eChildChild = new JobNode(childChildJob.getName(), childJob.getName());
    eChild.getChildren().add(eChildChild);

    JobNode actual = dao.getTree(parent.getId(), null);
    assertEquals(expected, actual);

    expected = new JobNode(childJob.getName(), null);
    expected.getChildren().add(eChildChild);
    actual = dao.getTree(childJob.getId(), null);
    assertEquals(expected, actual);
  }
}
