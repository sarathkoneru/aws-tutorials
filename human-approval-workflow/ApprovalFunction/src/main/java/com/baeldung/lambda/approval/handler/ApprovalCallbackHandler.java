package com.baeldung.lambda.approval.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.baeldung.lambda.approval.model.ExpenseReport;
import com.baeldung.lambda.approval.model.WorkflowState;
import com.baeldung.lambda.approval.service.EmailService;
import com.baeldung.lambda.approval.service.StateManager;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for manager approval/rejection callbacks
 *
 * This demonstrates the SECOND PHASE of the durable function workflow:
 * 1. Receive callback from manager (approve/reject)
 * 2. Validate the approval token
 * 3. RESUME the workflow from checkpoint
 * 4. Process the approval decision
 * 5. Complete the workflow (payment or rejection)
 *
 * The amazing part: This could be called 5 DAYS after the initial submission!
 * The workflow state was safely persisted in DynamoDB, and we resume from
 * exactly where we left off - demonstrating true durable function behavior
 */
public class ApprovalCallbackHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalCallbackHandler.class);

    private final StateManager stateManager;
    private final EmailService emailService;
    private final Gson gson;

    public ApprovalCallbackHandler() {
        this.stateManager = new StateManager();
        this.emailService = new EmailService();
        this.gson = new Gson();
    }

    public ApprovalCallbackHandler(StateManager stateManager, EmailService emailService) {
        this.stateManager = stateManager;
        this.emailService = emailService;
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Received approval callback request");

        try {
            // Extract parameters from query string
            Map<String, String> queryParams = input.getQueryStringParameters();
            String workflowId = queryParams.get("workflowId");
            String token = queryParams.get("token");
            String action = queryParams.get("action"); // "approve" or "reject"

            logger.info("Processing callback for workflow: {}, action: {}", workflowId, action);

            // Validate token for security
            if (!stateManager.validateApprovalToken(workflowId, token)) {
                logger.warn("Invalid approval token for workflow: {}", workflowId);
                return createHtmlResponse(403, buildErrorHtml("Invalid or expired approval link"));
            }

            // RESUME workflow from checkpoint
            WorkflowState workflowState = stateManager.getWorkflowState(workflowId);
            if (workflowState == null) {
                logger.warn("Workflow not found: {}", workflowId);
                return createHtmlResponse(404, buildErrorHtml("Workflow not found"));
            }

            // Check if already processed
            if (workflowState.getStatus() != WorkflowState.Status.PENDING_APPROVAL) {
                logger.warn("Workflow {} is not in PENDING_APPROVAL state: {}",
                        workflowId, workflowState.getStatus());
                return createHtmlResponse(400, buildErrorHtml(
                        "This approval has already been processed. Current status: " + workflowState.getStatus()));
            }

            // Calculate how long the workflow was suspended
            long suspensionDurationSeconds = workflowState.getSuspensionDurationSeconds();
            String suspensionDuration = formatDuration(suspensionDurationSeconds);

            logger.info("Workflow was suspended for: {}", suspensionDuration);

            boolean approved = "approve".equalsIgnoreCase(action);
            ExpenseReport report = workflowState.getExpenseReport();

            if (approved) {
                // APPROVE: Resume workflow and process payment
                logger.info("Approving expense report: {} for ${}", report.getReportId(), report.getAmount());

                stateManager.resumeWorkflow(workflowId, WorkflowState.Status.APPROVED, null);

                // Simulate payment processing
                processPayment(report);

                // Update workflow to completion
                stateManager.updateWorkflowStatus(workflowId,
                        WorkflowState.Status.PAYMENT_PROCESSED,
                        "COMPLETED");

                // Notify employee
                emailService.sendDecisionNotification(workflowState, true);

                logger.info("Expense report approved and payment processed for workflow: {}", workflowId);

                return createHtmlResponse(200, buildSuccessHtml(
                        "Expense Approved",
                        String.format("The expense report for $%s from %s has been approved. " +
                                        "Payment processing has been initiated.",
                                report.getAmount(), report.getEmployeeName()),
                        suspensionDuration
                ));

            } else {
                // REJECT: Resume workflow and mark as rejected
                logger.info("Rejecting expense report: {}", report.getReportId());

                String rejectionReason = "Manager declined the expense report";
                stateManager.resumeWorkflow(workflowId, WorkflowState.Status.REJECTED, rejectionReason);
                workflowState.setRejectionReason(rejectionReason);

                // Notify employee
                emailService.sendDecisionNotification(workflowState, false);

                logger.info("Expense report rejected for workflow: {}", workflowId);

                return createHtmlResponse(200, buildSuccessHtml(
                        "Expense Rejected",
                        String.format("The expense report for $%s from %s has been rejected.",
                                report.getAmount(), report.getEmployeeName()),
                        suspensionDuration
                ));
            }

        } catch (Exception e) {
            logger.error("Error processing approval callback", e);
            return createHtmlResponse(500, buildErrorHtml("An error occurred while processing your request"));
        }
    }

    /**
     * Simulates payment processing
     * In a real system, this would integrate with a payment processor
     */
    private void processPayment(ExpenseReport report) {
        logger.info("Processing payment of ${} to employee: {}",
                report.getAmount(), report.getEmployeeName());

        // Simulate payment processing delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Payment processed successfully");
    }

    private String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long secs = duration.getSeconds() % 60;

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, secs);
        } else {
            return String.format("%d seconds", secs);
        }
    }

    private APIGatewayProxyResponseEvent createHtmlResponse(int statusCode, String htmlBody) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(htmlBody);
    }

    private String buildSuccessHtml(String title, String message, String suspensionDuration) {
        return String.format(
                "<!DOCTYPE html><html><head><title>%s</title>" +
                        "<style>" +
                        "body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }" +
                        ".success { background-color: #d4edda; border: 1px solid #c3e6cb; color: #155724; padding: 20px; border-radius: 5px; }" +
                        ".info { background-color: #d1ecf1; border: 1px solid #bee5eb; color: #0c5460; padding: 15px; margin-top: 20px; border-radius: 5px; }" +
                        "h1 { color: #155724; }" +
                        "</style></head><body>" +
                        "<div class='success'>" +
                        "<h1>✓ %s</h1>" +
                        "<p>%s</p>" +
                        "</div>" +
                        "<div class='info'>" +
                        "<h3>🎉 Durable Function Demo</h3>" +
                        "<p><strong>Workflow was suspended for:</strong> %s</p>" +
                        "<p>This demonstrates the power of Lambda Durable Functions: " +
                        "The workflow paused execution while waiting for your approval, " +
                        "without any Lambda running or incurring costs during that time!</p>" +
                        "</div>" +
                        "</body></html>",
                title, title, message, suspensionDuration
        );
    }

    private String buildErrorHtml(String errorMessage) {
        return String.format(
                "<!DOCTYPE html><html><head><title>Error</title>" +
                        "<style>" +
                        "body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }" +
                        ".error { background-color: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; padding: 20px; border-radius: 5px; }" +
                        "h1 { color: #721c24; }" +
                        "</style></head><body>" +
                        "<div class='error'>" +
                        "<h1>✗ Error</h1>" +
                        "<p>%s</p>" +
                        "</div>" +
                        "</body></html>",
                errorMessage
        );
    }
}
