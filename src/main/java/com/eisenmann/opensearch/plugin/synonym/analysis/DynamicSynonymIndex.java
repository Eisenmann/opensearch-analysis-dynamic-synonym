package com.eisenmann.opensearch.plugin.synonym.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.analysis.common.OpenSearchSolrSynonymParser;
import org.opensearch.analysis.common.OpenSearchWordnetSynonymParser;
import org.opensearch.client.Client;
import org.opensearch.env.Environment;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;

/**
 * Loads synonym rules from an OpenSearch index in the local cluster.
 *
 * <p>Expected document format:
 * <pre>
 * PUT /my-synonyms/_doc/1
 * { "synonyms": "car, automobile, vehicle" }
 * </pre>
 *
 * <p>Change detection uses the maximum {@code _seq_no} across all documents
 * combined with the total document count, so any add, update, or delete
 * triggers a reload on the next monitor tick.
 */
public class DynamicSynonymIndex implements SynonymIndex {

    private static final int MAX_SYNONYMS = 10_000;
    private static final Logger logger = LogManager.getLogger("dynamic-synonym-index");

    private final Client client;
    private final String indexName;
    private final Analyzer analyzer;
    private final boolean expand;
    private final boolean lenient;
    private final String format;

    /** Tracks the highest _seq_no seen; -1 forces reload on first check. */
    private volatile long lastSeqNo = -1;
    /** Tracks total document count to detect deletes. */
    private volatile long lastTotalHits = -1;

    DynamicSynonymIndex(
            Client client,
            Environment env,
            Analyzer analyzer,
            boolean expand,
            boolean lenient,
            String format,
            String indexName
    ) {
        this.client = client;
        this.analyzer = analyzer;
        this.expand = expand;
        this.lenient = lenient;
        this.format = format;
        this.indexName = indexName;
    }

    /**
     * Returns {@code true} if the synonym index has been modified since the
     * last check. Uses a lightweight search (size=1, sort by _seq_no desc)
     * so no bulk document fetching happens during change detection.
     */
    @Override
    public boolean isNeedReloadSynonymMap() {
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(1)
                    .sort(SortBuilders.fieldSort("_seq_no").order(SortOrder.DESC))
                    .seqNoAndPrimaryTerm(true)
                    .trackTotalHits(true)
                    .fetchSource(false);

            SearchResponse response = client.search(
                    new SearchRequest(indexName).source(sourceBuilder)).actionGet();

            long currentTotal = response.getHits().getTotalHits().value;
            long currentSeqNo = response.getHits().getHits().length > 0
                    ? response.getHits().getHits()[0].getSeqNo()
                    : -1;

            if (currentSeqNo != lastSeqNo || currentTotal != lastTotalHits) {
                lastSeqNo = currentSeqNo;
                lastTotalHits = currentTotal;
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.warn("Cannot check synonym index '{}' for changes; skipping reload", indexName, e);
            return false;
        }
    }

    @Override
    public SynonymMap reloadSynonymMap() {
        try {
            Reader reader = getReader();
            SynonymMap.Builder parser = getSynonymParser(reader, format, expand, lenient, analyzer);
            return parser.build();
        } catch (Exception e) {
            logger.error("Failed to reload synonyms from index '{}'", indexName, e);
            throw new IllegalArgumentException(
                    "Could not reload synonyms from index: " + indexName, e);
        }
    }

    /**
     * Fetches all documents from the synonym index and returns them as a
     * {@link Reader} where each line is one synonym rule.
     */
    @Override
    public Reader getReader() {
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(MAX_SYNONYMS)
                    .fetchSource(new String[]{"synonyms"}, null);

            SearchResponse response = client.search(
                    new SearchRequest(indexName).source(sourceBuilder)).actionGet();

            StringBuilder sb = new StringBuilder();
            for (SearchHit hit : response.getHits().getHits()) {
                Object value = hit.getSourceAsMap().get("synonyms");
                if (value != null) {
                    String rule = value.toString().trim();
                    if (!rule.isEmpty()) {
                        sb.append(rule).append('\n');
                    }
                }
            }
            logger.debug("Loaded {} synonym rules from index '{}'",
                    response.getHits().getHits().length, indexName);
            return new StringReader(sb.toString());
        } catch (Exception e) {
            logger.error("Failed to fetch synonyms from index '{}'", indexName, e);
            return new StringReader("");
        }
    }

    static SynonymMap.Builder getSynonymParser(
            Reader rulesReader, String format, boolean expand, boolean lenient, Analyzer analyzer
    ) throws IOException, ParseException {
        SynonymMap.Builder parser;
        if ("wordnet".equalsIgnoreCase(format)) {
            parser = new OpenSearchWordnetSynonymParser(true, expand, lenient, analyzer);
            ((OpenSearchWordnetSynonymParser) parser).parse(rulesReader);
        } else {
            parser = new OpenSearchSolrSynonymParser(true, expand, lenient, analyzer);
            ((OpenSearchSolrSynonymParser) parser).parse(rulesReader);
        }
        return parser;
    }
}
