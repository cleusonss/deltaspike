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
package org.apache.deltaspike.data.impl.handler;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.QueryHint;

import org.apache.deltaspike.data.api.EntityGraph;
import org.apache.deltaspike.data.api.SingleResultType;
import org.apache.deltaspike.data.api.mapping.QueryInOutMapper;
import org.apache.deltaspike.data.impl.graph.EntityGraphHelper;
import org.apache.deltaspike.data.impl.meta.RepositoryMethod;
import org.apache.deltaspike.data.impl.param.Parameters;
import org.apache.deltaspike.data.impl.property.Property;
import org.apache.deltaspike.data.impl.util.EntityUtils;
import org.apache.deltaspike.data.impl.util.bean.Destroyable;
import org.apache.deltaspike.data.spi.QueryInvocationContext;

public class CdiQueryInvocationContext implements QueryInvocationContext
{

    private final EntityManager entityManager;
    private final Parameters params;
    private final Class<?> entityClass;
    private final Object proxy;
    private final Method method;
    private final Object[] args;
    private final RepositoryMethod repoMethod;
    private final List<QueryStringPostProcessor> queryPostProcessors;
    private final List<JpaQueryPostProcessor> jpaPostProcessors;
    private final List<Destroyable> cleanup;

    private String queryString;

    public CdiQueryInvocationContext(Object proxy, Method method, Object[] args, RepositoryMethod repoMethod,
                                     EntityManager entityManager)
    {
        this.entityManager = entityManager;
        this.args = args == null ? new Object[]{} : args;
        this.params = Parameters.create(method, this.args);
        this.proxy = proxy;
        this.method = method;
        this.repoMethod = repoMethod;
        this.entityClass = repoMethod.getRepository().getEntityClass();
        this.queryPostProcessors = new LinkedList<QueryStringPostProcessor>();
        this.jpaPostProcessors = new LinkedList<JpaQueryPostProcessor>();
        this.cleanup = new LinkedList<Destroyable>();
    }

    public void initMapper()
    {
        if (hasQueryInOutMapper())
        {
            QueryInOutMapper<?> mapper = getQueryInOutMapper();
            params.applyMapper(mapper);
            for (int i = 0; i < args.length; i++)
            {
                if (mapper.mapsParameter(args[i]))
                {
                    args[i] = mapper.mapParameter(args[i]);
                }
            }
        }
    }

    @Override
    public EntityManager getEntityManager()
    {
        return entityManager;
    }

    @Override
    public boolean isNew(Object entity)
    {
        try
        {
            Property<Serializable> versionProperty = EntityUtils.getVersionProperty(entity);
            if (versionProperty != null)
            {
                return versionProperty.getValue(entity) == null;
            }

            if (EntityUtils.primaryKeyValue(entity) == null)
            {
                return true;
            }

            if (!entityManager.contains(entity) && countCheck(entity))
            {
                return true;
            }
            return false;
        }
        catch (IllegalArgumentException e)
        {
            // Not an entity
            return false;
        }
    }

    @Override
    public Class<?> getEntityClass()
    {
        return entityClass;
    }

    @Override
    public Class<?> getRepositoryClass()
    {
        return repoMethod.getRepository().getRepositoryClass();
    }

    public Object proceed() throws Exception
    {
        return method.invoke(proxy, args);
    }

    @Override
    public Method getMethod()
    {
        return method;
    }

    public Query applyRestrictions(Query query)
    {
        Parameters params = getParams();
        Method method = getMethod();
        if (params.hasSizeRestriction())
        {
            query.setMaxResults(params.getSizeRestriciton());
        }
        if (params.hasFirstResult())
        {
            query.setFirstResult(params.getFirstResult());
        }
        if (hasLockMode(method))
        {
            query.setLockMode(extractLockMode(method));
        }
        if (hasQueryHints(method))
        {
            QueryHint[] hints = extractQueryHints(method);
            for (QueryHint hint : hints)
            {
                query.setHint(hint.name(), hint.value());
            }
        }
        applyEntityGraph(query, method);
        query = applyJpaQueryPostProcessors(query);
        return query;
    }

    public Object[] getMethodParameters()
    {
        return args;
    }

