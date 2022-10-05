package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Strings;
import edu.uth.sbmi.olympia.util.TreeNode;
import edu.uth.sbmi.olympia.util.attr.Attributes;
import edu.uth.sbmi.olympia.util.attr.HasAttributes;
import edu.uth.sbmi.olympia.util.xml.XMLUtil;
import org.jdom2.Element;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A tree-structure indicating the logical structure for a question's semantic
 * parse.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class LogicalTree implements HasAttributes {
  private static final Log log = new Log(LogicalTree.class);

  private final TreeNode<String> root;
  private final LexiconMatchTree lexiconMatchTree;
  private final Attributes attr = new Attributes();

  /**
   * Creates a new <code>LogicalTree</code> using the <var>root</var> of the
   * tree, as well as the originating {@link LexiconMatchTree}.
   */
  public LogicalTree(final TreeNode<String> root,
                     final LexiconMatchTree lexiconMatchTree) {
    this.root = instantiateVars(root, new TreeMap<String,String>());
    this.lexiconMatchTree = lexiconMatchTree;
  }

  /**
   * Returns the {@link TreeNode} root of this <code>LogicalTree</code>.
   */
  public TreeNode<String> getRoot() {
    return root;
  }

  /**
   * Returns all the {@link TreeNode}s in this <code>LogicalTree</code>.
   */
  public List<TreeNode<String>> getNodes() {
    final List<TreeNode<String>> nodes = new ArrayList<>();
    nodes.add(root);
    nodes.addAll(root.getAllChildren());
    return nodes;
  }

  /**
   * Returns the {@link LexiconMatchTree} that this <code>LogicalTree</code>
   * originated from.
   */
  public LexiconMatchTree getLexiconMatchTree() {
    return lexiconMatchTree;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Attributes getAttributes() {
    return attr;
  }

  /**
   * Instatiates the variables, e.g., changing _1 to x.
   */
  private TreeNode<String> instantiateVars(final TreeNode<String> node,
                                           final Map<String,String> idMap) {
    // Terrible coding here.
    String newOp = node.getItem();
    for (final String var : Arrays.asList("_1", "_2", "_3")) {
      if (newOp.contains(var)) {
        if (idMap.containsKey(var) == false) {
          if (idMap.values().contains("x") == false) {
            idMap.put(var, "x");
          }
          else if (idMap.values().contains("y") == false) {
            idMap.put(var, "y");
          }
          else if (idMap.values().contains("z") == false) {
            idMap.put(var, "z");
          }
          else {
            log.severe("More than 3 variables!");
            System.exit(1);
          }
        }
        newOp = newOp.replace(var, idMap.get(var));
      }
    }
    // Remove the parameters if a predicate has children
    // E.g., is_normal(x)(latest(lambda x.has_concept(x))) --> is_normal(latest(lambda x.has_concept(x)))
    if (node.getChildren().size() > 0) {
      newOp = newOp.replaceAll("\\(x\\)", "").replaceAll("\\(y\\)", "").replaceAll("\\(z\\)", "");
    }

    final TreeNode<String> newNode = new TreeNode<>(newOp);
    for (final TreeNode<String> child : node.getChildren()) {
      newNode.addChild(instantiateVars(child, idMap));
    }
    return newNode;
  }
  
  /**
   * Flattens the tree into a <code>String</code>.
   */
  public static String flattenTree(final TreeNode<String> root) {
    return flattenTree(root, false);
  }

  /**
   * Flattens the tree into a <code>String</code>.
   * @param simple Simplify the logical form.
   */
  public static String flattenTree(final TreeNode<String> node,
                                   final boolean simple) {
    return flattenTree(node, simple, false);
  }

  /**
   * Flattens the tree into a <code>String</code>.
   * @param simple Simplify the logical form.
   * @param debugging Turn on debugging mode, allow non-valid trees to print
   */
  public static String flattenTree(final TreeNode<String> node,
                                   final boolean simple,
                                   final boolean debugging) {
    final String op;
    if (simple) {
      String item = node.getItem();
      item = item.replace("has_device", "has_concept")
                 .replace("has_doctor", "has_concept")
                 .replace("has_event", "has_concept")
                 .replace("has_finding", "has_concept")
                 .replace("has_function", "has_concept")
                 .replace("has_problem", "has_concept")
                 .replace("has_substance", "has_concept")
                 .replace("has_test", "has_concept")
                 .replace("has_treatment", "has_concept")
                 .replace("has_attribute", "has_concept");
      if (item.startsWith("has_concept")) {
        item = item.replaceAll("\\(x, .*", "(x)")
                   .replaceAll("\\(y, .*", "(y)")
                   .replaceAll("\\(z, .*", "(z)");
      }
      else {
        item = item.replaceAll("\\(x, .*", "")
                   .replaceAll("\\(y, .*", "")
                   .replaceAll("\\(z, .*", "");
      }
      op = item;
    }
    else {
      op = node.getItem();
    }

    if (node.isLeaf()) {
      return op;
    }

    final List<String> children = new ArrayList<>();
    for (final TreeNode<String> child : node.getChildren()) {
      children.add(flattenTree(child, simple, debugging));
    }

    if (op.startsWith("lambda ")) {
      return op + "." + Strings.join(children, " ^ ");
    }
    else if (op.equals("and")) {
      return Strings.join(children, " and ");
    }
    else {
      return op + "(" + Strings.join(children, ", ") + ")";
    }
  }

  public static String stringifyTree(final TreeNode<String> node) {
    final List<String> children = new ArrayList<>();
    for (final TreeNode<String> child : node.getChildren()) {
      children.add(stringifyTree(child));
    }

    if (children.size() > 2) {
      return "Size greater than 2 :(";
    } else if (children.size() == 2) {
      return children.get(0) + " " + node.getItem() + " " + children.get(1);
    } else if (children.size() == 1) {
      return node.getItem() + " " + children.get(0);
    } else {
      return node.getItem();
    }
  }

  public static List<Concept> getMedicalConcepts(final LogicalTree logicalTree, final Document question) {
    List<Concept> medicalConcepts = new ArrayList<>();
    final List<LexiconMatch> medicalConceptLexiconMatches =
        logicalTree.getLexiconMatchTree().getLexiconMatches().stream()
            .filter(lm -> lm.getEntry().getLogicalForm().equals("lambda.concept")).collect(Collectors.toList());
    for (LexiconMatch medicalConceptLexiconMatch: medicalConceptLexiconMatches) {
      final List<Token> medicalConceptTokens = medicalConceptLexiconMatch.getTokens();
      log.DBG("Concept predicate tokens: {0}", medicalConceptTokens != null ? medicalConceptTokens : "null");
      final Text medicalConceptText = Text.create(
          medicalConceptTokens.get(0), medicalConceptTokens.get(medicalConceptTokens.size() - 1));
      final Concept medicalConcept = question.getAnnotations(Concept.class).stream()
          .filter(c -> c.getText().isSuper(medicalConceptText, true))
          .max(Comparator.comparing(c -> c.getText().getCharLength())).orElse(null);
      assert medicalConcept != null: "No medical concept attached to the question: " +
          logicalTree.getLexiconMatchTree().getLexiconMatches();
      medicalConcepts.add(medicalConcept);
    }

    medicalConcepts = ConceptExtractor.removeContainedConcepts(medicalConcepts);

    return medicalConcepts;
  }

  public static List<String> getMedicalConceptCodes(final LogicalTree logicalTree, final Document question) {
    final List<Concept> medicalConcepts = getMedicalConcepts(logicalTree, question);
    final List<String> conceptCodes = new ArrayList<>();
    for (Concept medicalConcept: medicalConcepts) {
      final String conceptCode;
      conceptCode = medicalConcept.getCUI() != null ? medicalConcept.getCUI() : medicalConcept.getType();
      log.DBG("Medical concept CUI: {0}", conceptCode);
      conceptCodes.add(conceptCode);
    }

    return conceptCodes;
  }

  public static Element convertToXML(
      final LogicalTree logicalTree, final Document question, final TimeFrame timeFrame, final boolean isGold) {
    return convertToXML(logicalTree.getRoot(), logicalTree, question, timeFrame, 0, isGold);
  }

  private static Element convertToXML(
      final TreeNode<String> node,
      final LogicalTree logicalTree,
      final Document question,
      final TimeFrame timeFrame,
      int medicalConceptsEncountered,
      final boolean isGold) {
    String item;
    item = node.getItem();
    // Only replace the concepts in case of non-gold logical tree
    if (!isGold) {
      if (item.startsWith("has_")) {
        final List<String> conceptCodes = LogicalTree.getMedicalConceptCodes(logicalTree, question);
        String conceptCode;
        if (medicalConceptsEncountered < conceptCodes.size()) {
          conceptCode = conceptCodes.get(medicalConceptsEncountered);
        } else if (conceptCodes.size() > 0) {
          conceptCode = conceptCodes.get(conceptCodes.size() - 1);
        } else {
          conceptCode = "none";
        }
        item = item.replace(
            ")", String.format(", %s, %s)", conceptCode, timeFrame.getValue()));
        medicalConceptsEncountered++;
      } else if (item.equals("time_within")) {
        final String temporalReference = question.getTemporalReference();
        item = item + String.format("(x, %s)", temporalReference);
      } else if (item.equals("at_location")) {
        final String locationReference = question.getLocationReference();
        item = item + String.format("(x, %s)", locationReference);
      } else if (item.equals("greater_than") || item.equals("less_than")) {
        final String measurementReference = question.getMeasurementReference();
        item = item + String.format("(x, %s)", measurementReference);
      }
    }

    Element currentElement = XMLUtil.createElement("Node");
    currentElement = XMLUtil.addAttribute(currentElement, "value", item);

    for (final TreeNode<String> child : node.getChildren()) {
      currentElement.addContent(
          convertToXML(child, logicalTree, question, timeFrame, medicalConceptsEncountered, isGold));
    }

    return currentElement;
  }
}
