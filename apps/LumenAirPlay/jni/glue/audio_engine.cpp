/*
 * AirPlay audio engine for Z9xAirPlay.
 *
 *   decode (ALAC: Apple decoder on the RTP thread; AAC-LC/ELD: AMediaCodec + drain thread)
 *     -> PcmTimeline: SPSC ring of 44.1 kHz stereo int16 + anchors (ring position -> T)
 *     -> AAudio data callback: plays the sample whose T matches the presentation time of
 *        the next output frame (AAudioStream_getTimestamp on CLOCK_MONOTONIC, filtered).
 *
 * T = clock(sender time) + latency + extra (+ the debug.z9x.airplay.av_offset_ms
 * calibration, 0 by default). clock is AvClock (av_clock.cpp): one smoothed sender ->
 * CLOCK_MONOTONIC offset per session, shared with the video decoder, so audio and video
 * stay in sync with each other and with the sender and play times never jump per packet.
 *
 * Output timing. The timestamp is sampled every 50 ms. The HAL / DSP chain of this SoC may
 * report its position in coarse steps, and a position that lags only ever makes the
 * presentation time of frame 0 ("epoch" = t - pos / rate) look later, so the sample with the
 * earliest epoch of the last 3.2 s is used; the window restarts after an xrun (an underrun
 * delays everything after it). Until the first timestamp (at most 500 ms after the stream
 * opens) the callback plays silence instead of aligning on a guess. Corrections: an error
 * above 4 ms starts single-frame drops / repeats, blended with the neighbouring frame, at most
 * 0.5% of the frames (1% beyond 20 ms), until it is below 1 ms; a hard skip or silence only
 * when the error stays above 50 ms for 3 callbacks or exceeds 250 ms, and after an underrun,
 * a flush, a discontinuity or a reopen of the output.
 *
 * Writer (pcm_timeline.h, 1.0.12). Audio is placed by frame counting: a packet that follows its
 * predecessor is written right after it whatever its T says; only audio known to be missing
 * is concealed, exactly by its length: lost packets (RTP sequence gaps after the reorder
 * buffer, times their frames per packet) and packets the decoder did not decode (a failed
 * ALAC frame, a full AAC input, an AAC input with no output, found by its timestamp). T only
 * steers the timeline: the median of the last ~5 s of (T - frame-counted time), followed at
 * most 0.4 % and handed to the reader's drift correction; a step of the sender's clock (every
 * packet > 150 ms off on one side for 2 s) re-anchors once. A sequence restart re-anchors at
 * once. Until 1.0.11 every T difference above 40 ms was concealed and every one below -40 ms
 * cut: play times jumping back and forth (owner's Mac, 4K mirroring) concealed ~40 % of the
 * sound and realigned the reader ~10 times per 5 s with no packet lost.
 *
 * Extra delay (shared with the video decoder). Sized once per audio session: a session
 * starts with the extra remembered for its audio type (AAC-ELD mirroring: 60 ms the first
 * time), the first second of packets measures how early they arrive before their play time,
 * and with the measured output lead (AAudio buffer + HAL / DSP latency) the extra is set so
 * that the worst packet of that second still has 70 ms to spare (kSpareTargetNs), within
 * 0..300 ms; changes below 20 ms are not made. Later it only grows when packets came after
 * their play deadline in 3 of the last 10 one-second windows, by 20-60 ms, at most every
 * 20 s (each raise logged); 0 again when the session ends. (1.0.1 started at 0 and raised
 * the delay mid-stream whenever the spare time was low: an underrun burst at the start, then
 * +100 ms, each raise a gap.) Resend deadlines: a missing packet is waited for (UxPlay patch
 * z9x-0011) until its play time less the output lead and 30 ms.
 *
 * AAudio: shared, PERFORMANCE_MODE_NONE (a normal AudioFlinger mixer track: the Harman/DTS
 * chain applies, and AudioFlinger resamples our 44.1 kHz to the 48 kHz mixer, with the larger
 * buffer it uses when it resamples). xruns are counted; each one grows the buffer by a burst
 * while it is below its capacity; 3 within 10 s at full capacity reopen the stream with twice
 * the capacity (at most 250 ms). The decoder threads (UxPlay's RTP thread, the AAC drain
 * thread) run at audio priority; the data callback never allocates, locks or logs.
 *
 * Diagnostics, every 5 s while the output is open and packets arrive (one log line): packets,
 * lost (sequence gaps after the reorder buffer), late (written after their play deadline),
 * resends (requested, received, received too late, given up), the mean play lead at arrival
 * (play time - now), worst spare, ring level, output lead, buffer / capacity / burst, xruns,
 * timestamp spread, drift corrections, realigns, underruns with the late audio skipped, the
 * concealed audio and the extra delay. One line per session timing decision and per raise.
 *
 * The output stays open (playing silence) while a session lasts, up to 60 s without data,
 * so the first sound after a pause is not lost to the stream start-up; it closes 2 s after
 * the session ends. A failed AAC decoder is recreated on the next packet. After stop(),
 * packets are dropped until the next setFormat() (audio SETUP), so stragglers of an ended
 * session cannot reopen a decoder and the output.
 *
 * Adapted from jqssun/android-airplay-server v0.0.31 (GPL-3.0): audio_engine.cpp,
 * audio_decoder.h (AAC AudioSpecificConfig bytes, ALAC magic cookie, MediaCodec drain
 * thread), timeline_buffer.h (SPSC ring). Modified for Z9xAirPlay: Oboe -> AAudio, FFmpeg
 * ALAC -> Apple ALAC reference decoder, absolute timeline scheduling instead of the
 * adaptive jitter cushion, sender volume applied as software gain, debug struct dropped.
 *
 * Copyright (C) jqssun / android-airplay-server contributors
 * Copyright (C) 2026 Z9X project
 * SPDX-License-Identifier: GPL-3.0-only
 */

#include "audio_engine.h"

#include <aaudio/AAudio.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

#include "ALACBitUtilities.h"
#include "ALACDecoder.h"
#include "av_clock.h"
#include "pcm_timeline.h"
#include "z9x_common.h"

