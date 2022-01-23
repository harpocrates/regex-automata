package automata.graph;

import java.util.Optional;

/**
 * @param state ID of the state
 * @param unavoidable whether the state must be reached for there to be a full NFA match
 * @param insideRepetition if inside a repetition, skip groups
 * @param fixedGroup if present, indicates that the position of any match at this state will have
 *                   to be relative the position of the specified group
 */
public record NfaState<Q>(
  Q state,
  boolean insideRepetition,
  boolean unavoidable,
  Optional<GroupLocation> fixedGroup
) {

  public NfaState<Q> withoutFixed() {
    return fixedGroup.isPresent() ? new NfaState<Q>(state, insideRepetition, unavoidable, Optional.empty()) : this;
  }

  public NfaState<Q> withRepetition(boolean insideRep) {
    return insideRep != insideRepetition ? new NfaState<Q>(state, insideRep, unavoidable, fixedGroup) : this;
  }
}
