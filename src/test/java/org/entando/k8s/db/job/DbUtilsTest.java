/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.k8s.db.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(@Tag("integration"))
class DbUtilsTest {

    @Test
    void shouldSurroundTheReceivedValueWithDoubleQuotes() {
        String actual = DbUtils.quoteValidUsername("username");
        assertEquals("\"username\"", actual);

        actual = DbUtils.quoteValidAdminUsername("username");
        assertEquals("\"username\"", actual);
    }

    @Test
    void shouldThrowExceptionWhenReceivingValuesWithQuotesOrDoubleQuotes() {
        List<String> testCasesList = List.of(
                "us'er'na'me",
                "us\"ern\"ame",
                "", ""
        );

        testCasesList.forEach(value -> {
            assertThrows(DbJobException.class, () -> DbUtils.quoteValidUsername(value));
            assertThrows(DbJobException.class, () -> DbUtils.quoteValidAdminUsername(value));
        });
    }

    @Test
    void shouldNotBreakWhenSanitizingANulLValue() {
        assertThrows(DbJobException.class, () -> DbUtils.quoteValidUsername(null));
        assertThrows(DbJobException.class, () -> DbUtils.quoteValidAdminUsername(null));
    }
}