namespace {

using namespace z9x_timeline;  /* kRate, kCh, kMs, framesToNs, bump, PcmTimeline */

/* extra delay (audio and video): session timing */
constexpr int64_t kSpareTargetNs = 70 * kMs;
constexpr int64_t kExtraMaxNs = 300 * kMs;
constexpr int64_t kExtraHysteresisNs = 20 * kMs;
constexpr int64_t kProvisionalEldNs = 60 * kMs;  /* mirroring, before the first measurement */
constexpr int64_t kAssumedOutLeadNs = 230 * kMs; /* no output lead known (Z9X: 214-218 ms) */
constexpr int kMeasureMaxWindows = 3;            /* wait this long for the output lead */
/* extra delay: raise after repeated late packets */
constexpr int kLateHistory = 10;                 /* one-second windows looked at */
constexpr int kLateWindows = 3;                  /* late ones among them that raise the delay */
constexpr int64_t kRaiseIntervalNs = 20000 * kMs;
constexpr int64_t kRaiseSpareNs = 30 * kMs;
constexpr int64_t kRaiseMinNs = 20 * kMs;
constexpr int64_t kRaiseMaxNs = 60 * kMs;
/* resend deadline: play time - output lead - this (decode + hand-over) */
constexpr int64_t kResendMarginNs = 30 * kMs;
/* output */
constexpr int64_t kIdleCloseNs = 2000 * kMs;          /* after the session ended */
constexpr int64_t kSessionIdleCloseNs = 60000 * kMs;  /* session open, sender silent */
/* Presentation-time fallback when AAudioStream_getTimestamp does not work within
   kTimestampWaitNs of the start: buffer + this. The HAL / DSP path (MTK HAL, Harman/DTS) is not
   in the buffer size; 40 ms is a typical TV-SoC output latency. Logged when used, so the
   device A/V sync test can see it. */
constexpr int64_t kFallbackHalLatencyNs = 40 * kMs;
constexpr int64_t kTimestampWaitNs = 500 * kMs;
constexpr int kTsWindow = 64;                         /* timestamp samples, 50 ms apart */
/* a timestamp whose frame-0 time (epoch) lies this far from the window's is implausible (a
   position in another unit or rate, a HAL that restarted its count): skipped, unless
   kTsAgreeSamples in a row agree with each other (a real latency change: new window) */
constexpr int64_t kTsEpochTolNs = 200 * kMs;
constexpr int kTsAgreeSamples = 20;
constexpr int32_t kMaxCapacityFrames = kRate / 4;     /* 250 ms */
constexpr int64_t kXrunWindowNs = 10000 * kMs;
constexpr int64_t kStatsPeriodNs = 5000 * kMs;
constexpr int64_t kDecoderRetryNs = 1000 * kMs;       /* AAC decoder failed twice in a row */
constexpr uint32_t kOlderMax = 32;                    /* older packets dropped before a restart */
constexpr int CT_ALAC = 2, CT_AAC_LC = 4, CT_AAC_ELD = 8;

inline long long toMs(int64_t ns) { return (long long) (ns / kMs); }
/* up to a multiple of 10 ms */
inline int64_t roundUp10Ms(int64_t ns) {
    const int64_t q = 10 * kMs;
    return ns <= 0 ? -((-ns) / q * q) : (ns + q - 1) / q * q;
}

/* ------------------------------------------------------------------------------------ */
/* Decoders                                                                               */

class Decoder {
public:
    virtual ~Decoder() = default;
    /* missingFrames: audio lost just before this packet; discontinuity: a new stream starts
       here; extraNs: the extra delay in targetNs */
    virtual void decode(const uint8_t *data, int len, int64_t targetNs, int missingFrames, bool discontinuity,
                        int64_t extraNs) = 0;
    /* the decoder hit a fatal error and must be recreated */
    virtual bool failed() const { return false; }

protected:
    int mPendingMissing = 0;  /* packets not decoded: the next one follows a gap this long */
    bool mPendingDisc = false;
};

/* 24-byte ALACSpecificConfig (big-endian), from jqssun buildAlacMagicCookie */
void buildAlacCookie(uint8_t out[24], int spf) {
    const int bitDepth = 16, pb = 40, mb = 10, kb = 14;
    out[0] = (uint8_t) (spf >> 24); out[1] = (uint8_t) (spf >> 16);
    out[2] = (uint8_t) (spf >> 8);  out[3] = (uint8_t) spf;
    out[4] = 0;                    /* compatibleVersion */
    out[5] = (uint8_t) bitDepth;
    out[6] = (uint8_t) pb; out[7] = (uint8_t) mb; out[8] = (uint8_t) kb;
    out[9] = (uint8_t) kCh;
    out[10] = 0; out[11] = 0xFF;   /* maxRun 255 */
    memset(out + 12, 0, 8);        /* maxFrameBytes, avgBitRate */
    out[20] = (uint8_t) (kRate >> 24); out[21] = (uint8_t) (kRate >> 16);
    out[22] = (uint8_t) (kRate >> 8);  out[23] = (uint8_t) kRate;
}

class AlacDecoder : public Decoder {
public:
    AlacDecoder(PcmTimeline &tl, int spf) : mTl(tl), mSpf(spf) {}
    bool init() {
        uint8_t cookie[24];
        buildAlacCookie(cookie, mSpf);
        if (mDec.Init(cookie, sizeof(cookie)) != 0) return false;
        mPcm.resize((size_t) mSpf * kCh);
        return true;
    }
    void decode(const uint8_t *data, int len, int64_t targetNs, int missingFrames, bool discontinuity,
                int64_t extraNs) override {
        /* The reference decoder only checks the input end between elements; give it a
           zero-padded copy large enough for a whole uncompressed ("escape") frame. */
        const size_t pad = (size_t) mSpf * kCh * 4 + 64;
        if (mIn.size() < (size_t) len + pad) mIn.resize((size_t) len + pad);
        memcpy(mIn.data(), data, (size_t) len);
        memset(mIn.data() + len, 0, pad);
        BitBuffer bits;
        BitBufferInit(&bits, mIn.data(), (uint32_t) len);
        uint32_t outFrames = 0;
        if (mDec.Decode(&bits, (uint8_t *) mPcm.data(), (uint32_t) mSpf, kCh, &outFrames) != 0 ||
            outFrames > (uint32_t) mSpf) {
            if (++mErrors % 100 == 1) Z9X_LOGW("audio: ALAC decode error (%u so far)", mErrors);
            mPendingMissing += missingFrames + mSpf;
            mPendingDisc = mPendingDisc || discontinuity;
            return;
        }
        mTl.write(mPcm.data(), (int) outFrames, targetNs, mPendingMissing + missingFrames, mPendingDisc || discontinuity,
                  extraNs);
        mPendingMissing = 0;
        mPendingDisc = false;
    }

private:
    PcmTimeline &mTl;
    const int mSpf;
    ALACDecoder mDec;
    std::vector<int16_t> mPcm;
    std::vector<uint8_t> mIn;
    uint32_t mErrors = 0;
};

class AacDecoder : public Decoder {
public:
    explicit AacDecoder(PcmTimeline &tl) : mTl(tl) {}
    ~AacDecoder() override {
        if (mDrain.joinable()) {
            mRun.store(false);
            mDrain.join();
        }
        if (mCodec) {
            AMediaCodec_stop(mCodec);
            AMediaCodec_delete(mCodec);
        }
    }

