/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.deltaspike.test.core.api.exclude;


import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.apache.deltaspike.core.impl.projectstage.ProjectStageProducer;
import org.apache.deltaspike.test.core.api.temptestutil.ShrinkWrapArchiveUtil;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link org.apache.deltaspike.core.api.exclude.Exclude}
 */
@RunWith(Arquillian.class)
public class ExcludeTest
{
    /**
     * X TODO creating a WebArchive is only a workaround because JavaArchive cannot contain other archives.
     */
    @Deployment
    public static WebArchive deploy()
    {
        System.setProperty("org.apache.deltaspike.ProjectStage", "Production");
        ProjectStageProducer.setProjectStage(null);

        return ShrinkWrap.create(WebArchive.class)
                .addAsLibraries(ShrinkWrapArchiveUtil.getArchives(null,
                        "META-INF/beans.xml",
                        new String[]{"org.apache.deltaspike.core",
                                "org.apache.deltaspike.test.core.api.exclude"},
                        null))
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    /**
     * check if this package is included at all
     */
    @Test
    public void simpleCheckOfBeansInPackage()
    {
        Bean testBean = BeanProvider.getContextualReference(Bean.class, true);

        Assert.assertNotNull(testBean);
    }

    /**
     * bean is excluded in any case
     */
    @Test
    public void excludeWithoutCondition()
    {
        NoBean noBean = BeanProvider.getContextualReference(NoBean.class, true);

        Assert.assertNull(noBean);
    }

    /**
     * bean included in case of project-stage development
     */
    @Test
    public void includeInCaseOfProjectStageProduction()
    {
        StdBean stdBean = BeanProvider.getContextualReference(StdBean.class, true);

        Assert.assertNotNull(stdBean);
    }

    /**
     * bean excluded in case of project-stage development
     */
    @Test
    public void excludedInCaseOfProjectStageProduction()
    {
        DevBean devBean = BeanProvider.getContextualReference(DevBean.class, true);

        Assert.assertNull(devBean);
    }

    /**
     * beans de-/activated via expressions
     */
    @Test
    public void excludedIfExpressionMatch()
    {
        ProdDbBean prodDbBean = BeanProvider.getContextualReference(ProdDbBean.class, true);

        Assert.assertNotNull(prodDbBean);

        DevDbBean devDbBean = BeanProvider.getContextualReference(DevDbBean.class, true);

        Assert.assertNull(devDbBean);
    }
}