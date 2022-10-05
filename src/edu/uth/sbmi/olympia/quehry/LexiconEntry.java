package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Pair;
import edu.uth.sbmi.olympia.util.Triple;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An entry in the semantic parsing lexicon.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class LexiconEntry {
  private static final Log log = new Log(LexiconEntry.class);

  private final String id;
  private final String pattern;
  private final Set<String> nodes = new LinkedHashSet<>();
  private final List<Triple<String,String,String>> edges = new ArrayList<>();
  private final String logicalForm;
  private final Integer lineNum;

  /**
   * Creates a new <code>LexiconEntry</code> using the given <code>String</code>
   * representation for the text to match, and the <var>logicalForm</var> for
   * the entry value.
   */
  public LexiconEntry(final String id,
                      final String pattern,
                      final String logicalForm,
                      final Integer lineNum) {
    this.id = id;
    this.pattern = pattern;
	  if (!pattern.contains("(")) { //for logical form like "what \t  latest"
		  this.nodes.add(pattern);
	  }
	  else {
		  parse(pattern);
	  }
	  this.logicalForm = logicalForm;
    this.lineNum = lineNum;
  }

  /**
   * Parses the lexicon entry pattern, creating the nodes and edges.
   */
  private void parse(final String pattern) {
    ArrayList<String> nodeList = new ArrayList<>();
    
    String[] splitPattern = pattern.split("\\(");
    String headWord = splitPattern[0];
    nodeList.add(headWord);
    for(int i = 1; i < splitPattern.length; i++){
      String restPattern = splitPattern[i];
      if(restPattern.contains(", ")){
        for(String s: restPattern.split(", ")){
          final Pair<String, String> depRelPhrasePair = parseSubPattern(s);
          final String dependencyRelation = depRelPhrasePair.getFirst();
          final String phrase = depRelPhrasePair.getSecond();
          nodeList.add(phrase);
          this.edges.add(Triple.of(headWord, dependencyRelation, phrase));// adding edges one by one
        }
      }
      else{
        if(restPattern.endsWith(")")){
          restPattern = restPattern.substring(0, restPattern.indexOf(")"));
        }
        final Pair<String, String> depRelPhrasePair = parseSubPattern(restPattern);
        final String dependencyRelation = depRelPhrasePair.getFirst();
        final String phrase = depRelPhrasePair.getSecond();
        nodeList.add(phrase);
        this.edges.add(Triple.of(headWord, dependencyRelation, phrase));// adding edges one by one
      }
      headWord = restPattern.substring(restPattern.lastIndexOf(":")+1);
    }
    this.nodes.addAll(nodeList); //adding all the nodes together
  }

  private Pair<String, String> parseSubPattern(final String subPattern) {
    final String subPatternTrimmed;
    if(subPattern.endsWith(")")){
      subPatternTrimmed = subPattern.substring(0, subPattern.indexOf(")"));
    } else {
      subPatternTrimmed = subPattern;
    }
    String[] x = subPatternTrimmed.split(":");
    final String dependencyRelation;
    final String phrase;
    if(x.length == 2) {
      dependencyRelation = x[0];
      phrase = x[1];
    } else if(x.length == 3) {
      assert x[0].startsWith("\"") : "invalid pattern: " + pattern;
      assert x[1].endsWith("\"") : "invalid pattern: " + pattern;
      dependencyRelation = x[0].substring(1) + ":" + x[1].substring(0, x[1].length() - 1);
      phrase = x[2];
    } else {
      throw new AssertionError("should be just 2 items for each relation (found " + x.length + "): " + pattern);
    }

    return Pair.of(dependencyRelation, phrase);
  }

  /**
   * Returns the ID of this <code>LexiconEntry</code>.
   */
  public String getID() {
    return id;
  }

  /**
   * Returns the pattern for this <code>LexiconEntry</code>.
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * Returns the nodes for this <code>LexiconEntry</code>.
   */
  public Set<String> getNodes() {
    return nodes;
  }

  /**
   * Returns the edges for this <code>LexiconEntry</code>.
   */
  public List<Triple<String,String,String>> getEdges() {
    return edges;
  }

  /**
   * Returns the logical form for this <code>LexiconEntry</code>.
   */
  public String getLogicalForm() {
    return logicalForm;
  }

  /**
   * Returns the line of the lexicon file this <code>LexiconEntry</code> is
   * found on, or <code>null</code> if unknown.
   */
  public Integer getLineNumber() {
    return lineNum;
  }

  /**
   * Returns a <code>String</code> representation of this <code>LexiconEntry</code>.
   */
  @Override
  public String toString() {
    return getPattern() + " -> " + getLogicalForm();
  }

}
