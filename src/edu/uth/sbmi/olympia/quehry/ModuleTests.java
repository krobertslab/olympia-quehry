package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.DependencyTree;
import edu.uth.sbmi.olympia.text.Sentence;
import edu.uth.sbmi.olympia.text.TextComparators;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.TreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Sanity checks for the input/output of major <code>quehry</code> modules
 * (e.g., lexicon matching, lexicon tree generation, logical tree generation).
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class ModuleTests {
	private static final Log log = new Log(ModuleTests.class);
  public static final boolean ACTIVE = true;
  public static final boolean CRASH = false;

  /**
   * Prints out the failure message and (if enabled) exits the program.
   */
  private static void fail(final String message, final Object... params) {
    log.severe(message, params);
    if (CRASH) {
      System.exit(1);
    }
  }

  /**
   * Tests the read-in {@link LexiconEntry}s.
   */
  public static void checkLoadedLexiconEntries(
          final List<LexiconEntry> entries) {
    if (ACTIVE) {
      // We don't want duplicate keys
      {
        final Map<String,Integer> lineNums = new HashMap<>();
        for (final LexiconEntry entry : entries) {
          final String pattern = entry.getPattern();
          final Integer lineNum = entry.getLineNumber();
          assert lineNum != null : "not prepared to test null line numbers";
          final Integer prev = lineNums.put(pattern, lineNum);
          if (prev != null && prev.equals(lineNum) == false) {
            fail("Duplicate Pattern: {0}  Lines {1} and {2}", pattern,
                prev, lineNum);
          }
        }
      }

      // Make sure all the logical operations exist
      {
        final Set<String> checkedFunctions = new HashSet<>();
        checkedFunctions.add("null");
        checkedFunctions.add("lambda.concept");
        checkedFunctions.add("lambda.hascall");
        checkedFunctions.add("lambda.hasrelative");
        for (final LexiconEntry entry : entries) {
          final String op = entry.getLogicalForm();
          if (checkedFunctions.add(op)) {
            if (LogicalTreeFilteringRules.getTypes(op, false) == null) {
              fail("Logical operation in lexicon has no specified " +
                  "input/output types: {0}", op);
            }
          }
        }
      }
    }
  }

  /**
   * Tests the generated {@link LexiconMatchTree}s.
   */
  public static void checkGeneratedLexiconMatchTrees(
          final List<LexiconMatchTree> lexiconMatchTrees) {
    if (ACTIVE) {
      // Every Token in the Sentence should have a LexiconMatch, except the
      // final question mark, and not be in more than one LexiconMatch
      {
        for (final LexiconMatchTree lexiconMatchTree : lexiconMatchTrees) {
          final Map<Token,LexiconMatch> tokenMap = new LinkedHashMap<>();
          for (final LexiconMatch lexiconMatch :
               lexiconMatchTree.getLexiconMatches()) {
            for (final Token token : lexiconMatch.getTokens()) {
              final LexiconMatch prev = tokenMap.put(token, lexiconMatch);
              if (prev != null) {
                fail("Token in multiple LexiconMatches: {0}\n" +
                    "  1: {1} -> {2}\n  2: {3} -> {4}", token,
                    prev.getEntry(), prev.getTokens(),
                    lexiconMatch.getEntry(), lexiconMatch.getTokens());
              }
            }
          }
          final DependencyTree depTree = lexiconMatchTree.getDependencyTree();
          final Sentence sentence = depTree.getSentence();
          for (final Token token : sentence.getTokens()) {
            if (token == sentence.getLastToken() &&
                (token.asRawString().equals("?") || token.asRawString().equals("."))) {
              continue;
            }
            if (tokenMap.containsKey(token) == false) {
              log.severe("Token not matched: {0}", token);
            }
          }
        }
      }

      // No duplicate LexiconMatchTrees
      final Set<String> uniqueTrees = new HashSet<>();
      for (final LexiconMatchTree lexiconMatchTree : lexiconMatchTrees) {
        final Set<String> treeKey = new TreeSet<>();
        for (final LexiconMatch match : lexiconMatchTree.getLexiconMatches()) {
          final LexiconEntry entry = match.getEntry();
          final List<Token> tokens = new ArrayList<>(match.getTokens());
          Collections.sort(tokens, TextComparators.startToken());
          final String key = entry.toString() + "|" + tokens.toString();
          if (treeKey.add(key) == false) {
            fail("Duplicate LexiconMatches: {0}", key);
          }
        }
        if (uniqueTrees.add(treeKey.toString()) == false) {
          fail("Duplicate LexiconMatchTrees");
        }
      }
    }
  }

  /**
   * Tests the generated {@link LogicalTree}s.
   */
  public static void checkGeneratedLogicalTrees(
          final List<LogicalTree> logicalTrees) {
    if (ACTIVE) {
      // Every LogicalTree must have all the non-null logical operations of
      // its originating LexiconMatchTree
      {
        for (final LogicalTree logicalTree : logicalTrees) {
          final Set<String> logicalTreeOps = new TreeSet<>();
          final Set<String> lexiconMatchTreeOps = new TreeSet<>();
          for (final TreeNode<String> node : logicalTree.getNodes()) {
            logicalTreeOps.add(node.getItem());
          }
          for (final LexiconMatch lexiconMatch :
               logicalTree.getLexiconMatchTree().getLexiconMatches()) {
            //added for lambda root
            if(lexiconMatch.getEntry().getLogicalForm().equals("lambda.concept")){
              lexiconMatchTreeOps.add("lambda");
              lexiconMatchTreeOps.add("concept");
              continue;
            }
            lexiconMatchTreeOps.add(lexiconMatch.getEntry().getLogicalForm());
          }
          lexiconMatchTreeOps.remove("null");
          

          if (logicalTreeOps.equals(lexiconMatchTreeOps) == false) {
            fail("LogicalTree contains different operations than " +
                "LexiconMatchTree:\n  LogicalTree:      {0}\n" +
                "LexiconMatchTree: {1}", logicalTreeOps, lexiconMatchTreeOps);
          }
        }
      }
    }
  }

}