    bool init(int ct, int spf) {
        mSpf = spf;
        uint8_t csd[4];
        size_t csdLen;
        int profile;
        if (ct == CT_AAC_ELD) {
            /* AOT 39 (ER AAC-ELD), 44100, stereo; ELDSpecificConfig frameLengthFlag 1 = 480 */
            profile = 39;
            csd[0] = 0xF8; csd[1] = 0xE8; csd[2] = (spf == 512) ? 0x40 : 0x50; csd[3] = 0x00;
            csdLen = 4;
        } else {
            /* AOT 2 (AAC-LC), 44100, stereo; frameLengthFlag 0 = 1024 */
            profile = 2;
            csd[0] = 0x12; csd[1] = (spf == 960) ? 0x14 : 0x10;
            csdLen = 2;
        }
        const char *names[] = {"c2.android.aac.decoder", nullptr};
        for (const char *name : names) {
            AMediaCodec *c = name ? AMediaCodec_createCodecByName(name)
                                  : AMediaCodec_createDecoderByType("audio/mp4a-latm");
            if (!c) continue;
            AMediaFormat *fmt = AMediaFormat_new();
            AMediaFormat_setString(fmt, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
            AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, kRate);
            AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, kCh);
            AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_AAC_PROFILE, profile);
            AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_IS_ADTS, 0);
            AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_PRIORITY, 0);
            AMediaFormat_setBuffer(fmt, "csd-0", csd, csdLen);
            media_status_t st = AMediaCodec_configure(c, fmt, nullptr, nullptr, 0);
            if (st == AMEDIA_OK) st = AMediaCodec_start(c);
            AMediaFormat_delete(fmt);
            if (st == AMEDIA_OK) {
                mCodec = c;
                Z9X_LOGI("audio: %s decoder started (%s, spf %d)", ct == CT_AAC_ELD ? "AAC-ELD" : "AAC-LC",
                         name ? name : "default", spf);
                break;
            }
            AMediaCodec_delete(c);
        }
        if (!mCodec) return false;
        mRun.store(true);
        mDrain = std::thread(&AacDecoder::drainLoop, this);
        return true;
    }

    void decode(const uint8_t *data, int len, int64_t targetNs, int missingFrames, bool discontinuity,
                int64_t extraNs) override {
        if (mFailed.load(std::memory_order_relaxed)) return;
        ssize_t idx = AMediaCodec_dequeueInputBuffer(mCodec, 5000);
        if (idx < 0) {
            mPendingMissing += missingFrames + mSpf;
            mPendingDisc = mPendingDisc || discontinuity;
            if (idx != AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                fail("dequeueInputBuffer", idx);
                return;
            }
            if (++mDropped % 50 == 1) Z9X_LOGW("audio: AAC input full, dropped %u packets", mDropped);
            return;
        }
        size_t cap = 0;
        uint8_t *buf = AMediaCodec_getInputBuffer(mCodec, (size_t) idx, &cap);
        const size_t n = buf ? std::min((size_t) len, cap) : 0;
        if (n) memcpy(buf, data, n);
        const int64_t ptsUs = targetNs / 1000;
        /* side information for the drain thread, matched by timestamp (SPSC) */
        const uint32_t w = mMetaW.load(std::memory_order_relaxed);
        mMeta[w % kMeta] = {ptsUs, mPendingMissing + missingFrames, mPendingDisc || discontinuity, extraNs};
        mMetaW.store(w + 1, std::memory_order_release);
        mPendingMissing = 0;
        mPendingDisc = false;
        media_status_t st = AMediaCodec_queueInputBuffer(mCodec, (size_t) idx, 0, n, (uint64_t) ptsUs, 0);
        if (st != AMEDIA_OK) fail("queueInputBuffer", st);
    }

    bool failed() const override { return mFailed.load(std::memory_order_relaxed); }

private:
    void fail(const char *what, ssize_t status) {
        if (!mFailed.exchange(true)) Z9X_LOGE("audio: AAC decoder error in %s (%zd)", what, status);
    }

    void drainLoop() {
        /* decoded PCM must reach the ring before its play time: audio priority */
        z9x_set_thread_priority(Z9X_PRIORITY_AUDIO, "AAC decoder");
        AMediaCodecBufferInfo info;
        int channels = kCh;
        std::vector<int16_t> stereo;
        while (mRun.load(std::memory_order_relaxed)) {
            ssize_t idx = AMediaCodec_dequeueOutputBuffer(mCodec, &info, 20000);
            if (idx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                AMediaFormat *fmt = AMediaCodec_getOutputFormat(mCodec);
                int32_t rate = 0, ch = 0;
                AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, &rate);
                AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &ch);
                AMediaFormat_delete(fmt);
                Z9X_LOGI("audio: AAC output %d Hz, %d ch", rate, ch);
                if (ch == 1 || ch == 2) channels = ch;
                continue;
            }
            if (idx < 0) {
                if (idx == AMEDIACODEC_INFO_TRY_AGAIN_LATER || idx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) continue;
                /* error state returns at once: stop instead of spinning; decode() sees
                   failed() and the engine recreates the decoder */
                fail("dequeueOutputBuffer", idx);
                break;
            }
            size_t size = 0;
            uint8_t *out = AMediaCodec_getOutputBuffer(mCodec, (size_t) idx, &size);
            if (out && info.size > 0) {
                const int16_t *pcm = (const int16_t *) (out + info.offset);
                int frames = info.size / (int) (sizeof(int16_t) * channels);
                if (channels == 1) {  /* upmix */
                    stereo.resize((size_t) frames * 2);
                    for (int i = 0; i < frames; i++) stereo[2 * i] = stereo[2 * i + 1] = pcm[i];
                    pcm = stereo.data();
                }
                /* the packet's side information; inputs before it that gave no output were
                   not decoded: their audio is missing too */
                int missing = 0;
                bool disc = false;
                int64_t extra = 0;
                const uint32_t mw = mMetaW.load(std::memory_order_acquire);
                if (mw - mMetaR > kMeta) mMetaR = mw - kMeta;  /* overwritten: never matched */
                for (uint32_t r = mMetaR; r != mw; r++) {
                    const Meta &m = mMeta[r % kMeta];
                    if (m.ptsUs == info.presentationTimeUs) {
                        for (uint32_t q = mMetaR; q != r; q++) {
                            missing += mMeta[q % kMeta].missingFrames + mSpf;
                            disc = disc || mMeta[q % kMeta].disc;
                        }
                        missing += m.missingFrames;
                        disc = disc || m.disc;
                        extra = m.extraNs;
                        mMetaR = r + 1;
                        break;
                    }
                }
                mTl.write(pcm, frames, (int64_t) info.presentationTimeUs * 1000, missing, disc, extra);
            }
            AMediaCodec_releaseOutputBuffer(mCodec, (size_t) idx, false);
        }
    }

    struct Meta { int64_t ptsUs; int missingFrames; bool disc; int64_t extraNs; };
    static constexpr uint32_t kMeta = 64;

    PcmTimeline &mTl;
    int mSpf = 480;
    AMediaCodec *mCodec = nullptr;
    Meta mMeta[kMeta] = {};
    std::atomic<uint32_t> mMetaW{0};
    uint32_t mMetaR = 0;  /* drain thread */
    std::thread mDrain;
    std::atomic<bool> mRun{false};
    std::atomic<bool> mFailed{false};
    uint32_t mDropped = 0;
};

}  // namespace

/* ------------------------------------------------------------------------------------ */
/* Engine: decoders + AAudio output + housekeeping thread                                */

class AudioEngineImpl {
public:
    AudioEngineImpl(int64_t latencyNs, int64_t offsetNs, std::atomic<int64_t> *extraNs, AvClock *clock)
        : mLatencyNs(latencyNs), mOffsetNs(offsetNs), mExtra(extraNs ? extraNs : &mOwnExtra), mClock(clock) {
        for (auto &r : mRemembered) r.store(0);
        mRemembered[ctIndex(CT_AAC_ELD)].store(kProvisionalEldNs);
        clearLateHistory();
        mWorker = std::thread(&AudioEngineImpl::workerLoop, this);
    }

    ~AudioEngineImpl() {
        {
            std::lock_guard<std::mutex> lk(mDecMu);
            mDecoder.reset();
        }
        {
            std::lock_guard<std::mutex> lk(mMu);
            mQuit = true;
        }
        mCv.notify_all();
        if (mWorker.joinable()) mWorker.join();
        closeStream();
    }