    public void addQueryStringPostProcessor(QueryStringPostProcessor postProcessor)
    {
        queryPostProcessors.add(postProcessor);
    }

    public void addJpaQueryPostProcessor(JpaQueryPostProcessor postProcessor)
    {
        jpaPostProcessors.add(postProcessor);
    }

    public void removeJpaQueryPostProcessor(JpaQueryPostProcessor postProcessor)
    {
        jpaPostProcessors.remove(postProcessor);
    }

    public boolean hasQueryStringPostProcessors()
    {
        return !queryPostProcessors.isEmpty();
    }

    public String applyQueryStringPostProcessors(String queryString)
    {
        String result = queryString;
        for (QueryStringPostProcessor processor : queryPostProcessors)
        {
            result = processor.postProcess(result);
        }
        return result;
    }

    public Query applyJpaQueryPostProcessors(Query query)
    {
        Query result = query;
        for (JpaQueryPostProcessor processor : jpaPostProcessors)
        {
            result = processor.postProcess(this, result);
        }
        return result;
    }

    public void addDestroyable(Destroyable destroyable)
    {
        cleanup.add(destroyable);
    }

    public void cleanup()
    {
        for (Destroyable destroy : cleanup)
        {
            destroy.destroy();
        }
        cleanup.clear();
    }

    public Object executeQuery(Query jpaQuery)
    {
        return repoMethod.getQueryProcessor().executeQuery(jpaQuery, this);
    }

    public Parameters getParams()
    {
        return params;
    }

    public RepositoryMethod getRepositoryMethod()
    {
        return repoMethod;
    }

    public String getQueryString()
    {
        return queryString;
    }

    public void setQueryString(String queryString)
    {
        this.queryString = queryString;
    }

    public List<QueryStringPostProcessor> getQueryStringPostProcessors()
    {
        return queryPostProcessors;
    }

    public boolean hasQueryInOutMapper()
    {
        return repoMethod.hasQueryInOutMapper();
    }

    public QueryInOutMapper<?> getQueryInOutMapper()
    {
        return repoMethod.getQueryInOutMapperInstance(this);
    }

    public SingleResultType getSingleResultStyle()
    {
        return repoMethod.getSingleResultStyle();
    }

    public Object getProxy()
    {
        return proxy;
    }

    private boolean hasLockMode(Method method)
    {
        return extractLockMode(method) != null;
    }

    private LockModeType extractLockMode(Method method)
    {
        Class<org.apache.deltaspike.data.api.Query> query = org.apache.deltaspike.data.api.Query.class;
        if (method.isAnnotationPresent(query) &&
                method.getAnnotation(query).lock() != LockModeType.NONE)
        {
            return method.getAnnotation(query).lock();
        }
        return null;
    }

    private QueryHint[] extractQueryHints(Method method)
    {
        Class<org.apache.deltaspike.data.api.Query> query = org.apache.deltaspike.data.api.Query.class;
        if (method.isAnnotationPresent(query) &&
                method.getAnnotation(query).hints().length > 0)
        {
            return method.getAnnotation(query).hints();
        }
        return null;
    }

    private boolean hasQueryHints(Method method)
    {
        return extractQueryHints(method) != null;
    }
    
    private void applyEntityGraph(Query query, Method method)
    {
        EntityGraph entityGraphAnn = method.getAnnotation(EntityGraph.class);
        if (entityGraphAnn == null)
        {
            return;
        }
        
        Object graph = EntityGraphHelper.getEntityGraph(getEntityManager(), entityClass, entityGraphAnn);
        query.setHint(entityGraphAnn.type().getHintName(), graph);
    }

    private boolean countCheck(Object entity)
    {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(e) FROM " + getEntityClass()
                .getSimpleName() + " e ");
        jpql.append("WHERE e.");
        jpql.append(EntityUtils.primaryKey(getEntityClass()).getName());
        jpql.append(" = :id");

        final Query query = entityManager.createQuery(jpql.toString());
        query.setParameter("id", EntityUtils.primaryKeyValue(entity));
        final Long result = (Long) query.getSingleResult();
        if (Long.valueOf(0).equals(result))
        {
            return true;
        }
        return false;
    }
}
