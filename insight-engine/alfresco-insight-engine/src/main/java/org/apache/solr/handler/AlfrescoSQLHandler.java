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
package org.apache.solr.handler;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.solr.query.AbstractQParser;
import org.alfresco.solr.sql.AlfrescoCalciteSolrDriver;
import org.alfresco.solr.sql.SqlUtil;
import org.alfresco.solr.stream.AlfrescoExceptionStream;
import org.apache.calcite.config.Lex;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.comp.StreamComparator;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

public class AlfrescoSQLHandler extends RequestHandlerBase implements SolrCoreAware, PermissionNameProvider {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static String defaultZkhost = null;
  private static String defaultWorkerCollection = null;
  public static final String IS_SELECT_STAR = "SELECT_STAR";

  private boolean isCloud = false;
  private String localCore;

  public void inform(SolrCore core) {
    this.localCore = core.getName();
    AlfrescoCalciteSolrDriver.registerCore(core);
    CoreContainer coreContainer = core.getCoreContainer();

    if(coreContainer.isZooKeeperAware()) {
      defaultZkhost = core.getCoreContainer().getZkController().getZkServerAddress();
      defaultWorkerCollection = core.getCoreDescriptor().getCollectionName();
      isCloud = true;
    }
  }

  @Override
  public PermissionNameProvider.Name getPermissionName(AuthorizationContext request) {
    return PermissionNameProvider.Name.READ_PERM;
  }

  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    ModifiableSolrParams params = new ModifiableSolrParams(req.getParams());
    params = adjustParams(params);
    req.setParams(params);

    String sql = params.get("stmt");

    // Set defaults for parameters
    params.set("numWorkers", params.getInt("numWorkers", 1));
    params.set("workerCollection", params.get("workerCollection", defaultWorkerCollection));
    params.set("workerZkhost", params.get("workerZkhost", defaultZkhost));
    params.set("aggregationMode", params.get("aggregationMode", "facet"));

