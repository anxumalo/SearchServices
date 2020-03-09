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
package org.alfresco.solr.tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.alfresco.httpclient.AuthenticationException;
import org.alfresco.solr.InformationServer;
import org.alfresco.solr.client.NodeMetaData;
import org.alfresco.solr.client.SOLRAPIClient;
import org.alfresco.solr.client.Transaction;
import org.apache.commons.codec.EncoderException;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This tracks Cascading Updates
 * @author Joel Bernstein
 */
public class CascadeTracker extends AbstractTracker implements Tracker
{

    protected final static Logger log = LoggerFactory.getLogger(CascadeTracker.class);



    public CascadeTracker(Properties p, SOLRAPIClient client, String coreName,
                           InformationServer informationServer)
    {
        super(p, client, coreName, informationServer, Tracker.Type.CASCADE);

        threadHandler = new ThreadHandler(p, coreName, "CascadeTracker");
    }

    CascadeTracker()
    {
       super(Tracker.Type.CASCADE);
    }

    @Override
    protected void doTrack() throws AuthenticationException, IOException, JSONException, EncoderException
    {
        // MetadataTracker must wait until ModelTracker has run
        ModelTracker modelTracker = this.infoSrv.getAdminHandler().getTrackerRegistry().getModelTracker();
        if (modelTracker != null && modelTracker.hasModels())
        {
            trackRepository();
        }
    }

    public void maintenance() throws Exception {

    }

    public boolean hasMaintenance() {
        return false;
    }

    private void trackRepository() throws IOException, AuthenticationException, JSONException, EncoderException
    {
        checkShutdown();
        processCascades();
    }

    private void updateTransactionsAfterAsynchronous(List<Transaction> txsIndexed)
            throws IOException
    {
        waitForAsynchronous();
        for (Transaction tx : txsIndexed)
        {
            super.infoSrv.updateTransaction(tx);
        }
    }

    class CascadeIndexWorkerRunnable extends AbstractWorkerRunnable
    {
        InformationServer infoServer;
        List<NodeMetaData> nodes;

        CascadeIndexWorkerRunnable(QueueHandler queueHandler, List<NodeMetaData> nodes, InformationServer infoServer)
        {
            super(queueHandler);
            this.infoServer = infoServer;
            this.nodes = nodes;
        }

        @Override
        protected void doWork() throws IOException, AuthenticationException, JSONException
        {
            this.infoServer.cascadeNodes(nodes, true);
        }
        
        @Override
        protected void onFail(Throwable failCausedBy) 
        {
        	setRollback(true, failCausedBy);
        }
    }

    public void invalidateState() {
        super.invalidateState();
        infoSrv.setCleanCascadeTxnFloor(-1);
    }

    private void processCascades() throws IOException
    {
        int num = 50;
        List<Transaction> txBatch = null;
        do {
            try {
                getWriteLock().acquire();
                txBatch = infoSrv.getCascades(num);
                if(txBatch.size() == 0) {
                    //No transactions to process for cascades.
                    return;
                }

                ArrayList<Long> txIds = new ArrayList<Long>();
                Set<Long> txIdSet = new HashSet<Long>();
                for (Transaction tx : txBatch) {
                    txIds.add(tx.getId());
                    txIdSet.add(tx.getId());
                }

                List<NodeMetaData> nodeMetaDatas = infoSrv.getCascadeNodes(txIds);

                if(nodeMetaDatas.size() > 0) {
                    LinkedList<NodeMetaData> stack = new LinkedList<NodeMetaData>();
                    stack.addAll(nodeMetaDatas);
                    int batchSize = 10;

                    do {
                        List<NodeMetaData> batch = new ArrayList<NodeMetaData>();
                        while (batch.size() < batchSize && stack.size() > 0) {
                            batch.add(stack.removeFirst());
                        }

                        CascadeIndexWorkerRunnable worker = new CascadeIndexWorkerRunnable(this.threadHandler, batch, infoSrv);
                        this.threadHandler.scheduleTask(worker);
                    }
                    while (stack.size() > 0);
                }
                //Update the transaction records.
                updateTransactionsAfterAsynchronous(txBatch);
            }
            catch (AuthenticationException e)
            {
                throw new IOException(e);
            }
            catch (JSONException e)
            {
                throw new IOException(e);
            }
            catch(InterruptedException e)
            {
                throw new IOException(e);
            }
            finally
            {
                getWriteLock().release();
            }

        } while(txBatch.size() > 0);
    }
}