    void setFormat(int ct, int spf) {
        std::lock_guard<std::mutex> lk(mDecMu);
        const int i = ctIndex(ct);
        if (i >= 0 && spf > 0 && spf <= 4096) mWantSpf[i] = spf;
        mClosed = false;  /* a new audio SETUP: accept packets again */
        mHaveSeq = false;
        if (i >= 0) {
            mSessionCt.store(ct);
            /* a new session starts with the extra remembered for its type; a new SETUP
               within a running session keeps the current one; both are measured again */
            if (!mSession.load()) mExtra->store(mRemembered[i].load());
            mSizeReq.store(true);
        }
    }

    void expectSession(int ct) {
        const int i = ctIndex(ct);
        if (i < 0 || mSession.load()) return;
        mExtra->store(mRemembered[i].load());
    }

    void decode(const uint8_t *data, int len, int ct, uint64_t ntpLocalNs, uint64_t ntpRemoteNs, uint16_t seqnum,
                uint32_t rtp) {
        if (!data || len <= 0) return;
        const int64_t now = z9x_mono_ns();
        const int64_t extra = mExtra->load(std::memory_order_relaxed);
        int64_t ts = mClock ? mClock->toMono(ntpRemoteNs, ntpLocalNs) : 0;
        if (ts && ts + mLatencyNs - now > 3 * Z9X_NS_PER_SEC) ts = 0;  /* sender clock not usable */
        const bool synced = ts != 0;
        const int64_t target = (synced ? ts : now) + mLatencyNs + mOffsetNs + extra;
        {
            std::lock_guard<std::mutex> lk(mDecMu);
            /* after stop(), packets of the ended session that are still in flight (UxPlay
               calls conn_destroy before it stops the RTP threads) must not bring a decoder
               and the output back: drop them until the next SETUP (setFormat) */
            if (mClosed) return;
            const int i = ctIndex(ct);
            if (i < 0) return;
            const int spf = mWantSpf[i];
            bool disc = false;
            const int lost = countPacket(seqnum, rtp, spf, target, &disc);
            if (lost < 0) return;  /* older than the last packet: already played */
            if (mDecoder && mDecoder->failed()) {
                mDecoder.reset();
                /* recreate at once; back off if it keeps failing */
                mRetryAtNs = now - mLastFailNs < 2 * kDecoderRetryNs ? now + kDecoderRetryNs : now;
                mLastFailNs = now;
                Z9X_LOGW("audio: recreating the decoder");
            }
            if (ct != mDecCt || spf != mDecSpf || (!mDecoder && now >= mRetryAtNs)) {
                mDecoder.reset();  /* the old one stops writing before the new one starts */
                mDecCt = ct;
                mDecSpf = spf;
                mDecoder = makeDecoder(ct, spf);
                mRetryAtNs = mDecoder ? 0 : now + Z9X_NS_PER_SEC;
            }
            if (mDecoder) mDecoder->decode(data, len, target, lost * spf, disc, extra);
        }
        if (mClock) mClock->noteAudio(synced ? ts : 0, target, now);
        mLeadSum.fetch_add(target - now, std::memory_order_relaxed);
        mLeadN.fetch_add(1, std::memory_order_relaxed);
        mSession.store(true, std::memory_order_relaxed);
        if (!mStreamOpen.load(std::memory_order_relaxed)) {
            mCv.notify_all();  /* worker opens the output */
        }
    }

    uint64_t resendDeadline(uint64_t ntpLocalNs, uint64_t ntpRemoteNs) {
        if (!mClock) return 0;
        const int64_t ts = mClock->toMono(ntpRemoteNs, ntpLocalNs, false);
        if (!ts) return 0;
        const int64_t now = z9x_mono_ns();
        int64_t lead = mTl.outLead();
        if (lead <= 0) lead = mLastOutLeadNs.load(std::memory_order_relaxed);
        if (lead <= 0) lead = kAssumedOutLeadNs;
        const int64_t deadline = ts + mLatencyNs + mOffsetNs + mExtra->load(std::memory_order_relaxed) - lead -
                                 kResendMarginNs;
        if (deadline - now > 3 * Z9X_NS_PER_SEC) return 0;  /* not plausible: fixed wait */
        return deadline > 0 ? (uint64_t) deadline : 1;
    }

    void resendStats(unsigned requested, unsigned received, unsigned late, unsigned givenUp) {
        bump(mResendReq, requested);
        bump(mResendGot, received);
        bump(mResendLate, late);
        bump(mResendGaveUp, givenUp);
    }

    void flush() {
        {
            std::lock_guard<std::mutex> lk(mDecMu);
            mHaveSeq = false;  /* the sender may restart its sequence numbers */
        }
        mTl.requestFlush();
    }

    void stop() {
        {
            std::lock_guard<std::mutex> lk(mDecMu);
            mDecoder.reset();
            mDecCt = -1;
            mClosed = true;
            mHaveSeq = false;
        }
        mSession.store(false);
        /* the extra delay belongs to this session (the worker forgets its timing state) */
        mSessionReset.store(true);
        const int64_t extra = mExtra->exchange(0);
        if (extra > 0) Z9X_LOGI("audio: session ended, extra delay %lld ms -> 0", toMs(extra));
        mTl.requestFlush();
        mCv.notify_all();
    }

    void setVolumeDb(float db) {
        mVolumeDb.store(db);
        double gain;
        if (db <= -144.0f || db <= -30.0f) {
            gain = 0.0;
        } else if (db >= 0.0f) {
            gain = 1.0;
        } else {
            /* UxPlay's mapping: slider fraction, tapered so that halving the remaining
               slider length is -10 dB ("dasl" taper), never below the flat -30..0 dB line */
            const double frac = (30.0 + db) / 30.0;
            const double flat = -30.0 + 30.0 * frac;
            double tapered = 10.0 * std::log2(frac);
            const double outDb = std::max(tapered, flat);
            gain = std::pow(10.0, outDb / 20.0);
        }
        mGain.store((float) gain);
    }
    float volumeDb() const { return mVolumeDb.load(); }
    void setMuted(bool m) { mMuted.store(m); }

private:
    static int ctIndex(int ct) {
        switch (ct) {
        case CT_ALAC: return 0;
        case CT_AAC_LC: return 1;
        case CT_AAC_ELD: return 2;
        default: return -1;
        }
    }

    /* mDecMu: packets in / lost (sequence gaps left by the reorder buffer). Returns the number
       of packets missing just before this one, or -1 for a packet older than the last one
       (dropped: its slot was played; UxPlay's reorder buffer sends none, but kOlderMax of them
       in a row are a sender that restarted its numbers: accepted as a new stream). *disc: a
       new stream starts here (restart, or a forward jump of 1000 packets or more). Also the
       timing statistics at arrival: RTP timestamp steps other than spf between consecutive
       packets, and play time jumps of more than kJumpNs against the RTP step. */
    int countPacket(uint16_t seq, uint32_t rtp, int spf, int64_t target, bool *disc) {
        int lost = 0;
        if (mHaveSeq) {
            const uint16_t gap = (uint16_t) (seq - (uint16_t) (mLastSeq + 1));
            if (gap >= 0x8000) {
                if (++mOlderRun < kOlderMax) {
                    bump(mPktOlder);
                    return -1;
                }
                *disc = true;
            } else if (gap >= 1000) {
                *disc = true;
            } else if (gap > 0) {
                bump(mPktLost, gap);
                lost = gap;
            }
            if (!*disc) {
                const int64_t step = (int64_t) (uint32_t) (rtp - mLastRtp);
                if (step != (int64_t) spf * (lost + 1)) bump(mRtpOff);
                const int64_t dt = target - mLastTarget - framesToNs(step);
                if (dt > kJumpNs || dt < -kJumpNs) bump(mInJumps);
            }
        }
        mOlderRun = 0;
        mHaveSeq = true;
        mLastSeq = seq;
        mLastRtp = rtp;
        mLastTarget = target;
        bump(mPktIn);
        return lost;
    }

