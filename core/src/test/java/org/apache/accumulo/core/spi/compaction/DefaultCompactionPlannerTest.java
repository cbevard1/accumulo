/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.core.spi.compaction;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.accumulo.core.client.admin.compaction.CompactableFile;
import org.apache.accumulo.core.conf.ConfigurationCopy;
import org.apache.accumulo.core.conf.ConfigurationTypeHelper;
import org.apache.accumulo.core.conf.DefaultConfiguration;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.TableId;
import org.apache.accumulo.core.spi.common.ServiceEnvironment;
import org.apache.accumulo.core.spi.common.ServiceEnvironment.Configuration;
import org.apache.accumulo.core.spi.compaction.CompactionPlan.Builder;
import org.apache.accumulo.core.spi.compaction.CompactionPlanner.InitParameters;
import org.apache.accumulo.core.util.ConfigurationImpl;
import org.apache.accumulo.core.util.compaction.CompactionExecutorIdImpl;
import org.apache.accumulo.core.util.compaction.CompactionPlanImpl;
import org.apache.accumulo.core.util.compaction.CompactionPlannerInitParams;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParseException;

public class DefaultCompactionPlannerTest {

  private static <T> T getOnlyElement(Collection<T> c) {
    return c.stream().collect(onlyElement());
  }

  private static final Configuration defaultConf =
      new ConfigurationImpl(DefaultConfiguration.getInstance());
  private static final CompactionServiceId csid = CompactionServiceId.of("cs1");
  private static final String prefix = Property.COMPACTION_SERVICE_PREFIX.getKey();

  @Test
  public void testFindFilesToCompact() throws Exception {

    testFFtC(createCFs("F4", "1M", "F5", "1M", "F6", "1M"),
        createCFs("F1", "100M", "F2", "100M", "F3", "100M", "F4", "1M", "F5", "1M", "F6", "1M"),
        2.0);

    testFFtC(createCFs("F1", "100M", "F2", "100M", "F3", "100M", "F4", "1M"), 2.0);

    testFFtC(
        createCFs("F1", "100M", "F2", "99M", "F3", "33M", "F4", "33M", "F5", "33M", "F6", "33M"),
        2.0);
    testFFtC(
        createCFs("F1", "100M", "F2", "99M", "F3", "33M", "F4", "33M", "F5", "33M", "F6", "33M"),
        3.0);

    testFFtC(createCFs("F3", "10M", "F4", "10M", "F5", "10M", "F6", "10M"),
        createCFs("F1", "50M", "F2", "49M", "F3", "10M", "F4", "10M", "F5", "10M", "F6", "10M"),
        2.0);

    testFFtC(createCFs("F3", "10M", "F4", "10M", "F5", "10M", "F6", "10M"),
        createCFs("F1", "50M", "F2", "49M", "F3", "10M", "F4", "10M", "F5", "10M", "F6", "10M"),
        3.0);

    testFFtC(createCFs("S1", "1M", "S2", "1M", "S3", "1M", "S4", "1M"),
        createCFs("B1", "100M", "B2", "100M", "B3", "100M", "B4", "100M", "M1", "10M", "M2", "10M",
            "M3", "10M", "M4", "10M", "S1", "1M", "S2", "1M", "S3", "1M", "S4", "1M"),
        3.0);
    testFFtC(createCFs("M1", "10M", "M2", "10M", "M3", "10M", "M4", "10M", "C1", "4M"),
        createCFs("B1", "100M", "B2", "100M", "B3", "100M", "B4", "100M", "M1", "10M", "M2", "10M",
            "M3", "10M", "M4", "10M", "C1", "4M"),
        3.0);
    testFFtC(createCFs("B1", "100M", "B2", "100M", "B3", "100M", "B4", "100M", "C2", "44M"),
        createCFs("B1", "100M", "B2", "100M", "B3", "100M", "B4", "100M", "C2", "44M"), 3.0);
    testFFtC(createCFs(), createCFs("C3", "444M"), 3.0);

    testFFtC(createCFs(), createCFs("A1", "17M", "S1", "11M", "S2", "11M", "S3", "11M"), 3.0);
    testFFtC(createCFs("A1", "16M", "S1", "11M", "S2", "11M", "S3", "11M"), 3.0);

    testFFtC(
        createCFs("A1", "1M", "A2", "1M", "A3", "1M", "A4", "1M", "A5", "3M", "A6", "3M", "A7",
            "5M", "A8", "5M"),
        createCFs("A1", "1M", "A2", "1M", "A3", "1M", "A4", "1M", "A5", "3M", "A6", "3M", "A7",
            "5M", "A8", "5M", "A9", "100M", "A10", "100M", "A11", "100M", "A12", "500M"),
        3.0);

    testFFtC(
        createCFs("F1", "100M", "F2", "99M", "F3", "33M", "F4", "33M", "F5", "33M", "F6", "33M"),
        3.0);

    testFFtC(createCFs("F3", "10M", "F4", "9M", "F5", "8M", "F6", "7M"),
        createCFs("F1", "12M", "F2", "11M", "F3", "10M", "F4", "9M", "F5", "8M", "F6", "7M"), 3.0,
        4);

    testFFtC(createCFs("F3", "4M", "F4", "8M", "F5", "9M", "F6", "10M"),
        createCFs("F1", "1M", "F2", "2M", "F3", "4M", "F4", "8M", "F5", "9M", "F6", "10M"), 3.0, 4);

    testFFtC(createCFs(),
        createCFs("F1", "1M", "F2", "2M", "F3", "4M", "F4", "8M", "F5", "16M", "F6", "32M"), 3.0,
        4);

    testFFtC(createCFs(), createCFs("F1", "200M", "F2", "200M", "F3", "200M", "F4", "200M", "F5",
        "200M", "F6", "200M"), 3.0, 4, 100_000_000L);

    testFFtC(createCFs("F2", "2M", "F3", "30M", "F4", "30M", "F5", "30M"),
        createCFs("F1", "1M", "F2", "2M", "F3", "30M", "F4", "30M", "F5", "30M", "F6", "30M"), 3.0,
        4, 100_000_000L);

    testFFtC(createCFs("F1", "1M", "F2", "2M", "F3", "30M", "F4", "30M", "F5", "30M"),
        createCFs("F1", "1M", "F2", "2M", "F3", "30M", "F4", "30M", "F5", "30M", "F6", "30M"), 3.0,
        8, 100_000_000L);

    testFFtC(createCFs("F1", "1M", "F2", "2M", "F3", "30M", "F4", "30M", "F5", "30M", "F6", "30M"),
        createCFs("F1", "1M", "F2", "2M", "F3", "30M", "F4", "30M", "F5", "30M", "F6", "30M"), 3.0,
        8, 200_000_000L);

  }

