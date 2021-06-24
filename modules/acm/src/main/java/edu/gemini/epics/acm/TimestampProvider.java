/*
 * Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
 * For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
 */

package edu.gemini.epics.acm;

import java.time.Instant;

public interface TimestampProvider {
    Instant now();

    TimestampProvider Default = () -> Instant.now();
}
