package com.banking.api;

import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import com.banking.api.entity.User;
import com.banking.api.dto.request.DepositRequest;
import com.banking.api.dto.request.CreatePaymentOrderRequest;
import com.banking.api.dto.request.WithdrawRequest;
import com.banking.api.dto.response.TransactionResponse;
import com.banking.api.exception.ConflictException;
import com.banking.api.exception.InsufficientFundsException;
import com.banking.api.enums.AccountStatus;
import com.banking.api.enums.AccountType;
import com.banking.api.enums.Role;
import com.banking.api.enums.TransactionType;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.IdempotencyRecordRepository;
import com.banking.api.repository.PaymentOrderRepository;
import com.banking.api.repository.TransactionRepository;
import com.banking.api.repository.UserRepository;
import com.banking.api.service.TransactionService;
import com.banking.api.service.ErpPaymentOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class TransactionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "bG9jYWwtdGVzdC1vbmx5LXByb2R1Y3Rpb24ta2V5LWRvLW5vdC1yZXVzZS0wMQ==");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }

    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired PaymentOrderRepository paymentOrderRepository;
    @Autowired TransactionService transactionService;
    @Autowired ErpPaymentOrderService erpPaymentOrderService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        idempotencyRecordRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentErpPaymentRetriesSettleOnceAndPostOneDebit() throws Exception {
        Account account = createAccount();
        account.setBalance(new BigDecimal("5000.00"));
        accountRepository.save(account);
        User owner = account.getUser();
        CreatePaymentOrderRequest request = CreatePaymentOrderRequest.builder()
                .sourceAccountId(account.getId())
                .erpPaymentReference("PAY-" + UUID.randomUUID())
                .invoiceReference("INV-1001")
                .beneficiaryName("Northwind Supplies")
                .beneficiaryAccount("SIM-001-9284")
                .amount(new BigDecimal("1250.00"))
                .currency("USD")
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        String idempotencyKey = "chaste-payment-" + UUID.randomUUID();
        try {
            Future<?> firstFuture = executor.submit(() -> runAsOwner(owner, ready, start,
                    () -> erpPaymentOrderService.create(request, idempotencyKey)));
            Future<?> retryFuture = executor.submit(() -> runAsOwner(owner, ready, start,
                    () -> erpPaymentOrderService.create(request, idempotencyKey)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = (com.banking.api.dto.response.PaymentOrderResponse) firstFuture.get(15, TimeUnit.SECONDS);
            var retry = (com.banking.api.dto.response.PaymentOrderResponse) retryFuture.get(15, TimeUnit.SECONDS);

            assertThat(retry.getId()).isEqualTo(first.getId());
            assertThat(first.getStatus().name()).isEqualTo("SETTLED");
            assertThat(first.getTransactionReference()).isEqualTo(retry.getTransactionReference());
            assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("3750.00");
            assertThat(paymentOrderRepository.count()).isEqualTo(1);
            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ledgerQueriesIncludeBothTransferSidesAndRespectDateBoundaries() {
        Account account = createAccount();
        LocalDateTime march1 = LocalDateTime.parse("2026-03-01T09:00:00");
        LocalDateTime march2 = LocalDateTime.parse("2026-03-02T09:00:00");
        LocalDateTime march3 = LocalDateTime.parse("2026-03-03T09:00:00");
        LocalDateTime march4 = LocalDateTime.parse("2026-03-04T09:00:00");

        save(TransactionType.DEPOSIT, account, null, "100.00", march1);
        save(TransactionType.WITHDRAWAL, account, null, "15.00", march2);
        save(TransactionType.TRANSFER, account, null, "25.00", march3);
        save(TransactionType.TRANSFER, null, account, "10.00", march4);

        BigDecimal opening = transactionRepository.calculateBalanceBefore(account.getId(), march3,
                TransactionType.DEPOSIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER);
        BigDecimal outgoing = transactionRepository.sumOutgoingSpending(account.getId(), march1, march4,
                List.of(TransactionType.WITHDRAWAL, TransactionType.TRANSFER));
        List<Transaction> onMarch3 = transactionRepository.findAccountTransactionsBetween(
                account.getId(), march3, march3.plusDays(1), PageRequest.of(0, 100));

        assertThat(opening).isEqualByComparingTo("85.00");
        assertThat(outgoing).isEqualByComparingTo("40.00");
        assertThat(onMarch3).hasSize(1);
        assertThat(onMarch3.get(0).getAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void depositRetryReturnsOriginalTransactionAndDoesNotApplyTheBalanceTwice() throws Exception {
        Account account = createAccount();
        User owner = account.getUser();
        DepositRequest request = DepositRequest.builder()
                .accountId(account.getId())
                .amount(new BigDecimal("100.00"))
                .description("Integration test funding")
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<TransactionResponse> firstFuture = executor.submit(() -> runAsOwner(owner, ready, start,
                    () -> transactionService.deposit(request, "demo-retry-key")));
            Future<TransactionResponse> retryFuture = executor.submit(() -> runAsOwner(owner, ready, start,
                    () -> transactionService.deposit(request, "demo-retry-key")));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = firstFuture.get(10, TimeUnit.SECONDS);
            var retry = retryFuture.get(10, TimeUnit.SECONDS);

            assertThat(retry.getId()).isEqualTo(first.getId());
            assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("100.00");
            assertThat(transactionRepository.count()).isEqualTo(1);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
            assertThatThrownBy(() -> transactionService.deposit(
                    DepositRequest.builder().accountId(account.getId()).amount(new BigDecimal("101.00")).build(),
                    "demo-retry-key"))
                    .isInstanceOf(ConflictException.class);
        } finally {
            SecurityContextHolder.clearContext();
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawAnAccount() throws Exception {
        Account account = createAccount();
        User owner = account.getUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        transactionService.deposit(DepositRequest.builder()
                .accountId(account.getId())
                .amount(new BigDecimal("100.00"))
                .build(), "withdrawal-test-funding");
        SecurityContextHolder.clearContext();

        WithdrawRequest request = WithdrawRequest.builder()
                .accountId(account.getId())
                .amount(new BigDecimal("80.00"))
                .build();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<TransactionResponse> first = executor.submit(() -> runAsOwner(owner, ready, start,
                    () -> withdrawIfFundsRemain(request, "withdrawal-race-a")));
            Future<TransactionResponse> second = executor.submit(() -> runAsOwner(owner, ready, start,
                    () -> withdrawIfFundsRemain(request, "withdrawal-race-b")));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            TransactionResponse firstResult = first.get(10, TimeUnit.SECONDS);
            TransactionResponse secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat((firstResult == null ? 0 : 1) + (secondResult == null ? 0 : 1)).isEqualTo(1);
            assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("20.00");
            assertThat(transactionRepository.count()).isEqualTo(2);
        } finally {
            SecurityContextHolder.clearContext();
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void apiFlowRegistersCustomersMovesMoneyOnceAndEnforcesLimits() throws Exception {
        String firstToken = registerAndGetToken("first-" + UUID.randomUUID() + "@example.test");
        String secondToken = registerAndGetToken("second-" + UUID.randomUUID() + "@example.test");
        long firstAccount = createAccount(firstToken);
        long secondAccount = createAccount(secondToken);

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("Idempotency-Key", "api-demo-deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + firstAccount + ",\"amount\":100.00}"))
                .andExpect(status().isCreated());

        String transferBody = "{\"sourceAccountId\":" + firstAccount +
                ",\"destinationAccountId\":" + secondAccount + ",\"amount\":35.00}";
        JsonNode postedTransfer = objectMapper.readTree(mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("Idempotency-Key", "api-demo-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        JsonNode replayedTransfer = objectMapper.readTree(mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("Idempotency-Key", "api-demo-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(replayedTransfer.path("id").asLong()).isEqualTo(postedTransfer.path("id").asLong());
        assertThat(balance(firstToken, firstAccount)).isEqualByComparingTo("65.00");
        assertThat(balance(secondToken, secondAccount)).isEqualByComparingTo("35.00");

        mockMvc.perform(put("/api/accounts/{id}/spending-limits", firstAccount)
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":40.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("Idempotency-Key", "api-demo-over-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + firstAccount + ",\"amount\":10.00}"))
                .andExpect(status().isBadRequest());
        assertThat(balance(firstToken, firstAccount)).isEqualByComparingTo("65.00");
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "fullName", "Integration Test",
                "email", email,
                "password", "demo-password-1"));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("token").asText();
    }

    private long createAccount(String token) throws Exception {
        String response = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private BigDecimal balance(String token, long accountId) throws Exception {
        String response = mockMvc.perform(get("/api/accounts/{id}/balance", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("balance").decimalValue();
    }

    private TransactionResponse withdrawIfFundsRemain(WithdrawRequest request, String key) {
        try {
            return transactionService.withdraw(request, key);
        } catch (InsufficientFundsException exception) {
            return null;
        }
    }

    private <T> T runAsOwner(User owner, CountDownLatch ready, CountDownLatch start,
                             java.util.concurrent.Callable<T> action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
            return action.call();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Account createAccount() {
        User user = userRepository.save(User.builder()
                .fullName("Integration User")
                .email(UUID.randomUUID() + "@example.test")
                .password("not-a-real-password")
                .role(Role.CUSTOMER)
                .build());
        return accountRepository.save(Account.builder()
                .accountNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .user(user)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .build());
    }

    private void save(TransactionType type, Account source, Account destination,
                      String amount, LocalDateTime timestamp) {
        transactionRepository.save(Transaction.builder()
                .type(type)
                .sourceAccount(source == null ? createAccount() : source)
                .destinationAccount(destination)
                .amount(new BigDecimal(amount))
                .timestamp(timestamp)
                .referenceNumber(UUID.randomUUID().toString())
                .build());
    }
}
