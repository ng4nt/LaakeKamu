package fi.hh.laakekamu.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the device's **current** time zone. `Clock.systemDefaultZone()` keeps the
 * zone it saw when it was created, but the zone can change while the app runs (travel,
 * TIMEZONE_CHANGED); this clock reads it on every call, so "today" always matches the phone.
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}
