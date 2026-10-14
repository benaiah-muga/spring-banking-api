package com.banking.api.service;

import com.banking.api.dto.response.TransactionResponse;
import com.banking.api.entity.IdempotencyRecord;
import com.banking.api.entity.Transaction;
import com.banking.api.exception.BadRequestException;
import com.banking.api.exception.ConflictException;
import com.banking.api.repository.IdempotencyLockRepository;
import com.banking.api.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int LOCK_STRIPES = 64;

    private final IdempotencyLockRepository lockRepository;
    private final IdempotencyRecordRepository recordRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionResponse findReplay(Long userId, String key, String operation, List<String> requestParts) {
        validateKey(key);
        int stripe = Math.floorMod((userId + ":" + key).hashCode(), LOCK_STRIPES);
        lockRepository.findByIdForUpdate(stripe)
                .orElseThrow(() -> new IllegalStateException("Idempotency lock rows are not initialized"));

        return recordRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(record -> {
                    if (!record.getOperation().equals(operation)
                            || !record.getRequestHash().equals(fingerprint(requestParts))) {
                        throw new ConflictException("This Idempotency-Key was already used with a different request");
                    }
                    return TransactionResponse.fromEntity(record.getTransaction());
                })
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long userId, String key, String operation, List<String> requestParts,
                       Transaction transaction) {
        recordRepository.save(IdempotencyRecord.builder()
                .userId(userId)
                .idempotencyKey(key)
                .operation(operation)
                .requestHash(fingerprint(requestParts))
                .transaction(transaction)
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BadRequestException("Idempotency-Key must contain between 1 and 128 characters");
        }
    }

    private String fingerprint(List<String> requestParts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder canonicalRequest = new StringBuilder();
            for (String part : requestParts) {
                canonicalRequest.append(part.length()).append(':').append(part);
            }
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
