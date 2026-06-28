package com.eisenmann.opensearch.plugin;

import static org.opensearch.plugins.AnalysisPlugin.requiresAnalysisSettings;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.io.stream.NamedWriteableRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.watcher.ResourceWatcherService;

import com.eisenmann.opensearch.plugin.synonym.analysis.DynamicSynonymGraphTokenFilterFactory;
import com.eisenmann.opensearch.plugin.synonym.analysis.DynamicSynonymIndexFilterFactory;
import com.eisenmann.opensearch.plugin.synonym.analysis.DynamicSynonymTokenFilterFactory;

public class DynamicSynonymPlugin extends Plugin implements AnalysisPlugin {

    private volatile Client client;

    @Override
    public Collection<Object> createComponents(
            Client client,
            ClusterService clusterService,
            ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService,
            ScriptService scriptService,
            NamedXContentRegistry xContentRegistry,
            Environment environment,
            NodeEnvironment nodeEnvironment,
            NamedWriteableRegistry namedWriteableRegistry,
            IndexNameExpressionResolver indexNameExpressionResolver,
            Supplier<RepositoriesService> repositoriesServiceSupplier
    ) {
        this.client = client;
        return Collections.emptyList();
    }

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
        extra.put("dynamic_synonym", requiresAnalysisSettings(DynamicSynonymTokenFilterFactory::new));
        extra.put("dynamic_synonym_graph", requiresAnalysisSettings(DynamicSynonymGraphTokenFilterFactory::new));
        extra.put("dynamic-synonym-index", requiresAnalysisSettings(
                (indexSettings, env, name, settings) ->
                        new DynamicSynonymIndexFilterFactory(indexSettings, env, name, settings, client)));
        return extra;
    }
}
