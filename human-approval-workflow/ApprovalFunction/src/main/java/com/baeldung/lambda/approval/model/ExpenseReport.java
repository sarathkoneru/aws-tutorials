package com.baeldung.lambda.approval.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an expense report submitted by an employee
 */
public class ExpenseReport {

    private String reportId;
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    private String managerId;
    private String managerEmail;
    private BigDecimal amount;
    private String category;
    private String description;
    private Instant submittedAt;

    public ExpenseReport() {
    }

    public ExpenseReport(String reportId, String employeeId, String employeeName,
                        String employeeEmail, String managerId, String managerEmail,
                        BigDecimal amount, String category, String description) {
        this.reportId = reportId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.managerId = managerId;
        this.managerEmail = managerEmail;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.submittedAt = Instant.now();
    }

    // Getters and Setters

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getManagerEmail() {
        return managerEmail;
    }

    public void setManagerEmail(String managerEmail) {
        this.managerEmail = managerEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String toString() {
        return "ExpenseReport{" +
                "reportId='" + reportId + '\'' +
                ", employeeName='" + employeeName + '\'' +
                ", amount=" + amount +
                ", category='" + category + '\'' +
                ", description='" + description + '\'' +
                ", submittedAt=" + submittedAt +
                '}';
    }
}