    TupleStream tupleStream = null;
    try {

      /*
      if(!isCloud) {
        throw new IllegalStateException(sqlNonCloudErrorMsg);
      }
      */

      if(sql == null) {
        throw new Exception("stmt parameter cannot be null");
      }

      String url = AlfrescoCalciteSolrDriver.CONNECT_STRING_PREFIX;

      Properties properties = new Properties();
      // Add all query parameters
      Iterator<String> parameterNamesIterator = params.getParameterNamesIterator();
      while(parameterNamesIterator.hasNext()) {
        String param = parameterNamesIterator.next();
        properties.setProperty(param, params.get(param));
      }

      // Set these last to ensure that they are set properly
      properties.setProperty("lex", Lex.MYSQL.toString());
      if(defaultZkhost != null) {
        properties.setProperty("zk", defaultZkhost);
      }

      String driverClass = AlfrescoCalciteSolrDriver.class.getCanonicalName();

      // JDBC driver requires metadata from the SQLHandler. Default to false since this adds a new Metadata stream.
      boolean includeMetadata = params.getBool("includeMetadata", false);
      properties.put("localCore", localCore);

      String json = getAlfrescoJson(req);
      if(json != null) {
        properties.put(AbstractQParser.ALFRESCO_JSON, json);
      }

      properties.setProperty(IS_SELECT_STAR, Boolean.toString(SqlUtil.isSelectStar(sql)));

      sql = adjustSQL(sql);


      /*
      *  The SqlHandlerStream sets up the Apache Calcite JDBC Connection.
      *  This will trigger the code in AlfrescoCalciteSolrDriver which sets up the
      *  Schema. The SqlHandlerStream inherits from JDBCStreams that use the Calcite JDBC driver
      *  to make the SQL request.The Calcite JDBC Driver then kicks of the Apache Calcite
      *  query processing.
      */

      tupleStream = new SqlHandlerStream(url, sql, null, properties, driverClass, includeMetadata);

      tupleStream = new StreamHandler.TimerStream(new AlfrescoExceptionStream(tupleStream));

      rsp.add("result-set", tupleStream);
    } catch(Exception e) {
      //Catch the SQL parsing and query transformation exceptions.
      if(tupleStream != null) {
        tupleStream.close();
      }
      SolrException.log(logger, e);
      rsp.add("result-set", new StreamHandler.DummyErrorStream(e));
    }
  }

  private String adjustSQL(String sql) {
      return sql.replace("!=", "<>");
  }

  public String getDescription() {
    return "SQLHandler";
  }

  public String getSource() {
    return null;
  }

  /*
   * Only necessary for SolrJ JDBC driver since metadata has to be passed backF
   */
  private static class SqlHandlerStream extends CalciteJDBCStream {
    private final boolean includeMetadata;
    private boolean firstTuple = true;
    private Tuple firstTupleRead = null;
    List<String> metadataFields = new ArrayList<>();
    Map<String, String> metadataAliases = new HashMap<>();
    private boolean isSelectStar;

    SqlHandlerStream(String connectionUrl, String sqlQuery, StreamComparator definedSort,
                     Properties connectionProperties, String driverClassName, boolean includeMetadata)
        throws IOException {
      super(connectionUrl, sqlQuery, definedSort, connectionProperties, driverClassName);

      this.includeMetadata = includeMetadata;
      this.isSelectStar = Boolean.parseBoolean(connectionProperties.getProperty(AlfrescoSQLHandler.IS_SELECT_STAR));
    }

    @Override
    public Tuple read() throws IOException {
      // Return a metadata tuple as the first tuple and then pass through to the JDBCStream.
      if(firstTuple) {
        try {
            firstTupleRead = super.read();
            Map<String, Object> fields = new HashMap<>();

            firstTuple = false;

            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();

            for(int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                String columnName = resultSetMetaData.getColumnName(i);

                if(isSelectStar) {

                    if (columnName.contains(":") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("_query_")) {
                        continue;
                    }

                    if (columnName.equals("score") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.endsWith("_day") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.endsWith("_month") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.endsWith("_year") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("READER") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("DENIED") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("TXCOMMITTIME") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("S_ACLTXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("S_TXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("S_INTXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("ACLTXCOMMITTIME") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("ACLTXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("TXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("S_INACLTXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("S_ACLTXCOMMITTIME") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("S_TXCOMMITTIME") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("INACLTXID") && firstTupleRead.get(columnName) == null) {
                        continue;
                    }

                    if (columnName.equals("FIELDS")) {
                        continue;
                    }

                    if (columnName.equals("PROPERTIES")) {
                        continue;
                    }
                }


                String columnLabel = resultSetMetaData.getColumnLabel(i);
                metadataFields.add(columnName);
                metadataAliases.put(columnName, columnLabel);
            }

            if(includeMetadata) {
                fields.put("isMetadata", true);
                fields.put("fields", metadataFields);
                fields.put("aliases", metadataAliases);
                return new Tuple(fields);
            }
        } catch (SQLException e) {
          throw new IOException(e);
        }
      }

      if(firstTupleRead != null) {
          if (!firstTupleRead.EOF) {
              firstTupleRead.fieldNames = metadataFields;
              firstTupleRead.fieldLabels = metadataAliases;
          }

          Tuple ttup = firstTupleRead;
          firstTupleRead = null;
          return ttup;
      } else {
          Tuple tuple = super.read();
          if (!tuple.EOF) {
              tuple.fieldNames = metadataFields;
              tuple.fieldLabels = metadataAliases;
          }
          return tuple;
      }
    }
  }

  /**
   * Get the Alfresco Json from the request
   * @param req SolrQueryRequest
   * @return Alfresco json
   */
  public String getAlfrescoJson(SolrQueryRequest req)
  {
    Iterable<ContentStream> streams = req.getContentStreams();
    if (streams != null)
    {
      try
      {
        Reader reader = null;
        for (ContentStream stream : streams)
        {
          reader = new BufferedReader(new InputStreamReader(stream.getStream(), "UTF-8"));
        }

        if (reader != null)
        {
          JSONObject json = new JSONObject(new JSONTokener(reader));
          return json.toString();
        }
      }
      catch (JSONException e)
      {
        // This is expected when there is no json element to the request
      }
      catch (IOException e)
      {
        throw new AlfrescoRuntimeException("IO Error parsing query parameters", e);
      }
    }

    return null;
  }


  private ModifiableSolrParams adjustParams(SolrParams params) {
    ModifiableSolrParams adjustedParams = new ModifiableSolrParams();
    adjustedParams.add(params);
    adjustedParams.add(CommonParams.OMIT_HEADER, "true");
    return adjustedParams;
  }
}
