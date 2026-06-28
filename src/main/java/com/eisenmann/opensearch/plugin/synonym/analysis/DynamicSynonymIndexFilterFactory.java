package com.eisenmann.opensearch.plugin.synonym.analysis;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;
import org.opensearch.index.analysis.AnalysisMode;
import org.opensearch.index.analysis.CharFilterFactory;
import org.opensearch.index.analysis.CustomAnalyzer;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.index.analysis.TokenizerFactory;

/**
 * Token filter factory that loads synonyms from an OpenSearch index in the
 * local cluster and reloads them dynamically at a configurable interval.
 *
 * <p>Configuration example:
 * <pre>
 * "filter": {
 *   "my_index_synonyms": {
 *     "type":          "dynamic-synonym-index",
 *     "synonyms_index": "my-synonyms-index",
 *     "interval":      60,
 *     "expand":        true,
 *     "lenient":       false,
 *     "format":        "",
 *     "updateable":    false
 *   }
 * }
 * </pre>
 *
 * <p>The synonym index must contain documents with a {@code synonyms} field
 * holding one Solr-format (or WordNet) synonym rule per document, e.g.
 * {@code "car, automobile, vehicle"} or {@code "car => auto"}.
 */
public class DynamicSynonymIndexFilterFactory extends AbstractTokenFilterFactory {

    private static final Logger logger = LogManager.getLogger("dynamic-synonym-index");

    private static final AtomicInteger id = new AtomicInteger(1);
    private static final ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setName("monitor-synonym-index-Thread-" + id.getAndIncrement());
        t.setDaemon(true);
        return t;
    });

    private volatile ScheduledFuture<?> scheduledFuture;

    private final Client client;
    private final String synonymsIndex;
    private final boolean expand;
    private final boolean lenient;
    private final String format;
    private final int interval;
    private final AnalysisMode analysisMode;

    protected volatile SynonymMap synonymMap;
    protected final Environment environment;
    protected final Map<AbsSynonymFilter, Integer> dynamicSynonymFilters = new WeakHashMap<>();

    public DynamicSynonymIndexFilterFactory(
            IndexSettings indexSettings,
            Environment env,
            String name,
            Settings settings,
            Client client
    ) throws IOException {
        super(indexSettings, name, settings);

        this.client = client;
        this.environment = env;

        this.synonymsIndex = settings.get("synonyms_index");
        if (this.synonymsIndex == null || this.synonymsIndex.isBlank()) {
            throw new IllegalArgumentException(
                    "dynamic-synonym-index requires `synonyms_index` to be configured");
        }

        this.interval = settings.getAsInt("interval", 60);
        this.expand = settings.getAsBoolean("expand", true);
        this.lenient = settings.getAsBoolean("lenient", false);
        this.format = settings.get("format", "");
        boolean updateable = settings.getAsBoolean("updateable", false);
        this.analysisMode = updateable ? AnalysisMode.SEARCH_TIME : AnalysisMode.ALL;
    }

    @Override
    public AnalysisMode getAnalysisMode() {
        return this.analysisMode;
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        throw new IllegalStateException(
                "Call getChainAwareTokenFilterFactory to specialise this factory for an analysis chain first");
    }

    @Override
    public TokenFilterFactory getChainAwareTokenFilterFactory(
            TokenizerFactory tokenizer,
            List<CharFilterFactory> charFilters,
            List<TokenFilterFactory> previousTokenFilters,
            Function<String, TokenFilterFactory> allFilters
    ) {
        final Analyzer analyzer = buildSynonymAnalyzer(tokenizer, charFilters, previousTokenFilters);
        final SynonymIndex synonymIndex = buildSynonymIndex(analyzer);
        synonymMap = buildSynonyms(synonymIndex);

        final String filterName = name();
        return new TokenFilterFactory() {
            @Override
            public String name() {
                return filterName;
            }

            @Override
            public TokenStream create(TokenStream tokenStream) {
                if (synonymMap.fst == null) {
                    return tokenStream;
                }
                DynamicSynonymFilter filter = new DynamicSynonymFilter(tokenStream, synonymMap, false);
                dynamicSynonymFilters.put(filter, 1);
                return filter;
            }

            @Override
            public TokenFilterFactory getSynonymFilter() {
                return IDENTITY_FILTER;
            }

            @Override
            public AnalysisMode getAnalysisMode() {
                return analysisMode;
            }
        };
    }

    private Analyzer buildSynonymAnalyzer(
            TokenizerFactory tokenizer,
            List<CharFilterFactory> charFilters,
            List<TokenFilterFactory> tokenFilters
    ) {
        return new CustomAnalyzer(
                tokenizer,
                charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream()
                        .map(TokenFilterFactory::getSynonymFilter)
                        .toArray(TokenFilterFactory[]::new)
        );
    }

    private SynonymIndex buildSynonymIndex(Analyzer analyzer) {
        DynamicSynonymIndex synonymIndex = new DynamicSynonymIndex(
                client, environment, analyzer, expand, lenient, format, synonymsIndex);
        if (scheduledFuture == null) {
            scheduledFuture = pool.scheduleAtFixedRate(
                    new Monitor(synonymIndex), interval, interval, TimeUnit.SECONDS);
        }
        return synonymIndex;
    }

    private SynonymMap buildSynonyms(SynonymIndex synonymIndex) {
        try {
            return synonymIndex.reloadSynonymMap();
        } catch (Exception e) {
            logger.error("Failed to build synonyms from index '{}'", synonymsIndex, e);
            throw new IllegalArgumentException(
                    "Failed to load synonyms from index: " + synonymsIndex, e);
        }
    }

    public class Monitor implements Runnable {

        private final SynonymIndex synonymIndex;

        Monitor(SynonymIndex synonymIndex) {
            this.synonymIndex = synonymIndex;
        }

        @Override
        public void run() {
            try {
                if (synonymIndex.isNeedReloadSynonymMap()) {
                    synonymMap = synonymIndex.reloadSynonymMap();
                    for (AbsSynonymFilter filter : dynamicSynonymFilters.keySet()) {
                        filter.update(synonymMap);
                    }
                    logger.info("Reloaded synonyms from index '{}'", synonymsIndex);
                }
            } catch (Exception e) {
                logger.error("Error reloading synonyms from index '{}'", synonymsIndex, e);
            }
        }
    }
}
