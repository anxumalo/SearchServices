/*
 * Copyright (C) 2005-2016 Alfresco Software Limited.
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
package org.alfresco.solr.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests configuring and setup of Alfresco and Solr properties
 *
 * @author Gethin James
 */

public class ConfigUtilTest {

    @Test
    public void locateProperty() throws Exception {
        assertEquals("king", ConfigUtil.locateProperty("find.me", "king"));
        System.setProperty("solr.find.me", "iamfound");
        assertEquals("iamfound", ConfigUtil.locateProperty("find.me", "king"));
        System.clearProperty("solr.find.me");
        assertEquals("king", ConfigUtil.locateProperty("find.me", "king"));
        System.setProperty("find.me", "iamfoundagain");
        assertEquals("iamfoundagain", ConfigUtil.locateProperty("find.me", "king"));
        System.clearProperty("find.me");

        //Assumes there is always a PATH environment variable
        assertNotEquals("king", ConfigUtil.locateProperty("PATH", "king"));
    }

    @Test
    public void convertPropertyNameToJNDIPath() throws Exception {
        assertEquals("java:comp/env/gethin",ConfigUtil.convertPropertyNameToJNDIPath("gethin"));
        assertEquals("java:comp/env/solr/content/dir",ConfigUtil.convertPropertyNameToJNDIPath("solr.content.dir"));
        assertEquals("java:comp/env/solr/model/dir",ConfigUtil.convertPropertyNameToJNDIPath("solr.model.dir"));
        assertEquals("java:comp/env/",ConfigUtil.convertPropertyNameToJNDIPath(""));
        assertEquals("java:comp/env/",ConfigUtil.convertPropertyNameToJNDIPath(null));
    }

    @Test
    public void convertPropertyNameToEnvironmentParam() throws Exception {
        assertEquals("GETHIN",ConfigUtil.convertPropertyNameToEnvironmentParam("gethin"));
        assertEquals("SOLR_CONTENT_DIR",ConfigUtil.convertPropertyNameToEnvironmentParam("solr.content.dir"));
        assertEquals("SOLR_MODEL_DIR",ConfigUtil.convertPropertyNameToEnvironmentParam("solr.model.dir"));
        assertEquals("SOLR_HOST",ConfigUtil.convertPropertyNameToEnvironmentParam("solr.host"));
    }

}