    std::unique_ptr<Decoder> makeDecoder(int ct, int spf) {
        if (ct == CT_ALAC) {
            auto d = std::make_unique<AlacDecoder>(mTl, spf);
            if (d->init()) {
                Z9X_LOGI("audio: ALAC decoder (Apple reference), spf %d", spf);
                return d;
            }
            Z9X_LOGE("audio: ALAC init failed (spf %d)", spf);
            return nullptr;
        }
        auto d = std::make_unique<AacDecoder>(mTl);
        if (d->init(ct, spf)) return d;
        Z9X_LOGE("audio: AAC decoder init failed (ct %d)", ct);
        return nullptr;
    }

    /* ---- AAudio ---- */

    static aaudio_data_callback_result_t dataCb(AAudioStream *, void *user, void *audioData,
                                                int32_t numFrames) {
        static_cast<AudioEngineImpl *>(user)->render((int16_t *) audioData, numFrames);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static void errorCb(AAudioStream *, void *user, aaudio_result_t error) {
        auto *self = static_cast<AudioEngineImpl *>(user);
        Z9X_LOGW("audio: AAudio error %d (%s), reopening", error, AAudio_convertResultToText(error));
        self->mDisconnected.store(true);
        self->mCv.notify_all();  /* never close the stream from this callback */
    }

    /* data callback: no allocation, no lock, no logging */
    void render(int16_t *out, int32_t frames) {
        if (frames <= 0) return;
        const int64_t now = z9x_mono_ns();
        /* presentation time of out[0] */
        int64_t p0;
        int64_t pos, t;
        if (readTimestamp(&pos, &t)) {
            p0 = t + framesToNs((int64_t) mDelivered - pos);
        } else if (now - mOpenedNs < kTimestampWaitNs) {
            /* no timestamp yet: keep the data (silence now) rather than align it on a guess */
            memset(out, 0, (size_t) frames * kCh * sizeof(int16_t));
            mDelivered += (uint64_t) frames;
            return;
        } else {
            p0 = now + framesToNs(mBufferFrames.load(std::memory_order_relaxed)) + kFallbackHalLatencyNs;
            mFallbackCallbacks.fetch_add(1, std::memory_order_relaxed);
        }
        mTl.setOutLead(p0 + framesToNs(frames) - now);
        mTl.read(out, frames, p0);
        mDelivered += (uint64_t) frames;

        float target = mMuted.load(std::memory_order_relaxed) ? 0.0f : mGain.load(std::memory_order_relaxed);
        float g = mCurGain;
        if (g == 1.0f && target == 1.0f) return;
        const float step = (target - g) / (float) std::max(frames, 1);
        for (int i = 0; i < frames; i++) {
            g += step;
            for (int c = 0; c < kCh; c++) {
                const float v = out[i * kCh + c] * g;
                out[i * kCh + c] = (int16_t) std::max(-32768.0f, std::min(32767.0f, v));
            }
        }
        mCurGain = target;
    }

    bool readTimestamp(int64_t *pos, int64_t *t) {
        for (int i = 0; i < 4; i++) {
            const uint32_t s1 = mTsSeq.load(std::memory_order_acquire);
            if (s1 & 1) continue;
            *pos = mTsPos.load(std::memory_order_relaxed);
            *t = mTsTime.load(std::memory_order_relaxed);
            std::atomic_thread_fence(std::memory_order_acquire);
            if (mTsSeq.load(std::memory_order_relaxed) == s1) return s1 != 0;
        }
        return false;
    }

    void writeTimestamp(int64_t pos, int64_t t) {
        const uint32_t s = mTsSeq.load(std::memory_order_relaxed);
        mTsSeq.store(s + 1, std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_release);
        mTsPos.store(pos, std::memory_order_relaxed);
        mTsTime.store(t, std::memory_order_relaxed);
        mTsSeq.store(s + 2, std::memory_order_release);
    }

    bool openStream() {
        AAudioStreamBuilder *b = nullptr;
        if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return false;
        AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_NONE);
        AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setChannelCount(b, kCh);
        AAudioStreamBuilder_setSampleRate(b, kRate);
        AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
        AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
        /* 0 = AudioFlinger's own size; set after repeated xruns at full capacity */
        if (mCapacityFrames > 0) AAudioStreamBuilder_setBufferCapacityInFrames(b, mCapacityFrames);
        AAudioStreamBuilder_setDataCallback(b, dataCb, this);
        AAudioStreamBuilder_setErrorCallback(b, errorCb, this);
        AAudioStream *s = nullptr;
        aaudio_result_t r = AAudioStreamBuilder_openStream(b, &s);
        AAudioStreamBuilder_delete(b);
        if (r != AAUDIO_OK || !s) {
            Z9X_LOGE("audio: AAudio open failed: %s", AAudio_convertResultToText(r));
            dropCapacityRequest();
            return false;
        }
        if (AAudioStream_getSampleRate(s) != kRate || AAudioStream_getChannelCount(s) != kCh) {
            Z9X_LOGE("audio: AAudio gave %d Hz / %d ch", AAudioStream_getSampleRate(s),
                     AAudioStream_getChannelCount(s));
            AAudioStream_close(s);
            return false;
        }
        mDelivered = 0;
        mTsSeq.store(0);
        mTsCount = 0;
        mTsOutliers = 0;
        mTsLastPos = -1;
        mTsSpreadMaxNs = 0;
        mFallbackCallbacks.store(0);
        mFallbackLogged = false;
        mOpenedNs = z9x_mono_ns();
        mCurGain = mMuted.load() ? 0.0f : mGain.load();
        mBufferFrames.store(AAudioStream_getBufferSizeInFrames(s));
        mXrunSeen = std::max(AAudioStream_getXRunCount(s), 0);
        mXrunWinCount = 0;
        mStatsNs = 0;
        mTl.requestRealign();  /* a reopen: the ring's position belonged to the old stream */
        r = AAudioStream_requestStart(s);
        if (r != AAUDIO_OK) {
            Z9X_LOGE("audio: AAudio start failed: %s", AAudio_convertResultToText(r));
            AAudioStream_close(s);
            dropCapacityRequest();
            return false;
        }
        mStream = s;
        mStreamOpen.store(true);
        Z9X_LOGI("audio: AAudio out %d Hz, buffer %d/%d frames (capacity asked %d), burst %d, perf %d", kRate,
                 AAudioStream_getBufferSizeInFrames(s), AAudioStream_getBufferCapacityInFrames(s),
                 mCapacityFrames, AAudioStream_getFramesPerBurst(s), AAudioStream_getPerformanceMode(s));
        return true;
    }

