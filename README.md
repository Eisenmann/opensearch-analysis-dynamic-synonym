# opensearch-analysis-dynamic-synonym

Dynamic synonym plugin for OpenSearch. Provides token filters that reload synonym rules automatically at a configurable interval — from a local file, a remote HTTP endpoint, or an **OpenSearch index in the same cluster**.

Adapted from [elasticsearch-analysis-dynamic-synonym](https://github.com/bells/elasticsearch-analysis-dynamic-synonym).

---

## Requirements

| Plugin version | OpenSearch | Java |
|---|---|---|
| 3.7.0 | 3.7.0 | 21+ |
| 2.7.0 | 2.7.0 | 11+ |

---

## Installation

```bash
mvn package
```

Copy and unzip the release archive into the OpenSearch plugins directory:

```bash
cp target/releases/opensearch-analysis-dynamic-synonym-3.7.0.zip \
   $OPENSEARCH_HOME/plugins/

cd $OPENSEARCH_HOME
bin/opensearch-plugin install file:plugins/opensearch-analysis-dynamic-synonym-3.7.0.zip
```

Or extract manually:

```bash
mkdir $OPENSEARCH_HOME/plugins/dynamic-synonym
unzip target/releases/opensearch-analysis-dynamic-synonym-3.7.0.zip \
      -d $OPENSEARCH_HOME/plugins/dynamic-synonym
```

Restart OpenSearch after installation.

---

## Filter types

| Type | Description |
|---|---|
| `dynamic_synonym` | Standard synonym filter (flat token stream) |
| `dynamic_synonym_graph` | Graph synonym filter — correct positional handling for multi-token synonyms |
| `dynamic-synonym-index` | Loads synonyms from an OpenSearch index in the local cluster |

---

## 1. File-based synonyms (`dynamic_synonym` / `dynamic_synonym_graph`)

Synonyms are loaded from a **local file** or a **remote HTTP/HTTPS URL** and reloaded whenever the source changes.

### Configuration

```json
PUT /my-index
{
  "settings": {
    "analysis": {
      "analyzer": {
        "my_analyzer": {
          "tokenizer": "whitespace",
          "filter": ["my_synonyms"]
        }
      },
      "filter": {
        "my_synonyms": {
          "type": "dynamic_synonym",
          "synonyms_path": "http://host:port/synonyms.txt",
          "interval": 30
        }
      }
    }
  }
}
```

### Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `synonyms_path` | yes | — | Path relative to the OpenSearch `config/` directory, or an `http://` / `https://` URL |
| `interval` | no | `60` | Reload interval in seconds |
| `expand` | no | `true` | Expand synonyms bidirectionally |
| `lenient` | no | `false` | Ignore errors when parsing individual synonym rules |
| `format` | no | `""` | Synonym format: `""` (Solr) or `"wordnet"` |
| `updateable` | no | `false` | Set to `true` to restrict the filter to search-time analysis only (required for index-time synonym reloading in some configurations) |

### Change detection

| Source | Mechanism |
|---|---|
| Local file | File modification timestamp |
| Remote URL | `Last-Modified` and `ETag` HTTP response headers |

### Examples

```json
"filter": {
  "remote_synonym": {
    "type": "dynamic_synonym",
    "synonyms_path": "http://host:port/synonyms.txt",
    "interval": 30
  },
  "local_synonym": {
    "type": "dynamic_synonym",
    "synonyms_path": "synonyms.txt"
  },
  "graph_synonym": {
    "type": "dynamic_synonym_graph",
    "synonyms_path": "http://host:port/synonyms.txt",
    "interval": 60
  }
}
```

### Synonym file format

Standard Solr format (one rule per line):

```
# equivalents
car, automobile, vehicle

# one-way mapping
laptop => notebook

# multi-word
new york city, nyc, the big apple
```

WordNet format is also supported via `"format": "wordnet"`.

File encoding must be **UTF-8**.

---

## 2. Index-based synonyms (`dynamic-synonym-index`)

Synonym rules are stored as documents inside an OpenSearch index in the **same cluster** where the plugin is installed. The plugin queries the index at startup and then polls for changes at the configured interval.

### Setting up the synonym index

Create the index with the expected mapping (keyword field so rules are stored as-is):

```bash
PUT /my-synonyms
{
  "mappings": {
    "properties": {
      "synonyms": { "type": "keyword" }
    }
  }
}
```

Each document represents one synonym rule:

```bash
PUT /my-synonyms/_doc/rule-cars
{ "synonyms": "car, automobile, vehicle" }

PUT /my-synonyms/_doc/rule-laptop
{ "synonyms": "laptop => notebook" }

PUT /my-synonyms/_doc/rule-nyc
{ "synonyms": "new york city, nyc, the big apple" }
```

### Configuring the filter

```json
PUT /my-index
{
  "settings": {
    "analysis": {
      "analyzer": {
        "my_analyzer": {
          "tokenizer": "whitespace",
          "filter": ["my_index_synonyms"]
        }
      },
      "filter": {
        "my_index_synonyms": {
          "type": "dynamic-synonym-index",
          "synonyms_index": "my-synonyms",
          "interval": 60
        }
      }
    }
  }
}
```

### Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `synonyms_index` | yes | — | Name of the OpenSearch index that stores synonym rules |
| `interval` | no | `60` | How often (in seconds) to poll the index for changes |
| `expand` | no | `true` | Expand synonyms bidirectionally |
| `lenient` | no | `false` | Ignore errors when parsing individual rules |
| `format` | no | `""` | Synonym format: `""` (Solr) or `"wordnet"` |
| `updateable` | no | `false` | Set to `true` to restrict the filter to search-time analysis |

### Change detection

The plugin performs a lightweight query on every monitor tick (`size=1`, sorted by `_seq_no` descending). It reloads only when the highest `_seq_no` or the total document count changes — detecting additions, updates, and deletions without fetching all documents on every check.

### Managing synonyms at runtime

Add, update, or delete rules via the standard OpenSearch Document API:

```bash
# add a rule
PUT /my-synonyms/_doc/rule-1
{ "synonyms": "car, automobile, vehicle" }

# update a rule
POST /my-synonyms/_update/rule-1
{ "doc": { "synonyms": "car, automobile, vehicle, auto" } }

# delete a rule
DELETE /my-synonyms/_doc/rule-1

# list all rules
GET /my-synonyms/_search
{ "query": { "match_all": {} }, "size": 100 }
```

The `SynonymIndexCreator` utility class is also available for programmatic access from custom code:

```java
SynonymIndexCreator creator = new SynonymIndexCreator(client, "my-synonyms");
creator.createIndexIfNotExists();
creator.addSynonym("rule-1", "car, automobile, vehicle");
creator.updateSynonym("rule-1", "car, automobile, vehicle, auto");
creator.deleteSynonym("rule-1");
Map<String, String> all = creator.listSynonyms();
```

---

## License

Apache License 2.0
