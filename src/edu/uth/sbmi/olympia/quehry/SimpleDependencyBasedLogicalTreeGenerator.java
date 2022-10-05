package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.DependencyTree;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Maps;
import edu.uth.sbmi.olympia.util.MutableInteger;
import edu.uth.sbmi.olympia.util.TreeNode;
import edu.uth.sbmi.olympia.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Generates the {@link LogicalTree}s from a {@link LexiconMatchTree}.
 *
 * @author Kirk Roberts, kirk.roberts@uth.tmc.edu
 */
public class SimpleDependencyBasedLogicalTreeGenerator extends LogicalTreeGenerator {
  private static final Log log = new Log(SimpleDependencyBasedLogicalTreeGenerator.class);

  private final LogicalTreeFilteringRules logicalTreeFilter =
      new LogicalTreeFilteringRules();

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LogicalTree> getLogicalTrees(
          final LexiconMatchTree lexMatchTree) {
    log.DBG("----------- {0} ------------", lexMatchTree.getDocumentID());
    if (log.fine()) {
      log.fine("LexiconMatchTree[{0}]: {1}", lexMatchTree.getDocumentID(),
          lexMatchTree.getDependencyTree().getSentence().wrap());
      for (final LexiconMatch match : lexMatchTree.getLexiconMatches()) {
        log.fine("  Entry: {0}", match.getEntry());
        log.fine("    Tokens: {0}", match.getTokens());
      }
      log.fine("DependencyTree:\n{0}", lexMatchTree.getDependencyTree());
    }

    final TreeNode<String> initTree = createInitialTree(lexMatchTree);
    if (log.finer()) {
      log.finer("Initial Tree:\n{0}", initTree.treeString());
    }

    final TreeNode<String> nullPrunedTree = pruneNull(initTree);
    if (log.finer()) {
      log.finer("Null-Pruned Tree:\n{0}", nullPrunedTree.treeString());
    }

    // Every node is null
    if (nullPrunedTree.getItem().equals("null")) {
      return new ArrayList<>();
    }

    final List<TreeNode<String>> trees = runGenerationRules(nullPrunedTree);
    log.fine("Generated {0} LogicalTrees", trees.size());
    log.DBG("Generated {0} LogicalTrees", trees.size());

    final List<LogicalTree> logicalTrees = new ArrayList<>();
    for (final TreeNode<String> tree : trees) {
      final LogicalTree logicalTree = new LogicalTree(tree, lexMatchTree);
      if (logicalTreeFilter.hasTypeMismatch(logicalTree) == false &&
          logicalTreeFilter.hasInvalidAnd(logicalTree) == false) {
        logicalTrees.add(logicalTree);
      }
    }
    log.fine("Filtered to {0} LogicalTrees", logicalTrees.size());
    log.DBG("Filtered to {0} LogicalTrees", logicalTrees.size());

    return logicalTrees;
  }

  /**
   * Generates an initial tree based on an alignment between the
   * {@link LexiconMatchTree} and the {@link DependencyTree}.
   */
  private TreeNode<String> createInitialTree(final LexiconMatchTree lexMatchTree) {
    final TreeNode<Text> depRoot = lexMatchTree.getDependencyTree().typelessTree();
    if (log.finest()) {
      log.finest("Typeless Dependency Tree:\n{0}", depRoot.treeString());
    }
    log.DBG("Typeless Dependency Tree:\n{0}", depRoot.treeString());
    final Map<Token,TreeNode<Text>> allNodesMap = new LinkedHashMap<>();
    for (final TreeNode<Text> node : depRoot.getAllNodes()) {
      for (final Token token : node.getItem().getTokens()) {
        final TreeNode<Text> prev = allNodesMap.put(token, node);
        assert prev == null;
      }
    }

    final Map<TreeNode<Text>,LexiconMatch> alignment = new LinkedHashMap<>();
    for (final LexiconMatch match : lexMatchTree.getLexiconMatches()) {
      TreeNode<Text> highestNode = null;
      for (final Token token : match.getTokens()) {
        assert allNodesMap.containsKey(token) : token;
        final TreeNode<Text> node = allNodesMap.get(token);
        if (highestNode == null) {
          highestNode = node;
        }
        else if (node.depth() < highestNode.depth()) {
          highestNode = node;
        }
        else if (node.depth() == highestNode.depth()) {
          log.DBG("Nodes at same depth");
        }
      }
      assert highestNode != null;
      assert alignment.containsKey(highestNode) == false:
          "highestNode \"" + highestNode + "\" for match \"" + match + "\" already in alignment:\n  " + Maps.prettyPrint(alignment);
      alignment.put(highestNode, match);
    }

    if (!alignment.containsKey(depRoot)) {
      log.DBG("root node not in alignment");
    }

    final MutableInteger lambdaCount = new MutableInteger(0);
    final List<TreeNode<String>> roots =
        createInitialTreeNodes(depRoot, alignment, lambdaCount);
    if (roots.size() == 1) {
      return roots.get(0);
    } else {
      return new TreeNode<String>("has_concept");
    }
  }

