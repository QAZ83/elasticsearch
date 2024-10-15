/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata.State;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver.ResolvedExpression;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.indices.SystemIndices.SystemIndexAccessLevel;
import org.elasticsearch.test.ESTestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.createBackingIndex;
import static org.elasticsearch.common.util.set.Sets.newHashSet;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class WildcardExpressionResolverTests extends ESTestCase {

    private static final Predicate<String> NONE = name -> false;

    public void testConvertWildcardsJustIndicesTests() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("testXXX"))
            .put(indexBuilder("testXYY"))
            .put(indexBuilder("testYYY"))
            .put(indexBuilder("kuku"));
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        IndicesOptions indicesOptions = randomFrom(IndicesOptions.strictExpandOpen(), IndicesOptions.lenientExpandOpen());
        IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
            state,
            indicesOptions,
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testXXX"))),
            equalTo(resolvedExpressionsSet("testXXX"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testXXX", "testYYY"))),
            equalTo(resolvedExpressionsSet("testXXX", "testYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testXXX", "ku*"))),
            equalTo(resolvedExpressionsSet("testXXX", "kuku"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("test*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testX*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testX*", "kuku"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "kuku"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY", "kuku"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("*", "-kuku"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
        assertThat(
            newHashSet(
                IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                    context,
                    resolvedExpressions("testX*", "-doe", "-testXXX", "-testYYY")
                )
            ),
            equalTo(resolvedExpressionsSet("testXYY"))
        );
        if (indicesOptions == IndicesOptions.lenientExpandOpen()) {
            assertThat(
                newHashSet(
                    IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testXXX", "-testXXX"))
                ),
                equalTo(resolvedExpressionsSet("testXXX", "-testXXX"))
            );
        } else if (indicesOptions == IndicesOptions.strictExpandOpen()) {
            IndexNotFoundException infe = expectThrows(
                IndexNotFoundException.class,
                () -> IndexNameExpressionResolver.resolveExpressions(context, "testXXX", "-testXXX")
            );
            assertEquals("-testXXX", infe.getIndex().getName());
        }
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testXXX", "-testX*"))),
            equalTo(resolvedExpressionsSet("testXXX"))
        );
    }

    public void testConvertWildcardsTests() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("testXXX").putAlias(AliasMetadata.builder("alias1")).putAlias(AliasMetadata.builder("alias2")))
            .put(indexBuilder("testXYY").putAlias(AliasMetadata.builder("alias2")))
            .put(indexBuilder("testYYY").putAlias(AliasMetadata.builder("alias3")))
            .put(indexBuilder("kuku"));
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.lenientExpandOpen(),
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testYY*", "alias*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("-kuku"))),
            equalTo(resolvedExpressionsSet("-kuku"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("test*", "-testYYY"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testX*", "testYYY"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testYYY", "testX*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
    }

    public void testConvertWildcardsOpenClosedIndicesTests() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("testXXX").state(IndexMetadata.State.OPEN))
            .put(indexBuilder("testXXY").state(IndexMetadata.State.OPEN))
            .put(indexBuilder("testXYY").state(IndexMetadata.State.CLOSE))
            .put(indexBuilder("testYYY").state(IndexMetadata.State.OPEN))
            .put(indexBuilder("testYYX").state(IndexMetadata.State.CLOSE))
            .put(indexBuilder("kuku").state(IndexMetadata.State.OPEN));
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.fromOptions(true, true, true, true),
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testX*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXXY", "testXYY"))
        );
        context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.fromOptions(true, true, false, true),
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testX*"))),
            equalTo(resolvedExpressionsSet("testXYY"))
        );
        context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.fromOptions(true, true, true, false),
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("testX*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXXY"))
        );
        context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.fromOptions(true, true, false, false),
            SystemIndexAccessLevel.NONE
        );
        assertThat(IndexNameExpressionResolver.resolveExpressions(context, "testX*").size(), equalTo(0));
        context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.fromOptions(false, true, false, false),
            SystemIndexAccessLevel.NONE
        );
        IndexNameExpressionResolver.Context finalContext = context;
        IndexNotFoundException infe = expectThrows(
            IndexNotFoundException.class,
            () -> IndexNameExpressionResolver.resolveExpressions(finalContext, "testX*")
        );
        assertThat(infe.getIndex().getName(), is("testX*"));
    }

    // issue #13334
    public void testMultipleWildcards() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("testXXX"))
            .put(indexBuilder("testXXY"))
            .put(indexBuilder("testXYY"))
            .put(indexBuilder("testYYY"))
            .put(indexBuilder("kuku"))
            .put(indexBuilder("kukuYYY"));

        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.lenientExpandOpen(),
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("test*X*"))),
            equalTo(resolvedExpressionsSet("testXXX", "testXXY", "testXYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("test*X*Y"))),
            equalTo(resolvedExpressionsSet("testXXY", "testXYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("kuku*Y*"))),
            equalTo(resolvedExpressionsSet("kukuYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("*Y*"))),
            equalTo(resolvedExpressionsSet("testXXY", "testXYY", "testYYY", "kukuYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("test*Y*X"))).size(),
            equalTo(0)
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolve(context, resolvedExpressions("*Y*X"))).size(),
            equalTo(0)
        );
    }

    public void testAll() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("testXXX"))
            .put(indexBuilder("testXYY"))
            .put(indexBuilder("testYYY"));
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
            state,
            IndicesOptions.lenientExpandOpen(),
            SystemIndexAccessLevel.NONE
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolveAll(context)),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
        assertThat(
            newHashSet(IndexNameExpressionResolver.resolveExpressions(context, "_all")),
            equalTo(resolvedExpressionsSet("testXXX", "testXYY", "testYYY"))
        );
        IndicesOptions noExpandOptions = IndicesOptions.fromOptions(
            randomBoolean(),
            true,
            false,
            false,
            randomBoolean(),
            randomBoolean(),
            randomBoolean(),
            randomBoolean()
        );
        IndexNameExpressionResolver.Context noExpandContext = new IndexNameExpressionResolver.Context(
            state,
            noExpandOptions,
            SystemIndexAccessLevel.NONE
        );
        assertThat(IndexNameExpressionResolver.resolveExpressions(noExpandContext, "_all").size(), equalTo(0));
    }

    public void testAllAliases() {
        {
            // hidden index with hidden alias should not be returned
            Metadata.Builder mdBuilder = Metadata.builder()
                .put(
                    indexBuilder("index-hidden-alias", true) // index hidden
                        .state(State.OPEN)
                        .putAlias(AliasMetadata.builder("alias-hidden").isHidden(true)) // alias hidden
                );

            ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

            IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
                state,
                IndicesOptions.lenientExpandOpen(), // don't include hidden
                SystemIndexAccessLevel.NONE
            );
            assertThat(newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolveAll(context)), equalTo(Set.of()));
        }

        {
            // hidden index with visible alias should be returned
            Metadata.Builder mdBuilder = Metadata.builder()
                .put(
                    indexBuilder("index-visible-alias", true) // index hidden
                        .state(State.OPEN)
                        .putAlias(AliasMetadata.builder("alias-visible").isHidden(false)) // alias visible
                );

            ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

            IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
                state,
                IndicesOptions.lenientExpandOpen(), // don't include hidden
                SystemIndexAccessLevel.NONE
            );
            assertThat(
                newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolveAll(context)),
                equalTo(resolvedExpressionsSet("index-visible-alias"))
            );
        }
    }

    public void testAllDataStreams() {

        String dataStreamName = "foo_logs";
        long epochMillis = randomLongBetween(1580536800000L, 1583042400000L);
        IndexMetadata firstBackingIndexMetadata = createBackingIndex(dataStreamName, 1, epochMillis).build();

        IndicesOptions indicesAndAliasesOptions = IndicesOptions.fromOptions(
            randomBoolean(),
            randomBoolean(),
            true,
            false,
            true,
            false,
            false,
            false
        );

        {
            // visible data streams should be returned by _all even show backing indices are hidden
            Metadata.Builder mdBuilder = Metadata.builder()
                .put(firstBackingIndexMetadata, true)
                .put(DataStreamTestHelper.newInstance(dataStreamName, List.of(firstBackingIndexMetadata.getIndex())));

            ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

            IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
                state.metadata().getProject(),
                indicesAndAliasesOptions,
                false,
                false,
                true,
                SystemIndexAccessLevel.NONE,
                NONE,
                NONE
            );

            assertThat(
                newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolveAll(context)),
                equalTo(resolvedExpressionsSet(DataStream.getDefaultBackingIndexName("foo_logs", 1, epochMillis)))
            );
        }

        {
            // if data stream itself is hidden, backing indices should not be returned
            var dataStream = DataStream.builder(dataStreamName, List.of(firstBackingIndexMetadata.getIndex())).setHidden(true).build();

            Metadata.Builder mdBuilder = Metadata.builder().put(firstBackingIndexMetadata, true).put(dataStream);

            ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

            IndexNameExpressionResolver.Context context = new IndexNameExpressionResolver.Context(
                state.metadata().getProject(),
                indicesAndAliasesOptions,
                false,
                false,
                true,
                SystemIndexAccessLevel.NONE,
                NONE,
                NONE
            );

            assertThat(newHashSet(IndexNameExpressionResolver.WildcardExpressionResolver.resolveAll(context)), equalTo(Set.of()));
        }
    }

    public void testResolveEmpty() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(
                indexBuilder("index_open").state(State.OPEN)
                    .putAlias(AliasMetadata.builder("alias_open"))
                    .putAlias(AliasMetadata.builder("alias_hidden").isHidden(true))
            )
            .put(
                indexBuilder("index_closed").state(State.CLOSE)
                    .putAlias(AliasMetadata.builder("alias_closed"))
                    .putAlias(AliasMetadata.builder("alias_hidden").isHidden(true))
            )
            .put(
                indexBuilder("index_hidden_open", true).state(State.OPEN)
                    .putAlias(AliasMetadata.builder("alias_open"))
                    .putAlias(AliasMetadata.builder("alias_hidden").isHidden(true))
            )
            .put(
                indexBuilder("index_hidden_closed", true).state(State.CLOSE)
                    .putAlias(AliasMetadata.builder("alias_closed"))
                    .putAlias(AliasMetadata.builder("alias_hidden").isHidden(true))
            )
            .put(
                indexBuilder(".dot_index_hidden_open", true).state(State.OPEN)
                    .putAlias(AliasMetadata.builder("alias_open"))
                    .putAlias(AliasMetadata.builder("alias_hidden").isHidden(true))
            )
            .put(
                indexBuilder(".dot_index_hidden_closed", true).state(State.CLOSE)
                    .putAlias(AliasMetadata.builder("alias_closed"))
                    .putAlias(AliasMetadata.builder("alias_hidden").isHidden(true))
            );
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();
        IndicesOptions onlyOpenIndicesAndAliasesDisallowNoIndicesOption = IndicesOptions.fromOptions(
            randomBoolean(),
            false,
            true,
            false,
            false,
            randomBoolean(),
            randomBoolean(),
            false,
            randomBoolean()
        );
        IndexNameExpressionResolver.Context indicesAndAliasesContext = new IndexNameExpressionResolver.Context(
            state,
            onlyOpenIndicesAndAliasesDisallowNoIndicesOption,
            randomFrom(SystemIndexAccessLevel.values())
        );
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "index_closed*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "index_hidden_open*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "index_hidden_closed*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, ".dot_index_hidden_closed*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "alias_closed*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "alias_hidden*");
        IndicesOptions closedAndHiddenIndicesAndAliasesDisallowNoIndicesOption = IndicesOptions.fromOptions(
            randomBoolean(),
            false,
            false,
            true,
            true,
            randomBoolean(),
            randomBoolean(),
            false,
            randomBoolean()
        );
        indicesAndAliasesContext = new IndexNameExpressionResolver.Context(
            state,
            closedAndHiddenIndicesAndAliasesDisallowNoIndicesOption,
            randomFrom(SystemIndexAccessLevel.values())
        );
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "index_open*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "index_hidden_open*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, ".dot_hidden_open*");
        assertWildcardResolvesToEmpty(indicesAndAliasesContext, "alias_open*");
    }

    public void testResolveAliases() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("foo_foo").state(State.OPEN))
            .put(indexBuilder("bar_bar").state(State.OPEN))
            .put(indexBuilder("foo_index").state(State.OPEN).putAlias(AliasMetadata.builder("foo_alias")))
            .put(indexBuilder("bar_index").state(State.OPEN).putAlias(AliasMetadata.builder("foo_alias")));
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();
        // when ignoreAliases option is not set, WildcardExpressionResolver resolves the provided
        // expressions against the defined indices and aliases
        IndicesOptions indicesAndAliasesOptions = IndicesOptions.fromOptions(
            randomBoolean(),
            randomBoolean(),
            true,
            false,
            true,
            false,
            false,
            false
        );
        IndexNameExpressionResolver.Context indicesAndAliasesContext = new IndexNameExpressionResolver.Context(
            state,
            indicesAndAliasesOptions,
            SystemIndexAccessLevel.NONE
        );
        // ignoreAliases option is set, WildcardExpressionResolver throws error when
        IndicesOptions skipAliasesIndicesOptions = IndicesOptions.fromOptions(true, true, true, false, true, false, true, false);
        IndexNameExpressionResolver.Context skipAliasesLenientContext = new IndexNameExpressionResolver.Context(
            state,
            skipAliasesIndicesOptions,
            SystemIndexAccessLevel.NONE
        );
        // ignoreAliases option is set, WildcardExpressionResolver resolves the provided expressions only against the defined indices
        IndicesOptions errorOnAliasIndicesOptions = IndicesOptions.fromOptions(false, false, true, false, true, false, true, false);
        IndexNameExpressionResolver.Context skipAliasesStrictContext = new IndexNameExpressionResolver.Context(
            state,
            errorOnAliasIndicesOptions,
            SystemIndexAccessLevel.NONE
        );

        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAndAliasesContext,
                resolvedExpressions("foo_a*")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_index", "bar_index")));
        }
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                skipAliasesLenientContext,
                resolvedExpressions("foo_a*")
            );
            assertEquals(0, indices.size());
        }
        {
            IndexNotFoundException infe = expectThrows(
                IndexNotFoundException.class,
                () -> IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                    skipAliasesStrictContext,
                    resolvedExpressions("foo_a*")
                )
            );
            assertEquals("foo_a*", infe.getIndex().getName());
        }
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAndAliasesContext,
                resolvedExpressions("foo*")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_foo", "foo_index", "bar_index")));
        }
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                skipAliasesLenientContext,
                resolvedExpressions("foo*")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_foo", "foo_index")));
        }
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                skipAliasesStrictContext,
                resolvedExpressions("foo*")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_foo", "foo_index")));
        }
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAndAliasesContext,
                resolvedExpressions("foo_alias")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_alias")));
        }
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                skipAliasesLenientContext,
                resolvedExpressions("foo_alias")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_alias")));
        }
        {
            IllegalArgumentException iae = expectThrows(
                IllegalArgumentException.class,
                () -> IndexNameExpressionResolver.resolveExpressions(skipAliasesStrictContext, "foo_alias")
            );
            assertEquals(
                "The provided expression [foo_alias] matches an alias, specify the corresponding concrete indices instead.",
                iae.getMessage()
            );
        }
        IndicesOptions noExpandNoAliasesIndicesOptions = IndicesOptions.fromOptions(true, false, false, false, true, false, true, false);
        IndexNameExpressionResolver.Context noExpandNoAliasesContext = new IndexNameExpressionResolver.Context(
            state,
            noExpandNoAliasesIndicesOptions,
            SystemIndexAccessLevel.NONE
        );
        {
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                noExpandNoAliasesContext,
                resolvedExpressions("foo_alias")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_alias")));
        }
        IndicesOptions strictNoExpandNoAliasesIndicesOptions = IndicesOptions.fromOptions(
            false,
            true,
            false,
            false,
            true,
            false,
            true,
            false
        );
        IndexNameExpressionResolver.Context strictNoExpandNoAliasesContext = new IndexNameExpressionResolver.Context(
            state,
            strictNoExpandNoAliasesIndicesOptions,
            SystemIndexAccessLevel.NONE
        );
        {
            IllegalArgumentException iae = expectThrows(
                IllegalArgumentException.class,
                () -> IndexNameExpressionResolver.resolveExpressions(strictNoExpandNoAliasesContext, "foo_alias")
            );
            assertEquals(
                "The provided expression [foo_alias] matches an alias, specify the corresponding concrete indices instead.",
                iae.getMessage()
            );
        }
    }

    public void testResolveDataStreams() {
        String dataStreamName = "foo_logs";
        long epochMillis = randomLongBetween(1580536800000L, 1583042400000L);
        IndexMetadata firstBackingIndexMetadata = createBackingIndex(dataStreamName, 1, epochMillis).build();
        IndexMetadata secondBackingIndexMetadata = createBackingIndex(dataStreamName, 2, epochMillis).build();

        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("foo_foo").state(State.OPEN))
            .put(indexBuilder("bar_bar").state(State.OPEN))
            .put(indexBuilder("foo_index").state(State.OPEN).putAlias(AliasMetadata.builder("foo_alias")))
            .put(indexBuilder("bar_index").state(State.OPEN).putAlias(AliasMetadata.builder("foo_alias")))
            .put(firstBackingIndexMetadata, true)
            .put(secondBackingIndexMetadata, true)
            .put(
                DataStreamTestHelper.newInstance(
                    dataStreamName,
                    List.of(firstBackingIndexMetadata.getIndex(), secondBackingIndexMetadata.getIndex())
                )
            );

        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        {
            IndicesOptions indicesAndAliasesOptions = IndicesOptions.fromOptions(
                randomBoolean(),
                randomBoolean(),
                true,
                false,
                true,
                false,
                false,
                false
            );
            IndexNameExpressionResolver.Context indicesAndAliasesContext = new IndexNameExpressionResolver.Context(
                state,
                indicesAndAliasesOptions,
                SystemIndexAccessLevel.NONE
            );

            // data streams are not included but expression matches the data stream
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAndAliasesContext,
                resolvedExpressions("foo_*")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("foo_index", "foo_foo", "bar_index")));

            // data streams are not included and expression doesn't match the data steram
            indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAndAliasesContext,
                resolvedExpressions("bar_*")
            );
            assertThat(newHashSet(indices), equalTo(resolvedExpressionsSet("bar_bar", "bar_index")));
        }

        {
            IndicesOptions indicesAndAliasesOptions = IndicesOptions.fromOptions(
                randomBoolean(),
                randomBoolean(),
                true,
                false,
                true,
                false,
                false,
                false
            );
            IndexNameExpressionResolver.Context indicesAliasesAndDataStreamsContext = new IndexNameExpressionResolver.Context(
                state.metadata().getProject(),
                indicesAndAliasesOptions,
                false,
                false,
                true,
                SystemIndexAccessLevel.NONE,
                NONE,
                NONE
            );

            // data stream's corresponding backing indices are resolved
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAliasesAndDataStreamsContext,
                resolvedExpressions("foo_*")
            );
            assertThat(
                newHashSet(indices),
                equalTo(
                    resolvedExpressionsSet(
                        "foo_index",
                        "bar_index",
                        "foo_foo",
                        DataStream.getDefaultBackingIndexName("foo_logs", 1, epochMillis),
                        DataStream.getDefaultBackingIndexName("foo_logs", 2, epochMillis)
                    )
                )
            );

            // include all wildcard adds the data stream's backing indices
            indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAliasesAndDataStreamsContext,
                resolvedExpressions("*")
            );
            assertThat(
                newHashSet(indices),
                equalTo(
                    resolvedExpressionsSet(
                        "foo_index",
                        "bar_index",
                        "foo_foo",
                        "bar_bar",
                        DataStream.getDefaultBackingIndexName("foo_logs", 1, epochMillis),
                        DataStream.getDefaultBackingIndexName("foo_logs", 2, epochMillis)
                    )
                )
            );
        }

        {
            IndicesOptions indicesAliasesAndExpandHiddenOptions = IndicesOptions.fromOptions(
                randomBoolean(),
                randomBoolean(),
                true,
                false,
                true,
                true,
                false,
                false,
                false
            );
            IndexNameExpressionResolver.Context indicesAliasesDataStreamsAndHiddenIndices = new IndexNameExpressionResolver.Context(
                state.metadata().getProject(),
                indicesAliasesAndExpandHiddenOptions,
                false,
                false,
                true,
                SystemIndexAccessLevel.NONE,
                NONE,
                NONE
            );

            // data stream's corresponding backing indices are resolved
            Collection<ResolvedExpression> indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAliasesDataStreamsAndHiddenIndices,
                resolvedExpressions("foo_*")
            );
            assertThat(
                newHashSet(indices),
                equalTo(
                    resolvedExpressionsSet(
                        "foo_index",
                        "bar_index",
                        "foo_foo",
                        DataStream.getDefaultBackingIndexName("foo_logs", 1, epochMillis),
                        DataStream.getDefaultBackingIndexName("foo_logs", 2, epochMillis)
                    )
                )
            );

            // include all wildcard adds the data stream's backing indices
            indices = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                indicesAliasesDataStreamsAndHiddenIndices,
                resolvedExpressions("*")
            );
            assertThat(
                newHashSet(indices),
                equalTo(
                    resolvedExpressionsSet(
                        "foo_index",
                        "bar_index",
                        "foo_foo",
                        "bar_bar",
                        DataStream.getDefaultBackingIndexName("foo_logs", 1, epochMillis),
                        DataStream.getDefaultBackingIndexName("foo_logs", 2, epochMillis)
                    )
                )
            );
        }
    }

    public void testMatchesConcreteIndicesWildcardAndAliases() {
        Metadata.Builder mdBuilder = Metadata.builder()
            .put(indexBuilder("foo_foo").state(State.OPEN))
            .put(indexBuilder("bar_bar").state(State.OPEN))
            .put(indexBuilder("foo_index").state(State.OPEN).putAlias(AliasMetadata.builder("foo_alias")))
            .put(indexBuilder("bar_index").state(State.OPEN).putAlias(AliasMetadata.builder("foo_alias")));
        ClusterState state = ClusterState.builder(new ClusterName("_name")).metadata(mdBuilder).build();

        // when ignoreAliases option is not set, WildcardExpressionResolver resolves the provided
        // expressions against the defined indices and aliases
        IndicesOptions indicesAndAliasesOptions = IndicesOptions.fromOptions(false, false, true, false, true, false, false, false);
        IndexNameExpressionResolver.Context indicesAndAliasesContext = new IndexNameExpressionResolver.Context(
            state,
            indicesAndAliasesOptions,
            SystemIndexAccessLevel.NONE
        );

        // ignoreAliases option is set, WildcardExpressionResolver resolves the provided expressions
        // only against the defined indices
        IndicesOptions onlyIndicesOptions = IndicesOptions.fromOptions(false, false, true, false, true, false, true, false);
        IndexNameExpressionResolver.Context onlyIndicesContext = new IndexNameExpressionResolver.Context(
            state,
            onlyIndicesOptions,
            SystemIndexAccessLevel.NONE
        );

        Collection<ResolvedExpression> matches = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
            indicesAndAliasesContext,
            List.of(new ResolvedExpression("*"))
        );
        assertThat(newHashSet(matches), equalTo(resolvedExpressionsSet("bar_bar", "foo_foo", "foo_index", "bar_index")));
        matches = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(onlyIndicesContext, List.of(new ResolvedExpression("*")));
        assertThat(newHashSet(matches), equalTo(resolvedExpressionsSet("bar_bar", "foo_foo", "foo_index", "bar_index")));
        matches = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
            indicesAndAliasesContext,
            List.of(new ResolvedExpression("foo*"))
        );
        assertThat(newHashSet(matches), equalTo(resolvedExpressionsSet("foo_foo", "foo_index", "bar_index")));
        matches = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
            onlyIndicesContext,
            List.of(new ResolvedExpression("foo*"))
        );
        assertThat(newHashSet(matches), equalTo(resolvedExpressionsSet("foo_foo", "foo_index")));
        matches = IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
            indicesAndAliasesContext,
            List.of(new ResolvedExpression("foo_alias"))
        );
        assertThat(newHashSet(matches), equalTo(resolvedExpressionsSet("foo_alias")));
        IllegalArgumentException iae = expectThrows(
            IllegalArgumentException.class,
            () -> IndexNameExpressionResolver.resolveExpressions(onlyIndicesContext, "foo_alias")
        );
        assertThat(
            iae.getMessage(),
            containsString("The provided expression [foo_alias] matches an alias, specify the corresponding concrete indices instead")
        );
    }

    private static IndexMetadata.Builder indexBuilder(String index, boolean hidden) {
        return IndexMetadata.builder(index)
            .settings(indexSettings(IndexVersion.current(), 1, 0).put(IndexMetadata.INDEX_HIDDEN_SETTING.getKey(), hidden));
    }

    private static IndexMetadata.Builder indexBuilder(String index) {
        return indexBuilder(index, false);
    }

    private static void assertWildcardResolvesToEmpty(IndexNameExpressionResolver.Context context, String wildcardExpression) {
        IndexNotFoundException infe = expectThrows(
            IndexNotFoundException.class,
            () -> IndexNameExpressionResolver.WildcardExpressionResolver.resolve(
                context,
                List.of(new ResolvedExpression(wildcardExpression))
            )
        );
        assertEquals(wildcardExpression, infe.getIndex().getName());
    }

    private List<ResolvedExpression> resolvedExpressions(String... expressions) {
        return Arrays.stream(expressions).map(ResolvedExpression::new).toList();
    }

    private Set<ResolvedExpression> resolvedExpressionsSet(String... expressions) {
        return Arrays.stream(expressions).map(ResolvedExpression::new).collect(Collectors.toSet());
    }
}
