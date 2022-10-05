package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Maps;
import edu.uth.sbmi.olympia.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Filtering rules to prune candidate {@link LexiconMatchTree}s.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public class LexiconMatchTreeFilteringRules {
  private static final Log log = new Log(LexiconMatchTreeFilteringRules.class);
  
  /**
   * Checks the logical operations in the {@link LexiconMatchTree} to determine
   * whether any valid {@link LogicalTree} is even possible.
   */
  public boolean hasTypeMismatch(final LexiconMatchTree tree) {
    if (log.fine()) {
      log.fine("Testing Type Match for Tree:\n{0}", tree.getMatchedRules());
    }

    final List<String> functions = new ArrayList<>();
    for (final LexiconMatch match : tree.getLexiconMatches()) {
      final String function = match.getEntry().getLogicalForm();
      if (function.equals("null")) {
        continue;
      }
      else if (function.equals("lambda.concept")) {
        functions.add("lambda");
        functions.add("has_concept");
      }
      else if (function.equals("lambda.hascall")) {
        functions.add("lambda");
        functions.add("has_call");
      }
      else if (function.equals("lambda.hasrelative")) {
        functions.add("lambda");
        functions.add("is_relative");
      }
      else {
        functions.add(function);
      }
    }

    final Map<String,Integer> lhs = new TreeMap<>();
    final Map<String,Integer> rhs = new TreeMap<>();
    final Set<String> ops = new TreeSet<>();
    int eventInputAndNonTrueFalseOutput = 0;
    for (final String function : functions) {
      final Pair<String,String> types =
          LogicalTreeFilteringRules.getTypes(function, true);
      final String input = types.getFirst();
      final String output = types.getSecond();
      if (input.equals("Event") && !output.equals("TrueFalse")) {
        eventInputAndNonTrueFalseOutput++;
      }
      // Leaf nodes can take NULL arguments
      if (!input.equals("NULL")) {
        Maps.increment(lhs, input);
        ops.add(input);
      }
      Maps.increment(rhs, output);
      ops.add(output);
    }
    log.fine("LHS: {0}", lhs);
    log.fine("RHS: {0}", rhs);

    final List<String> missingInput = new ArrayList<>();
    final List<String> missingOutput = new ArrayList<>();
    final List<String> countMismatch = new ArrayList<>();
    for (final String op : ops) {
      final Integer lCount = lhs.get(op);
      final Integer rCount = rhs.get(op);
      assert lCount != null || rCount != null : op;
      if (lCount == null) {
        for (int i = 0; i < rCount; i++) {
          missingInput.add(op);
        }
      }
      else if (rCount == null) {
        int actualMissingCount = !op.equals("Event") ? lCount : eventInputAndNonTrueFalseOutput;
        for (int i = 0; i < actualMissingCount; i++) {
          missingOutput.add(op);
        }
      }
      else if (lCount != rCount) {
        if (!op.equals("TrueFalse")) {
          countMismatch.add(op);
        }
      }
    }

    if (missingInput.size() > 1) {
      log.fine("Missing Input: {0}", missingInput);
      return true;
    }
    if (missingOutput.size() > 0) {
      log.fine("Missing Output: {0}", missingOutput);
      return true;
    }
    if (countMismatch.size() > 0) {
      log.fine("Count Mismatch: {0}", countMismatch);
      return true;
    }

    log.fine("ACCEPT");
    return false;
  }


}
