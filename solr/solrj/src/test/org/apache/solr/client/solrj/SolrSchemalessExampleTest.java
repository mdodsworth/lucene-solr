/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.client.solrj;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.util.ExternalPaths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.Properties;

public class SolrSchemalessExampleTest extends SolrExampleTestsBase {
  private static Logger log = LoggerFactory.getLogger(SolrSchemalessExampleTest.class);

  @BeforeClass
  public static void beforeClass() throws Exception {
    File tempSolrHome = createTempDir().toFile();
    // Schemaless renames schema.xml -> schema.xml.bak, and creates + modifies conf/managed-schema,
    // which violates the test security manager's rules, which disallow writes outside the build dir,
    // so we copy the example/example-schemaless/solr/ directory to a new temp dir where writes are allowed.
    FileUtils.copyFileToDirectory(new File(ExternalPaths.SERVER_HOME, "solr.xml"), tempSolrHome);
    File collection1Dir = new File(tempSolrHome, "collection1");
    FileUtils.forceMkdir(collection1Dir);
    FileUtils.copyDirectoryToDirectory(new File(ExternalPaths.SCHEMALESS_CONFIGSET), collection1Dir);
    Properties props = new Properties();
    props.setProperty("name","collection1");
    OutputStreamWriter writer = null;
    try {
      writer = new OutputStreamWriter(FileUtils.openOutputStream(new File(collection1Dir, "core.properties")), "UTF-8");
      props.store(writer, null);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (Exception ignore){}
      }
    }
    createJetty(tempSolrHome.getAbsolutePath(), null, null);
  }
  @Test
  public void testArbitraryJsonIndexing() throws Exception  {
    HttpSolrServer server = (HttpSolrServer) getSolrServer();
    server.deleteByQuery("*:*");
    server.commit();
    assertNumFound("*:*", 0); // make sure it got in

    // two docs, one with uniqueKey, another without it
    String json = "{\"id\":\"abc1\", \"name\": \"name1\"} {\"name\" : \"name2\"}";
    HttpClient httpClient = server.getHttpClient();
    HttpPost post = new HttpPost(server.getBaseURL() + "/update/json/docs");
    post.setHeader("Content-Type", "application/json");
    post.setEntity(new InputStreamEntity(new ByteArrayInputStream(json.getBytes("UTF-8")), -1));
    HttpResponse response = httpClient.execute(post);
    assertEquals(200, response.getStatusLine().getStatusCode());
    server.commit();
    assertNumFound("*:*", 2);
  }


  @Override
  public SolrServer createNewSolrServer() {
    try {
      // setup the server...
      String url = jetty.getBaseUrl().toString() + "/collection1";
      HttpSolrServer s = new HttpSolrServer(url);
      s.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
      s.setDefaultMaxConnectionsPerHost(100);
      s.setMaxTotalConnections(100);
      s.setUseMultiPartPost(random().nextBoolean());
      
      if (random().nextBoolean()) {
        s.setParser(new BinaryResponseParser());
        s.setRequestWriter(new BinaryRequestWriter());
      }
      
      return s;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
