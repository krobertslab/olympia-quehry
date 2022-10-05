package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.ml.SVMMulti;
import edu.uth.sbmi.olympia.ml.feature.Feature;
import edu.uth.sbmi.olympia.ml.feature.StringFeature;
import edu.uth.sbmi.olympia.ml.feature.StringSetFeature;
import edu.uth.sbmi.olympia.ml.svm_multi.LibLinearSVM;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.TreeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * SVM-based {@link MachineLogicalTreeScorer}.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public class SVMLogicalTreeScorer extends MachineLogicalTreeScorer {
  private static final Log log = new Log(SVMLogicalTreeScorer.class);

  private SVMMulti<LogicalTree> svm;

  /**
   * {@inheritDoc}
   */
  @Override
  protected SVMMulti<LogicalTree> getClassifier() {
    if (svm == null) {
      svm = new LibLinearSVM<LogicalTree>();
      svm.setTargetFeature(getTargetFeature());
      svm.setFeatures(getFeatures());
    }
    return svm;
  }

  /**
   * Returns the {@link Feature}s to use for this
   * <code>SVMLogicalTreeScorer</code>.
   */
  List<Feature<LogicalTree,?>> getFeatures() {
    final List<Feature<LogicalTree,?>> features = new ArrayList<>();

    features.add(new StringSetFeature<LogicalTree>("LexiconMatch") {
      @Override
      public Set<String> compute(final LogicalTree tree) {
        final Set<String> set = new TreeSet<>();
        for (final LexiconMatch lexMatch : tree.getLexiconMatchTree().getLexiconMatches()) {
          final String rule = lexMatch.getEntry().getPattern().replace(" ", "") + "->" +
                              lexMatch.getEntry().getLogicalForm();
          assert rule.contains(" ") == false : rule;
          set.add(rule);
        }
        log.fine("{0} returning {1}", getName(), set);
        return set;
      }
    });

    features.add(new StringSetFeature<LogicalTree>("NodeRelation") {
      @Override
      public Set<String> compute(final LogicalTree tree) {
        final Set<String> set = new TreeSet<>();
        
        for (final TreeNode<String> node : tree.getNodes()) {
          for (final TreeNode<String> node2: node.getChildren()) {
            set.add(node.toString() + "->" + node2.toString());
          }
          if (node.getChildren().isEmpty()) {
            set.add(node.toString() + "->LEAF");
          }
          if (node.getParent() == null) {
            set.add("ROOT->" + node.toString());
          }
        }
        log.fine("{0} returning {1}", getName(), set);
        return set;
      }
    });

    features.add(new StringFeature<LogicalTree>("FirstWord+Root") {
      @Override
      public String compute(final LogicalTree tree) {
        final String questn = question.asRawString();
        final int s1 = questn.indexOf(' ');
        final int s2 = questn.indexOf(' ', s1+1);
        final String word1 = questn.substring(0, s1).toLowerCase();
        final String stem;
        if (word1.equals("how") && s2 > 0) {
          final String word2 = questn.substring(s1+1, s2).toLowerCase();
          stem = word1 + "_" + word2;
        }
        else {
          stem = word1;
        }

        final String rootName = tree.getRoot().toString();

        final String toReturn = stem + "--" + rootName;
        log.fine("{0} returning {1}", getName(), toReturn);
        return toReturn;
      }
    });

    return features;
  }

}
