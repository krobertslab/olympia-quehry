package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.DependencyTree;
import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.text.Sentence;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tree-structure indicating the {@link LexiconMatch}es for a
 * {@link DependencyTree}.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class LexiconMatchTree {
  private static final Log log = new Log(LexiconMatchTree.class);

  private final DependencyTree dependencyTree;
  private final List<LexiconMatch> matches = new ArrayList<>();

  /**
   * Creates a new <code>LexiconMatchTree</code> based on the given
   * {@link DependencyTree}.
   */
  public LexiconMatchTree(final DependencyTree dependencyTree) {
    this.dependencyTree = dependencyTree;
  }

  /**
   * Returns the {@link DependencyTree} for this <code>LexiconMatchTree</code>.
   */
  public DependencyTree getDependencyTree() {
    return dependencyTree;
  }

  /**
   * Returns the ID of the {@link Document} this <code>LexiconMatchTree</code>
   * is associated with.
   */
  public String getDocumentID() {
    return dependencyTree.getDocumentID();
  }

  /**
   * Adds the {@link LexiconMatch} to this <code>LexiconMatchTree</code>.
   */
  public void addLexiconMatch(final LexiconMatch match) {
    matches.add(match);
  }
  
  /**
   * Returns the {@link LexiconMatch}es for this <code>LexiconMatchTree</code>.
   */
  public List<LexiconMatch> getLexiconMatches() {
    return matches;
  }

  /**
   * Indicates if every node of the dependency tree has a {@link LexiconMatch}.
   */
  public boolean isFullyMatched() {
    final Sentence sentence = dependencyTree.getSentence();
    final Set<Token> tokens = new HashSet<>(sentence.getTokens());
    for (final LexiconMatch match : matches) {
      for (final Token token : match.getTokens()) {
        final boolean removed = tokens.remove(token);
        if (!removed) {
          log.severe("Token in more than one LexiconMatch");
          System.exit(1);
        }
      }
    }
    if (sentence.getLastToken().asRawString().equals("?") || sentence.getLastToken().asRawString().equals(".")) {
      tokens.remove(sentence.getLastToken());
    }
    if (tokens.isEmpty() == false) {
      log.severe("Remaining Tokens: {0}", tokens);
      log.severe("  Document: {0}", sentence.getDocumentID());
      return true;
    }
    return tokens.isEmpty();
  }

  /**
   * Returns a sorted <code>List</code> of all the lexicon rules used to
   * generate this <code>LexiconMatchTree</code>.
   */
  public List<String> getMatchedRules() {
    final List<String> rules = new ArrayList<>();
    for (final LexiconMatch match : matches) {
      final LexiconEntry entry = match.getEntry();
      rules.add(entry.getPattern() + " -> " + entry.getLogicalForm());
    }
    Collections.sort(rules);
    return rules;
  }

}
