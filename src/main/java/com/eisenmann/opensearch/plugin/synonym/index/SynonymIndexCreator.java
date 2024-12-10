package com.eisenmann.opensearch.plugin.synonym.index;

import org.opensearch.common.settings.Settings;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.LogMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;


public class SynonymIndexCreator {
    
    private final String indexName;
    private final IndexWriter writer;
    private final String indexDirectoryPath;

    public SynonymIndexCreator(Settings settings)
    {
        indexName = settings.get("synonyms_index");
          //this directory will contain the indexes

      

      Directory indexDirectory = 
         FSDirectory.open(indexDirectoryPath);

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
