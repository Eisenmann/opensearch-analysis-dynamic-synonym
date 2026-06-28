/**
 *
 */
package com.eisenmann.opensearch.plugin.synonym.analysis;

import org.apache.lucene.analysis.synonym.SynonymMap;
import java.io.Reader;

/**
 * @author eisenmann
 */
public interface SynonymIndex {

    SynonymMap reloadSynonymMap();

    boolean isNeedReloadSynonymMap();

    Reader getReader();

}