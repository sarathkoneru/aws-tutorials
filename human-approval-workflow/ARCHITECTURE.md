# Architecture Deep Dive

## Durable Functions Pattern Implementation

This document explains how the Human Approval Workflow implements the Durable Functions pattern using AWS services.

## Core Concepts

### 1. Checkpointing

**Problem**: Standard Lambda functions are stateless. When a function completes, all state is lost.

**Solution**: We persist workflow state to DynamoDB after each significant step.

```java
// In ExpenseSubmissionHandler
WorkflowState workflowState = new WorkflowState(workflowId, reportId, expenseReport);
stateManager.saveWorkflowState(workflowState);  // Checkpoint saved!
```

**DynamoDB Schema**:
```
workflowId (PK)          | String  | Unique workflow identifier
reportId                 | String  | Expense report ID
status                   | String  | SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, etc.
expenseReportJson        | String  | Serialized expense report data
approvalToken            | String  | Security token for callback validation
currentStep              | String  | Current workflow step
createdAt                | Number  | Workflow creation timestamp
updatedAt                | Number  | Last update timestamp
suspendedAt              | Number  | When workflow was suspended (if applicable)
resumedAt                | Number  | When workflow resumed (if applicable)
rejectionReason          | String  | Reason for rejection (if rejected)
```

### 2. Suspension

**Problem**: Lambda has a maximum timeout of 15 minutes. We need to wait potentially days.

**Solution**: Instead of keeping Lambda running, we:
1. Save checkpoint
2. Exit the Lambda function
3. Send callback mechanism (email with link)
4. Lambda finishes, no longer running

```java
// Mark workflow as suspended
stateManager.suspendWorkflow(workflowId);

// Lambda exits here - NO LAMBDA RUNNING = NO COST
// Workflow state safely persisted in DynamoDB
```

**Cost Comparison**:
```
Traditional Polling Approach:
- Lambda runs every 30 seconds checking for approval
- 30 days × 2,880 checks/day = 86,400 invocations
- Cost: ~$15-20 per workflow

Durable Functions Approach:
- 2 Lambda invocations total (submit + callback)
- Cost: ~$0.0000002 per workflow
- Savings: 99.999%
```

### 3. Resumption via Callbacks

**Problem**: How do we resume the workflow when the manager responds?

**Solution**: Email contains callback URL that triggers a new Lambda invocation:

```
https://api.example.com/approval?workflowId=workflow-123&token=abc123&action=approve
                                        ↓
                              ApprovalCallbackHandler
                                        ↓
                         Load checkpoint from DynamoDB
                                        ↓
                              Resume workflow execution
```

**Security**:
- Approval token validates the callback
- Token stored in DynamoDB checkpoint
- Prevents unauthorized workflow resumption

```java
// Validate token before resuming
if (!stateManager.validateApprovalToken(workflowId, token)) {
    return error("Invalid approval link");
}

// Load checkpoint and resume
WorkflowState state = stateManager.getWorkflowState(workflowId);
// Continue execution from where we left off...
```

## Workflow State Machine

```
┌─────────────┐
│  SUBMITTED  │  Initial state when expense report is submitted
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│ PENDING_APPROVAL │  SUSPENDED - waiting for manager
└──────┬───────────┘  (This is where suspension happens)
       │
       │  Manager clicks approve/reject
       │  (Could be days later!)
       │
       ├───────────┬──────────┐
       ▼           ▼          ▼
   ┌─────────┐ ┌──────────┐ │
   │APPROVED │ │ REJECTED │ │
   └────┬────┘ └────┬─────┘ │
        │           │        │
        ▼           ▼        ▼
┌──────────────┐ (End)   (Timeout)
│PAYMENT_PROC │
└──────┬───────┘
       │
       ▼
    (End)
```

## Data Flow

### Phase 1: Expense Submission

```
1. API Gateway receives POST /submit
   ↓
2. ExpenseSubmissionHandler Lambda invoked
   ↓
3. Parse ExpenseReport from request body
   ↓
4. Generate workflowId and approvalToken
   ↓
5. Create WorkflowState (checkpoint)
   ↓
6. Save to DynamoDB (CHECKPOINTING)
   ↓
7. Send approval email via SES
   ↓
8. Update state to PENDING_APPROVAL (SUSPENSION)
   ↓
9. Return response to user
   ↓
10. Lambda exits (NO LAMBDA RUNNING)
```

**Lambda Execution Time**: ~3 seconds
**Cost**: ~$0.0000001

### Phase 2: Waiting Period

```
🕐 Time passes (minutes, hours, or days)
   ↓
📧 Manager sees email in inbox
   ↓
🤔 Manager decides to approve/reject
   ↓
📝 No Lambda running during this time!
   ↓
💰 No cost during this time!
   ↓
✅ DynamoDB stores checkpoint (minimal cost: ~$0.0000001/month)
```

### Phase 3: Approval Callback

