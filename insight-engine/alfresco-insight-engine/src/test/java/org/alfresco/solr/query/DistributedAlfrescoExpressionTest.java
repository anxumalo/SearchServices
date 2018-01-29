/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.solr.query;

import static org.alfresco.solr.AlfrescoSolrUtils.getAcl;
import static org.alfresco.solr.AlfrescoSolrUtils.getAclChangeSet;
import static org.alfresco.solr.AlfrescoSolrUtils.getAclReaders;
import static org.alfresco.solr.AlfrescoSolrUtils.getNode;
import static org.alfresco.solr.AlfrescoSolrUtils.getNodeMetaData;
import static org.alfresco.solr.AlfrescoSolrUtils.getTransaction;
import static org.alfresco.solr.AlfrescoSolrUtils.indexAclChangeSet;
import static org.alfresco.solr.AlfrescoSolrUtils.list;

import java.lang.invoke.MethodHandles;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.adaptor.lucene.QueryConstants;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.solr.AbstractAlfrescoDistributedTest;
import org.alfresco.solr.SolrInformationServer;
import org.alfresco.solr.client.Acl;
import org.alfresco.solr.client.AclChangeSet;
import org.alfresco.solr.client.AclReaders;
import org.alfresco.solr.client.Node;
import org.alfresco.solr.client.NodeMetaData;
import org.alfresco.solr.client.StringPropertyValue;
import org.alfresco.solr.client.Transaction;
import org.alfresco.solr.stream.AlfrescoSolrStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.LegacyNumericRangeQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.common.params.SolrParams;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joel
 */
@SolrTestCaseJ4.SuppressSSL
@LuceneTestCase.SuppressCodecs({"Appending","Lucene3x","Lucene40","Lucene41","Lucene42","Lucene43", "Lucene44", "Lucene45","Lucene46","Lucene47","Lucene48","Lucene49"})
public class DistributedAlfrescoExpressionTest extends AbstractAlfrescoDistributedTest
{
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Rule
    public JettyServerRule jetty = new JettyServerRule(2, this);

    @Test
    public void testExpression() throws Exception
    {
        putHandleDefaults();

        /*
        * Create and index an AclChangeSet.
        */

        AclChangeSet aclChangeSet = getAclChangeSet(1);

        Acl acl = getAcl(aclChangeSet);
        Acl acl2 = getAcl(aclChangeSet);

        AclReaders aclReaders = getAclReaders(aclChangeSet, acl, list("joel"), list("phil"), null);
        AclReaders aclReaders2 = getAclReaders(aclChangeSet, acl2, list("jim"), list("phil"), null);

        indexAclChangeSet(aclChangeSet,
                list(acl, acl2),
                list(aclReaders, aclReaders2));


        //Check for the ACL state stamp.
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(new TermQuery(new Term(QueryConstants.FIELD_SOLR4_ID, "TRACKER!STATE!ACLTX")), BooleanClause.Occur.MUST));
        builder.add(new BooleanClause(LegacyNumericRangeQuery.newLongRange(QueryConstants.FIELD_S_ACLTXID, aclChangeSet.getId(), aclChangeSet.getId() + 1, true, false), BooleanClause.Occur.MUST));
        BooleanQuery waitForQuery = builder.build();
        waitForDocCountAllCores(waitForQuery, 1, 80000);

        //Check that both ACL's are in the index
        BooleanQuery.Builder builder1 = new BooleanQuery.Builder();
        builder1.add(new BooleanClause(new TermQuery(new Term(QueryConstants.FIELD_DOC_TYPE, SolrInformationServer.DOC_TYPE_ACL)), BooleanClause.Occur.MUST));
        BooleanQuery waitForQuery1 = builder1.build();
        waitForDocCountAllCores(waitForQuery1, 2, 80000);

        /*
        * Create and index a Transaction
        */

        //First create a transaction.
        Transaction txn = getTransaction(0, 4);

