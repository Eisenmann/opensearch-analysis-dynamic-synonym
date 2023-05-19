package com.eisenmann.opensearch.plugin;

import static org.opensearch.plugins.AnalysisPlugin.requiresAnalysisSettings;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;

import com.eisenmann.opensearch.plugin.synonym.analysis.DynamicSynonymGraphTokenFilterFactory;
import com.eisenmann.opensearch.plugin.synonym.analysis.DynamicSynonymTokenFilterFactory;


/**
 * @author bellszhu
 */
public class DynamicSynonymPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
        extra.put("dynamic_synonym", requiresAnalysisSettings(DynamicSynonymTokenFilterFactory::new));
        extra.put("dynamic_synonym_graph", requiresAnalysisSettings(DynamicSynonymGraphTokenFilterFactory::new));
        return extra;
    }
}