  /**
   * Creates the {@link TreeNode}s for the initial {@link LogicalTree}.
   */
  private List<TreeNode<String>> createInitialTreeNodes(
          final TreeNode<Text> node,
          final Map<TreeNode<Text>,LexiconMatch> alignment,
          final MutableInteger lambdaCount) {
    if (alignment.containsKey(node)) {
      final LexiconMatch match = alignment.get(node);
      final String form = match.getEntry().getLogicalForm();
      final TreeNode<String> logicalNodeTop;
      final TreeNode<String> logicalNodeBot;
      if (form.startsWith("lambda.")) {
        lambdaCount.increment();
        final int count = lambdaCount.getValue();
        logicalNodeTop = new TreeNode<>("lambda _" + count);
        if (form.endsWith(".concept")) {
          logicalNodeBot = new TreeNode<>("has_concept(_" + count + ")");
        }
        else if (form.endsWith(".hascall")) {
          logicalNodeBot = new TreeNode<>("has_call(_" + count + ")");
        }
        else if (form.endsWith(".hasrelative")) {
          logicalNodeBot = new TreeNode<>("is_relative(_" + count + ")");
        }
        else {
          log.DBG("Unhandled logical form: {0}", form);
          System.exit(1);
          return null;
        }
        logicalNodeTop.addChild(logicalNodeBot);
      }
      else if (Util.hashSet(
          "is_large", "is_problem", "is_healed", "is_serious", "is_positive", "is_significant", "is_normal")
          .contains(form)) {
        final int count = 1;
        logicalNodeTop = new TreeNode<>(form + "(_" + count + ")");
        logicalNodeBot = logicalNodeTop;
      }
      else {
        logicalNodeTop = new TreeNode<>(form);
        logicalNodeBot = logicalNodeTop;
      }
      for (final TreeNode<Text> child : node.getChildren()) {
        for (final TreeNode<String> newNode :
             createInitialTreeNodes(child, alignment, lambdaCount)) {
          logicalNodeBot.addChild(newNode);
        }
      }
      return Collections.singletonList(logicalNodeTop);
    }
    else {
      final List<TreeNode<String>> newNodes = new ArrayList<>();
      for (final TreeNode<Text> child : node.getChildren()) {
        newNodes.addAll(createInitialTreeNodes(child, alignment, lambdaCount));
      }
      return newNodes;
    }
  }

  /**
   * Prunes the null {@link TreeNode}s from the tree.
   */
  private TreeNode<String> pruneNull(final TreeNode<String> initTree) {
    final TreeNode<String> root = initTree.deepCopy();
    final List<TreeNode<String>> nonRootNodes = new ArrayList<>(
        root.getAllChildren());
    Collections.sort(nonRootNodes, new Comparator<TreeNode<String>>() {
      @Override
      public int compare(final TreeNode<String> node1,
                         final TreeNode<String> node2) {
        return Integer.valueOf(node1.height()).compareTo(node2.height());
      }
    });
    for (final TreeNode<String> node : nonRootNodes) {
      if (node.getItem().equals("null")) {
        final List<TreeNode<String>> children = new ArrayList<>(
            node.getChildren());
        for (final TreeNode<String> child : children) {
          node.removeChild(child);
          node.getParent().addChild(child);
        }
        node.getParent().removeChild(node);
      }
    }

    TreeNode<String> finalRoot = root;
    while (finalRoot.getItem().equals("null")) {
      if (finalRoot.getChildren().size() == 1) {
        final TreeNode<String> child = finalRoot.getChildren().get(0);
        finalRoot.removeChild(child);
        finalRoot = child;
      }
      else {
        // Every node is null
        if (finalRoot.getChildren().size() == 0) {
          log.severe("Ignoring the questions with logical tree having all nulls.");
          return finalRoot;
        }

        // Null root with multiple children.  Just need to take one and hope
        // the generation rules solve the issue.
        final List<TreeNode<String>> children = new ArrayList<>(
            finalRoot.getChildren());
        final TreeNode<String> newRoot = children.get(0);
        for (int i = 1; i < children.size(); i++) {
          final TreeNode<String> child = children.get(i);
          child.getParent().removeChild(child);
          newRoot.addChild(child);
        }
        finalRoot.removeChild(newRoot);
        finalRoot = newRoot;
      }
    }

    // Sanity Check
    assert finalRoot.getItem().equals("null") == false;
    for (final TreeNode<String> child : finalRoot.getAllChildren()) {
      assert child.getItem().equals("null") == false;
    }

    return finalRoot;
  }

