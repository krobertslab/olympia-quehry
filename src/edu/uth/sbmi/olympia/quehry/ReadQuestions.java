package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.text.Sentence;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Pair;
import edu.uth.sbmi.olympia.util.Place;
import edu.uth.sbmi.olympia.util.Strings;
import edu.uth.sbmi.olympia.util.TreeNode;
import edu.uth.sbmi.olympia.util.xml.XMLUtil;
import org.jdom2.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Reads in the annotated questions.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class ReadQuestions {
  private static final Log log = new Log(ReadQuestions.class);
  private static final List<String> QUESTION_CHILDREN =
      Arrays.asList("Lexical", "Tokens", "Concepts", "LogicalForm",
                    "LogicalFormTree", "SimpleLogicalForm");
  private static final List<String> QUESTION_CHILDREN_WITH_ANSWER =
      Arrays.asList("Lexical", "Tokens", "Concepts", "LogicalForm",
          "LogicalFormTree", "SimpleLogicalForm", "Answer");
  private static final List<String> ANSWER_CHILDREN =
      Arrays.asList("PatientID", "Resources");

  private final Place file;
  private boolean readAnswerAnnotations = false;

  /**
   * Creates a new <code>ReadQuestions</var> with the given <var>file</var>.
   */
  public ReadQuestions(final Place file) {
    this.file = file;
  }

  /**
   * Read the questions and its properties from the XML file, returning them as
   * a <code>List</code> of {@link Document}s.
   */
  public List<Document> readQuestions() {
    log.info("Reading Questions from File: {0}", file);
    final List<Document> questions = new ArrayList<>();
    try {
      final Element root = XMLUtil.getDocument(file).getRootElement();
      for (final Element item : root.getChildren()) {
        assert item.getName().equals("Item");
        final String itemID = item.getAttributeValue("id");
        final List<Element> itemChildren = item.getChildren();
        assert itemChildren.get(0).getName().equals("OriginalQuestion");
        for (int j = 1; j < itemChildren.size(); j++) {
          final Element questionElem = itemChildren.get(j);
          assert questionElem.getName().equals("Question");
          final String questionID = questionElem.getAttributeValue("id");
            
          final List<String> questionChildNames =
              XMLUtil.getChildrenNames(questionElem);
          if (this.readAnswerAnnotations) {
            assert questionChildNames.equals(QUESTION_CHILDREN_WITH_ANSWER) :
                questionChildNames;
          } else {
            assert questionChildNames.equals(QUESTION_CHILDREN) :
                questionChildNames;
          }

          final String lexical =
              XMLUtil.getOnlyChild(questionElem, "Lexical").getText();
          final Element tokensElem =
              XMLUtil.getOnlyChild(questionElem, "Tokens");
          final Element conceptsElem =
              XMLUtil.getOnlyChild(questionElem, "Concepts");
          final String logicalForm =
              XMLUtil.getOnlyChild(questionElem, "LogicalForm").getText();
          final String simpleLogicalForm =
              XMLUtil.getOnlyChild(questionElem, "SimpleLogicalForm").getText();

          final Element logicalFormTreeElem = 
              XMLUtil.getOnlyChild(questionElem, "LogicalFormTree");
          assert logicalFormTreeElem.getChildren().size() == 1;
          final TreeNode<String> logicalFormTree =
              getLogicalFormTree(logicalFormTreeElem.getChildren().get(0));
          
          final Document document = new Document(lexical);
          document.setDocumentID(itemID + "." + questionID);
          document.annotate(Sentence.TYPE);

          new LogicalForm(document, logicalFormTree, simpleLogicalForm).attach();

          String timeFrameValue = extractImplicitTimeFrame(logicalFormTree);
          if (timeFrameValue == null) {
            timeFrameValue = "status";
          }
          new TimeFrame(document, timeFrameValue).attach();

          final List<Pair<Integer,Integer>> tokens = new ArrayList<>();
          for (final Element tokenElem : tokensElem.getChildren()) {
            assert tokenElem.getName().equals("Token");
            tokens.add(Pair.of(
                Integer.valueOf(tokenElem.getAttributeValue("cs")),
                Integer.valueOf(tokenElem.getAttributeValue("cl"))));
          }

          for (final Element conceptElem : conceptsElem.getChildren()) {
            assert conceptElem.getName().equals("Concept");
            final int ts = Integer.valueOf(conceptElem.getAttributeValue("ts"));
            final int tl = Integer.valueOf(conceptElem.getAttributeValue("tl"));

            final int firstTokenStartChar = tokens.get(ts).getFirst();
            final int lastTokenStartChar = tokens.get(ts + tl - 1).getFirst();

            final Token firstToken = document.findToken(firstTokenStartChar);
            final Token lastToken = document.findToken(lastTokenStartChar);
            final Text span = firstToken.union(lastToken);

            final Concept concept = new Concept(span,
                conceptElem.getAttributeValue("type"),
                conceptElem.getAttributeValue("pos"),
                conceptElem.getAttributeValue("CUI"),
                conceptElem.getAttributeValue("value"));
            concept.attach();
          }

          if (this.readAnswerAnnotations) {
            final Element answerElem =
                XMLUtil.getOnlyChild(questionElem, "Answer");

            final List<String> answerChildNames =
                XMLUtil.getChildrenNames(answerElem);
            assert answerChildNames.equals(ANSWER_CHILDREN) :
                answerChildNames;

            final String patientID =
                XMLUtil.getOnlyChild(answerElem, "PatientID").getText();
            final Element resourcesElem =
                XMLUtil.getOnlyChild(answerElem, "Resources");

            final List<String> resourceIDs = new ArrayList<>();
            for (final Element resource : resourcesElem.getChildren()) {
              resourceIDs.add(resource.getAttribute("id").getValue());
            }

            new Answer(document, patientID, resourceIDs).attach();
          }
          
          document.setDocumentID(questionID);
          questions.add(document);
        }
      }
      log.info("Loaded {0} Questions", questions.size());
    }
    catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return questions;
  }

  /**
   * Read the questions and its properties from the XML file, returning them as
   * a <code>List</code> of {@link Document}s.
   */
  public List<Document> readQuestionsWithAnswers() {
    this.readAnswerAnnotations = true;
    List<Document> questions = this.readQuestions();
    this.readAnswerAnnotations = false;

    return questions;
  }

  /**
   * Converts a <code>Node</code> element in the XML to a {@link TreeNode}.
   */
  public TreeNode<String> getLogicalFormTree(final Element nodeElem) {
    assert nodeElem.getName().equals("Node");

    String v = nodeElem.getAttributeValue("value");

    final TreeNode<String> node = new TreeNode<>(v);
    for (final Element child : nodeElem.getChildren()) {
      node.addChild(getLogicalFormTree(child));
    }
    return node;
  }

  public static String extractImplicitTimeFrame(TreeNode<String> node) {
    if (node.getItem().startsWith("has_")) {
      final List<String> split = Strings.split(node.getItem(), ",");
      assert split.size() < 5: "more than 4 elements in a logical form node";
      if (split.size() > 2) {
        String timeFrame = Strings.trim(split.get(2));
        if (timeFrame.endsWith(")")) {
          timeFrame = timeFrame.substring(0, timeFrame.length() - 1);
        }
        return timeFrame;
      } else {
        return null;
      }
    } else if (node.isLeaf()) {
      return null;
    } else {
      final List<String> children = new ArrayList<>();
      for (final TreeNode<String> child : node.getChildren()) {
        children.add(extractImplicitTimeFrame(child));
      }
      List<String> nonNullResults = children.stream().filter(Objects::nonNull).collect(Collectors.toList());
      int nonNullCount = nonNullResults.size();
      if (nonNullCount > 0) {
        if (nonNullResults.stream().distinct().count() != 1) {
          log.severe("more than one implicit time frames found: " + nonNullResults + " for: " + node.flatTreeString());
        }
        return nonNullResults.get(0);
      } else {
        return null;
      }
    }
  }
}
