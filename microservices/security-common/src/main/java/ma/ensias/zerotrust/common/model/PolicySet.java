package ma.ensias.zerotrust.common.model;

import java.util.List;
import java.util.Map;

public class PolicySet {
    private String version;
    private String defaultEffect;
    private Map<String, Long> tierQuotasBytes;
    private int maxTtlHours;
    private List<PolicyRule> policies;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDefaultEffect() { return defaultEffect; }
    public void setDefaultEffect(String defaultEffect) { this.defaultEffect = defaultEffect; }

    public Map<String, Long> getTierQuotasBytes() { return tierQuotasBytes; }
    public void setTierQuotasBytes(Map<String, Long> tierQuotasBytes) { this.tierQuotasBytes = tierQuotasBytes; }

    public int getMaxTtlHours() { return maxTtlHours; }
    public void setMaxTtlHours(int maxTtlHours) { this.maxTtlHours = maxTtlHours; }

    public List<PolicyRule> getPolicies() { return policies; }
    public void setPolicies(List<PolicyRule> policies) { this.policies = policies; }
}
