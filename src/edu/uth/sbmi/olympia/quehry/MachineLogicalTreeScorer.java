package edu.uth.sbmi.olympia.quehry;


import edu.uth.sbmi.olympia.ml.Classifier;
import edu.uth.sbmi.olympia.ml.FeatureExtractor;
import edu.uth.sbmi.olympia.ml.MulticlassResult;
import edu.uth.sbmi.olympia.ml.feature.Feature;
import edu.uth.sbmi.olympia.ml.feature.StringFeature;
import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Maps;
import edu.uth.sbmi.olympia.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Machine learning-based {@link LogicalTreeScorer}.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public abstract class MachineLogicalTreeScorer extends LogicalTreeScorer {
  private static final Log log = new Log(MachineLogicalTreeScorer.class);
  
  Document question;

  private final Map<String,Integer> goldCounts = new TreeMap<>();
  private final StringFeature<LogicalTree> targetFeature =
      new StringFeature<LogicalTree>("IsTree") {
    @Override
    public String compute(final LogicalTree tree) {
      final LogicalForm logForms = question.getOnlySub(LogicalForm.class);
      final String goldForm = logForms.simpleLogicalForm();
      final String guessForm = LogicalTree.flattenTree(tree.getRoot());
      
      if (guessForm.equals(goldForm)) {
        Maps.increment(goldCounts, "TRUE");
        return "TRUE";
      }
      else {
        Maps.increment(goldCounts, "FALSE");
        return "FALSE";
      }
    }
  };

  /**
   * {@inheritDoc}
   */
  @Override
  public double score(final LogicalTree tree) {
    final MulticlassResult result = getClassifier().classifyMulti(tree);

    final double score;
    if (result.getScore("FALSE") == 0.0) {
      score = result.getScore("TRUE");
    }
    else {
      assert result.getScore("TRUE") == 0.0 : "neither 0.0";
      score = -1 * result.getScore("FALSE");
    }

    log.fine("Score: {0}", score);
    return score;
  }

  /**
   * Returns a {@link Classifier} for {@link LogicalTree}s.
   */
  protected abstract Classifier<LogicalTree> getClassifier();

  /**
   * Returns a target {@link Feature} to use.
   */
  protected StringFeature<LogicalTree> getTargetFeature() {
    return targetFeature;
  }

  /**
   * Trains this <code>MachineLogicalTreeScorer</code> on the given
   * question {@link Document}s.
   */
  public void train(final Map<Document,List<LogicalTree>> logicalTrees) {
    final Classifier<LogicalTree> classifier = getClassifier();
    final FeatureExtractor<LogicalTree> fe = classifier.extractFeatures();
    for (final Document trainQuestion : logicalTrees.keySet()) {
    	if (logicalTrees.get(trainQuestion).isEmpty()) {
    		continue;
    	}
    	this.question = trainQuestion;
      final boolean DBG = log.DBGOnce("Question {0}: {1}",
          trainQuestion.getDocumentID(), trainQuestion.asString(), trainQuestion);
      for (final LogicalTree candidate : logicalTrees.get(trainQuestion)) {
        if (DBG) {
          log.DBG("  Training on Candidate: {0}  [{1}]",
                  LogicalTree.flattenTree(candidate.getRoot()),
                  targetFeature.get(candidate)
          );
        }
        fe.sample(candidate);
      }
    }
    
    fe.finish();

    classifier.train();
  }

  /**
   * Performs a leave-one-out evaluation of the question {@link Document}s.
   */
  public Pair<Double, Map<String, LogicalFormPrediction>> leaveOneOut(
          final Map<Document,List<LogicalTree>> logicalTreesOriginal,
          final Map<Document,List<LogicalTree>> logicalTreesToTrain,
          final Map<Document,List<LogicalTree>> logicalTreesToTest) {
    final Map<String, LogicalFormPrediction> lfPredictions = new LinkedHashMap<>();
    int correct = 0;
    int missing = 0;
    int skipped = 0;
    int correctConceptsWRTCUI = 0;
    int correctConceptsWRTBoundary = 0;
    for (final Document testQuestion : logicalTreesToTest.keySet()) {
      final Document originalQuestion =
          logicalTreesOriginal.keySet().stream()
              .filter(d -> d.getDocumentID().equals(testQuestion.getDocumentID())).findFirst().orElse(null);
      final Document testQuestionToRemoveFromTrain =
              logicalTreesToTrain.keySet().stream()
                      .filter(d -> d.getDocumentID().equals(testQuestion.getDocumentID())).findFirst().orElse(null);
      final Map<Document,List<LogicalTree>> trainQuestions = new HashMap<>(logicalTreesToTrain);

      trainQuestions.remove(testQuestionToRemoveFromTrain);
      train(trainQuestions);
      final LogicalTree predictedLogicalTree;
      final boolean predictedLogicalTreeMatchGold;
      final List<Concept> predictedConcepts;
      final boolean predictedConceptsMatchGoldCUI;
      final boolean predictedConceptsMatchGoldBoundary;
      if (logicalTreesToTest.get(testQuestion).isEmpty()) {
        log.warning("No candidate LogicalTrees for Question: {0}",
            testQuestion.wrap());
        missing++;
        predictedLogicalTree = null;
        predictedLogicalTreeMatchGold = false;
        predictedConcepts = new ArrayList<>();
        predictedConceptsMatchGoldCUI = false;
        predictedConceptsMatchGoldBoundary = false;
      } else {
        this.question = originalQuestion;
        predictedLogicalTreeMatchGold = test(question, logicalTreesToTest.get(testQuestion));
        if (predictedLogicalTreeMatchGold) {
          correct++;
        }

        final Pair<Boolean, Boolean> booleanPair =
            testConcept(question, testQuestion, logicalTreesToTest.get(testQuestion));
        if (booleanPair.getFirst()) {
          correctConceptsWRTCUI++;
        }
        if (booleanPair.getSecond()) {
          correctConceptsWRTBoundary++;
        }

        predictedLogicalTree = getPrediction(logicalTreesToTest.get(testQuestion));
        predictedConcepts = getPredictionForConcepts(logicalTreesToTest.get(testQuestion), testQuestion);
        predictedConceptsMatchGoldCUI = booleanPair.getFirst();
        predictedConceptsMatchGoldBoundary = booleanPair.getSecond();
      }

      LogicalFormPrediction logicalFormPrediction =
          new LogicalFormPrediction(
              testQuestion,
              predictedLogicalTree,
              predictedLogicalTreeMatchGold,
              predictedConcepts,
              predictedConceptsMatchGoldCUI,
              predictedConceptsMatchGoldBoundary);

      lfPredictions.put(testQuestion.getDocumentID(), logicalFormPrediction);
    }
    
    final int total = logicalTreesToTest.size() - missing - skipped;
    final double accuracy = 100.0 * correct / total;
    log.info("Accuracy: {0}%  ({1}/{2})", accuracy, correct, total);
    log.info("Missing: {0}, Skipped: {1}", missing, skipped);

    final int totalConcepts = logicalTreesToTest.size();
    final double accuracyConceptsWRTCUI = 100.0 * correctConceptsWRTCUI / totalConcepts;
    log.info("Accuracy for Concepts w.r.t. CUI match: {0}%  ({1}/{2})",
        accuracyConceptsWRTCUI, correctConceptsWRTCUI, totalConcepts);
    final double accuracyConceptsWRTBoundary = 100.0 * correctConceptsWRTBoundary / totalConcepts;
    log.info("Accuracy for Concepts w.r.t. Boundary match: {0}%  ({1}/{2})",
        accuracyConceptsWRTBoundary, correctConceptsWRTBoundary, totalConcepts);

    return Pair.of(accuracy, lfPredictions);
  }
}
