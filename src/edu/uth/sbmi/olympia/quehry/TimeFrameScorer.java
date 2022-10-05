package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.util.Comparators;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Maps;
import edu.uth.sbmi.olympia.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Abstract class for object that assigns an implicit time frame to a {@link Document}.
 */
public abstract class TimeFrameScorer {
  private static final Log log = new Log(TimeFrameScorer.class);

  /**
   * Scores the given {@link TimeFrame}.
   */
  public abstract double score(TimeFrame timeFrame);

  /**
   * Use this <code>TimeFrameScorer</code> to return a predicted {@link TimeFrame}
   * from the candidate {@link TimeFrame}s for the given {@link Document}.
   * @return the predicted {@link TimeFrame} for a given {@link Document}.
   */
  public TimeFrame getPrediction(List<TimeFrame> timeFrames) {
    final Pair<TimeFrame, List<Pair<TimeFrame, Double>>> predictionAndScores = getPredictionAndScores(timeFrames);
    return predictionAndScores.getFirst();
  }

  /**
   * Use this <code>TimeFrameScorer</code> to return a predicted {@link TimeFrame}
   * from the candidate {@link TimeFrame}s and their corresponding scores for the given {@link Document}.
   * @return the predicted {@link TimeFrame} and all the scores for a given {@link Document}.
   */
  public Pair<TimeFrame, List<Pair<TimeFrame, Double>>> getPredictionAndScores(List<TimeFrame> timeFrames) {
    final List<Pair<TimeFrame, Double>> scores = new ArrayList<>();
    for (final TimeFrame candidate : timeFrames) {
      scores.add(Pair.of(candidate, score(candidate)));
    }
    Collections.sort(scores, Comparators.doubleWeightedPair().reverse());
    final TimeFrame guessTimeFrame = scores.get(0).getFirst();

    return Pair.of(guessTimeFrame, scores);
  }

  /**
   * Tests the performance of this <code>TimeFrameScorer</code> at ranking
   * candidate {@link TimeFrame}s for the given {@link Document}.
   * @return <code>true</code> if the {@link Document} is classified correctly.
   */
  public boolean test(final Document question, List<TimeFrame> timeFrames) {
    log.setLevel(Log.ALL);
    final TimeFrame timeFrame = question.getOnlySub(TimeFrame.class);
    final String implicitTimeFrame = timeFrame.getValue();
    if (implicitTimeFrame == null) {
      log.severe("No Gold Implicit Time Frame: {0} ", question.wrap());
      return false;
    }

    final Pair<TimeFrame, List<Pair<TimeFrame, Double>>> predictionAndScores = getPredictionAndScores(timeFrames);
    TimeFrame guessTimeFrame = predictionAndScores.getFirst();
    List<Pair<TimeFrame, Double>> scores = predictionAndScores.getSecond();

    final String guess = guessTimeFrame.getValue();
    final String gold = implicitTimeFrame;

    if (log.fine()) {
      log.fine("Question : {0}", question.toString());
      log.fine("  Gold time frame:\n  {0}",
              gold);
      log.fine("  Top 5 [of {0}] Time Frames:", timeFrames.size());
      log.fine("    1. {0}   [{1}] --- {2}", guess, scores.get(0).getSecond(),
              gold.equals(guess) ? "CORRECT" : "WRONG");
      boolean DBG_has_gold = false;
      if (guess.equals(gold)) {
        DBG_has_gold = true;
        Maps.increment(DBG_gold_rank, 0);
      }
      for (int i = 1; i < 5 && i < scores.size(); i++) {
        final String form = scores.get(i).getFirst().getValue();
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
      log.DBG("DBG_gold_rank: {0}", Maps.normalizeInt(DBG_gold_rank));
    }

    return guess.equals(gold);
  }

  private final Map<Integer,Integer> DBG_gold_rank = new TreeMap<>();
}
