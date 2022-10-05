package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Place;
import edu.uth.sbmi.olympia.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads in the lexicon.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class ReadLexicon {
  private static final Log log = new Log(ReadLexicon.class);
  
  /**
   * Read the lexicon and returns a <code>List</code> of {@link LexiconEntry}s.
   */
  
  public static List<LexiconEntry> parseLexicon(final Place file) {
    final List<LexiconEntry> lexEntries = new ArrayList<>();
    int lineNum = 0;
    try {
      for (final String line : file.readLines()) {
        lineNum++;
        assert line.equals(line.trim()) :
            "line " + lineNum + " has whitespace: " + line;
        if (line.startsWith("#")) {
          continue;
        }
        final List<String> split = Strings.split(line, "\t");
        assert split.size() == 2 :
            "should be just 2 fields (found " + split.size() + "): " + line;
        final String phrase = split.get(0);
        final String ops = split.get(1);
        int opNum = 0;
        for (final String op : Strings.split(ops, "/")) {
          opNum++;
          final String id = lineNum + ":" + opNum;
          lexEntries.add(new LexiconEntry(id, phrase, op, lineNum));
        }
      }
      log.info("Loaded {0} lexicon entries", lexEntries.size());
    }
    catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    ModuleTests.checkLoadedLexiconEntries(lexEntries);
    return lexEntries;
  }

}
