package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.util.Comparators;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Maps;
import edu.uth.sbmi.olympia.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract class for object that assigns a score to a {@link LogicalTree}.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public abstract class LogicalTreeScorer {
  private static final Log log = new Log(LogicalTreeScorer.class);

  /**
   * Scores the given {@link LogicalTree}.
   */
  public abstract double score(LogicalTree tree);

  /**
   * Use this <code>LogicalTreeScorer</code> to return a predicted {@link LogicalTree}
   * from the candidate {@link LogicalTree}s for the given question {@link Document}.
   * @return the predicted {@link LogicalTree} for a given question {@link Document}.
   */
  public LogicalTree getPrediction(List<LogicalTree> logTrees) {
    final List<Pair<LogicalTree,Double>> scores = new ArrayList<>();
    for (final LogicalTree candidate : logTrees) {
      scores.add(Pair.of(candidate, score(candidate)));
    }
    Collections.sort(scores, Comparators.doubleWeightedPair().reverse());
    final LogicalTree guessTree = scores.get(0).getFirst();

    return guessTree;
  }

  /**
   * Use this <code>LogicalTreeScorer</code> to return a predicted {@link LogicalTree}
   * from the candidate {@link LogicalTree}s and their corresponding scores for the given question {@link Document}.
   * @return the predicted {@link LogicalTree} and all the scores for a given question {@link Document}.
   */
  public Pair<LogicalTree, List<Pair<LogicalTree, Double>>> getPredictionAndScores(List<LogicalTree> logTrees) {
    final List<Pair<LogicalTree,Double>> scores = new ArrayList<>();
    for (final LogicalTree candidate : logTrees) {
      scores.add(Pair.of(candidate, score(candidate)));
    }
    Collections.sort(scores, Comparators.doubleWeightedPair().reverse());
    final LogicalTree guessTree = scores.get(0).getFirst();

    return Pair.of(guessTree, scores);
  }

  /**
   * Use this <code>LogicalTreeScorer</code> to return predicted {@link Concept}s
   * from the candidate {@link LogicalTree}s for the given question {@link Document}.
   * @return the predicted {@link Concept}s for a given question {@link Document}.
   */
  public List<Concept> getPredictionForConcepts(List<LogicalTree> logTrees, Document questionAutoAnnotated) {
    final LogicalTree guessTree = getPrediction(logTrees);
    return LogicalTree.getMedicalConcepts(guessTree, questionAutoAnnotated);
  }

  /**
   * Tests the performance of this <code>LogicalTreeScorer</code> at ranking
   * candidate {@link LogicalTree}s for the given question {@link Document}s.
   */
  public void test(final Map<Document,List<LogicalTree>> logicalTrees) {
    int correct = 0;
    for (final Document question : logicalTrees.keySet()) {
      if (test(question, logicalTrees.get(question))) {
        correct++;
      }
    }
    final int total = logicalTrees.size();
    log.info("Accuracy: {0}%  ({1}/{2})",
        (100.0 * correct)/total, correct, total);
  }

  /**
   * Tests the performance of this <code>LogicalTreeScorer</code> at ranking
   * candidate {@link LogicalTree}s for the given question {@link Document}.
   * @return <code>true</code> if the question {@link Document} is classified correctly.
   */
  public boolean test(final Document question, List<LogicalTree> logTrees) {
    final LogicalForm logForms = question.getOnlySub(LogicalForm.class);
    final String simpleLogicalForm = logForms.simpleLogicalForm();
    if (simpleLogicalForm == null) {
      log.severe("No Gold LogicalForm : {0} ", question.wrap());
      return false;
    }

    final Pair<LogicalTree, List<Pair<LogicalTree, Double>>> predictionAndScores = getPredictionAndScores(logTrees);
    LogicalTree guessTree = predictionAndScores.getFirst();
    List<Pair<LogicalTree, Double>> scores = predictionAndScores.getSecond();

    final String guess = LogicalTree.flattenTree(guessTree.getRoot());
    final String gold = simpleLogicalForm;

    if (log.fine() && guess.equals(gold) == false) {
      log.fine("Question : {0}", question.toString());
      log.fine("  Gold logical form:\n  {0}",
          gold);
      log.fine("  Top 5 [of {0}] Logical Forms:", logTrees.size());
      log.fine("    1. {0}   [{1}] --- {2}", guess, scores.get(0).getSecond(),
          gold.equals(guess) ? "CORRECT" : "WRONG");
      boolean DBG_has_gold = false;
      if (guess.equals(gold)) {
        DBG_has_gold = true;
        Maps.increment(DBG_gold_rank, 0);
      }
      for (int i = 1; i < 5 && i < scores.size(); i++) {
        final String form = LogicalTree.flattenTree(
            scores.get(i).getFirst().getRoot());
        final double score = scores.get(i).getSecond();
        if (i < 5 || form.equals(gold)) {
          log.fine("    {0}. {1}   [{2}]{3}", i+1, form, score,
              form.equals(gold) ? "    <-- GOLD" : "");
        }
        if (form.equals(gold)) {
          DBG_has_gold = true;
          Maps.increment(DBG_gold_rank, i);
        }
      }
      if (!DBG_has_gold) {
        Maps.increment(DBG_gold_rank, -1);
      }
    }
    return guess.equals(gold);
  }
  private final Map<Integer,Integer> DBG_gold_rank = new TreeMap<>();

  /**
   * Tests the performance of this <code>LogicalTreeScorer</code> at predicting
   * the correct {@link Concept} for the given {@link Question}.
   * @return {@link Pair} of two {@link Boolean}s with both <code>true</code>s
   * if the {@link Question} is classified correctly w.r.t. CUI as well as boundary match.
   */
  public Pair<Boolean, Boolean> testConcept(
      final Document question, final Document questionAutoAnnotated, List<LogicalTree> logTrees) {
    final List<Concept> goldConcepts =
        question.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() != null).collect(Collectors.toList());
    final Map<String, List<Concept>> goldBoundariesToConcepts = goldConcepts.stream()
        .collect(Collectors.groupingBy(
            (Concept concept) -> concept.getStartTokenOffset() + "," + concept.getEndTokenOffset(),
            Collectors.mapping(
                Function.identity(), Collectors.toList()
            )));

    List<Concept> guessConcepts = getPredictionForConcepts(logTrees, questionAutoAnnotated);

    final List<Concept> guessConceptsWithNonNullCUI =
        guessConcepts.stream().filter(c -> c.getCUI() != null).collect(Collectors.toList());

    boolean matchCUIForAll = true;
    boolean matchBoundaryForAll = true;
    if (goldBoundariesToConcepts.size() == guessConceptsWithNonNullCUI.size()) {
      for (final List<Concept> goldCongruentConcepts: goldBoundariesToConcepts.values()) {
        // To check the concept boundary
        Concept goldConceptForBoundary = goldCongruentConcepts.get(0);
        // To check if the guessed concept matches any of the gold CUIs
        final Set<String> goldCUIs = goldCongruentConcepts.stream().map(Concept::getCUI).collect(Collectors.toSet());
        boolean matchCUI = false;
        boolean matchBoundary = false;
        for (final Concept guessConcept: guessConceptsWithNonNullCUI) {
          if (goldCUIs.contains(guessConcept.getCUI())) {
            // A match at CUI level
            matchCUI = true;
          }
          if (guessConcept.isCongruent(goldConceptForBoundary, true)) {
            // A match at boundary level
            matchBoundary = true;
          }
        }

        if (!matchCUI) {
          matchCUIForAll = false;
        }
        if (!matchBoundary) {
          matchBoundaryForAll = false;
        }
      }
    } else {
      matchCUIForAll = false;
      matchBoundaryForAll = false;
    }

    if (!matchCUIForAll || !matchBoundaryForAll) {
      // A mismatch
      log.DBG("Question : {0}", question.toString());
      log.DBG("  Gold:  {0}",
          goldConcepts.stream().map(c -> Arrays.asList(c, c.getType(), c.getCUI())).collect(Collectors.toList()));
      log.DBG("  Guess: {0}",
          guessConceptsWithNonNullCUI.stream().map(c -> Arrays.asList(c, c.getType(), c.getCUI()))
              .collect(Collectors.toList()));
      log.DBG("--> CUI matched: {0}, Boundary matched: {1}",
          matchCUIForAll ? "TRUE" : "FALSE", matchBoundaryForAll ? "TRUE" : "FALSE");
    }

    return Pair.of(matchCUIForAll, matchBoundaryForAll);
  }
}
