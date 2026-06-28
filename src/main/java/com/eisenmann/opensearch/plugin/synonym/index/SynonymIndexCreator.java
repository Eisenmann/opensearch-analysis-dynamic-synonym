package com.eisenmann.opensearch.plugin.synonym.index;

import org.opensearch.common.settings.Settings;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.FSDirectory;


public class SynonymIndexCreator {
    
    private final String indexName;
    private final IndexWriter writer;
    private final String indexDirectoryPath;

    public SynonymIndexCreator(Settings settings) throws IOException
    {
        indexName = settings.get("synonyms_index");
        indexDirectoryPath = settings.get("synonyms_index_path", indexName);

        FSDirectory indexDirectory =
         FSDirectory.open(Paths.get(indexDirectoryPath));

      //create the indexer
      writer = new IndexWriter(indexDirectory, new IndexWriterConfig(new StandardAnalyzer())
                .setOpenMode(OpenMode.CREATE));
      indexDocument();
       writer.close();         
    }    


   private void indexDocument() throws IOException {
    Document document = getDocument();
    writer.addDocument(document);
 }

 public void close() throws CorruptIndexException, IOException {
    writer.close();
 }

 private Document getDocument() throws IOException {
    Document document = new Document(); 
    
    TextField synonymsField = new TextField("synonyms", 
       "synonym, equivalent",     
       Field.Store.YES);     
 
    document.add(synonymsField);

    return document;
 }   

}
