/**
 *
 */
package com.eisenmann.opensearch.plugin.synonym.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.opensearch.analysis.common.OpenSearchSolrSynonymParser;
import org.opensearch.analysis.common.OpenSearchWordnetSynonymParser;
import org.opensearch.env.Environment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;


/**
 * @author eisenmann
 */
public class DynamicSynonymIndex implements SynonymIndex {

    private static final Integer RESULT_LIMIT = 10000;
    private static final String RELOAD_SYNONYM_MAP_FIELD = "lastModified";    

    private static final Logger logger = LogManager.getLogger("dynamic-synonym");    

    private String format;

    private boolean expand;

    private boolean lenient;

    private Analyzer analyzer;

    private Environment env;

    private static IndexSearcher indexSearcher;

    private static IndexReader indexReader;

     /**
     * Dynamic synonym index
     */
    private String location;    

    DynamicSynonymIndex(Environment env, Analyzer analyzer,
                      boolean expand, boolean lenient, String format, String location) {
        this.analyzer = analyzer;
        this.expand = expand;
        this.lenient = lenient;
        this.format = format;
        this.env = env;
        this.location = location;

        isNeedReloadSynonymMap();
    }


    @Override
    public SynonymMap reloadSynonymMap() {
          Reader rulesReader = null;
        try {
            logger.debug("start reload remote synonym from {}.", location);
            rulesReader = getReader();
            SynonymMap.Builder parser;

            parser = getSynonymParser(rulesReader, format, expand, lenient, analyzer);
            return parser.build();
        } catch (Exception e) {
            logger.error("reload remote synonym {} error!", location, e);
            throw new IllegalArgumentException(
                    "could not reload remote synonyms file to build synonyms",
                    e);
        } finally {
            if (rulesReader != null) {
                try {
                    rulesReader.close();
                } catch (Exception e) {
                    logger.error("failed to close rulesReader", e);
                }
            }
        }
    }

    @Override
    public boolean isNeedReloadSynonymMap() {
        logger.info("==== isNeedReloadSynonymMap ====");
        try {
                      
                    Term t = new Term(RELOAD_SYNONYM_MAP_FIELD);
                   
                    Query query = new TermQuery(t);
                    TopDocs topDocs = indexSearcher.search(query, 1);
         
                     if (topDocs.scoreDocs!=null && topDocs.scoreDocs.length>0)
                     {
                        String isNeedReload = indexReader.storedFields().document(topDocs.scoreDocs[0].doc).getField("isNeedReload").stringValue();
                         if(isNeedReload != null && !isNeedReload.isEmpty() && isNeedReload=="y")
                         {
                           return true;
                         }
                     }
                     
                     return false;
                 }
                 catch (IOException e)
                 {
                     logger.error("failed to get synonyms from index", e);
                    return false;
                 }
    }

    @Override
    public Reader getReader() {
        Reader reader;
        ArrayList<String> synonyms = null;
        try {
            synonyms = getSynonymsFromIndex();
            if (synonyms != null) {
                 StringBuilder sb = new StringBuilder();

                for (String line : synonyms) {
                    logger.debug("reload in index synonym: {}", line);
                    sb.append(line)
                            .append(System.getProperty("line.separator"));
                }
                reader = new StringReader(sb.toString());
            } else reader = new StringReader("");
        } catch (Exception e) {
            logger.error("get index synonym reader {} error!", location, e);
            reader = new StringReader("1=>1");
        }
        return reader;
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

    static ArrayList<String> getSynonymsFromIndex()
    {    
        try {   
           Query query = new MatchAllDocsQuery();
           TopDocs topDocs = indexSearcher.search(query, RESULT_LIMIT);

           ArrayList<String> synonyms = new ArrayList<String>();

            for (ScoreDoc topDoc : topDocs.scoreDocs) 
            {            
               String text = indexReader.storedFields().document(topDoc.doc).getField("text").stringValue();
                if(text != null && !text.isEmpty())
                {
                 synonyms.add(text);
                }
            }
            return synonyms;             
        }
        catch (IOException e)
        {
            logger.error("failed to get synonyms from index", e);
           return new ArrayList<String>();
        }

    }    
}