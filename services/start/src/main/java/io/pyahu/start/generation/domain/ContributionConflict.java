package io.pyahu.start.generation.domain;

/** Two contributions write the same file or key. Never merged: the recipe that produced them is wrong. */
public class ContributionConflict extends RuntimeException {

    public ContributionConflict(Contribution first, Contribution second) {
        super(second.key() + " is contributed twice: " + first + " and " + second);
    }
}
