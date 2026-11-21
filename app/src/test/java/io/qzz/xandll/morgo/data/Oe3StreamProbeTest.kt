package io.qzz.xandll.morgo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Der Ö3-Stream, an dem 1.6.1 auf den Klingelton gefallen ist.
 * HTTP/2, audio/mpeg, ICY-Metadaten alle icy-metaint Bytes, danach wieder MP3.
 */
class Oe3StreamProbeTest {

    @Test
    fun oe3DeliversMpegWithIcyMetadata() {
        val connection = URL("https://ors-sn03.ors-shoutcast.at/oe3-q1a").openConnection() as HttpURLConnection
        connection.setRequestProperty("Icy-MetaData", "1")
        connection.setRequestProperty("User-Agent", "Morgo/1.6.2")
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.inputStream.use { input ->
            val buffer = ByteArray(20_000)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            assertEquals(200, connection.responseCode)
            assertTrue(connection.contentType.orEmpty().startsWith("audio/mpeg"))
            val interval = connection.getHeaderField("icy-metaint")?.toIntOrNull()
            assertTrue("icy-metaint fehlt", interval != null && interval in 1 until read)
            assertEquals("MP3-Sync", 0xFF.toByte(), buffer[0])
            assertEquals(0xE0, buffer[1].toInt() and 0xE0)
            val metaUnits = buffer[interval!!].toInt() and 0xFF
            assertTrue(metaUnits > 0)
            val metaStart = interval + 1
            val metaEnd = metaStart + metaUnits * 16
            assertTrue(metaEnd < read)
            val meta = buffer.copyOfRange(metaStart, metaEnd).toString(Charsets.ISO_8859_1)
            assertTrue(meta, meta.contains("StreamTitle"))
            assertTrue(read > metaEnd)
        }
    }
}
