// Adapted from Moonfin (github.com/Moonfin-Client/Moonfin-Core, packages/moonfin_native_video: ts/HdmvTsExtractor,
// ts/HdmvDtsReader, iec/DtsFraming), GPL-3.0. See THIRD_PARTY.md.
package io.github.kentprodan.mediatheq.core.playback

import android.util.SparseArray
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableBitArray
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.DtsUtil
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.ElementaryStreamReader
import androidx.media3.extractor.ts.PesReader
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsPayloadReader

/** Looks for the HDMV registration before the TS extractor reads the PMT (#634). */
@UnstableApi
internal class HdmvTsExtractor(
    delegate: TsExtractor,
    private val readers: HdmvTsPayloadReaderFactory,
) : ForwardingExtractor(delegate) {
    override fun sniff(input: ExtractorInput): Boolean {
        readers.hdmv = HdmvRegistration.find(input)
        input.resetPeekPosition()
        return super.sniff(input)
    }
}

/**
 * Blu-ray gives DTS its own stream types, which Media3 skips. One of them is SCTE-35 in broadcast, so they are read as
 * DTS only when the program carries the HDMV registration (#634).
 */
@UnstableApi
internal class HdmvTsPayloadReaderFactory(
    private val delegate: TsPayloadReader.Factory,
) : TsPayloadReader.Factory {
    var hdmv = false

    override fun createInitialPayloadReaders(): SparseArray<TsPayloadReader> = delegate.createInitialPayloadReaders()

    override fun createPayloadReader(
        streamType: Int,
        esInfo: TsPayloadReader.EsInfo,
    ): TsPayloadReader? =
        if (hdmv && streamType in HDMV_DTS_STREAM_TYPES) {
            PesReader(HdmvDtsReader(esInfo.language, esInfo.roleFlags))
        } else {
            delegate.createPayloadReader(streamType, esInfo)
        }

    private companion object {
        // Core, DTS-HD High Resolution and DTS-HD Master Audio.
        val HDMV_DTS_STREAM_TYPES = setOf(0x82, 0x85, 0x86)
    }
}

/** Blu-ray's HDMV registration in the PMT's program descriptors, which Media3 skips. A Blu-ray TS opens with PAT, PMT. */
@UnstableApi
internal object HdmvRegistration {
    fun find(input: ExtractorInput): Boolean {
        val data = ByteArray(PEEK_BYTES)
        var length = 0
        while (length < data.size) {
            val read = input.peek(data, length, data.size - length)
            if (read == C.RESULT_END_OF_INPUT) break
            length += read
        }
        return find(data, length)
    }

    fun find(
        data: ByteArray,
        length: Int,
    ): Boolean {
        var packet = firstPacket(data, length) ?: return false
        var pmtPid = -1
        while (packet + PACKET_SIZE <= length && data[packet] == SYNC_BYTE) {
            val section = sectionStart(data, packet)
            if (section >= 0) {
                val pid = ((data[packet + 1].toInt() and 0x1F) shl 8) or (data[packet + 2].toInt() and 0xFF)
                val end = packet + PACKET_SIZE
                if (pid == 0 && pmtPid < 0) {
                    pmtPid = pmtPid(data, section, end)
                } else if (pid == pmtPid) {
                    return hasRegistration(data, section, end)
                }
            }
            packet += PACKET_SIZE
        }
        return false
    }

    private fun firstPacket(
        data: ByteArray,
        length: Int,
    ): Int? =
        (0 until minOf(PACKET_SIZE, length)).firstOrNull { start ->
            (start until length step PACKET_SIZE).take(3).all { data[it] == SYNC_BYTE }
        }

    // Where a section starting in this packet begins, or -1 when none does.
    private fun sectionStart(
        data: ByteArray,
        packet: Int,
    ): Int {
        val end = packet + PACKET_SIZE
        if (data[packet + 1].toInt() and 0x40 == 0) return -1
        val adaptation = (data[packet + 3].toInt() shr 4) and 0x03
        if (adaptation and 0x01 == 0) return -1
        var pos = packet + 4
        if (adaptation == 0x03) pos += 1 + (data[pos].toInt() and 0xFF)
        if (pos >= end) return -1
        pos += 1 + (data[pos].toInt() and 0xFF)
        return if (pos < end) pos else -1
    }

