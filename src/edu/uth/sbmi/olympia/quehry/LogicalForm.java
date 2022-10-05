package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Annotation;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.TreeNode;

/**
 * Keep all the information regarding the logical forms.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class LogicalForm extends Annotation {
  private static final Log log = new Log(LogicalForm.class);

  private final TreeNode<String> logicalTree;
  private final String simpleLogicalForm;

  /**
   * Creates a new <code>LogicalForm</code> with a <var>logicalTree</var> and
   * <var>simpleLogicalForm</var>.
   * @throws IllegalArgumentException If <var>simpleLogicalForm</var> is <code>null</code>.
   */
  public LogicalForm(final Text text,
                     final TreeNode<String> logicalTree,
                     final String simpleLogicalForm) {
    super(text);
    this.logicalTree = logicalTree;
    this.simpleLogicalForm = simpleLogicalForm;
    if (logicalTree == null) {
      throw new IllegalArgumentException("logicalTree cannot be NULL");
    }
    if (simpleLogicalForm == null) {
      throw new IllegalArgumentException("simpleLogicalForm cannot be NULL");
    }
  }

  /**
   * Creates a copy of <code>LogicalForm</code> from the given <var>text</var> and <var>logicalForm</var>.
   */
  public LogicalForm(final Text text, final LogicalForm logicalForm) {
    this(text, logicalForm.getLogicalFormTree(), logicalForm.simpleLogicalForm());
  }

  /**
   * Returns the {@link TreeNode} root of the logical form.
   */
  public TreeNode<String> getLogicalFormTree() {
    return logicalTree;
  }

  /**
   * Returns the type of this <code>Concept</code>.
   */
  public String simpleLogicalForm() {
    return LogicalTree.flattenTree(logicalTree, true);
  }

}
