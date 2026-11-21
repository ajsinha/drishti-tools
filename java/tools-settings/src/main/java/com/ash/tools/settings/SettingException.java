/*
 * Tools · shared, domain-free Java utilities.
 *
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>.
 * All rights reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL.
 *
 * This file is the confidential and proprietary property of Ashutosh Sinha.
 * Unauthorised copying, use, modification, distribution or disclosure of this
 * file, via any medium, is strictly prohibited except with the express prior
 * written permission of the copyright holder.
 *
 * See the LICENSE file in the root of this repository for the full terms.
 */
package com.ash.tools.settings;

/**
 * A setting that cannot be used: a placeholder nothing names, a password file that cannot be read. The message names the setting and
 * the reason, so the operator reads what to fix straight from the start-up error or the health text. A library above this one (TLS,
 * the proxy) wraps it in its own exception when its callers expect that.
 */
public class SettingException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** A setting that cannot be used; {@code message} names it. */
    public SettingException(String message) {
        super(message);
    }

    /** As {@link #SettingException(String)}, with the cause. */
    public SettingException(String message, Throwable cause) {
        super(message, cause);
    }
}
