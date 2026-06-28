package com.eisenmann.opensearch.plugin.synonym.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

/**
 * Utility for creating and managing an OpenSearch synonym-store index.
 *
 * <p>Index document format:
 * <pre>
 * PUT /my-synonyms/_doc/{id}
 * { "synonyms": "car, automobile, vehicle" }
 * </pre>
 *
 * <p>Each document represents one synonym rule in Solr or WordNet format.
 * The {@code synonyms} field is stored as-is (keyword mapping) so the plugin
 * can fetch it without any analysis being applied.
 *
 * <p>Usage example:
 * <pre>
 * SynonymIndexCreator creator = new SynonymIndexCreator(client, "my-synonyms");
 * creator.createIndexIfNotExists();
 * creator.addSynonym("rule-1", "car, automobile, vehicle");
 * creator.addSynonym("rule-2", "laptop => notebook");
 * creator.updateSynonym("rule-1", "car, automobile, vehicle, auto");
 * creator.deleteSynonym("rule-2");
 * </pre>
 */
public class SynonymIndexCreator {

    private static final Logger logger = LogManager.getLogger("dynamic-synonym-index");

    private static final int MAX_RESULTS = 10_000;

    private final Client client;
    private final String indexName;

    public SynonymIndexCreator(Client client, String indexName) {
        this.client = client;
        this.indexName = indexName;
    }

    /**
     * Creates the synonym index with proper keyword mapping if it does not
     * already exist. Safe to call multiple times (idempotent).
     *
     * @throws IOException if the create-index request fails
     */
    public void createIndexIfNotExists() throws IOException {
        boolean exists = client.admin().indices()
                .exists(new IndicesExistsRequest(indexName))
                .actionGet()
                .isExists();

        if (!exists) {
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> synonymsField = new HashMap<>();
            synonymsField.put("type", "keyword");
            synonymsField.put("store", true);
            properties.put("synonyms", synonymsField);

            Map<String, Object> mapping = new HashMap<>();
            mapping.put("properties", properties);

            CreateIndexRequest request = new CreateIndexRequest(indexName).mapping(mapping);
            AcknowledgedResponse response = client.admin().indices().create(request).actionGet();
            if (response.isAcknowledged()) {
                logger.info("Created synonym index '{}'", indexName);
            } else {
                throw new IOException("Create-index request for '" + indexName + "' was not acknowledged");
            }
        }
    }

    /**
     * Adds or replaces a synonym rule identified by {@code id}.
     *
     * @param id          document ID (arbitrary string, used to identify the rule)
     * @param synonymRule a Solr-format rule such as {@code "car, automobile"} or
     *                    {@code "car => automobile"}
     */
    public void addSynonym(String id, String synonymRule) {
        Map<String, Object> source = new HashMap<>();
        source.put("synonyms", synonymRule);
        client.index(new IndexRequest(indexName).id(id).source(source)).actionGet();
        logger.debug("Added synonym rule '{}' to index '{}'", id, indexName);
    }

    /**
     * Updates the synonym rule for an existing document. If the document does
     * not exist it is created (upsert behaviour).
     *
     * @param id          document ID of the rule to update
     * @param synonymRule new synonym rule text
     */
    public void updateSynonym(String id, String synonymRule) {
        Map<String, Object> source = new HashMap<>();
        source.put("synonyms", synonymRule);
        client.update(
                new UpdateRequest(indexName, id).doc(source).upsert(source)
        ).actionGet();
        logger.debug("Updated synonym rule '{}' in index '{}'", id, indexName);
    }

    /**
     * Deletes a synonym rule by document ID.
     *
     * @param id document ID to delete
     */
    public void deleteSynonym(String id) {
        client.delete(new DeleteRequest(indexName, id)).actionGet();
        logger.debug("Deleted synonym rule '{}' from index '{}'", id, indexName);
    }

    /**
     * Returns all synonym rules currently stored in the index.
     * Fetches at most {@value #MAX_RESULTS} documents.
     *
     * @return map of document ID → synonym rule text
     */
    public Map<String, String> listSynonyms() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(QueryBuilders.matchAllQuery())
                .size(MAX_RESULTS)
                .fetchSource(new String[]{"synonyms"}, null);

        SearchResponse response = client.search(
                new SearchRequest(indexName).source(sourceBuilder)).actionGet();

        Map<String, String> result = new HashMap<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Object value = hit.getSourceAsMap().get("synonyms");
            if (value != null) {
                result.put(hit.getId(), value.toString());
            }
        }
        return result;
    }
}