```
1. Manager clicks approve/reject link
   ↓
2. API Gateway receives GET /approval?workflowId=...&token=...
   ↓
3. ApprovalCallbackHandler Lambda invoked (RESUMPTION)
   ↓
4. Validate approvalToken for security
   ↓
5. Load WorkflowState from DynamoDB (CHECKPOINT RESTORED)
   ↓
6. Check current status (must be PENDING_APPROVAL)
   ↓
7. Calculate suspension duration
   ↓
8. Process decision:
   - If APPROVE: Update to APPROVED → Process payment → PAYMENT_PROCESSED
   - If REJECT: Update to REJECTED → Send rejection notification
   ↓
9. Send notification email to employee
   ↓
10. Return HTML response to manager
   ↓
11. Lambda exits
```

**Lambda Execution Time**: ~2 seconds
**Cost**: ~$0.0000001

## Key Design Patterns

### 1. Idempotency

Same approval link clicked multiple times = same result:

```java
if (workflowState.getStatus() != WorkflowState.Status.PENDING_APPROVAL) {
    return error("This approval has already been processed");
}
```

### 2. Security

Approval token prevents unauthorized resumption:

```java
String approvalToken = UUID.randomUUID().toString();
// Stored in DynamoDB checkpoint
// Validated on callback
```

### 3. Observability

Track suspension duration for monitoring:

```java
public long getSuspensionDurationSeconds() {
    if (suspendedAt == null) return 0;
    Instant endTime = resumedAt != null ? resumedAt : Instant.now();
    return endTime.getEpochSecond() - suspendedAt.getEpochSecond();
}
```

### 4. Fault Tolerance

Workflow can be queried and resumed at any time:

```java
// Query workflow status
WorkflowState state = stateManager.getWorkflowState(workflowId);

// Can resume from any checkpoint
// Can retry failed operations
// State is durable and consistent
```

## Comparison: This vs AWS Step Functions

| Feature | This PoC | AWS Step Functions |
|---------|----------|-------------------|
| Checkpointing | Manual (DynamoDB) | Built-in |
| Suspension | Manual (email callback) | Built-in (`.waitForTaskToken`) |
| Cost | Pay for Lambda + DynamoDB | Pay for state transitions |
| Flexibility | Full control | Opinionated workflow |
| Learning Curve | Shows underlying concepts | Abstracts complexity |
| Use Case | Educational, custom needs | Production workflows |

**Why This PoC Matters**:
- Understanding the underlying patterns helps you use Step Functions more effectively
- Some scenarios require custom implementations
- Demonstrates the core concepts of durable execution

## Extension Ideas

### 1. Add Timeout Handling

```java
// If manager doesn't respond in 7 days, auto-reject
if (Duration.between(suspendedAt, Instant.now()).toDays() > 7) {
    autoReject(workflowId);
}
```

### 2. Add Workflow History

```java
// Track every state transition
List<WorkflowEvent> history = [
    {timestamp: "2025-12-11T10:00:00Z", event: "SUBMITTED"},
    {timestamp: "2025-12-11T10:00:03Z", event: "SUSPENDED"},
    {timestamp: "2025-12-13T14:23:15Z", event: "APPROVED"},
    ...
]
```

### 3. Add Retry Logic

```java
// Retry email sending on failure
int maxRetries = 3;
for (int i = 0; i < maxRetries; i++) {
    try {
        emailService.sendApprovalRequest(state);
        break;
    } catch (Exception e) {
        if (i == maxRetries - 1) throw e;
        Thread.sleep(1000 * (i + 1));
    }
}
```

### 4. Add Multi-Level Approvals

```java
// Require multiple approvers
if (amount > 5000) {
    requireApprovals(["manager", "director", "cfo"]);
}
```

## Performance Metrics

### Latency
- Submission: ~3 seconds (API Gateway + Lambda + DynamoDB + SES)
- Callback: ~2 seconds (API Gateway + Lambda + DynamoDB)

### Throughput
- Limited by AWS service quotas:
  - API Gateway: 10,000 requests/second
  - Lambda: 1,000 concurrent executions (default)
  - DynamoDB: 40,000 read/write capacity units
  - SES: 200 emails/second (sandbox), 50,000/day (production)

### Cost per 1000 Workflows
```
Lambda:
  - 2 invocations × 1000 workflows = 2000 invocations
  - ~3 seconds avg × 512 MB = ~$0.0001

DynamoDB:
  - 1000 writes + 1000 reads ≈ $0.0013

SES:
  - 2000 emails (approval + notification) ≈ $0.20

API Gateway:
  - 2000 requests ≈ $0.007

Total: ~$0.21 per 1000 workflows (~$0.0002 per workflow)
```

## Conclusion

This architecture demonstrates that with proper checkpointing, suspension, and callback mechanisms, you can build durable, long-running workflows on top of Lambda functions - achieving the "impossible" behavior of pausing execution for days without timeouts or excessive costs!