  /**
   * Runs the generation rules.
   */
  private List<TreeNode<String>> runGenerationRules(final TreeNode<String> root) {
    final List<TreeNode<String>> generated = new ArrayList<>();
    generated.add(root);
    // Begin DBG
    assert root.getItem().equals("null") == false;
    for (final TreeNode<String> child : root.getAllChildren()) {
      assert child.getItem().equals("null") == false;
    }
    // End DBG

    final Set<String> processed = new HashSet<>();
    processed.add(root.treeString());
    final Queue<TreeNode<String>> queue = new LinkedList<>();
    queue.add(root);
    while (queue.peek() != null) {
      final TreeNode<String> tree = queue.remove();
      if (log.finest()) {
        log.finest("Generating from Tree:\n{0}", tree.treeString());
      }

      for (final TreeNode<String> node : new ArrayList<>(tree.getAllNodes())) {
        final TreeNode<String> parent = node.getParent();
        final List<TreeNode<String>> children = node.getChildren();

        // Flip Rule
        if (children.size() == 1) {
          final TreeNode<String> newNode = deepCopyFrom(node);
          if (flip(newNode)) {
            assert newNode.getRoot().size() == node.getRoot().size();
            addNewTree(newNode, processed, generated, queue, "Flip");
          }
        }
      
        // Promote-Child Rule
        if (children.size() > 1) {
          for (int i = 0; i < children.size(); i++) {
            final TreeNode<String> newNode = deepCopyFrom(node);
            if (newNode.getChildren().size() >= 4) {
              continue;
            }
            final TreeNode<String> newChild = newNode.getChildren().get(i);
            final TreeNode<String> oldChild = children.get(i);
            assert oldChild.getItem().equals(newChild.getItem()) : "wrong child";
            if (promote(newChild)) {
              assert newNode.getRoot().size() == node.getRoot().size();
              assert newNode.getChildren().size() <= 3;
              addNewTree(newNode, processed, generated, queue, "Promote-Child");
            }
          }
        }

        // Demote-Child Rule
        if (children.size() == 1 && node != node.getRoot()) {
          final TreeNode<String> newNode = deepCopyFrom(node);
          if (demote(newNode)) {
            assert newNode.getRoot().size() == node.getRoot().size();
            addNewTree(newNode, processed, generated, queue, "Demote-Child");
          }
        }

        // Lambda-And
        if (node.getItem().startsWith("lambda ") && !children.isEmpty()) {
          final TreeNode<String> newNode = deepCopyFrom(node);
          if (lambdaAnd(newNode)) {
            assert newNode.getRoot().size() == (node.getRoot().size() + 1);
            addNewTree(newNode, processed, generated, queue, "Lambda-And");
          }
        }
      }
    }

    return generated;
  }

  /**
   * Returns the reference to the equivalent node in a deep copy of the full tree.
   */
  private TreeNode<String> deepCopyFrom(final TreeNode<String> node) {
    TreeNode<String> nodeCopy = node.getRoot().deepCopy();
    for (final Integer index : node.getPathFromRoot()) {
      nodeCopy = nodeCopy.getChildren().get(index);
    }
    assert nodeCopy.getItem().equals(node.getItem());
    assert nodeCopy.getRoot().size() == node.getRoot().size();
    assert nodeCopy.getRoot().getItem().equals(node.getRoot().getItem());
    return nodeCopy;
  }