    private fun pmtPid(
        data: ByteArray,
        section: Int,
        end: Int,
    ): Int {
        if (section + 8 > end || data[section].toInt() != 0x00) return -1
        val entriesEnd = minOf(end, section + 3 + sectionLength(data, section) - 4)
        var pos = section + 8
        while (pos + 4 <= entriesEnd) {
            val program = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            if (program != 0) return ((data[pos + 2].toInt() and 0x1F) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4
        }
        return -1
    }

    private fun hasRegistration(
        data: ByteArray,
        section: Int,
        end: Int,
    ): Boolean {
        if (section + 12 > end || data[section].toInt() != 0x02) return false
        val infoLength = ((data[section + 10].toInt() and 0x0F) shl 8) or (data[section + 11].toInt() and 0xFF)
        var pos = section + 12
        val infoEnd = minOf(end, pos + infoLength)
        while (pos + 2 <= infoEnd) {
            val tag = data[pos].toInt() and 0xFF
            val size = data[pos + 1].toInt() and 0xFF
            if (tag == REGISTRATION_TAG && size >= 4 && pos + 6 <= infoEnd) {
                val identifier = data.rb32(pos + 2)
                if (identifier == HDMV || identifier == HDPR) return true
            }
            pos += 2 + size
        }
        return false
    }

    private fun sectionLength(
        data: ByteArray,
        section: Int,
    ): Int = ((data[section + 1].toInt() and 0x0F) shl 8) or (data[section + 2].toInt() and 0xFF)

    private const val PACKET_SIZE = TsExtractor.TS_PACKET_SIZE
    private const val PEEK_BYTES = PACKET_SIZE * 8
    private const val SYNC_BYTE = 0x47.toByte()
    private const val REGISTRATION_TAG = 0x05
    private const val HDMV = 0x48444D56L
    private const val HDPR = 0x48445052L
}

/**
 * Blu-ray DTS audio from a transport stream (#634). A DTS-HD access unit is a core frame with its extension substream
 * right behind it, and both go out as one sample, the way MKV and MP4 hand DTS-HD over. Blu-ray PES packets carry whole
 * access units, so each packet is parsed once it is complete.
 */
@UnstableApi
internal class HdmvDtsReader(
    private val language: String?,
    private val roleFlags: Int,
) : ElementaryStreamReader {
    private lateinit var output: TrackOutput
    private lateinit var formatId: String
    private var formatPublished = false
    private var packet = ByteArray(0)
    private var packetSize = 0
    private val sample = ParsableByteArray()
    private var timeUs = C.TIME_UNSET

    override fun seek() {
        packetSize = 0
        timeUs = C.TIME_UNSET
    }

    override fun createTracks(
        extractorOutput: ExtractorOutput,
        idGenerator: TsPayloadReader.TrackIdGenerator,
    ) {
        idGenerator.generateNewId()
        formatId = idGenerator.formatId
        output = extractorOutput.track(idGenerator.trackId, C.TRACK_TYPE_AUDIO)
    }

    override fun packetStarted(
        pesTimeUs: Long,
        flags: Int,
    ) {
        packetSize = 0
        if (pesTimeUs != C.TIME_UNSET) timeUs = pesTimeUs
    }

    override fun consume(data: ParsableByteArray) {
        val length = data.bytesLeft()
        if (packet.size < packetSize + length) packet = packet.copyOf(maxOf(packetSize + length, packet.size * 2))
        data.readBytes(packet, packetSize, length)
        packetSize += length
    }

    override fun packetFinished() {
        var pos = 0
        while (pos + CORE_HEADER_BYTES <= packetSize) {
            val core = coreHeaderAt(pos)
            if (core == null) {
                pos++
                continue
            }
            val extensionAt = pos + core.sizeBytes
            val hasExtension =
                extensionAt + EXTENSION_PREFIX_BYTES <= packetSize && packet.rb32(extensionAt) == SYNCWORD_EXTENSION
            val length = core.sizeBytes + if (hasExtension) extensionSize(extensionAt) else 0
            if (pos + length > packetSize) break
            if (!formatPublished) publishFormat(pos, if (hasExtension) extensionAt else null)
            if (timeUs != C.TIME_UNSET) {
                sample.reset(packet, pos + length)
                sample.setPosition(pos)
                output.sampleData(sample, length)
                output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, length, 0, null)
                timeUs += Util.sampleCountToDurationUs(core.samples.toLong(), core.sampleRate)
            }
            pos += length
        }
        packetSize = 0
    }

