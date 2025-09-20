package com.cooperativesolutionism.nmsci.util;

import java.time.Instant;

public class DateUtil {

    /**
     * 获取当前时间的微秒级时间戳(UTC)
     *
     * @return 微秒级时间戳
     */
    public static long getCurrentMicros() {
        Instant instant = Instant.now();
        return instant.getEpochSecond() * 1_000_000 + instant.getNano() / 1000;
    }

}
