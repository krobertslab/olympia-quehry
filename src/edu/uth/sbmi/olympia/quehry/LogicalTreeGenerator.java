package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the {@link LogicalTree}s from a {@link LexiconMatchTree}.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public abstract class LogicalTreeGenerator {
  private static final Log log = new Log(LogicalTreeGenerator.class);

  public String DBG_gold = null;

  /**
   * Generates the {@link LogicalTree}s from a <code>Collection</code> of
   * {@link LexiconMatchTree}. This is the preferred method since it filters
   * duplicates.
   */
  public List<LogicalTree> getLogicalTrees(
          final Collection<LexiconMatchTree> lexiconMatchTrees) {
    log.fine("Generating LogicalTrees from {0} LexiconMatchTrees",
        lexiconMatchTrees.size());
    final List<LogicalTree> logicalTrees = new ArrayList<>();
    final Set<String> uniqueTrees = new HashSet<>();
    
    for (final LexiconMatchTree lexiconMatchTree : lexiconMatchTrees) {
      for (final LogicalTree logicalTree : getLogicalTrees(lexiconMatchTree)) {
        if (uniqueTrees.add(LogicalTree.flattenTree(logicalTree.getRoot()))) {
          logicalTrees.add(logicalTree);
        }
      }
    }

    return logicalTrees;
  }

  /**
   * Generates the {@link LogicalTree}s from a single {@link LexiconMatchTree}.
   */
  public abstract List<LogicalTree> getLogicalTrees(
          LexiconMatchTree lexiconMatchTree);
   
  /**
   * Returns the logical operations found in the given {@link LexiconMatchTree}.
   */
  List<String> getLogicalOps(final LexiconMatchTree lexMatchTree) {
    final List<String> logicalOps = new ArrayList<>();
    for (final LexiconMatch lexMatch : lexMatchTree.getLexiconMatches()) {
      final String op = lexMatch.getEntry().getLogicalForm();
      if (op.equals("null") == false) {
        logicalOps.add(op);
      }
    }
    return logicalOps;
  }

}