        Node node1 = getNode(txn, acl, Node.SolrApiNodeStatus.UPDATED);
        Node node2 = getNode(txn, acl, Node.SolrApiNodeStatus.UPDATED);
        Node node3 = getNode(txn, acl2, Node.SolrApiNodeStatus.UPDATED);
        Node node4 = getNode(txn, acl2, Node.SolrApiNodeStatus.UPDATED);

        //Next create the NodeMetaData for each node. TODO: Add more metadata

        NodeMetaData nodeMetaData1 = getNodeMetaData(node1, txn, acl, "mike", null, false);
        Date date1 = getDate(2000, 0, 2);
        nodeMetaData1.getProperties().put(ContentModel.PROP_CREATED,
                new StringPropertyValue(DefaultTypeConverter.INSTANCE.convert(String.class, date1)));

        NodeMetaData nodeMetaData2 = getNodeMetaData(node2, txn, acl, "mike", null, false);
        Date date2 = getDate(2000, 1, 2);
        nodeMetaData2.getProperties().put(ContentModel.PROP_CREATED,
                new StringPropertyValue(DefaultTypeConverter.INSTANCE.convert(String.class, date2)));

        NodeMetaData nodeMetaData3 = getNodeMetaData(node3, txn, acl2, "mike", null, false);
        Date date3 = getDate(2000, 2, 2);
        nodeMetaData3.getProperties().put(ContentModel.PROP_CREATED,
                new StringPropertyValue(DefaultTypeConverter.INSTANCE.convert(String.class, date3)));

        NodeMetaData nodeMetaData4 = getNodeMetaData(node4, txn, acl2, "mike", null, false);
        Date date4 = getDate(2000, 3, 2);
        nodeMetaData4.getProperties().put(ContentModel.PROP_CREATED,
                new StringPropertyValue(DefaultTypeConverter.INSTANCE.convert(String.class, date4)));


        //Index the transaction, nodes, and nodeMetaDatas.
        //Note that the content is automatically created by the test framework.
        indexTransaction(txn,
                list(node1, node2, node3, node4),
                list(nodeMetaData1, nodeMetaData2, nodeMetaData3, nodeMetaData4));

