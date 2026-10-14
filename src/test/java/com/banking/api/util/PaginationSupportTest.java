package com.banking.api.util;

import com.banking.api.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginationSupportTest {

    @Test
    void capsLargePageSizesAndPreservesRequestedSort() {
        var pageable = PaginationSupport.page(2, 500, Sort.by("id").descending());

        assertEquals(2, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void rejectsNegativePageNumbers() {
        assertThrows(BadRequestException.class, () -> PaginationSupport.page(-1, 20));
    }

    @Test
    void rejectsNonPositivePageSizes() {
        assertThrows(BadRequestException.class, () -> PaginationSupport.page(0, 0));
    }
}
