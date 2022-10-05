package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.ml.SVMMulti;
import edu.uth.sbmi.olympia.ml.feature.Feature;
import edu.uth.sbmi.olympia.ml.feature.StringSetFeature;
import edu.uth.sbmi.olympia.ml.svm_multi.LibLinearSVM;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * SVM-based {@link MachineTimeFrameScorer}.
 */
public class SVMTimeFrameScorer extends MachineTimeFrameScorer {
  private static final Log log = new Log(SVMTimeFrameScorer.class);

  private SVMMulti<TimeFrame> svm;

  /**
   * {@inheritDoc}
   */
  @Override
  protected SVMMulti<TimeFrame> getClassifier() {
    if (svm == null) {
      svm = new LibLinearSVM<>();
      svm.setTargetFeature(getTargetFeature());
      svm.setFeatures(getFeatures());
    }
    return svm;
  }

  /**
   * Returns the {@link Feature}s to use for this
   * <code>SVMTimeFrameScorer</code>.
   */
  List<Feature<TimeFrame,?>> getFeatures() {
    final List<Feature<TimeFrame,?>> features = new ArrayList<>();

    features.add(new StringSetFeature<TimeFrame>("AllTokens") {
      @Override
      public Set<String> compute(final TimeFrame timeFrame) {
        final Set<String> set = new TreeSet<>();
        final String timeFrameValue = timeFrame.getValue();
        for (Token token: question.getTokens()) {
          set.add(token.asRawString().toLowerCase() + "--" + timeFrameValue);
        }
        log.DBG("{0} returning {1}", getName(), set);
        return set;
      }
    });

    return features;
  }
}
