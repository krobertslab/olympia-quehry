package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A match between a lexicon entry and a set of {@link Token}s.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class LexiconMatch {
  private static final Log log = new Log(LexiconMatch.class);

  private final LexiconEntry entry;
  private final List<Token> tokens;

  /**
   * Creates a new <code>LexiconMatch</code> between the given
   * {@link LexiconEntry} and {@link Token}s.
   */
  public LexiconMatch(final LexiconEntry entry, final List<Token> tokens) {
    this.entry = entry;
    this.tokens = tokens;
  }

  /**
   * Returns the {@link LexiconEntry} for this <code>LexiconMatch</code>.
   */
  public LexiconEntry getEntry() {
    return entry;
  }

  /**
   * Returns the {@link Token}s for this <code>LexiconMatch</code>.
   */
  public List<Token> getTokens() {
    return tokens;
  }

  /**
   * Returns the {@link Token} offsets for this <code>LexiconMatch</code>.
   */
  public List<Integer> getTokenOffsets() {
    final List<Integer> offsets = new ArrayList<>(tokens.size());
    for (final Token token : tokens) {
      offsets.add(token.getTokenOffset());
    }
    Collections.sort(offsets);
    return offsets;
  }

  /**
   * Returns a <code>String</code> representation of this
   * <code>LexiconMatch</code>.
   */
  @Override
  public String toString() {
    return "LexiconMatch{Entry=" + entry + ";Tokens=" + tokens + "}";
  }

}