    /* the capacity raised after xruns may be refused: retry with AudioFlinger's own size
       rather than failing every second for the rest of the receiver's life */
    void dropCapacityRequest() {
        if (mCapacityFrames <= 0) return;
        Z9X_LOGW("audio: retrying without the capacity request (%d frames)", mCapacityFrames);
        mCapacityFrames = 0;
    }

    void closeStream() {
        if (!mStream) return;
        AAudioStream_requestStop(mStream);
        AAudioStream_close(mStream);  /* no callback runs after this returns */
        mStream = nullptr;
        mStreamOpen.store(false);
        mTl.setOutLead(0);
        Z9X_LOGI("audio: AAudio closed");
    }

    /* worker, stream open: xruns (buffer growth), timestamp filter */
    void pollOutput(int64_t now) {
        const int32_t xr = AAudioStream_getXRunCount(mStream);
        if (xr > mXrunSeen) {
            const int32_t n = xr - mXrunSeen;
            mXrunSeen = xr;
            mXrunTotal += (uint32_t) n;
            mTsCount = 0;  /* an underrun delays everything after it: new timestamp window */
            if (now - mXrunWinStartNs > kXrunWindowNs) {
                mXrunWinStartNs = now;
                mXrunWinCount = 0;
            }
            mXrunWinCount += n;
            const int32_t size = AAudioStream_getBufferSizeInFrames(mStream);
            const int32_t cap = AAudioStream_getBufferCapacityInFrames(mStream);
            const int32_t burst = std::max(AAudioStream_getFramesPerBurst(mStream), kRate / 100);
            if (size < cap) {
                const int32_t got = AAudioStream_setBufferSizeInFrames(mStream, std::min(cap, size + burst));
                Z9X_LOGW("audio: %d output xrun(s); buffer %d -> %d frames (capacity %d)", n, size, got, cap);
            } else if (mXrunWinCount >= 3 && cap < kMaxCapacityFrames) {
                mCapacityFrames = std::min(kMaxCapacityFrames, cap * 2);
                Z9X_LOGW("audio: %d xruns within 10 s at full buffer (%d frames); reopening with capacity %d",
                         mXrunWinCount, cap, mCapacityFrames);
                closeStream();
                if (!openStream()) mOpenRetryAtNs = now + Z9X_NS_PER_SEC;
                return;
            }
        }
        int64_t pos = 0, t = 0;
        if (AAudioStream_getTimestamp(mStream, CLOCK_MONOTONIC, &pos, &t) == AAUDIO_OK && t > 0 && pos > 0) {
            if (pos != mTsLastPos && !plausibleTimestamp(pos, t)) {
                mTsLastPos = pos;
                mTsRejected++;  /* the window keeps its earlier samples (frame counting goes on) */
            } else if (pos != mTsLastPos) {  /* a repeated position is stale: skip it */
                mTsLastPos = pos;
                mTsWin[mTsCount % kTsWindow] = {pos, t};
                mTsCount++;
                /* earliest epoch (t - pos / rate) of the window, relative to this sample */
                const int n = std::min(mTsCount, kTsWindow);
                int best = 0;
                int64_t lo = 0, hi = 0;
                for (int i = 0; i < n; i++) {
                    const int64_t rel = (mTsWin[i].t - t) - framesToNs(mTsWin[i].pos - pos);
                    if (i == 0 || rel < lo) { lo = rel; best = i; }
                    if (i == 0 || rel > hi) hi = rel;
                }
                mTsSpreadMaxNs = std::max(mTsSpreadMaxNs, hi - lo);
                mTsBest = best;
                writeTimestamp(mTsWin[best].pos, mTsWin[best].t);
            }
        } else if (!mFallbackLogged && now - mOpenedNs > Z9X_NS_PER_SEC &&
                   mFallbackCallbacks.load(std::memory_order_relaxed) > 0) {
            mFallbackLogged = true;  /* once per stream */
            Z9X_LOGW("audio: no AAudio timestamp yet; A/V sync uses buffer %d frames + %lld ms "
                     "estimated output latency", mBufferFrames.load(), toMs(kFallbackHalLatencyNs));
        }
        mBufferFrames.store(AAudioStream_getBufferSizeInFrames(mStream));
    }


    /* worker: a new timestamp agrees with the window (frame-0 time within kTsEpochTolNs of its
       best, the position not behind it); kTsAgreeSamples outliers in a row that agree with each
       other are a real latency change: the window restarts with them */
    bool plausibleTimestamp(int64_t pos, int64_t t) {
        if (mTsCount == 0) return true;
        const TsSample &b = mTsWin[mTsBest];
        const int64_t rel = (t - b.t) - framesToNs(pos - b.pos);  /* epoch difference */
        if (pos >= b.pos && rel <= kTsEpochTolNs && rel >= -kTsEpochTolNs) {
            mTsOutliers = 0;
            return true;
        }
        const int64_t epoch = t - framesToNs(pos);
        if (mTsOutliers > 0 && (epoch - mTsOutlierEpoch > kTsEpochTolNs || mTsOutlierEpoch - epoch > kTsEpochTolNs)) {
            mTsOutliers = 0;
        }
        if (mTsOutliers++ == 0) mTsOutlierEpoch = epoch;
        if (mTsOutliers < kTsAgreeSamples) return false;
        Z9X_LOGW("audio: output timestamps moved by %lld ms for %d samples: new timestamp window", toMs(rel),
                 kTsAgreeSamples);
        mTsCount = 0;
        mTsOutliers = 0;
        return true;
    }

    /* ---- extra delay: session timing (worker) ---- */

    void clearLateHistory() {
        for (int64_t &s : mLateHist) s = INT64_MAX;
    }

    /* A/V play time summary for the logs: audio at sender time + this, video likewise */
    int64_t videoPlusNs(int64_t extra) const {
        return mLatencyNs + extra + (mClock ? mClock->videoCorrectionNs() : 0);
    }

    void sessionTiming(int64_t now) {
        if (mSessionReset.exchange(false)) {
            mPhase = Phase::kIdle;
            clearLateHistory();
            mLateCapLogged = false;
        }
        if (mSizeReq.exchange(false)) {
            mPhase = Phase::kMeasuring;
            mMeasureRawMin = INT64_MAX;
            mMeasureWindows = 0;
            mMeasureStartExtra = mExtra->load();
            clearLateHistory();
            mLateCapLogged = false;
            mWinSeqSeen = mTl.windowSeq();  /* windows from now on */
        }
        if (mStream && mFallbackCallbacks.load(std::memory_order_relaxed) == 0) {
            const int64_t lead = mTl.outLead();
            if (lead > 0) mLastOutLeadNs.store(lead, std::memory_order_relaxed);
        }
        const uint32_t seq = mTl.windowSeq();
        if (seq == mWinSeqSeen) return;
        mWinSeqSeen = seq;
        const PcmTimeline::Window win = mTl.window();
        mStatSpareMinNs = std::min(mStatSpareMinNs, win.spareMin);
        if (mPhase == Phase::kMeasuring) {
            measureWindow(win, now);
        } else if (mPhase == Phase::kSized) {
            watchLate(win, now);
        }
    }

