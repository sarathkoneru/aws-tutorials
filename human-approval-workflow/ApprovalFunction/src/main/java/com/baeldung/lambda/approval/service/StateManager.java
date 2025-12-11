package com.baeldung.lambda.approval.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.baeldung.lambda.approval.model.ExpenseReport;
import com.baeldung.lambda.approval.model.WorkflowState;
import com.google.gson.Gson;

import java.time.Instant;

/**
 * Manages workflow state persistence in DynamoDB
 * This enables CHECKPOINTING - the key feature that allows workflows to pause indefinitely
 */
public class StateManager {

    private final DynamoDB dynamoDB;
    private final String tableName;
    private final Gson gson;

    public StateManager() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        this.dynamoDB = new DynamoDB(client);
        this.tableName = System.getenv("WORKFLOW_TABLE_NAME");
        this.gson = new Gson();
    }

    public StateManager(DynamoDB dynamoDB, String tableName) {
        this.dynamoDB = dynamoDB;
        this.tableName = tableName;
        this.gson = new Gson();
    }

    /**
     * Saves a new workflow state (checkpoint)
     */
    public void saveWorkflowState(WorkflowState state) {
        Table table = dynamoDB.getTable(tableName);

        String expenseReportJson = gson.toJson(state.getExpenseReport());

        Item item = new Item()
                .withPrimaryKey("workflowId", state.getWorkflowId())
                .withString("reportId", state.getReportId())
                .withString("status", state.getStatus().name())
                .withString("expenseReportJson", expenseReportJson)
                .withString("approvalToken", state.getApprovalToken())
                .withString("currentStep", state.getCurrentStep())
                .withLong("createdAt", state.getCreatedAt().getEpochSecond())
                .withLong("updatedAt", state.getUpdatedAt().getEpochSecond());

        if (state.getSuspendedAt() != null) {
            item.withLong("suspendedAt", state.getSuspendedAt().getEpochSecond());
        }
        if (state.getResumedAt() != null) {
            item.withLong("resumedAt", state.getResumedAt().getEpochSecond());
        }
        if (state.getRejectionReason() != null) {
            item.withString("rejectionReason", state.getRejectionReason());
        }

        table.putItem(item);
    }

    /**
     * Retrieves a workflow state by ID (resumes from checkpoint)
     */
    public WorkflowState getWorkflowState(String workflowId) {
        Table table = dynamoDB.getTable(tableName);
        GetItemSpec spec = new GetItemSpec().withPrimaryKey("workflowId", workflowId);

        Item item = table.getItem(spec);
        if (item == null) {
            return null;
        }

        WorkflowState state = new WorkflowState();
        state.setWorkflowId(item.getString("workflowId"));
        state.setReportId(item.getString("reportId"));
        state.setStatus(WorkflowState.Status.valueOf(item.getString("status")));
        state.setCurrentStep(item.getString("currentStep"));
        state.setApprovalToken(item.getString("approvalToken"));
        state.setCreatedAt(Instant.ofEpochSecond(item.getLong("createdAt")));
        state.setUpdatedAt(Instant.ofEpochSecond(item.getLong("updatedAt")));

        if (item.hasAttribute("suspendedAt")) {
            state.setSuspendedAt(Instant.ofEpochSecond(item.getLong("suspendedAt")));
        }
        if (item.hasAttribute("resumedAt")) {
            state.setResumedAt(Instant.ofEpochSecond(item.getLong("resumedAt")));
        }
        if (item.hasAttribute("rejectionReason")) {
            state.setRejectionReason(item.getString("rejectionReason"));
        }

        String expenseReportJson = item.getString("expenseReportJson");
        ExpenseReport expenseReport = gson.fromJson(expenseReportJson, ExpenseReport.class);
        state.setExpenseReport(expenseReport);

        return state;
    }

    /**
     * Updates workflow status (checkpoint update)
     * This is called when the workflow transitions between states
     */
    public void updateWorkflowStatus(String workflowId, WorkflowState.Status newStatus, String currentStep) {
        Table table = dynamoDB.getTable(tableName);

        UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey("workflowId", workflowId)
                .withUpdateExpression("SET #status = :status, currentStep = :step, updatedAt = :updatedAt")
                .withNameMap(new com.amazonaws.services.dynamodbv2.document.utils.NameMap()
                        .with("#status", "status"))
                .withValueMap(new ValueMap()
                        .withString(":status", newStatus.name())
                        .withString(":step", currentStep)
                        .withLong(":updatedAt", Instant.now().getEpochSecond()))
                .withReturnValues(ReturnValue.UPDATED_NEW);

        table.updateItem(updateItemSpec);
    }

    /**
     * Marks workflow as suspended (waiting for human approval)
     * This demonstrates the SUSPENSION capability of durable functions
     */
    public void suspendWorkflow(String workflowId) {
        Table table = dynamoDB.getTable(tableName);

        UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey("workflowId", workflowId)
                .withUpdateExpression("SET #status = :status, suspendedAt = :suspendedAt, currentStep = :step, updatedAt = :updatedAt")
                .withNameMap(new com.amazonaws.services.dynamodbv2.document.utils.NameMap()
                        .with("#status", "status"))
                .withValueMap(new ValueMap()
                        .withString(":status", WorkflowState.Status.PENDING_APPROVAL.name())
                        .withLong(":suspendedAt", Instant.now().getEpochSecond())
                        .withString(":step", "AWAITING_MANAGER_APPROVAL")
                        .withLong(":updatedAt", Instant.now().getEpochSecond()))
                .withReturnValues(ReturnValue.UPDATED_NEW);

        table.updateItem(updateItemSpec);
    }

    /**
     * Marks workflow as resumed (after receiving callback)
     * This demonstrates the RESUMPTION capability of durable functions
     */
    public void resumeWorkflow(String workflowId, WorkflowState.Status newStatus, String rejectionReason) {
        Table table = dynamoDB.getTable(tableName);

        UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey("workflowId", workflowId)
                .withUpdateExpression("SET #status = :status, resumedAt = :resumedAt, currentStep = :step, updatedAt = :updatedAt")
                .withNameMap(new com.amazonaws.services.dynamodbv2.document.utils.NameMap()
                        .with("#status", "status"))
                .withValueMap(new ValueMap()
                        .withString(":status", newStatus.name())
                        .withLong(":resumedAt", Instant.now().getEpochSecond())
                        .withString(":step", newStatus == WorkflowState.Status.APPROVED ? "PROCESSING_PAYMENT" : "REJECTED")
                        .withLong(":updatedAt", Instant.now().getEpochSecond()));

        if (rejectionReason != null) {
            updateItemSpec = updateItemSpec
                    .withUpdateExpression("SET #status = :status, resumedAt = :resumedAt, currentStep = :step, updatedAt = :updatedAt, rejectionReason = :reason")
                    .withValueMap(new ValueMap()
                            .withString(":status", newStatus.name())
                            .withLong(":resumedAt", Instant.now().getEpochSecond())
                            .withString(":step", "REJECTED")
                            .withLong(":updatedAt", Instant.now().getEpochSecond())
                            .withString(":reason", rejectionReason));
        }

        table.updateItem(updateItemSpec.withReturnValues(ReturnValue.UPDATED_NEW));
    }

    /**
     * Validates approval token for security
     */
    public boolean validateApprovalToken(String workflowId, String token) {
        WorkflowState state = getWorkflowState(workflowId);
        return state != null && state.getApprovalToken() != null && state.getApprovalToken().equals(token);
    }
}
