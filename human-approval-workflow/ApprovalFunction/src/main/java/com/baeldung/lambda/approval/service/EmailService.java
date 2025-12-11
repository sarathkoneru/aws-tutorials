package com.baeldung.lambda.approval.service;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import com.baeldung.lambda.approval.model.ExpenseReport;
import com.baeldung.lambda.approval.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for sending approval request emails via Amazon SES
 */
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final AmazonSimpleEmailService sesClient;
    private final String fromEmail;
    private final String callbackBaseUrl;

    public EmailService() {
        this.sesClient = AmazonSimpleEmailServiceClientBuilder.standard().build();
        this.fromEmail = System.getenv("FROM_EMAIL");
        this.callbackBaseUrl = System.getenv("CALLBACK_BASE_URL");
    }

    public EmailService(AmazonSimpleEmailService sesClient, String fromEmail, String callbackBaseUrl) {
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    /**
     * Sends an approval request email to the manager
     * The email contains callback links that will resume the workflow when clicked
     */
    public void sendApprovalRequest(WorkflowState workflowState) {
        ExpenseReport report = workflowState.getExpenseReport();

        String approveUrl = buildCallbackUrl(workflowState.getWorkflowId(),
                                              workflowState.getApprovalToken(),
                                              true);
        String rejectUrl = buildCallbackUrl(workflowState.getWorkflowId(),
                                            workflowState.getApprovalToken(),
                                            false);

        String subject = String.format("Expense Approval Required: $%s from %s",
                report.getAmount(),
                report.getEmployeeName());

        String htmlBody = buildEmailHtml(report, approveUrl, rejectUrl);
        String textBody = buildEmailText(report, approveUrl, rejectUrl);

        try {
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(new Destination().withToAddresses(report.getManagerEmail()))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content().withCharset("UTF-8").withData(htmlBody))
                                    .withText(new Content().withCharset("UTF-8").withData(textBody)))
                            .withSubject(new Content().withCharset("UTF-8").withData(subject)))
                    .withSource(fromEmail);

            SendEmailResult result = sesClient.sendEmail(request);
            logger.info("Approval email sent to {} for workflow {}, MessageId: {}",
                    report.getManagerEmail(),
                    workflowState.getWorkflowId(),
                    result.getMessageId());

        } catch (Exception e) {
            logger.error("Failed to send approval email for workflow {}",
                        workflowState.getWorkflowId(), e);
            throw new RuntimeException("Failed to send approval email", e);
        }
    }

    /**
     * Sends a notification email to the employee about the approval decision
     */
    public void sendDecisionNotification(WorkflowState workflowState, boolean approved) {
        ExpenseReport report = workflowState.getExpenseReport();

        String subject = approved
                ? String.format("Expense Report Approved: $%s", report.getAmount())
                : String.format("Expense Report Rejected: $%s", report.getAmount());

        String message = approved
                ? String.format("Good news! Your expense report for $%s has been approved by your manager. " +
                        "Payment processing will begin shortly.", report.getAmount())
                : String.format("Your expense report for $%s has been rejected. " +
                        "Reason: %s", report.getAmount(),
                        workflowState.getRejectionReason() != null
                                ? workflowState.getRejectionReason()
                                : "No reason provided");

        try {
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(new Destination().withToAddresses(report.getEmployeeEmail()))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withText(new Content().withCharset("UTF-8").withData(message)))
                            .withSubject(new Content().withCharset("UTF-8").withData(subject)))
                    .withSource(fromEmail);

            sesClient.sendEmail(request);
            logger.info("Decision notification sent to {} for workflow {}",
                    report.getEmployeeEmail(), workflowState.getWorkflowId());

        } catch (Exception e) {
            logger.error("Failed to send decision notification for workflow {}",
                    workflowState.getWorkflowId(), e);
        }
    }

    private String buildCallbackUrl(String workflowId, String token, boolean approve) {
        return String.format("%s/approval?workflowId=%s&token=%s&action=%s",
                callbackBaseUrl,
                workflowId,
                token,
                approve ? "approve" : "reject");
    }

    private String buildEmailHtml(ExpenseReport report, String approveUrl, String rejectUrl) {
        return String.format(
                "<html><body>" +
                        "<h2>Expense Report Approval Request</h2>" +
                        "<p>A new expense report requires your approval:</p>" +
                        "<table style='border-collapse: collapse; margin: 20px 0;'>" +
                        "<tr><td style='padding: 8px; font-weight: bold;'>Employee:</td><td style='padding: 8px;'>%s</td></tr>" +
                        "<tr><td style='padding: 8px; font-weight: bold;'>Amount:</td><td style='padding: 8px;'>$%s</td></tr>" +
                        "<tr><td style='padding: 8px; font-weight: bold;'>Category:</td><td style='padding: 8px;'>%s</td></tr>" +
                        "<tr><td style='padding: 8px; font-weight: bold;'>Description:</td><td style='padding: 8px;'>%s</td></tr>" +
                        "<tr><td style='padding: 8px; font-weight: bold;'>Submitted:</td><td style='padding: 8px;'>%s</td></tr>" +
                        "</table>" +
                        "<div style='margin: 30px 0;'>" +
                        "<a href='%s' style='background-color: #28a745; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin-right: 10px;'>✓ APPROVE</a>" +
                        "<a href='%s' style='background-color: #dc3545; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px;'>✗ REJECT</a>" +
                        "</div>" +
                        "<p style='color: #666; font-size: 12px;'>Click one of the buttons above to approve or reject this expense report.</p>" +
                        "</body></html>",
                report.getEmployeeName(),
                report.getAmount(),
                report.getCategory(),
                report.getDescription(),
                report.getSubmittedAt(),
                approveUrl,
                rejectUrl
        );
    }

    private String buildEmailText(ExpenseReport report, String approveUrl, String rejectUrl) {
        return String.format(
                "Expense Report Approval Request\n\n" +
                        "Employee: %s\n" +
                        "Amount: $%s\n" +
                        "Category: %s\n" +
                        "Description: %s\n" +
                        "Submitted: %s\n\n" +
                        "To APPROVE, click: %s\n" +
                        "To REJECT, click: %s\n",
                report.getEmployeeName(),
                report.getAmount(),
                report.getCategory(),
                report.getDescription(),
                report.getSubmittedAt(),
                approveUrl,
                rejectUrl
        );
    }
}