    /* the first second(s) of a session: size the extra delay once */
    void measureWindow(const PcmTimeline::Window &win, int64_t now) {
        mMeasureRawMin = std::min(mMeasureRawMin, win.rawMin);
        mMeasureWindows++;
        int64_t lead = mStream ? mTl.outLead() : 0;
        bool estimated = false;
        if (lead <= 0) {
            if (mMeasureWindows < kMeasureMaxWindows) return;  /* the output is still starting */
            lead = mLastOutLeadNs.load(std::memory_order_relaxed);
            if (lead <= 0) lead = kAssumedOutLeadNs;
            estimated = true;
        }
        const int64_t spareAt0 = mMeasureRawMin - lead;  /* worst spare without extra delay */
        const int64_t want = std::clamp(roundUp10Ms(kSpareTargetNs - spareAt0), (int64_t) 0, kExtraMaxNs);
        const int64_t cur = mExtra->load();
        const int64_t next = std::abs(want - cur) >= kExtraHysteresisNs ? want : cur;
        if (next != cur) mExtra->store(next);
        const int i = ctIndex(mSessionCt.load());
        if (i >= 0) mRemembered[i].store(next);
        mPhase = Phase::kSized;
        mLastChangeNs = now;
        Z9X_LOGI("audio: session timing: packets arrive >= %lld ms before their play time at latency %lld ms, "
                 "output lead %lld ms%s -> extra %lld ms (started with %lld ms); sound at sender time + %lld ms, "
                 "picture + %lld ms (released %lld ms early for the display)",
                 toMs(mMeasureRawMin), toMs(mLatencyNs), toMs(lead), estimated ? " (estimated)" : "", toMs(next),
                 toMs(mMeasureStartExtra), toMs(mLatencyNs + next + mOffsetNs), toMs(videoPlusNs(next)),
                 toMs(mClock ? mClock->displayDelayNs() : 0));
    }

    /* after sizing: raise only when packets keep coming after their play deadline */
    void watchLate(const PcmTimeline::Window &win, int64_t now) {
        mLateHist[mLateHistPos] = win.late > 0 ? win.spareMin : INT64_MAX;
        mLateHistPos = (mLateHistPos + 1) % kLateHistory;
        int late = 0;
        int64_t worst = INT64_MAX;
        for (const int64_t s : mLateHist) {
            if (s == INT64_MAX) continue;
            late++;
            worst = std::min(worst, s);
        }
        if (late < kLateWindows || now - mLastChangeNs < kRaiseIntervalNs) return;
        const int64_t cur = mExtra->load();
        if (cur >= kExtraMaxNs) {
            if (!mLateCapLogged) {
                mLateCapLogged = true;
                Z9X_LOGW("audio: packets still come after their play deadline (worst %lld ms late) with the "
                         "maximum extra delay (%lld ms)", toMs(-worst), toMs(cur));
            }
            return;
        }
        const int64_t step = std::clamp(roundUp10Ms(kRaiseSpareNs - worst), kRaiseMinNs, kRaiseMaxNs);
        const int64_t next = std::min(cur + step, kExtraMaxNs);
        mExtra->store(next);
        const int i = ctIndex(mSessionCt.load());
        if (i >= 0) mRemembered[i].store(next);
        mLastChangeNs = now;
        clearLateHistory();
        Z9X_LOGW("audio: packets came after their play deadline in %d of the last %d s (worst %lld ms late); "
                 "extra %lld -> %lld ms (raised at most every %lld s); sound at sender time + %lld ms, picture + %lld ms",
                 late, kLateHistory, toMs(-worst), toMs(cur), toMs(next), toMs(kRaiseIntervalNs) / 1000,
                 toMs(mLatencyNs + next + mOffsetNs), toMs(videoPlusNs(next)));
    }

    /* worker, stream open: one line every kStatsPeriodNs while packets arrive */
    void logStats(int64_t now) {
        const PcmTimeline::Stats s = mTl.stats();
        const uint32_t pkts = mPktIn.load(std::memory_order_relaxed);
        const uint32_t lost = mPktLost.load(std::memory_order_relaxed);
        const uint32_t rq = mResendReq.load(std::memory_order_relaxed), rg = mResendGot.load(std::memory_order_relaxed),
                       rl = mResendLate.load(std::memory_order_relaxed), ru = mResendGaveUp.load(std::memory_order_relaxed);
        const uint32_t inJumps = mInJumps.load(std::memory_order_relaxed), rtpOff = mRtpOff.load(std::memory_order_relaxed),
                       older = mPktOlder.load(std::memory_order_relaxed);
        int64_t devLo = 0, devHi = 0;
        mTl.takeDeviation(&devLo, &devHi);
        const int64_t leadSum = mLeadSum.exchange(0, std::memory_order_relaxed);
        const int64_t leadN = mLeadN.exchange(0, std::memory_order_relaxed);
        if (mStatsNs != 0 && (pkts != mBase.pkts || mXrunTotal != mBase.xruns || s.underruns != mBase.s.underruns)) {
            const uint32_t fb = mFallbackCallbacks.load(std::memory_order_relaxed);
            Z9X_LOGI("audio %llds: %u pkts, %u lost, %u late; resend req %u got %u late %u gave up %u; "
                     "play lead at arrival avg %lld ms; spare min %lld ms; ring %lld ms; out lead %lld ms, "
                     "buffer %d/%d, burst %d, xruns %u; ts spread %lld ms%s; drift corr -%u +%u, realign %u, "
                     "underruns %u (%lld ms late audio skipped), concealed %lld ms; extra %lld ms; timing: play time "
                     "jumps in %u out %u, rtp steps off %u, older %u, deviation %lld..%lld ms, offset %lld ms, anchors soft %u "
                     "hard %u, "
                     "ts rejected %u",
                     (long long) ((now - mStatsNs) / Z9X_NS_PER_SEC), pkts - mBase.pkts, lost - mBase.lost,
                     s.lateWrites - mBase.s.lateWrites, rq - mBase.rq, rg - mBase.rg, rl - mBase.rl, ru - mBase.ru,
                     leadN ? toMs(leadSum / leadN) : -1LL,
                     mStatSpareMinNs == INT64_MAX ? -1LL : toMs(mStatSpareMinNs),
                     toMs(framesToNs(mTl.bufferedFrames())), toMs(mTl.outLead()),
                     AAudioStream_getBufferSizeInFrames(mStream), AAudioStream_getBufferCapacityInFrames(mStream),
                     AAudioStream_getFramesPerBurst(mStream), mXrunTotal - mBase.xruns,
                     toMs(mTsSpreadMaxNs), fb ? " (no timestamp: fallback)" : "",
                     s.drops - mBase.s.drops, s.repeats - mBase.s.repeats, s.realigns - mBase.s.realigns,
                     s.underruns - mBase.s.underruns, toMs(framesToNs(s.lateSkipFrames - mBase.s.lateSkipFrames)),
                     toMs(framesToNs(s.concealFrames - mBase.s.concealFrames)), toMs(mExtra->load()),
                     inJumps - mBase.inJumps, s.tsJumps - mBase.s.tsJumps, rtpOff - mBase.rtpOff, older - mBase.older,
                     devLo > devHi ? 0LL : toMs(devLo), devLo > devHi ? 0LL : toMs(devHi), toMs(mTl.steeredOffset()),
                     s.softAnchors - mBase.s.softAnchors, s.hardAnchors - mBase.s.hardAnchors,
                     mTsRejected - mBase.tsRejected);
        }
        mBase.s = s;
        mBase.pkts = pkts;
        mBase.lost = lost;
        mBase.xruns = mXrunTotal;
        mBase.rq = rq;
        mBase.rg = rg;
        mBase.rl = rl;
        mBase.ru = ru;
        mBase.inJumps = inJumps;
        mBase.rtpOff = rtpOff;
        mBase.older = older;
        mBase.tsRejected = mTsRejected;
        mStatsNs = now;
        mStatSpareMinNs = INT64_MAX;
        mTsSpreadMaxNs = 0;
    }