    private class CoreHeader(
        val samples: Int,
        val sizeBytes: Int,
        val sampleRate: Int,
    )

    // A 16-bit big-endian core syncframe header, the only kind Blu-ray carries.
    private fun coreHeaderAt(pos: Int): CoreHeader? {
        if (packet.rb32(pos) != SYNCWORD_CORE) return null
        val blocks = ((packet.rb16(pos + 4) shr 2) and 0x7F) + 1
        val size = ((packet.rb24(pos + 5) shr 4) and 0x3FFF) + 1
        val sampleRate = CORE_SAMPLE_RATES[(packet[pos + 8].toInt() shr 2) and 0x0F]
        return if (sampleRate > 0) CoreHeader(blocks * 32, size, sampleRate) else null
    }

    // Read straight from the header: parseDtsHdHeader refuses streams it cannot fully describe, and only the size counts.
    private fun extensionSize(at: Int): Int {
        val bits = ParsableBitArray(packet, packetSize)
        bits.setPosition(at * 8 + 42)
        val wideHeader = bits.readBit()
        bits.skipBits(if (wideHeader) 12 else 8)
        return bits.readBits(if (wideHeader) 20 else 16) + 1
    }

    private fun publishFormat(
        coreAt: Int,
        extensionAt: Int?,
    ) {
        val core =
            DtsUtil.parseDtsFormat(
                packet.copyOfRange(coreAt, coreAt + CORE_HEADER_BYTES),
                formatId,
                language,
                roleFlags,
                MimeTypes.VIDEO_MP2T,
                null,
            )
        val format =
            if (extensionAt == null) {
                core
            } else {
                val builder = core.buildUpon().setSampleMimeType(MimeTypes.AUDIO_DTS_HD)
                extensionHeader(extensionAt)?.let { extension ->
                    if (extension.channelCount != C.LENGTH_UNSET) builder.setChannelCount(extension.channelCount)
                    if (extension.sampleRate != C.RATE_UNSET_INT) builder.setSampleRate(extension.sampleRate)
                }
                builder.build()
            }
        output.format(format)
        formatPublished = true
    }

    // Only for the channel count and sample rate: a header it cannot describe leaves the core's in place.
    private fun extensionHeader(at: Int): DtsUtil.DtsHeader? =
        try {
            val size = DtsUtil.parseDtsHdHeaderSize(packet.copyOfRange(at, at + EXTENSION_PREFIX_BYTES))
            if (at + size > packetSize) null else DtsUtil.parseDtsHdHeader(packet.copyOfRange(at, at + size))
        } catch (_: ParserException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    private companion object {
        const val CORE_HEADER_BYTES = 18
        const val EXTENSION_PREFIX_BYTES = 10
        const val SYNCWORD_CORE = 0x7FFE8001L
        const val SYNCWORD_EXTENSION = 0x64582025L
        val CORE_SAMPLE_RATES =
            intArrayOf(0, 8000, 16000, 32000, 0, 0, 11025, 22050, 44100, 0, 0, 12000, 24000, 48000, 96000, 192000)
    }
}

private fun ByteArray.rb16(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.rb24(offset: Int): Int = (rb16(offset) shl 8) or (this[offset + 2].toInt() and 0xFF)

private fun ByteArray.rb32(offset: Int): Long =
    (rb24(offset).toLong() shl 8) or (this[offset + 3].toInt() and 0xFF).toLong()
