package automata.parser;

import java.util.regex.PatternSyntaxException;

/**
 * Pattern syntax exceptions for constructs which are valid regular expressions
 * but are intentionally not supported (and likely never will be).
 *
 * @author Alec Theriault
 */
public class UnsupportedPatternSyntaxException extends PatternSyntaxException {

  @java.io.Serial
  private static final long serialVersionUID = 8537717402669881349L;

  /**
   * (Capitalized) name of the unsupported feature category.
   */
  public final String unsupportedFeatureCategory;
  
  public UnsupportedPatternSyntaxException(
    String unsupportedFeatureCategory,
    String regex,
    int index
  ) {
    super(unsupportedFeatureCategory + " are not supported", regex, index);
    this.unsupportedFeatureCategory = unsupportedFeatureCategory;
  }
}