    void workerLoop() {
        std::unique_lock<std::mutex> lk(mMu);
        while (!mQuit) {
            /* 50 ms while the output runs (timestamp sampling), else 100 ms */
            mCv.wait_for(lk, std::chrono::milliseconds(mStream ? 50 : 100));
            if (mQuit) break;
            lk.unlock();
            const int64_t now = z9x_mono_ns();
            const int64_t idle = mSession.load() ? kSessionIdleCloseNs : kIdleCloseNs;
            const bool active = now - mTl.lastWriteNs() < idle || !mTl.empty();
            if (mDisconnected.exchange(false) && mStream) {
                closeStream();
            }
            if (!mStream && active && mTl.lastWriteNs() != 0) {
                if (now >= mOpenRetryAtNs && !openStream()) mOpenRetryAtNs = now + Z9X_NS_PER_SEC;
            } else if (mStream && !active) {
                closeStream();  /* idle: the ring is already empty */
            }
            if (mStream) pollOutput(now);
            sessionTiming(now);
            if (mStream && (mStatsNs == 0 || now - mStatsNs >= kStatsPeriodNs)) logStats(now);
            lk.lock();
        }
    }

    const int64_t mLatencyNs;
    const int64_t mOffsetNs;
    std::atomic<int64_t> mOwnExtra{0};  /* when the caller passes none */
    std::atomic<int64_t> *const mExtra;
    AvClock *const mClock;
    PcmTimeline mTl;

    std::mutex mDecMu;  /* decoder use vs. teardown */
    std::unique_ptr<Decoder> mDecoder;
    int mDecCt = -1, mDecSpf = 0;
    bool mClosed = false;  /* mDecMu: stop() until the next setFormat() */
    int mWantSpf[3] = {352, 1024, 480};
    int64_t mRetryAtNs = 0;
    int64_t mLastFailNs = INT64_MIN / 2;
    bool mHaveSeq = false;  /* mDecMu */
    uint16_t mLastSeq = 0;  /* mDecMu */
    uint32_t mLastRtp = 0;  /* mDecMu */
    int64_t mLastTarget = 0;  /* mDecMu */
    uint32_t mOlderRun = 0;  /* mDecMu */
    std::atomic<uint32_t> mPktIn{0}, mPktLost{0}, mPktOlder{0}, mRtpOff{0}, mInJumps{0};
    std::atomic<uint32_t> mResendReq{0}, mResendGot{0}, mResendLate{0}, mResendGaveUp{0};
    std::atomic<int64_t> mLeadSum{0}, mLeadN{0};
    std::atomic<bool> mSession{false};  /* data since the last stop() */

    /* session timing: requests (any thread) and remembered extra per audio type */
    std::atomic<int> mSessionCt{-1};
    std::atomic<bool> mSizeReq{false};
    std::atomic<bool> mSessionReset{false};
    std::atomic<int64_t> mRemembered[3];
    std::atomic<int64_t> mLastOutLeadNs{0};

    std::mutex mMu;
    std::condition_variable mCv;
    bool mQuit = false;
    std::thread mWorker;
    /* worker thread only */
    AAudioStream *mStream = nullptr;
    int64_t mOpenRetryAtNs = 0;
    bool mFallbackLogged = false;
    int32_t mCapacityFrames = 0;
    int32_t mXrunSeen = 0;
    uint32_t mXrunTotal = 0;
    int64_t mXrunWinStartNs = 0;
    int32_t mXrunWinCount = 0;
    struct TsSample { int64_t pos, t; };
    TsSample mTsWin[kTsWindow] = {};
    int mTsCount = 0;
    int mTsBest = 0;
    int mTsOutliers = 0;
    int64_t mTsOutlierEpoch = 0;
    uint32_t mTsRejected = 0;
    int64_t mTsLastPos = -1;
    int64_t mTsSpreadMaxNs = 0;
    enum class Phase { kIdle, kMeasuring, kSized };
    Phase mPhase = Phase::kIdle;
    uint32_t mWinSeqSeen = 0;
    int64_t mMeasureRawMin = INT64_MAX;
    int mMeasureWindows = 0;
    int64_t mMeasureStartExtra = 0;
    int64_t mLastChangeNs = 0;
    int64_t mLateHist[kLateHistory];  /* worst spare of a window with late writes, INT64_MAX = none */
    int mLateHistPos = 0;
    bool mLateCapLogged = false;
    int64_t mStatsNs = 0;
    int64_t mStatSpareMinNs = INT64_MAX;
    struct { PcmTimeline::Stats s; uint32_t pkts, lost, xruns, rq, rg, rl, ru, inJumps, rtpOff, older, tsRejected; } mBase = {};
    std::atomic<bool> mStreamOpen{false};
    std::atomic<bool> mDisconnected{false};

    /* callback state (mOpenedNs: written before the stream starts) */
    int64_t mOpenedNs = 0;
    uint64_t mDelivered = 0;
    float mCurGain = 1.0f;
    std::atomic<uint32_t> mTsSeq{0};
    std::atomic<int64_t> mTsPos{0}, mTsTime{0};
    std::atomic<int32_t> mBufferFrames{0};
    std::atomic<uint32_t> mFallbackCallbacks{0};

    std::atomic<float> mGain{1.0f};
    std::atomic<float> mVolumeDb{0.0f};
    std::atomic<bool> mMuted{false};
};

AudioEngine::AudioEngine(int64_t latencyNs, int64_t audioOffsetNs, std::atomic<int64_t> *extraNs, AvClock *clock)
    : mImpl(new AudioEngineImpl(latencyNs, audioOffsetNs, extraNs, clock)) {}
AudioEngine::~AudioEngine() = default;
void AudioEngine::setFormat(int ct, int spf) { mImpl->setFormat(ct, spf); }
void AudioEngine::expectSession(int ct) { mImpl->expectSession(ct); }
void AudioEngine::decode(const uint8_t *d, int len, int ct, uint64_t ntpLocal, uint64_t ntpRemote, uint16_t seq,
                         uint32_t rtp) {
    mImpl->decode(d, len, ct, ntpLocal, ntpRemote, seq, rtp);
}
uint64_t AudioEngine::resendDeadline(uint64_t ntpLocal, uint64_t ntpRemote) {
    return mImpl->resendDeadline(ntpLocal, ntpRemote);
}
void AudioEngine::resendStats(unsigned requested, unsigned received, unsigned late, unsigned givenUp) {
    mImpl->resendStats(requested, received, late, givenUp);
}
void AudioEngine::flush() { mImpl->flush(); }
void AudioEngine::stop() { mImpl->stop(); }
void AudioEngine::setVolumeDb(float db) { mImpl->setVolumeDb(db); }
float AudioEngine::volumeDb() const { return mImpl->volumeDb(); }
void AudioEngine::setMuted(bool m) { mImpl->setMuted(m); }
