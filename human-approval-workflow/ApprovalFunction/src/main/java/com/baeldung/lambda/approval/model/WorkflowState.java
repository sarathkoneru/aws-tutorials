package com.baeldung.lambda.approval.model;

import java.time.Instant;

/**
 * Represents the persisted state of a workflow (checkpoint)
 * This enables the "durable function" behavior - allowing workflows to pause for days/weeks
 */
public class WorkflowState {

    public enum Status {
        SUBMITTED,           // Expense report submitted
        PENDING_APPROVAL,    // Waiting for manager approval (SUSPENDED)
        APPROVED,            // Manager approved
        REJECTED,            // Manager rejected
        PAYMENT_PROCESSED,   // Payment completed
        FAILED               // Workflow failed
    }

    private String workflowId;
    private String reportId;
    private Status status;
    private ExpenseReport expenseReport;
    private String approvalToken;     // Unique token for callback authentication
    private Instant createdAt;
    private Instant updatedAt;
    private Instant suspendedAt;      // When workflow was suspended
    private Instant resumedAt;        // When workflow was resumed
    private String rejectionReason;
    private String currentStep;

    public WorkflowState() {
    }

    public WorkflowState(String workflowId, String reportId, ExpenseReport expenseReport) {
        this.workflowId = workflowId;
        this.reportId = reportId;
        this.expenseReport = expenseReport;
        this.status = Status.SUBMITTED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.currentStep = "INITIALIZATION";
    }

    // Getters and Setters

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public ExpenseReport getExpenseReport() {
        return expenseReport;
    }

    public void setExpenseReport(ExpenseReport expenseReport) {
        this.expenseReport = expenseReport;
    }

    public String getApprovalToken() {
        return approvalToken;
    }

    public void setApprovalToken(String approvalToken) {
        this.approvalToken = approvalToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public Instant getResumedAt() {
        return resumedAt;
    }

    public void setResumedAt(Instant resumedAt) {
        this.resumedAt = resumedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    /**
     * Calculates how long the workflow has been suspended
     */
    public long getSuspensionDurationSeconds() {
        if (suspendedAt == null) {
            return 0;
        }
        Instant endTime = resumedAt != null ? resumedAt : Instant.now();
        return endTime.getEpochSecond() - suspendedAt.getEpochSecond();
    }

    @Override
    public String toString() {
        return "WorkflowState{" +
                "workflowId='" + workflowId + '\'' +
                ", reportId='" + reportId + '\'' +
                ", status=" + status +
                ", currentStep='" + currentStep + '\'' +
                ", createdAt=" + createdAt +
                ", suspensionDuration=" + getSuspensionDurationSeconds() + "s" +
                '}';
    }
}
