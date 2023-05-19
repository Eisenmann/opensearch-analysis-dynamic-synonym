# opensearch-analysis-dynamic-synonym
Dynamic synonyms for Opensearch. The dynamic synonym plugin adds a synonym token filter that reloads the synonym file(local file or remote file) at given intervals (default 60s).

Plugin <a href="https://github.com/bells/elasticsearch-analysis-dynamic-synonym">elasticsearch-analysis-dynamic-synonym</a> adapted to OpenSearch.

<h1>Installation</h1>
<ol>
  <li>
    <code> mvn package </code>
  </li>
  <li>
 copy and unzip <code>target/releases/opensearch-analysis-dynamic-synonym-{version}.zip </code> to  <code>your-opensearch-root/plugins/dynamic-synonym </code>
  </li>
  </ol>
<h1>Example</h1>
<code>
{
    "index" : {
        "analysis" : {
            "analyzer" : {
                "synonym" : {
                    "tokenizer" : "whitespace",
                    "filter" : ["remote_synonym"]
                }
            },
            "filter" : {
                "remote_synonym" : {
                    "type" : "dynamic_synonym",
                    "synonyms_path" : "http://host:port/synonym.txt",
                    "interval": 30
                },
                "local_synonym" : {
                    "type" : "dynamic_synonym",
                    "synonyms_path" : "synonym.txt"
                },
                "synonym_graph" : {
                    "type" : "dynamic_synonym_graph",
                    "synonyms_path" : "http://host:port/synonym.txt"
                }
            }
        }
    }
}
  </code>
<h1>Configuration</h1>
<code>type</code>: <code>dynamic_synonym</code> or <code>dynamic_synonym_graph</code>, mandatory

<code>synonyms_path</code>: A file path relative to the OpenSearch config file or an URL, mandatory

<code>interval</code>: Refresh interval in seconds for the synonym file, default: <code>60</code>, optional

<code>ignore_case</code>: Ignore case in synonyms file, <code>default: false</code>, optional

<code>expand</code>: Expand, <code>default: true</code>, optional

<code>lenient</code>: Lenient on exception thrown when importing a synonym, default: false, optional

<code>format</code>: Synonym file format, <code>default: ''</code>, optional. For WordNet structure this can be set to <code>'wordnet'</code>

<h1>Update mechanism</h1>
Local files: Determined by modification time of the file, if it has changed the synonyms will
Remote files: Reads out the <code>Last-Modified</code> and <code>ETag</code> http header. If one of these changes, the synonyms will be reloaded.

<strong>Note:</strong> File encoding should be an utf-8 text file.
