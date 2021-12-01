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

import org.apache.commons.lang3.ObjectUtils;

public class DbUtils {

    private DbUtils() {
    }

    /**
     * remove quotes from the received db user value, then enclose it with double quotes.
     * @param value the value to sanitize and surround with double quotes
     * @return the generated value
     * @throws DbJobException if the received value is empty, null or if it contains quotes or doublequotes
     */
    public static String quoteValidUsername(String value) {
        return quoteValidValue(value, "db user");
    }

    /**
     * remove quotes from the received db admin user value, then enclose it with double quotes.
     * @param value the value to sanitize and surround with double quotes
     * @return the generated value
     * @throws DbJobException if the received value is empty, null or if it contains quotes or doublequotes
     */
    public static String quoteValidAdminUsername(String value) {
        return quoteValidValue(value, "db admin user");
    }

    /**
     * remove quotes from the received value, then enclose it with double quotes.
     * @param value the value to sanitize and surround with double quotes
     * @param valueName the name of the value to throw a meaningful exception in case of error
     * @return the generated value
     * @throws DbJobException if the received value is empty, null or if it contains quotes or doublequotes
     */
    private static String quoteValidValue(String value, String valueName) {

        if (ObjectUtils.isEmpty(value)) {
            throw new DbJobException("Empty value received as " + valueName);
        }
        if (value.contains("\"") || value.contains("'")) {
            throw new DbJobException("The value received as " + valueName + " contains invalid chars (\" or ')");
        }

        return "\"" + value + "\"";
    }
}