  /**
   * Adds a new tree to the given objects (when appropriate).
   */
  private void addNewTree(final TreeNode<String> newNode,
                          final Set<String> processed,
                          final List<TreeNode<String>> generated,
                          final Queue<TreeNode<String>> queue,
                          final String ruleName) {
    if (generated.size() > 1*100000) {
      return;
    }

    final TreeNode<String> newRoot = newNode.getRoot();
    if (processed.add(newRoot.treeString())) {
      generated.add(newRoot);
      if (generated.size() % 100000 == 0) {
        log.severe("Generated {0} trees so far...", generated.size());
      }
      else if (generated.size() % 1000 == 0) {
        log.finer("Generated {0} trees so far...", generated.size());
      }
      queue.add(newRoot);
      if (log.finest()) {
        log.finest("{0} Rule created Tree:\n{1}", ruleName, newRoot.treeString());
      }
    }
  }
   
  /**
   * Flips <var>node</var> with its (only) child.
   */
  private boolean flip(final TreeNode<String> node) {
    assert node.getChildren().size() == 1 : "node has multiple children";
    final TreeNode<String> node2 = node.getChildren().get(0);
    if (node.getItem().equals("and") || node2.getItem().equals("and")) {
      return false;
    }
    if (node.getItem().equals(node2.getItem())) {
      return false;
    }

    if (log.nano()) {
      log.nano("Logical Tree Before Flip:\n{0}", node.getRoot().treeString());
    }
    
    final int sizeBefore = node.getRoot().size();
    
    node.removeChild(node2);
    if (log.pico()) {
      log.pico("Step1:\n{0}", node.getRoot().treeString());
    }
    for (final TreeNode<String> node2Child : new ArrayList<>(node2.getChildren())) {
      node2.removeChild(node2Child);
      node.addChild(node2Child);
    }
    if (log.pico()) {
      log.pico("Step2:\n{0}", node.getRoot().treeString());
    }
    if (node == node.getRoot()) {
      node2.addChild(node);
    }
    else {
      final TreeNode<String> parent = node.getParent();
      parent.removeChild(node);
      parent.addChild(node2);
      node2.addChild(node);
    }
    if (log.nano()) {
      log.nano("Logical Tree After Flip:\n{0}", node2.getRoot().treeString());
    }
    assert node2.getRoot().size() == sizeBefore : "changed size";
    return true;
  }
  
  /**
   * Promotes the <var>child</var> above its siblings.
   */
  private boolean promote(final TreeNode<String> child) {
    final TreeNode<String> parent = child.getParent();
    final TreeNode<String> root = parent.getRoot();
    if (log.nano()) {
      log.nano("Logical Tree Before Promote:\n{0}", root.treeString());
    }
    assert parent.getChildren().size() > 1;
    
    for (final TreeNode<String> child2 : new ArrayList<>(parent.getChildren())) {
      if (child != child2) {
        parent.removeChild(child2);
        child.addChild(child2);
      }
    }
    if (log.nano()) {
      log.nano("Logical Tree After Promote:\n{0}", root.treeString());
    }
    return true;
  }

  /**
   * Demotes the <var>node</var> to be a sibling with it children.
   */
  private boolean demote(final TreeNode<String> node) {
    final TreeNode<String> parent = node.getParent();
    final List<TreeNode<String>> children = new ArrayList<>(
        node.getChildren());
    final TreeNode<String> root = parent.getRoot();
    if (log.nano()) {
      log.nano("Logical Tree Before Demote:\n{0}", root.treeString());
    }
    assert children.size() >= 0;

    for (final TreeNode<String> child : children) {
      node.removeChild(child);
      parent.addChild(child);
    }
    
    if (log.nano()) {
      log.nano("Logical Tree After Demote:\n{0}", root.treeString());
    }
    return true;
  }

  /**
   * Inserts an <code>and</code> node under a lambda.
   */
  private boolean lambdaAnd(final TreeNode<String> lambdaNode) {
    final TreeNode<String> root = lambdaNode.getRoot();
    int andCount = 0;
    for (final TreeNode<String> node : root.getAllNodes()) {
      if (node.getItem().equals("and")) {
        andCount++;
      }
    }
    if (andCount >= 2) {
      return false;
    }

    final List<TreeNode<String>> children = new ArrayList<>(
        lambdaNode.getChildren());

    if (log.nano()) {
      log.nano("Logical Tree Before Lambda-And:\n{0}", root.treeString());
    }
    assert lambdaNode.getItem().startsWith("lambda ");
    assert lambdaNode.getChildren().isEmpty() == false;

    final TreeNode<String> andNode = new TreeNode<>("and");
    lambdaNode.removeChildren(children);
    andNode.addChildren(children);
    lambdaNode.addChild(andNode);

    if (log.nano()) {
      log.nano("Logical Tree After Lambda-And:\n{0}", root.treeString());
    }

    return true;
  }

}