  @Test
  public void testRunningCompaction() throws Exception {
    String executors = "[{'name':'small','type': 'internal','maxSize':'32M','numThreads':1},"
        + "{'name':'medium','type': 'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type': 'internal','maxSize':'512M','numThreads':3},"
        + "{'name':'huge','type': 'internal','numThreads':4}]";

    var planner = createPlanner(defaultConf, executors);

    var all = createCFs("F1", "3M", "F2", "3M", "F3", "11M", "F4", "12M", "F5", "13M");
    var candidates = createCFs("F3", "11M", "F4", "12M", "F5", "13M");
    var compacting =
        Set.of(createJob(CompactionKind.SYSTEM, all, createCFs("F1", "3M", "F2", "3M")));
    var params = createPlanningParams(all, candidates, compacting, 2, CompactionKind.SYSTEM);
    var plan = planner.makePlan(params);

    // The result of the running compaction could be included in a future compaction, so the planner
    // should wait.
    assertTrue(plan.getJobs().isEmpty());

    all = createCFs("F1", "30M", "F2", "30M", "F3", "11M", "F4", "12M", "F5", "13M");
    candidates = createCFs("F3", "11M", "F4", "12M", "F5", "13M");
    compacting = Set.of(createJob(CompactionKind.SYSTEM, all, createCFs("F1", "30M", "F2", "30M")));
    params = createPlanningParams(all, candidates, compacting, 2, CompactionKind.SYSTEM);
    plan = planner.makePlan(params);

    // The result of the running compaction would not be included in future compactions, so the
    // planner should compact.
    var job = getOnlyElement(plan.getJobs());
    assertEquals(candidates, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "medium"), job.getExecutor());
  }

  @Test
  public void testUserCompaction() throws Exception {
    ConfigurationCopy aconf = new ConfigurationCopy(DefaultConfiguration.getInstance());
    aconf.set(prefix + "cs1.planner.opts.maxOpen", "15");
    ConfigurationImpl config = new ConfigurationImpl(aconf);

    String executors = "[{'name':'small','type': 'internal','maxSize':'32M','numThreads':1},"
        + "{'name':'medium','type': 'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type': 'internal','maxSize':'512M','numThreads':3},"
        + "{'name':'huge','type': 'internal','numThreads':4}]";

    var planner = createPlanner(config, executors);
    var all = createCFs("F1", "3M", "F2", "3M", "F3", "11M", "F4", "12M", "F5", "13M");
    var candidates = createCFs("F3", "11M", "F4", "12M", "F5", "13M");
    var compacting =
        Set.of(createJob(CompactionKind.SYSTEM, all, createCFs("F1", "3M", "F2", "3M")));
    var params = createPlanningParams(all, candidates, compacting, 2, CompactionKind.USER);
    var plan = planner.makePlan(params);

    // a running non-user compaction should not prevent a user compaction
    var job = getOnlyElement(plan.getJobs());
    assertEquals(candidates, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "medium"), job.getExecutor());

    // should only run one user compaction at a time
    compacting = Set.of(createJob(CompactionKind.USER, all, createCFs("F1", "3M", "F2", "3M")));
    params = createPlanningParams(all, candidates, compacting, 2, CompactionKind.USER);
    plan = planner.makePlan(params);
    assertTrue(plan.getJobs().isEmpty());

    // 17 files that do not meet the compaction ratio, when max files to compact is 15 should do 3
    // files then 15
    all = createCFs("F1", "1M", "F2", "2M", "F3", "4M", "F4", "8M", "F5", "16M", "F6", "32M", "F7",
        "64M", "F8", "128M", "F9", "256M", "FA", "512M", "FB", "1G", "FC", "2G", "FD", "4G", "FE",
        "8G", "FF", "16G", "FG", "32G", "FH", "64G");
    compacting = Set.of();
    params = createPlanningParams(all, all, compacting, 2, CompactionKind.USER);
    plan = planner.makePlan(params);
    job = getOnlyElement(plan.getJobs());
    assertEquals(createCFs("F1", "1M", "F2", "2M", "F3", "4M"), job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "small"), job.getExecutor());

    // should compact all 15
    all = createCFs("FI", "7M", "F4", "8M", "F5", "16M", "F6", "32M", "F7", "64M", "F8", "128M",
        "F9", "256M", "FA", "512M", "FB", "1G", "FC", "2G", "FD", "4G", "FE", "8G", "FF", "16G",
        "FG", "32G", "FH", "64G");
    params = createPlanningParams(all, all, compacting, 2, CompactionKind.USER);
    plan = planner.makePlan(params);
    job = getOnlyElement(plan.getJobs());
    assertEquals(all, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "huge"), job.getExecutor());

    // For user compaction, can compact a subset that meets the compaction ratio if there is also a
    // larger set of files the meets the compaction ratio
    all = createCFs("F1", "3M", "F2", "4M", "F3", "5M", "F4", "6M", "F5", "50M", "F6", "51M", "F7",
        "52M");
    params = createPlanningParams(all, all, compacting, 2, CompactionKind.USER);
    plan = planner.makePlan(params);
    job = getOnlyElement(plan.getJobs());
    assertEquals(createCFs("F1", "3M", "F2", "4M", "F3", "5M", "F4", "6M"), job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "small"), job.getExecutor());

    // There is a subset of small files that meets the compaction ratio, but the larger set does not
    // so compact everything to avoid doing more than logarithmic work
    all = createCFs("F1", "3M", "F2", "4M", "F3", "5M", "F4", "6M", "F5", "50M");
    params = createPlanningParams(all, all, compacting, 2, CompactionKind.USER);
    plan = planner.makePlan(params);
    job = getOnlyElement(plan.getJobs());
    assertEquals(all, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "medium"), job.getExecutor());

  }

  @Test
  public void testMaxSize() throws Exception {
    String executors = "[{'name':'small','type': 'internal','maxSize':'32M','numThreads':1},"
        + "{'name':'medium','type': 'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type': 'internal','maxSize':'512M','numThreads':3}]";

    var planner = createPlanner(defaultConf, executors);
    var all = createCFs("F1", "128M", "F2", "129M", "F3", "130M", "F4", "131M", "F5", "132M");
    var params = createPlanningParams(all, all, Set.of(), 2, CompactionKind.SYSTEM);
    var plan = planner.makePlan(params);

    // should only compact files less than max size
    var job = getOnlyElement(plan.getJobs());
    assertEquals(createCFs("F1", "128M", "F2", "129M", "F3", "130M"), job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "large"), job.getExecutor());

    // user compaction can exceed the max size
    params = createPlanningParams(all, all, Set.of(), 2, CompactionKind.USER);
    plan = planner.makePlan(params);
    job = getOnlyElement(plan.getJobs());
    assertEquals(all, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.internalId(csid, "large"), job.getExecutor());
  }

  @Test
  public void testQueueCreation() throws Exception {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();

    String queues = "[{\"name\": \"small\", \"maxSize\":\"32M\"},{\"name\":\"midsize\"}]";
    planner.init(getInitParamQueues(defaultConf, queues));

    var all = createCFs("F1", "1M", "F2", "1M", "F3", "1M", "F4", "1M");
    var params = createPlanningParams(all, all, Set.of(), 2, CompactionKind.SYSTEM);
    var plan = planner.makePlan(params);

    var job = getOnlyElement(plan.getJobs());
    assertEquals(all, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.externalId("small"), job.getExecutor());

    all = createCFs("F1", "100M", "F2", "100M", "F3", "100M", "F4", "100M");
    params = createPlanningParams(all, all, Set.of(), 2, CompactionKind.SYSTEM);
    plan = planner.makePlan(params);

    job = getOnlyElement(plan.getJobs());
    assertEquals(all, job.getFiles());
    assertEquals(CompactionExecutorIdImpl.externalId("midsize"), job.getExecutor());
  }

  /**
   * Tests that additional fields in the JSON objects cause errors to be thrown.
   */
  @Test
  public void testErrorAdditionalConfigFields() {
    DefaultCompactionPlanner QueuePlanner = new DefaultCompactionPlanner();

    String queues =
        "[{\"name\":\"smallQueue\", \"maxSize\":\"32M\"}, {\"name\":\"largeQueue\", \"type\":\"internal\", \"foo\":\"bar\", \"queue\":\"broken\"}]";

    final InitParameters queueParams = getInitParamQueues(defaultConf, queues);
    assertNotNull(queueParams);
    var e = assertThrows(JsonParseException.class, () -> QueuePlanner.init(queueParams),
        "Failed to throw error");
    assertTrue(e.getMessage().contains("[type, foo, queue]"),
        "Error message didn't contain '[type, foo, queue]'");

    String executors = "[{'name':'small','type': 'internal','maxSize':'32M','numThreads':1},"
        + "{'name':'medium','type': 'internal','maxSize':'128M','numThreads':2, 'foo':'bar'},"
        + "{'name':'large','type': 'internal','numThreads':1, 'unexpectedField':'foo'}]";

    final InitParameters execParams = getInitParams(defaultConf, executors);
    assertNotNull(execParams);

    DefaultCompactionPlanner ExecPlanner = new DefaultCompactionPlanner();
    var err = assertThrows(JsonParseException.class, () -> ExecPlanner.init(execParams),
        "Failed to throw error");
    assertTrue(err.getMessage().contains("Invalid fields: [foo]"),
        "Error message didn't contain '[foo]'");
  }

  /**
   * Tests internal type executor with no numThreads set throws error
   */
  @Test
  public void testErrorInternalTypeNoNumThreads() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    String executors = "[{'name':'small','type':'internal','maxSize':'32M'},"
        + "{'name':'medium','type':'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type':'internal','maxSize':'512M','numThreads':3}]";

    var e = assertThrows(NullPointerException.class,
        () -> planner.init(getInitParams(defaultConf, executors)), "Failed to throw error");
    assertTrue(e.getMessage().contains("numThreads"), "Error message didn't contain numThreads");
  }

  /**
   * Test external type executor with numThreads set throws error.
   */
  @Test
  public void testErrorExternalTypeNumThreads() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    String executors = "[{'name':'small','type':'internal','maxSize':'32M', 'numThreads':1},"
        + "{'name':'medium','type':'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type':'external','maxSize':'512M','numThreads':3}]";

    var e = assertThrows(IllegalArgumentException.class,
        () -> planner.init(getInitParams(defaultConf, executors)), "Failed to throw error");
    assertTrue(e.getMessage().contains("numThreads"), "Error message didn't contain numThreads");
  }

  /**
   * Tests external type executor missing queue throws error
   */
  @Test
  public void testErrorExternalNoQueue() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    String executors = "[{'name':'small','type':'internal','maxSize':'32M', 'numThreads':1},"
        + "{'name':'medium','type':'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type':'external','maxSize':'512M'}]";

    var e = assertThrows(NullPointerException.class,
        () -> planner.init(getInitParams(defaultConf, executors)), "Failed to throw error");
    assertTrue(e.getMessage().contains("queue"), "Error message didn't contain queue");
  }

  /**
   * Tests queue with missing name throws error
   */
  @Test
  public void testErrorQueueNoName() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    String queues = "[{\"name\":\"smallQueue\", \"maxSize\":\"32M\"}, {\"maxSize\":\"120M\"}]";

    final InitParameters params = getInitParamQueues(defaultConf, queues);
    assertNotNull(params);

    var e = assertThrows(NullPointerException.class, () -> planner.init(params),
        "Failed to throw error");
    assertEquals(e.getMessage(), "'name' must be specified", "Error message didn't contain 'name'");
  }

  /**
   * Tests not having executors or queues throws errors
   */
  @Test
  public void testErrorNoExecutors() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    var execParams = getInitParams(defaultConf, "");
    assertNotNull(execParams);

    var e = assertThrows(IllegalStateException.class, () -> planner.init(execParams),
        "Failed to throw error");
    assertEquals("No defined executors or queues for this planner", e.getMessage(),
        "Error message was not equal");

    var params = getInitParamQueues(defaultConf, "");
    assertNotNull(params);

    var e2 = assertThrows(IllegalStateException.class, () -> planner.init(params),
        "Failed to throw error");
    assertEquals("No defined executors or queues for this planner", e2.getMessage(),
        "Error message was not equal");
  }

  /**
   * Tests executors can only have one without a max size.
   */
  @Test
  public void testErrorOnlyOneMaxSize() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    String executors = "[{'name':'small','type':'internal','maxSize':'32M', 'numThreads':1},"
        + "{'name':'medium','type':'internal','numThreads':2},"
        + "{'name':'large','type':'external','queue':'q1'}]";

    var e = assertThrows(IllegalArgumentException.class,
        () -> planner.init(getInitParams(defaultConf, executors)), "Failed to throw error");
    assertTrue(e.getMessage().contains("maxSize"), "Error message didn't contain maxSize");
  }

  /**
   * Tests executors can only have one without a max size.
   */
  @Test
  public void testErrorDuplicateMaxSize() {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    String executors = "[{'name':'small','type':'internal','maxSize':'32M', 'numThreads':1},"
        + "{'name':'medium','type':'internal','maxSize':'128M','numThreads':2},"
        + "{'name':'large','type':'external','maxSize':'128M','queue':'q1'}]";

    var e = assertThrows(IllegalArgumentException.class,
        () -> planner.init(getInitParams(defaultConf, executors)), "Failed to throw error");
    assertTrue(e.getMessage().contains("maxSize"), "Error message didn't contain maxSize");
  }

  private CompactionJob createJob(CompactionKind kind, Set<CompactableFile> all,
      Set<CompactableFile> files) {
    return new CompactionPlanImpl.BuilderImpl(kind, all, all)
        .addJob((short) all.size(), CompactionExecutorIdImpl.internalId(csid, "small"), files)
        .build().getJobs().iterator().next();
  }

  private static Set<CompactableFile> createCFs(String... namesSizePairs)
      throws URISyntaxException {
    Set<CompactableFile> files = new HashSet<>();

    for (int i = 0; i < namesSizePairs.length; i += 2) {
      String name = namesSizePairs[i];
      long size = ConfigurationTypeHelper.getFixedMemoryAsBytes(namesSizePairs[i + 1]);
      files.add(CompactableFile
          .create(new URI("hdfs://fake/accumulo/tables/1/t-0000000z/" + name + ".rf"), size, 0));
    }

    return files;
  }

  private static void testFFtC(Set<CompactableFile> expected, double ratio) {
    testFFtC(expected, expected, ratio, 100);
  }

  private static void testFFtC(Set<CompactableFile> expected, Set<CompactableFile> files,
      double ratio) {
    testFFtC(expected, files, ratio, 100);
  }

  private static void testFFtC(Set<CompactableFile> expected, Set<CompactableFile> files,
      double ratio, int maxFiles) {
    testFFtC(expected, files, ratio, maxFiles, Long.MAX_VALUE);
  }

  private static void testFFtC(Set<CompactableFile> expected, Set<CompactableFile> files,
      double ratio, int maxFiles, long maxSize) {
    var result = DefaultCompactionPlanner.findDataFilesToCompact(files, ratio, maxFiles, maxSize);
    var expectedNames = expected.stream().map(CompactableFile::getUri).map(URI::getPath)
        .map(path -> path.split("/")).map(t -> t[t.length - 1]).collect(Collectors.toSet());
    var resultNames = result.stream().map(CompactableFile::getUri).map(URI::getPath)
        .map(path -> path.split("/")).map(t -> t[t.length - 1]).collect(Collectors.toSet());
    assertEquals(expectedNames, resultNames);
  }

  private static CompactionPlanner.PlanningParameters createPlanningParams(Set<CompactableFile> all,
      Set<CompactableFile> candidates, Set<CompactionJob> compacting, double ratio,
      CompactionKind kind) {
    return new CompactionPlanner.PlanningParameters() {

      @Override
      public TableId getTableId() {
        return TableId.of("42");
      }

      @Override
      public ServiceEnvironment getServiceEnvironment() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Collection<CompactionJob> getRunningCompactions() {
        return compacting;
      }

      @Override
      public double getRatio() {
        return ratio;
      }

      @Override
      public CompactionKind getKind() {
        return kind;
      }

      @Override
      public Map<String,String> getExecutionHints() {
        return Map.of();
      }

      @Override
      public Collection<CompactableFile> getCandidates() {
        return candidates;
      }

      @Override
      public Collection<CompactableFile> getAll() {
        return all;
      }

      @Override
      public Builder createPlanBuilder() {
        return new CompactionPlanImpl.BuilderImpl(kind, all, candidates);
      }
    };
  }

  private static CompactionPlanner.InitParameters getInitParamQueues(Configuration conf,
      String queues) {

    String maxOpen = conf.get(prefix + "cs1.planner.opts.maxOpen");
    Map<String,String> options = new HashMap<>();
    options.put("queues", queues.replaceAll("'", "\""));

    if (maxOpen != null) {
      options.put("maxOpen", maxOpen);
    }

    ServiceEnvironment senv = EasyMock.createMock(ServiceEnvironment.class);
    EasyMock.expect(senv.getConfiguration()).andReturn(conf).anyTimes();
    EasyMock.replay(senv);

    return new CompactionPlannerInitParams(csid, prefix, options, senv);
  }

  private static CompactionPlanner.InitParameters getInitParams(Configuration conf,
      String executors) {

    String maxOpen = conf.get(prefix + "cs1.planner.opts.maxOpen");
    Map<String,String> options = new HashMap<>();
    options.put("executors", executors.replaceAll("'", "\""));

    if (maxOpen != null) {
      options.put("maxOpen", maxOpen);
    }

    ServiceEnvironment senv = EasyMock.createMock(ServiceEnvironment.class);
    EasyMock.expect(senv.getConfiguration()).andReturn(conf).anyTimes();
    EasyMock.replay(senv);

    return new CompactionPlannerInitParams(csid, prefix, options, senv);
  }

  private static DefaultCompactionPlanner createPlanner(Configuration conf, String executors) {
    DefaultCompactionPlanner planner = new DefaultCompactionPlanner();
    var initParams = getInitParams(conf, executors);

    planner.init(initParams);
    return planner;
  }
}
