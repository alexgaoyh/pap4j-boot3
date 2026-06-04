package cn.net.pap.common.datastructure.lamba;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 模拟 Elasticsearch 的 Lambda Builder 嵌套写法
 * 帮助理解 Function<Builder, ObjectBuilder> 的巧妙结合
 */
public class ElasticsearchLambdaBuilderMockTest {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchLambdaBuilderMockTest.class);

    // ---------------------------------------------------------
    // 1. 核心接口：ObjectBuilder (所有 Builder 最终都要实现该接口并返回构建的对象)
    // ---------------------------------------------------------
    public interface ObjectBuilder<T> {
        T build();
    }

    // ---------------------------------------------------------
    // 2. 模拟的组件 DTO 及其 Builder
    // ---------------------------------------------------------

    // 2.1 MatchPhrase (精确匹配)
    public static class MatchPhrase {
        private final String field;
        private final String query;

        private MatchPhrase(Builder builder) {
            this.field = builder.field;
            this.query = builder.query;
        }

        public static class Builder implements ObjectBuilder<MatchPhrase> {
            private String field;
            private String query;

            public Builder field(String field) {
                this.field = field;
                return this;
            }

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            @Override
            public MatchPhrase build() {
                return new MatchPhrase(this);
            }
        }
    }

    // 2.2 Match (模糊匹配)
    public static class Match {
        private final String field;
        private final String query;

        private Match(Builder builder) {
            this.field = builder.field;
            this.query = builder.query;
        }

        public static class Builder implements ObjectBuilder<Match> {
            private String field;
            private String query;

            public Builder field(String field) {
                this.field = field;
                return this;
            }

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            @Override
            public Match build() {
                return new Match(this);
            }
        }
    }

    // 2.3 BoolQuery (布尔查询，包含 must, should 等组合)
    public static class BoolQuery {
        private final List<Query> must;
        private final List<Query> should;
        private final String minimumShouldMatch;

        private BoolQuery(Builder builder) {
            this.must = builder.must;
            this.should = builder.should;
            this.minimumShouldMatch = builder.minimumShouldMatch;
        }

        public static class Builder implements ObjectBuilder<BoolQuery> {
            private final List<Query> must = new ArrayList<>();
            private final List<Query> should = new ArrayList<>();
            private String minimumShouldMatch;

            public Builder must(Query query) {
                this.must.add(query);
                return this; // 返回当前 Builder 支持链式调用
            }

            public Builder should(Query query) {
                this.should.add(query);
                return this;
            }

            public Builder minimumShouldMatch(String minimumShouldMatch) {
                this.minimumShouldMatch = minimumShouldMatch;
                return this;
            }

            @Override
            public BoolQuery build() {
                return new BoolQuery(this);
            }
        }
    }

    // 2.4 最外层与嵌套层的核心 DTO: Query
    public static class Query {
        private final BoolQuery bool;
        private final Match match;
        private final MatchPhrase matchPhrase;

        private Query(Builder builder) {
            this.bool = builder.bool;
            this.match = builder.match;
            this.matchPhrase = builder.matchPhrase;
        }

        /**
         * Elasticsearch 标志性的静态入口方法
         * 接受一个针对当前 Builder 的 Lambda 表达式
         */
        public static Query of(Function<Builder, ObjectBuilder<Query>> fn) {
            // 实例化一个新的 Builder 交给 Lambda (由开发者在业务中组装属性)
            // fn.apply 执行完成后，会返回一个 ObjectBuilder<Query> (往往也就是当前的 Builder 本身)
            // 最后调用 build() 进行真正的对象创建。
            return fn.apply(new Builder()).build();
        }

        public static class Builder implements ObjectBuilder<Query> {
            private BoolQuery bool;
            private Match match;
            private MatchPhrase matchPhrase;

            // --- 核心设计：接受下一层 Builder 的 Lambda 表达式 --- //

            public Builder bool(Function<BoolQuery.Builder, ObjectBuilder<BoolQuery>> fn) {
                this.bool = fn.apply(new BoolQuery.Builder()).build();
                return this; // 注意这里返回的是当前级 (Query.Builder) 的 this
            }

            public Builder match(Function<Match.Builder, ObjectBuilder<Match>> fn) {
                this.match = fn.apply(new Match.Builder()).build();
                return this;
            }

            public Builder matchPhrase(Function<MatchPhrase.Builder, ObjectBuilder<MatchPhrase>> fn) {
                this.matchPhrase = fn.apply(new MatchPhrase.Builder()).build();
                return this;
            }

            @Override
            public Query build() {
                return new Query(this);
            }
        }
    }

    // ---------------------------------------------------------
    // 3. 单元测试演示
    // ---------------------------------------------------------
    @Test
    public void testElasticsearchLikeLambdaBuilder() {
        /*
         * 【原理解析】：
         * 1. Query.of(...) 提供了一个 Query.Builder (即 q)。
         * 2. q.bool(...) 提供了一个 BoolQuery.Builder (即 b)，而 q.bool 返回的是 q 本身，因此整个表达式返回的就是 Query.Builder。
         * 3. 在 b.should(...) 内部，又开始了嵌套的 Query.of(q1 -> ...) 进行子查询。
         * 4. 这种方式使得代码结构与 JSON 树形结构高度一致，阅读体验极佳。
         */

        // --- 这就是你要理解的 Elasticsearch API 用法示例 ---
        Query nativeQuery = Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(q1 -> q1
                                .bool(b1 -> b1
                                        .must(Query.of(q1a -> q1a.matchPhrase(m -> m.field("status").query("active"))))
                                        .must(Query.of(q1b -> q1b.matchPhrase(m -> m.field("category").query("tech"))))
                                )
                        ))
                        .should(Query.of(q2 -> q2
                                .match(m -> m.field("title").query("Elasticsearch"))
                        ))
                        .minimumShouldMatch("1")
                )
        );

        log.info("成功构建出类似 ES Client 语法的复杂查询对象！");
        assertNotNull(nativeQuery);
        assertNotNull(nativeQuery.bool);

        // 为了加深理解，如果你用传统的 Builder 写法（不使用 Lambda）来写，会是这样：
        Query traditionalQuery = new Query.Builder()
                .bool(bBuilder -> {
                    // 使用传统的嵌套，看起来也会非常冗余，所以 ES 采用了 of + Lambda
                    // 这里仅作对比
                    return bBuilder;
                })
                .build();
    }

    @Test
    public void testConditionLambdaBuilder() {
        // 你提到的场景：如果有条件判断该如何处理？
        // 由于 Lambda 内部是函数块，你可以随意插入条件。
        // 但需要注意：带有条件判断时，Lambda 无法使用 "单行表达式" 简写，必须加上 { } 并显式 return

        boolean isVip = true;

        Query nativeQuery = Query.of(q -> {
            return q.bool(b -> {
                b.must(Query.of(q1 -> q1.matchPhrase(m -> m.field("status").query("active"))));

                // --- 灵活插入判断条件 ---
                if (isVip) {
                    b.must(Query.of(q2 -> q2.matchPhrase(m -> m.field("tag").query("vip"))));
                }

                b.minimumShouldMatch("1");

                // 返回组装好的 builder
                return b;
            });
        });

        assertNotNull(nativeQuery);
    }
}
