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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Machine learning-based {@link TimeFrameScorer}.
 */
public abstract class MachineTimeFrameScorer extends TimeFrameScorer {
  private static final Log log = new Log(MachineTimeFrameScorer.class);
  
  Document question;

  private final Map<String,Integer> goldCounts = new TreeMap<>();
  private final StringFeature<TimeFrame> targetFeature =
      new StringFeature<TimeFrame>("IsTimeFrame") {
    @Override
    public String compute(final TimeFrame timeFrame) {
      final TimeFrame goldTimeFrame = question.getOnlySub(TimeFrame.class);
      final String goldValue = goldTimeFrame.getValue();
      final String guessValue = timeFrame.getValue();
      
      if (guessValue.equals(goldValue)) {
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
  public double score(final TimeFrame timeFrame) {
    final MulticlassResult result = getClassifier().classifyMulti(timeFrame);
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
   * Returns a {@link Classifier} for {@link TimeFrame}s.
   */
  protected abstract Classifier<TimeFrame> getClassifier();

  /**
   * Returns a target {@link Feature} to use.
   */
  protected StringFeature<TimeFrame> getTargetFeature() {
    return targetFeature;
  }

  /**
   * Trains this <code>MachineTimeFrameScorer</code> on the given
   * {@link Document}s.
   */
  public void train(final Map<Document,List<TimeFrame>> timeFramesMap) {
    final Classifier<TimeFrame> classifier = getClassifier();
    final FeatureExtractor<TimeFrame> fe = classifier.extractFeatures();
    for (final Document trainQuestion : timeFramesMap.keySet()) {
    	if (timeFramesMap.get(trainQuestion).isEmpty()) {
    		continue;
    	}
    	this.question = trainQuestion;
      final boolean DBG = log.DBGOnce("Question {0}: {1}", trainQuestion.getDocumentID(), trainQuestion.asString());
      for (final TimeFrame candidate : timeFramesMap.get(trainQuestion)) {
        if (DBG) log.DBG("  Training on Candidate: {0}  [{1}]",  candidate.getValue(), targetFeature.get(candidate));
        fe.sample(candidate);
      }
    }
    
    fe.finish();
    classifier.train();
  }

  /**
   * Performs a leave-one-out evaluation of the {@link Document}s.
   */
  public Pair<Double, Map<String, Pair<TimeFrame, Boolean>>> leaveOneOut(
      final Map<Document, List<TimeFrame>> timeFramesMap) {
    log.DBG("Gold Counts: {0}", goldCounts);
    final Map<String, Pair<TimeFrame, Boolean>> timeFramePredictions = new HashMap<>();
    int correct = 0;
    int missing = 0;
    int skipped = 0;
    for (final Document testQuestion : timeFramesMap.keySet()) {
      final Map<Document, List<TimeFrame>> trainQuestions = new HashMap<>(timeFramesMap);

      trainQuestions.remove(testQuestion);
      train(trainQuestions);
      if (timeFramesMap.get(testQuestion).isEmpty()) {
        log.warning("No candidate TimeFrames for Question: {0}", testQuestion.wrap());
        missing++;
        continue;
      }
      this.question = testQuestion;
      final boolean predictedTimeFrameMatchGold = test(question, timeFramesMap.get(testQuestion));
      if (predictedTimeFrameMatchGold) {
        correct++;
      }

      TimeFrame predictedTimeFrame = getPrediction(timeFramesMap.get(testQuestion));

      timeFramePredictions.put(question.getDocumentID(), Pair.of(predictedTimeFrame, predictedTimeFrameMatchGold));
    }
    
    final int total = timeFramesMap.size() - missing - skipped;
    final double accuracy = 100.0 * correct / total;
    log.info("Accuracy: {0}%  ({1}/{2})", accuracy, correct, total);
    log.info("Missing: {0}, Skipped: {1}", missing, skipped);
    return Pair.of(accuracy, timeFramePredictions);
  }
}
