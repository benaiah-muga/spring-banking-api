package com.banking.api.util;

import com.banking.api.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationSupport {

    public static final int MAX_PAGE_SIZE = 100;

    private PaginationSupport() {
    }

    public static Pageable page(int page, int size) {
        return page(page, size, Sort.unsorted());
    }

    public static Pageable page(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1) {
            throw new BadRequestException("Page size must be positive");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
    }
}
