package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.util.Config;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Pair;
import edu.uth.sbmi.olympia.util.Place;
import edu.uth.sbmi.olympia.util.Strings;
import edu.uth.sbmi.olympia.util.TreeNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filtering rules to prune candidate {@link LogicalTree}s.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public class LogicalTreeFilteringRules {
  private static final Log log = new Log(LogicalTreeFilteringRules.class);
  
  private final Map<String,Pair<String,String>> functionTypes = new HashMap<>();
  
  /**
   * Creates a new <code>LogicalTreeFilteringRules</code>, loading up the type
   * file from the {@link Config} file.
   */
  public LogicalTreeFilteringRules() {
    final Place file =
        Config.get(LogicalTreeFilteringRules.class, "typeRules").toPlace();
    int lineNum = 0;
    try {
      for (final String line : file.readLines()) {
        lineNum++;
        if (line.startsWith("#")) {
          continue;
        }
        final List<String> split = Strings.split(line, "\t");
        assert split.size() == 3 :
            "line " + lineNum + " does not have 3 items: " + line;
        
        final String function = split.get(0);
        final Pair<String,String> types = Pair.of(split.get(1), split.get(2));

        final Pair<String,String> prev = functionTypes.put(function, types);
        if (prev != null) {
          log.severe("Function has multiple type constraints: {0}", function);
          log.severe("  1: {0} -> {1}", prev.getFirst(), prev.getSecond());
          log.severe("  2: {0} -> {1}", types.getFirst(), types.getSecond());
          System.exit(1);
        }
      }
    }
    catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
  
  
  /**
   * Checks for type mismatches between parent and child.
   */
  public boolean hasTypeMismatch(final LogicalTree tree) {
    if (log.fine()) {
      log.fine("Testing Type Match for Tree:\n{0}", tree.getRoot().treeString());
    }
    for (final TreeNode<String> node : tree.getNodes()) {
      final String p_op = node.getItem();
      if (hasTypes(p_op) == false) {
        log.severe("Unknown Function: {0}", p_op);
        log.DBG("Unknown Function: {0}", function(p_op));
        new Throwable().printStackTrace(); // DBG
        System.exit(1);
      }

      final Pair<String,String> p_types = getTypes(p_op);

      if (node.isLeaf()) {
        final String input = p_types.getFirst();
        if (input.equals("NULL") == false && input.equals("Event") == false) {
          log.fine("Incompatible Leaf: {0}", node.getItem());
          return true;
        }
        continue;
      }

      // Only 'and' nodes can have more than one child
      final List<TreeNode<String>> children = new ArrayList<>(
          node.getChildren());
      if (children.size() > 1) {
        if (p_op.equals("and") == false) {
          log.fine("REJECT: node cannot have more than one child: {0}", p_op);
          return true;
        }
      }
      
      // Checking whether all the output types are same for the children 
      // under a single parent
      String childOutput = null;
      for(TreeNode<String> child: node.getChildren()){
        if (hasTypes(child.getItem()) == false) {
          log.severe("Unknown Function: {0}", child.getItem());
          log.DBG("Unknown Function: {0}", function(child.getItem()));
          new Throwable().printStackTrace(); // DBG
          System.exit(1);
        }
        final Pair<String,String> childType = getTypes(child.getItem());
        if(childOutput == null){
          childOutput = childType.getSecond();
        }
        else{
          if(!childOutput.equals(childType.getSecond())){
            log.fine("Removing these trees for child miss match: {0}", 
                                    LogicalTree.flattenTree(tree.getRoot()));
            return true;
          }
        }
      }

      // In the current (hackish) format, we're assuming the first child of
      // an 'and' dictates the overall type
      final TreeNode<String> child = node.getChildren().get(0);
      if (hasTypes(child.getItem()) == false) {
        log.severe("Unknown Function: {0}", child.getItem());
        log.DBG("Unknown Function: {0}", function(child.getItem()));
        new Throwable().printStackTrace(); // DBG
        System.exit(1);
      }
      
      final String c_op = child.getItem();
      final Pair<String,String> c_types = getTypes(c_op);

      final String p_input = p_types.getFirst();
      final String c_output = c_types.getSecond();
      if (p_input.equals(c_output) == false) {
        log.fine("REJECT: Parent/Child do not match: [{0}]->{1}  {2}->[{3}]",
            c_op, c_output, p_input, p_op);
        return true;
      }
    }

    log.fine("ACCEPT");
    return false;
  }

  /**
   * Indicates whether the input/output types for the given <var>function</var>
   * are known.
   */
  private boolean hasTypes(final String function) {
    return functionTypes.containsKey(function(function));
  }

  /**
   * Returns the input and output types for the given <var>function</var>.
   */
  private Pair<String,String> getTypes(final String function) {
    return functionTypes.get(function(function));
  }

  /**
   * Extracts the function from the <var>op</var>.
   */
  private String function(final String op) {
    if (op.startsWith("lambda ")) {
      return "lambda";
    }
    final int index = op.indexOf('(');
    if (index < 0) {
      return op;
    }
    else {
      return op.substring(0, index);
    }
  }
  
  
  
  static Pair<String,String> getTypes(final String function,
                                      final boolean failIfMissing) {
    final LogicalTreeFilteringRules instance = new LogicalTreeFilteringRules();
    final Pair<String,String> types = instance.getTypes(function);
    if (types == null && failIfMissing) {
      log.severe("Unknown Function: {0}", function);
      new Throwable().printStackTrace(); // DBG
      System.exit(1);
    }
    return types;
  }

  /**
   * Checks for AND statements that do not start with a lambda and contain terms
   * that are compatible with that lambda.  <b>Note:</b> this is not strictly
   * correct, but once lambda and AND statements are better handled, this can
   * probably be removed/modified.
   */
  public boolean hasInvalidAnd(final LogicalTree tree) {
    if (log.nano()) {
      log.nano("Testing Valid And for Tree:\n{0}", tree.getRoot().treeString());
    }
    for (final TreeNode<String> node : tree.getNodes()) {
      if (node.getItem().equals("and")) {
        for (final TreeNode<String> child : node.getChildren()) {
          if (child.getItem().equals("and")) {
            return true;
          }
        }
      }

      final List<TreeNode<String>> children = node.getChildren();
      if (children.size() > 1) {
        // First child of AND should be has_concept
        if (node.getItem().equals("and") && node.getParent().getItem().startsWith("lambda")) {
          final TreeNode<String> firstChild = children.get(0);
          if (firstChild.getItem().startsWith("has_") == false) {
            log.fine("Non-has_concept node as first child under AND: {0}({1})", node, node.getChildrenStrings());
            return true;
          } else {
            final List<TreeNode<String>> nonConceptChildren = new ArrayList<>(children);
            nonConceptChildren.remove(0);
            String previous = "";
            for (final TreeNode<String> current: nonConceptChildren) {
              if (current.getItem().compareTo(previous) < 0) {
                return true;
              }

              previous = current.getItem();
            }
          }
        }
      }
    }
    return false;
  }

}
