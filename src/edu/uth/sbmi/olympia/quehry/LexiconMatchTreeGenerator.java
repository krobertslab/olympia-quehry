package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.DependencyTree;
import edu.uth.sbmi.olympia.text.Sentence;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.text.TextComparators;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Strings;
import edu.uth.sbmi.olympia.util.Triple;
import edu.uth.sbmi.olympia.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Creates the {@link LexiconMatchTree}s for a {@link Sentence}.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class LexiconMatchTreeGenerator {
  private static final Log log = new Log(LexiconMatchTreeGenerator.class);
  private static final Set<String> CONCEPT_TYPES = Util.hashSet(
      "abnormality", "activity", "anatomy", "antibiotic", "attribute",
      "chemical", "concept", "device", "disease", "element", "finding", "food", "function",
      "injury", "intraoperative", "material", "organism",
      "problem", "procedure", "professional", "quantitative", "substance", "symptom", "virus",
      // FHIR resource types
      "Condition", "Encounter", "Immunization", "MedicationOrder", "Observation", "CarePlan", "DiagnosticReport",
      "Procedure", "AllergyIntolerance", "Goal",
      // MetaMap semantic types
      "Anatomical Abnormality", "Clinical Attribute", "Clinical Drug", "Daily or Recreational Activity",
      "Diagnostic Procedure", "Disease or Syndrome", "Drug Delivery Device", "Finding", "Health Care Activity",
      "Immunologic Factor", "Injury or Poisoning", "Intellectual Product", "Laboratory Procedure", "Neoplastic Process",
      "Organism Function", "Pathologic Function", "Temporal Concept", "Therapeutic or Preventive Procedure",
      "aapp", "acab", "acty", "aggp", "amas", "amph", "anab", "anim", "anst", "antb", "arch", "bacs", "bact", "bdsu",
      "bdsy", "bhvr", "biof", "bird", "blor", "bmod", "bodm", "bpoc", "bsoj", "celc", "celf", "cell", "cgab", "chem",
      "chvf", "chvs", "clas", "clna", "clnd", "cnce", "comd", "crbs", "diap", "dora", "drdd", "dsyn", "edac", "eehu",
      "elii", "emod", "emst", "enty", "enzy", "euka", "evnt", "famg", "ffas", "fish", "fndg", "fngs", "food", "ftcn",
      "genf", "geoa", "gngm", "gora", "grpa", "grup", "hcpp", "hcro", "hlca", "hops", "horm", "humn", "idcn", "imft",
      "inbe", "inch", "inpo", "inpr", "irda", "lang", "lbpr", "lbtr", "mamm", "mbrt", "mcha", "medd", "menp", "mnob",
      "mobd", "moft", "mosq", "neop", "nnon", "npop", "nusq", "ocac", "ocdi", "orch", "orga", "orgf", "orgm", "orgt",
      "ortf", "patf", "phob", "phpr", "phsf", "phsu", "plnt", "podg", "popg", "prog", "pros", "qlco", "qnco", "rcpt",
      "rept", "resa", "resd", "rnlw", "sbst", "shro", "socb", "sosy", "spco", "tisu", "tmco", "topp", "virs", "vita",
      "vtbt");

  private final List<LexiconEntry> entries;
  private final boolean allowConceptTokensMatching;
  private final LexiconMatchTreeFilteringRules treeFilter =
        new LexiconMatchTreeFilteringRules();

  /**
   * Creates a new <code>LexiconMatchTreeGenerator</code> using the set of
   * {@link LexiconEntry}s and an option to match the concept tokens.
   */
  public LexiconMatchTreeGenerator(final List<LexiconEntry> entries, final boolean allowConceptTokensMatching) {
    this.entries = entries;
    this.allowConceptTokensMatching = allowConceptTokensMatching;
  }

  /**
   * Creates the {@link LexiconMatchTree}s for the given {@link Sentence}.
   */
  public List<LexiconMatchTree> generate(final Sentence sentence) {
    log.fine("Generating LexiconMatchTrees for Sentence: {0}", sentence.wrap());
    final DependencyTree depTree = new DependencyTree(sentence);
    log.finest("Dependency Tree:\n{0}", depTree);

    // Build an inverse index of all the tokens and concepts in this sentence,
    // should make things a bit faster.
    final Map<String,List<Text>> index = new LinkedHashMap<>();
    final List<Token> conceptTokens = new ArrayList<>();

    // Index tokens and concepts
    for (final Concept concept : sentence.getSub(Concept.class)) {
      log.finer("Concept: {0}", concept);
      final String key;
      if (CONCEPT_TYPES.contains(concept.getType())) {
        key = "__concept__";
      }
      else {
        key = "__" + concept.getType() + "__";
      }

      if (index.containsKey(key) == false) {
        index.put(key, new ArrayList<Text>());
      }
      index.get(key).add(concept);
      for (Token token : concept.getText().getTokens()){
        conceptTokens.add(token);
      }
    }
    for (final Token token : sentence.getTokens()) {
      final String key = token.asRawString().toLowerCase();
      if (index.containsKey(key) == false) {
        index.put(key, new ArrayList<Text>());
      }
      index.get(key).add(token);
    }

    // Try to find a match for the entries
    final List<LexiconMatch> matches = new ArrayList<>();
    for (final LexiconEntry entry : entries) {
      final List<LexiconMatch> entryMatches = findMatches(entry, index, depTree);
      if (entryMatches != null) {
        matches.addAll(entryMatches);
      }
    }

    // Default any non-existing lexicon matches to null
    final Set<Token> sentenceTokens = new HashSet<>(sentence.getTokens());
    if (!this.allowConceptTokensMatching) {
      sentenceTokens.removeAll(conceptTokens);
    } else {
      final Set<Token> allNonConceptMatchedTokens = new HashSet<>();
      for (final LexiconMatch match : matches) {
        if (!match.getEntry().getLogicalForm().equals("lambda.concept")) {
          allNonConceptMatchedTokens.addAll(match.getTokens());
        }
      }
      sentenceTokens.removeAll(allNonConceptMatchedTokens);
    }

    int lineNum = 0;
    for (final Token token : sentenceTokens) {
      log.DBG("Token not covered by the lexicon: {0}", token);
      // Check if the token is a punctuation. If yes, do not create a default lexicon entry for it.
      if (token.asRawString().length() == 1 && Strings.isPunctuation(token.asRawString().charAt(0))) {
        log.DBG("  Skipping punctuation: {0}", token);
        continue;
      }
      // Adding a default entry for this token: "token --> null"
      lineNum--;
      final String id = lineNum + ":" + token.asRawString();
      final LexiconEntry entry = new LexiconEntry(id, token.asRawString(), "null", lineNum);
      matches.add(new LexiconMatch(entry, Collections.unmodifiableList(Collections.singletonList(token))));
    }

    final List<List<LexiconMatch>> candidates = identifyCandidates(matches);
    log.fine("Generated {0} Candidates", candidates.size());

    final List<LexiconMatchTree> trees = new ArrayList<>();
    for (final List<LexiconMatch> candidate : candidates) {
      final LexiconMatchTree tree = new LexiconMatchTree(depTree);
      for (final LexiconMatch match : candidate) {
        tree.addLexiconMatch(match);
      }
      assert tree.isFullyMatched();
      trees.add(tree);
    }
    log.fine("Created {0} Initial LexiconMatchTrees", trees.size());

    // Filter
    final Iterator<LexiconMatchTree> treeIterator = trees.iterator();
    while (treeIterator.hasNext()) {
      final LexiconMatchTree tree = treeIterator.next();
      if (treeFilter.hasTypeMismatch(tree)) {
        treeIterator.remove();
      }
    }
    log.fine("Created {0} Final LexiconMatchTrees", trees.size());

    // Logging
    if (log.finest()) {
      for (int i = 0; i < trees.size(); i++) {
        final List<LexiconMatch> lexMatches = new ArrayList<>(
            trees.get(i).getLexiconMatches());
        Collections.sort(lexMatches, new Comparator<LexiconMatch>() {
          @Override
          public int compare(final LexiconMatch match1, final LexiconMatch match2) {
            final Integer offset1 = match1.getTokenOffsets().get(0);
            final Integer offset2 = match2.getTokenOffsets().get(0);
            return offset1.compareTo(offset2);
          }
        });
        for (final LexiconMatch match : lexMatches) {
          log.finest("  {0}", match.getEntry());
        }
      }
    }

    ModuleTests.checkGeneratedLexiconMatchTrees(trees);
    return trees;
  }

  /**
   * Returns the {@link LexiconMatch} for the given {@link LexiconEntry} using
   * the {@link DependencyTree} and <var>index</var>, or <code>null</code> if
   * there is no match.
   */
  private List<LexiconMatch> findMatches(final LexiconEntry entry,
                                         final Map<String,List<Text>> index,
                                         final DependencyTree depTree) {
    log.nano("LexiconEntry: {0}", entry.getPattern());
    final Map<String,List<Text>> nodeMatchesMulti = new LinkedHashMap<>();
    for (final String node : entry.getNodes()) {
      if (!index.containsKey(node)) {
        return null;
      }
      nodeMatchesMulti.put(node, index.get(node));
    }

    final List<LexiconMatch> matches = new ArrayList<>();
    for (final Map<String,Text> nodeMatches : combs(nodeMatchesMulti)) {
      boolean match = true;
      for (final Triple<String,String,String> edge : entry.getEdges()) {
        log.nano("Edge: {0}", edge);
        log.nano("Nodes: {0}", nodeMatches);
        final Text text1 = nodeMatches.get(edge.getFirst());
        final Text text2 = nodeMatches.get(edge.getThird());
        final String type = DependencyTree.DOWN_DELIM + edge.getSecond();
        log.nano("Type: {0}", type);

        match = false;
        doubleTokenLoop:
        for (final Token token1 : text1.getTokens()) {
          for (final Token token2 : text2.getTokens()) {
            final String path = depTree.getPath(token1, token2, 1);
            log.pico("  Path({0}, {1}) = {2}", token1, token2, path);
            if (path != null && path.equals(type)) {
              match = true;
              break doubleTokenLoop;
            }
          }
        }
        log.nano("Match: {0}", match);
        if (!match) {
          break;
        }
      }

      if (match == false) {
        continue;
      }

      final List<Token> tokens = new ArrayList<>();
      for (final Text value : nodeMatches.values()) {
        tokens.addAll(value.getTokens());
      }
      Collections.sort(tokens, TextComparators.startToken());
      log.finer("Creating LexiconMatch.  Entry: \"{0}\"  Tokens: {1}", entry, tokens);
      matches.add(new LexiconMatch(entry, tokens));
    }
    return matches;
  }

  /**
   * Creates all possible node-to-{@link Text} combinations when multiple
   * <code>Text</code> spans are in the values of the given <code>Map</code>.
   */
  private List<Map<String,Text>> combs(final Map<String,List<Text>> map) {
    return combs(map, new ArrayList<String>(map.keySet()));
  }
  private List<Map<String,Text>> combs(final Map<String,List<Text>> map,
                                       final List<String> keys) {
    final List<Map<String,Text>> combs = new ArrayList<>();
    final String key = keys.get(0);
    if (keys.size() == 1) {
      for (final Text value : map.get(key)) {
        final Map<String,Text> comb = new TreeMap<>();
        comb.put(key, value);
        combs.add(comb);
      }
      return combs;
    }
    final List<String> subList = keys.subList(1, keys.size());
    for (final Map<String,Text> subComb : combs(map, subList)) {
      for (final Text value : map.get(key)) {
        final Map<String,Text> comb = new TreeMap<>();
        comb.put(key, value);
        for (final Map.Entry<String,Text> e : subComb.entrySet()) {
          comb.put(e.getKey(), e.getValue());
        }
        combs.add(comb);
      }
    }
    return combs;
  }

  /**
   * Generate all possible combinations of the lexicon matches.
   */
  private List<List<LexiconMatch>> identifyCandidates(
      final List<LexiconMatch> matches) {
    if (matches.size() >= 20) {
      log.severe("Too many matches.");
    }

    final Set<Token> allTokens = new HashSet<>();
    for (final LexiconMatch match : matches) {
      allTokens.addAll(match.getTokens());
    }

    final List<List<LexiconMatch>> candidates = new ArrayList<>();

    final int n = matches.size(); // let n = 5
    final int m = ((int) Math.pow(2.0, n)) - 1;
    final String s_all = Strings.charStr('0', n); // s_all = '00000
    iterLoop:
    for (int i = 0; i <= m; i++) {
      final String s1 = Integer.toBinaryString(i); // s1 = '10'
      final String s = s_all.substring(s1.length()) + s1; // s = '0' + '2'

      final List<LexiconMatch> subset = new ArrayList<>();
      final Set<Token> subsetTokens = new HashSet<>();
      for (int j = 0; j < n; j++) {
        if (s.charAt(j) == '1') {
          subset.add(matches.get(j));
          for (final Token token : matches.get(j).getTokens()) {
            if (subsetTokens.add(token) == false) {
              continue iterLoop;
            }
          }
        }
      }
      if (subsetTokens.size() == allTokens.size()) {
        candidates.add(subset);
      }
    }
    return candidates;
  }

}
