package ma.ensias.zerotrust.common.model;

public class PolicyDecision {
    private final boolean allowed;
    private final String policyId;
    private final String reason;

    public PolicyDecision(boolean allowed, String policyId, String reason) {
        this.allowed = allowed;
        this.policyId = policyId;
        this.reason = reason;
    }

    public boolean allowed() { return allowed; }
    public String policyId() { return policyId; }
    public String reason() { return reason; }

    public static PolicyDecision allow(String policyId, String reason) {
        return new PolicyDecision(true, policyId, reason);
    }

    public static PolicyDecision deny(String policyId, String reason) {
        return new PolicyDecision(false, policyId, reason);
    }
}