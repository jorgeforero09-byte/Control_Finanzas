# Security Specification (Phase 0 TDD)

## 1. Data Invariants
- All documents under `/users/{userId}/transactions/{transactionId}`, `/users/{userId}/bank_accounts/{accountId}`, and `/users/{userId}/budgets/{budgetId}` must belong exclusively to `userId == request.auth.uid`.
- Every document must include `userId == request.auth.uid`, valid `createdAt` and `updatedAt` server timestamps (`<= request.time`), and strictly conform to allowed keys (`hasAll` and `hasOnly`).
- `userId` and `createdAt` are immutable across all updates.

## 2. The Dirty Dozen Payloads
1. Unauthenticated read/list on `/users/alice_123/transactions`.
2. Cross-user read (`bob_456` reading `/users/alice_123/transactions/tx_1`).
3. Identity spoofing (`alice_123` creating a transaction with `userId: "bob_456"`).
4. Shadow update (injecting `isVerified: true` into a transaction update).
5. Negative or zero transaction amount (`amount: -50`).
6. Invalid transaction type (`type: "HACK"`).
7. Malformed `yearMonth` (`yearMonth: "2026-130"`).
8. Malformed `dateString` (`dateString: "02-11-2026"`).
9. Oversized `title` (`title` > 120 chars) or `notes` (> 500 chars).
10. Mutating immutable `createdAt` or `userId` during update.
11. Future timestamp (`createdAt` > `request.time`).
12. Cross-user bank account deletion (`bob_456` deleting `/users/alice_123/bank_accounts/acc_1`).
