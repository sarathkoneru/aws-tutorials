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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lambda handler for expense report submission
 *
 * This demonstrates the FIRST PHASE of the durable function workflow:
 * 1. Receive expense report
 * 2. Create workflow state (checkpoint)
 * 3. Send approval email to manager
 * 4. SUSPEND the workflow (mark as PENDING_APPROVAL)
 *
 * The workflow will remain suspended until the manager clicks approve/reject
 * This can take minutes, hours, or even DAYS - demonstrating the "impossible"
 * behavior for standard Lambda functions
 */
public class ExpenseSubmissionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseSubmissionHandler.class);

    private final StateManager stateManager;
    private final EmailService emailService;
    private final Gson gson;

    public ExpenseSubmissionHandler() {
        this.stateManager = new StateManager();
        this.emailService = new EmailService();
        this.gson = new Gson();
    }

    public ExpenseSubmissionHandler(StateManager stateManager, EmailService emailService) {
        this.stateManager = stateManager;
        this.emailService = emailService;
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Received expense submission request");

        try {
            // Parse expense report from request body
            ExpenseReport expenseReport = gson.fromJson(input.getBody(), ExpenseReport.class);

            // Generate unique IDs
            String reportId = UUID.randomUUID().toString();
            String workflowId = "workflow-" + reportId;
            String approvalToken = UUID.randomUUID().toString();

            expenseReport.setReportId(reportId);
            expenseReport.setSubmittedAt(Instant.now());

            logger.info("Processing expense report: {} from employee: {}",
                    reportId, expenseReport.getEmployeeName());

            // Create workflow state (CHECKPOINT #1)
            WorkflowState workflowState = new WorkflowState(workflowId, reportId, expenseReport);
            workflowState.setApprovalToken(approvalToken);
            workflowState.setCurrentStep("EXPENSE_SUBMITTED");

            // Persist the initial state
            stateManager.saveWorkflowState(workflowState);
            logger.info("Workflow state saved: {}", workflowId);

            // Send approval request email to manager
            emailService.sendApprovalRequest(workflowState);
            logger.info("Approval email sent to manager: {}", expenseReport.getManagerEmail());

            // SUSPEND the workflow (CHECKPOINT #2)
            // This is the key moment: the workflow pauses here
            // It could stay suspended for 5 minutes or 5 DAYS
            // No Lambda execution is running during this time = NO COST!
            stateManager.suspendWorkflow(workflowId);
            logger.info("Workflow suspended, waiting for manager approval: {}", workflowId);

            // Return response to user
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Expense report submitted successfully");
            responseBody.put("reportId", reportId);
            responseBody.put("workflowId", workflowId);
            responseBody.put("status", "PENDING_APPROVAL");
            responseBody.put("info", "Manager approval email sent. Workflow is now suspended until manager responds.");

            return createResponse(200, gson.toJson(responseBody));

        } catch (Exception e) {
            logger.error("Error processing expense submission", e);

            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("error", "Failed to process expense submission");
            errorBody.put("message", e.getMessage());

            return createResponse(500, gson.toJson(errorBody));
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }
}