        //Check for the TXN state stamp.
        builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(new TermQuery(new Term(QueryConstants.FIELD_SOLR4_ID, "TRACKER!STATE!TX")), BooleanClause.Occur.MUST));
        builder.add(new BooleanClause(LegacyNumericRangeQuery.newLongRange(QueryConstants.FIELD_S_TXID, txn.getId(), txn.getId() + 1, true, false), BooleanClause.Occur.MUST));
        waitForQuery = builder.build();

        waitForDocCountAllCores(waitForQuery, 1, 80000);

        /*
        * Query the index for the content
        */

        waitForDocCountAllCores(new TermQuery(new Term(QueryConstants.FIELD_READER, "jim")), 1, 80000);
        waitForDocCount(new TermQuery(new Term("content@s___t@{http://www.alfresco.org/model/content/1.0}content", "world")), 4, 80000);

        List<SolrClient> clusterClients = getClusterClients();

        String alfrescoJson = "{ \"authorities\": [ \"jim\", \"joel\" ], \"tenants\": [ \"\" ] }";

        String expr = "alfrescoExpr(let(a=timeSeries(myCollection, " +
                                                    "q=\"*.*\", " +
                                                    "field=\"cm:created\", " +
                                                    "start=\"2000-01-01T01:00:00.000Z\", " +
                                                    "end=\"2000-05-31T01:00:00.000Z\", " +
                                                    "gap=\"+1MONTH\", " +
                                                    "format=\"yyyy-MM\", " +
                                                    "count(*))," +
                                        "get(a)))";

        String shards = getShardsString(clusterClients);


        SolrParams params = params("expr", expr, "qt", "/stream", "myCollection.shards", shards);

        AlfrescoSolrStream tupleStream = new AlfrescoSolrStream(((HttpSolrClient)clusterClients.get(0)).getBaseURL(),
                params);
        tupleStream.setJson(alfrescoJson);
        List<Tuple> tuples = getTuples(tupleStream);
        assertTrue(tuples.size() == 5);
        assertBuckets("cm:created", tuples, "2000-01", "2000-02", "2000-03", "2000-04", "2000-05");
        assertCounts("count(*)", tuples, 1, 1, 1, 1, 0);

        //Test that the access control is being applied.
        String alfrescoJson2 = "{ \"authorities\": [ \"joel\" ], \"tenants\": [ \"\" ] }";
        tupleStream = new AlfrescoSolrStream(((HttpSolrClient)clusterClients.get(0)).getBaseURL(), params);
        tupleStream.setJson(alfrescoJson2);
        tuples = getTuples(tupleStream);
        assertTrue(tuples.size() == 5);
        assertBuckets("cm:created", tuples, "2000-01", "2000-02", "2000-03", "2000-04", "2000-05");
        assertCounts("count(*)", tuples, 1, 1, 0, 0, 0);

        expr = "alfrescoExpr(limit(facet("
                + "myCollection, "
                + "q=\"*.*\", "
                + "buckets=\"cm:created\", "
                + "bucketSorts=\"cm:created desc\", "
                + "bucketSizeLimit=100, "
                +"count(*)"
                + "),2))";

        params = params("expr", expr, "qt", "/stream", "myCollection.shards", shards);

        tupleStream = new AlfrescoSolrStream(((HttpSolrClient) clusterClients.get(0)).getBaseURL(), params);
        tupleStream.setJson(alfrescoJson);
        tuples = getTuples(tupleStream);
        assertTrue(tuples.size() == 2);

        expr = "alfrescoExpr(search(myCollection, q=\"cm:content:world\", sort=\"cm:created desc\"))";
        params = params("expr", expr, "qt", "/stream", "myCollection.shards", shards);

        tupleStream = new AlfrescoSolrStream(((HttpSolrClient) clusterClients.get(0)).getBaseURL(), params);
        tupleStream.setJson(alfrescoJson);
        tuples = getTuples(tupleStream);
        assertTrue(tuples.size() == 4);

        expr = "alfrescoExpr(having(facet("
                + "myCollection, "
                + "q=\"cm:content:world\", "
                + "buckets=\"cm:created\", "
                + "bucketSorts=\"cm:created desc\", "
                + "bucketSizeLimit=100, "
                +"count(*)"
                + "),eq(count(*), 1)))";
        params = params("expr", expr, "qt", "/stream", "myCollection.shards", shards);

        tupleStream = new AlfrescoSolrStream(((HttpSolrClient) clusterClients.get(0)).getBaseURL(), params);
        tupleStream.setJson(alfrescoJson);
        tuples = getTuples(tupleStream);
        assertTrue(tuples.size() == 4);

    }

    private void assertBuckets(String field, List<Tuple> tuples, String ... buckets) throws Exception {
        int i=0;
        for(String bucket : buckets) {
            Tuple tuple = tuples.get(i);
            if(!tuple.getString(field).equals(bucket)) {
                throw new Exception("Bad bucket found: "+tuple.getString(field)+" expected: "+bucket);
            }
            ++i;
        }
    }

    private void assertCounts(String field, List<Tuple> tuples, long ... counts) throws Exception {
        int i=0;
        for(long count : counts) {
            Tuple tuple = tuples.get(i);
            if(!tuple.getLong(field).equals(count)) {
                logger.error("Invalid tuple "+tuple.getMap());
                logger.error("Locale is "+Locale.getDefault());
                logger.error("TimeZone is "+TimeZone.getDefault());
                throw new Exception("Bad count found: "+tuple.getLong(field)+" expected: "+count);
            }
            ++i;
        }
    }

    private Date getDate(int year, int month, int day)
    {
        return new Date(new GregorianCalendar(year, month, day, 10, 0).getTimeInMillis());
    }

}

