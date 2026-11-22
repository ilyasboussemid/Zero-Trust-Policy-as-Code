package ma.ensias.zerotrust.common.model;

public class PolicyRule {
    private String id;
    private String description;
    private String targetService;
    private String endpointPattern;
    private String method;
    private String requiredClientCertCN;
    private String requiredJwtSubject;
    private String requiredRole;
    private boolean enforceTierQuota;
    private boolean requireQuotaApproved;
    private String effect;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }

    public String getEndpointPattern() { return endpointPattern; }
    public void setEndpointPattern(String endpointPattern) { this.endpointPattern = endpointPattern; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getRequiredClientCertCN() { return requiredClientCertCN; }
    public void setRequiredClientCertCN(String requiredClientCertCN) { this.requiredClientCertCN = requiredClientCertCN; }

    public String getRequiredJwtSubject() { return requiredJwtSubject; }
    public void setRequiredJwtSubject(String requiredJwtSubject) { this.requiredJwtSubject = requiredJwtSubject; }

    public String getRequiredRole() { return requiredRole; }
    public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }

    public boolean isEnforceTierQuota() { return enforceTierQuota; }
    public void setEnforceTierQuota(boolean enforceTierQuota) { this.enforceTierQuota = enforceTierQuota; }

    public boolean isRequireQuotaApproved() { return requireQuotaApproved; }
    public void setRequireQuotaApproved(boolean requireQuotaApproved) { this.requireQuotaApproved = requireQuotaApproved; }

    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
}
