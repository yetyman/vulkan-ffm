package io.github.yetyman.vulkan.graph;

/**
 * Configurable strategy for handling pass execution failures.
 * Set per-graph as a default, or overridden per-pass.
 */
public interface PassFailureStrategy {

    /**
     * Called when a pass fails during execution.
     *
     * @param failure details about the failure
     * @return the recovery action to take
     */
    RecoveryAction onFailure(PassFailure failure);

    /** Throw immediately, abort the submission */
    static PassFailureStrategy abort() {
        return failure -> RecoveryAction.ABORT;
    }

    /** Skip the failed pass, continue with remaining passes */
    static PassFailureStrategy skipAndContinue() {
        return failure -> RecoveryAction.SKIP;
    }

    /** Retry once, then abort if still failing */
    static PassFailureStrategy retryOnce() {
        return failure -> failure.retryCount() == 0 ? RecoveryAction.RETRY : RecoveryAction.ABORT;
    }

    /** Skip and disable the pass for N future submissions, then retry */
    static PassFailureStrategy skipAndBackoff(int disableFrames) {
        return failure -> RecoveryAction.skipFor(disableFrames);
    }

    /** Log and continue (for truly optional/cosmetic passes) */
    static PassFailureStrategy logAndContinue() {
        return failure -> RecoveryAction.LOG_AND_CONTINUE;
    }

    enum RecoveryAction {
        ABORT,
        SKIP,
        RETRY,
        LOG_AND_CONTINUE;

        private int disableFrames;

        static RecoveryAction skipFor(int frames) {
            RecoveryAction action = SKIP;
            action.disableFrames = frames;
            return action;
        }

        public int disableFrames() { return disableFrames; }
    }

    record PassFailure(
        String passName,
        FailureType type,
        String message,
        int submissionIndex,
        int retryCount
    ) {}

    enum FailureType {
        SHADER_ERROR,
        OUT_OF_MEMORY,
        DEVICE_LOST,
        TIMEOUT,
        UNKNOWN
    }
}
