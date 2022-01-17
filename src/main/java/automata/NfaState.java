package automata;

import java.util.Optional;

/**
 * @param state ID of the state
 * @param unavoidable whether the state must be reached for there to be a full NFA match
 * @param fixedGroup if present, indicates that the position of any match at this state will have
 *                   to be relative the position of the specified group
 */
public record NfaState<Q>(
  Q state,
  boolean unavoidable,
  Optional<RelativeGroupLocation> fixedGroup
) {

  public NfaState<Q> withoutFixed() {
    return fixedGroup.isPresent() ? new NfaState<Q>(state, unavoidable, Optional.empty()) : this;
  }
}
