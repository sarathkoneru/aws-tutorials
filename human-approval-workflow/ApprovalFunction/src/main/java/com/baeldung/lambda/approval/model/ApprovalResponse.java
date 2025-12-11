package com.baeldung.lambda.approval.model;

/**
 * Represents the response from a manager's approval or rejection
 */
public class ApprovalResponse {

    private String workflowId;
    private String token;
    private boolean approved;
    private String comments;

    public ApprovalResponse() {
    }

    public ApprovalResponse(String workflowId, String token, boolean approved, String comments) {
        this.workflowId = workflowId;
        this.token = token;
        this.approved = approved;
        this.comments = comments;
    }

    // Getters and Setters

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    @Override
    public String toString() {
        return "ApprovalResponse{" +
                "workflowId='" + workflowId + '\'' +
                ", approved=" + approved +
                ", comments='" + comments + '\'' +
                '}';
    }
}
