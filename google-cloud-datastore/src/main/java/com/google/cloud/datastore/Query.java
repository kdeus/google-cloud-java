/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.datastore;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.util.Map;

/**
 * A Google Cloud Datastore query.
 * For usage examples see {@link GqlQuery} and {@link StructuredQuery}.
 *
 * <p>Note that queries require proper indexing. See
 * <a href="https://cloud.google.com/datastore/docs/tools/indexconfig">
 * Cloud Datastore Index Configuration</a> for help configuring indexes.
 *
 * @param <V> the type of the values returned by this query.
 * @see <a href="https://cloud.google.com/datastore/docs/concepts/queries">Datastore Queries</a>
 */
public abstract class Query<V> implements Serializable {

  private static final long serialVersionUID = 7967659059395653941L;

  private final ResultType<V> resultType;
  private final String namespace;

  /**
   * This class represents the expected type of the result.
   *   ENTITY: A full entity represented by {@link Entity}.
   *   PROJECTION_ENTITY: A projection entity, represented by {@link ProjectionEntity}.
   *   KEY: An entity's {@link Key}.
   */
  public abstract static class ResultType<V> implements Serializable {

    private static final long serialVersionUID = 2104157695425806623L;
    private static final Map<com.google.datastore.v1.EntityResult.ResultType, ResultType<?>>
        PB_TO_INSTANCE = Maps.newEnumMap(
            com.google.datastore.v1.EntityResult.ResultType.class);

    static final ResultType<?> UNKNOWN = new ResultType<Object>(null, Object.class) {

      private static final long serialVersionUID = 1602329532153860907L;

      @Override
      Object convert(com.google.datastore.v1.Entity entityPb) {
        if (entityPb.getProperties().isEmpty()) {
          if (!entityPb.hasKey()) {
            return null;
          }
          return Key.fromPb(entityPb.getKey());
        }
        return ProjectionEntity.fromPb(entityPb);
      }
    };

    public static final ResultType<Entity> ENTITY =
        new ResultType<Entity>(com.google.datastore.v1.EntityResult.ResultType.FULL, Entity.class) {

          private static final long serialVersionUID = 7712959777507168274L;

          @Override
          Entity convert(com.google.datastore.v1.Entity entityPb) {
            return Entity.fromPb(entityPb);
          }
        };

    public static final ResultType<Key> KEY =
        new ResultType<Key>(com.google.datastore.v1.EntityResult.ResultType.KEY_ONLY, Key.class) {

          private static final long serialVersionUID = -8514289244104446252L;

          @Override
          Key convert(com.google.datastore.v1.Entity entityPb) {
            return Key.fromPb(entityPb.getKey());
          }
        };

    public static final ResultType<ProjectionEntity> PROJECTION_ENTITY =
        new ResultType<ProjectionEntity>(com.google.datastore.v1.EntityResult.ResultType.PROJECTION,
            ProjectionEntity.class) {

          private static final long serialVersionUID = -7591409419690650246L;

          @Override
          ProjectionEntity convert(com.google.datastore.v1.Entity entityPb) {
            return ProjectionEntity.fromPb(entityPb);
          }
        };

    private final Class<V> resultClass;
    private final com.google.datastore.v1.EntityResult.ResultType queryType;

    private ResultType(com.google.datastore.v1.EntityResult.ResultType queryType,
        Class<V> resultClass) {
      this.queryType = queryType;
      this.resultClass = resultClass;
      if (queryType != null) {
        PB_TO_INSTANCE.put(queryType, this);
      }
    }

    public Class<V> resultClass() {
      return resultClass;
    }

    @Override
    public int hashCode() {
      return resultClass.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof ResultType)) {
        return false;
      }
      ResultType<?> other = (ResultType<?>) obj;
      return resultClass.equals(other.resultClass);
    }

    @Override
    public String toString() {
      ToStringHelper toStringHelper = MoreObjects.toStringHelper(this);
      toStringHelper.add("queryType", queryType);
      toStringHelper.add("resultClass", resultClass);
      return toStringHelper.toString();
    }

    boolean isAssignableFrom(ResultType<?> otherResultType) {
      return resultClass.isAssignableFrom(otherResultType.resultClass);
    }

    abstract V convert(com.google.datastore.v1.Entity entityPb);

    static ResultType<?> fromPb(com.google.datastore.v1.EntityResult.ResultType typePb) {
      return MoreObjects.firstNonNull(PB_TO_INSTANCE.get(typePb), UNKNOWN);
    }
  }

  Query(ResultType<V> resultType, String namespace) {
    this.resultType = checkNotNull(resultType);
    this.namespace = namespace;
  }

  ResultType<V> type() {
    return resultType;
  }

  public String namespace() {
    return namespace;
  }

  ToStringHelper toStringHelper() {
    return MoreObjects.toStringHelper(this).add("type", resultType).add("namespace", namespace);
  }

  abstract void populatePb(com.google.datastore.v1.RunQueryRequest.Builder requestPb);

  abstract Query<V> nextQuery(com.google.datastore.v1.RunQueryResponse responsePb);

  /**
   * Returns a new {@link GqlQuery} builder.
   *
   * @see <a href="https://cloud.google.com/datastore/docs/apis/gql/gql_reference">GQL Reference</a>
   */
  public static GqlQuery.Builder<?> gqlQueryBuilder(String gql) {
    return gqlQueryBuilder(ResultType.UNKNOWN, gql);
  }

  /**
   * Returns a new {@link GqlQuery} builder.
   *
   * @see <a href="https://cloud.google.com/datastore/docs/apis/gql/gql_reference">GQL Reference</a>
   */
  public static <V> GqlQuery.Builder<V> gqlQueryBuilder(ResultType<V> resultType, String gql) {
    return new GqlQuery.Builder<>(resultType, gql);
  }

  /**
   * Returns a new {@link StructuredQuery} builder for full (complete entities) queries.
   */
  public static EntityQuery.Builder entityQueryBuilder() {
    return new EntityQuery.Builder();
  }

  /**
   * Returns a new {@link StructuredQuery} builder for key only queries.
   */
  public static KeyQuery.Builder keyQueryBuilder() {
    return new KeyQuery.Builder();
  }

  /**
   * Returns a new {@link StructuredQuery} builder for projection queries.
   */
  public static ProjectionEntityQuery.Builder projectionEntityQueryBuilder() {
    return new ProjectionEntityQuery.Builder();
  }
}
