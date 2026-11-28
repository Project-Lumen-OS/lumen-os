/*
 * PcmTimeline for Z9xAirPlay (audio_engine.cpp): the SPSC ring of 44.1 kHz stereo int16
 * between the decoder output (producer) and the AAudio data callback (consumer), with
 * anchors that map ring positions to presentation times (CLOCK_MONOTONIC ns). No Android
 * dependency, so the host simulation (test/native) runs this exact code.
 *
 * Writer (1.0.12). Audio is placed by frame counting: a packet that follows its predecessor
 * (no sequence gap, no packet the decoder dropped) is always written right after it, whatever
 * its play time says. Only audio known to be missing (the callers count it from the RTP
 * sequence numbers and from packets the decoder did not decode) is concealed, exactly by its
 * length: up to 30 ms by repeating the last 11.6 ms of sound back and forth (continuous at
 * both turns), longer gaps fade that out and continue with silence; the audio after a
 * concealment is blended in (1.5 ms after a short one, a 10 ms fade-in after silence).
 *
 * Play times only steer. Every packet's play time T is compared with the frame-counted time
 * of its first frame; the median of the last kDevWindow differences (about 5 s of audio) is
 * the timeline offset. The steered offset follows it at most kSteerPpm (0.4 %, below the
 * reader's 0.5 % correction rate, so the reader never falls 50 ms behind and realigns); each
 * move of kSoftAnchorMinNs or more is a soft anchor the reader's drift correction slews to:
 * clock drift between sender and output, a few ms a minute. Only when every packet
 * for kStepHoldNs is more than kStepNs off on the same side has the sender's clock really
 * stepped: a hard anchor at the packet's own time (the reader skips or waits once). Jitter of
 * single packets or of the sender's sync updates never moves or cuts audio.
 * Until 1.0.11 every T difference above 40 ms inserted concealment and every one below
 * -40 ms cut audio, each leaving a soft move of up to 40 ms: a Mac mirroring 4K whose play
 * times jumped back and forth had ~40 % of its sound concealed and ~10 reader realigns per
 * 5 s with no packet lost (2026-11-28; test/native/timeline_sim.cpp replays it).
 *
 * Reader (unchanged from 1.0.2). Not aligned (start, after an underrun, a flush, a hard
 * anchor or a realign request): silence / skip to the exact frame. Aligned: an error above
 * 4 ms starts single-frame drops / repeats, blended with the neighbouring frame, at most
 * 0.5 % of the frames (1 % beyond 20 ms), until it is below 1 ms; a hard skip or silence only
 * when the error stays above 50 ms for 3 callbacks or exceeds 250 ms. The reader fades into
 * an underrun (3 ms), fades back in (10 ms) after any silence, and blends over a skip
 * (1.5 ms): no hard clicks.
 *
 * Adapted from jqssun/android-airplay-server v0.0.31 (GPL-3.0) timeline_buffer.h (SPSC ring).
 * Copyright (C) jqssun / android-airplay-server contributors
 * Copyright (C) 2026 Z9X project
 * SPDX-License-Identifier: GPL-3.0-only
 */
#ifndef Z9X_PCM_TIMELINE_H
#define Z9X_PCM_TIMELINE_H

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>

#ifndef Z9X_TIMELINE_NOW
#include "z9x_common.h"
#define Z9X_TIMELINE_NOW() z9x_mono_ns()
#endif

namespace z9x_timeline {

constexpr int kRate = 44100;
constexpr int kCh = 2;
constexpr int64_t kMs = 1000000LL;
constexpr int64_t kSec = 1000000000LL;
/* reader corrections */
constexpr int64_t kSoftStartNs = 4 * kMs;
constexpr int64_t kSoftStopNs = 1 * kMs;
constexpr int64_t kSoftFastNs = 20 * kMs;
constexpr int64_t kHardErrNs = 50 * kMs;
constexpr int kHardPersistCallbacks = 3;
constexpr int64_t kHugeErrNs = 250 * kMs;
/* writer */
constexpr int kDevWindow = 512;                /* packets: ALAC 4 s, AAC-ELD 5.6 s, AAC-LC 12 s */
constexpr int kDevMinSamples = 16;             /* before that the start anchor stands */
constexpr int64_t kSoftAnchorMinNs = 2 * kMs;  /* smaller median moves are ignored */
constexpr int64_t kSteerPpm = 4000;            /* the steered offset moves at most 4 ms a second */
constexpr int64_t kStepNs = 150 * kMs;         /* every packet this far off on one side ... */
constexpr int64_t kStepHoldNs = 2000 * kMs;    /* ... for this long: the sender's clock stepped */
constexpr int64_t kJumpNs = 40 * kMs;          /* statistics: play time jumps between packets */
constexpr int64_t kMaxConcealNs = 750 * kMs;   /* more missing audio: a new timeline */
constexpr int64_t kSpareWindowNs = 1000 * kMs;
/* concealment and fades (frames at 44.1 kHz) */
constexpr int kPlcPeriodFrames = 512;  /* 11.6 ms of the last sound, played back and forth */
constexpr int kPlcMaxFrames = 1323;    /* 30 ms; a longer gap fades it out, then silence */
constexpr int kDeclickFrames = 64;     /* 1.5 ms blend from the last sample into new audio */
constexpr int kFadeInFrames = 441;     /* 10 ms fade-in after silence */
constexpr int kDecayFrames = 132;      /* 3 ms fade-out into an underrun */
constexpr int kRingFrames = kRate * 4; /* 4 s */

inline int64_t framesToNs(int64_t frames) { return frames * kSec / kRate; }
inline int64_t nsToFrames(int64_t ns) { return ns * kRate / kSec; }
inline int64_t absNs(int64_t v) { return v < 0 ? -v : v; }

/* single-writer counter, read by another thread */
inline void bump(std::atomic<uint32_t> &c, uint32_t n = 1) {
    c.store(c.load(std::memory_order_relaxed) + n, std::memory_order_relaxed);
}

class PcmTimeline {
public:
    PcmTimeline() : mBuf(new int16_t[(size_t) kRingFrames * kCh]) {}

    /* producer: `frames` frames whose first one should play at targetNs. missingFrames: audio
       known to be missing just before them (lost packets, packets the decoder dropped),
       concealed exactly. discontinuity: the sender started a new stream (sequence jump), the
       timeline starts again at targetNs. extraNs: the extra delay included in targetNs
       (session timing statistics). */
    void write(const int16_t *pcm, int frames, int64_t targetNs, int missingFrames, bool discontinuity,
               int64_t extraNs) {
        if (frames <= 0) return;
        const int64_t now = Z9X_TIMELINE_NOW();
        if (mReanchor.exchange(false, std::memory_order_acq_rel)) {
            mHaveAnchor = false;
            mRampLeft = 0;
            mWinSpareMin = mWinRawMin = INT64_MAX;
            mWinLate = 0;
            mWinStartNs = 0;
        }
        if (mDevReset.exchange(false, std::memory_order_acq_rel)) {
            mDevLo = INT64_MAX;
            mDevHi = INT64_MIN;
        }
        noteSpare(targetNs - now - mOutLeadNs.load(std::memory_order_relaxed), targetNs - extraNs - now, now);
        uint64_t w = mW.load(std::memory_order_relaxed);
        if (discontinuity) mHaveAnchor = false;
        if (mHaveAnchor && missingFrames > 0) {
            if (framesToNs(missingFrames) > kMaxConcealNs) {
                mHaveAnchor = false;  /* too much is gone: start again at this packet's time */
            } else {
                w = conceal(w, missingFrames);
            }
        }
        if (!mHaveAnchor) {
            startTimeline(w, targetNs);
        } else {
            steer(w, targetNs);
        }
        w = put(w, pcm, frames);
        mLastWriteNs.store(now, std::memory_order_relaxed);
    }

    /* any thread: the consumer drops everything written before this call at its next
       read (data written afterwards survives, so a flush while the output is closed
       cannot eat the start of the next session) */
    void requestFlush() {
        mFlushPos.store(mW.load(std::memory_order_acquire), std::memory_order_relaxed);
        mFlushSeq.fetch_add(1, std::memory_order_release);
        mReanchor.store(true, std::memory_order_release);
    }

    /* any thread (the output was reopened): the consumer realigns at its next read, so the
       data still in the ring is not played at the old stream's timing */
    void requestRealign() { mRealignReq.store(true, std::memory_order_release); }

    int64_t lastWriteNs() const { return mLastWriteNs.load(std::memory_order_relaxed); }
    bool empty() const {
        return mW.load(std::memory_order_acquire) == mR.load(std::memory_order_acquire);
    }
    /* frames written and not yet read (worker, for the statistics) */
    int64_t bufferedFrames() const {
        const uint64_t r = mR.load(std::memory_order_acquire);
        return (int64_t) (mW.load(std::memory_order_acquire) - r);
    }

    /* consumer: how far ahead of real time the callback reads (presentation time of the
       end of its buffer - now); the producer's spare time is measured against it */
    void setOutLead(int64_t ns) { mOutLeadNs.store(ns, std::memory_order_relaxed); }
    int64_t outLead() const { return mOutLeadNs.load(std::memory_order_relaxed); }

    /* worker: one window per second while data flows. spareMin: worst (T - now - output
       lead) of its writes; rawMin: worst (T - extra - now), how early the packets came
       before their play time without the extra delay and without the output lead; late:
       writes after their deadline (spare < 0). */
    struct Window { int64_t spareMin, rawMin; uint32_t late; };
    uint32_t windowSeq() const { return mPubSeq.load(std::memory_order_acquire); }
    Window window() const {
        return {mPubSpareMin.load(std::memory_order_relaxed), mPubRawMin.load(std::memory_order_relaxed),
                mPubLate.load(std::memory_order_relaxed)};
    }

    struct Stats {
        uint32_t writes, lateWrites, concealFrames;                  /* producer */
        uint32_t softAnchors, hardAnchors, tsJumps;                  /* producer: timing */
        uint32_t underruns, realigns, lateSkipFrames, drops, repeats;  /* consumer */
    };
    Stats stats() const {
        return {mWrites.load(std::memory_order_relaxed), mLateWrites.load(std::memory_order_relaxed),
                mConcealFrames.load(std::memory_order_relaxed), mSoftAnchors.load(std::memory_order_relaxed),
                mHardAnchors.load(std::memory_order_relaxed), mTsJumps.load(std::memory_order_relaxed),
                mUnderruns.load(std::memory_order_relaxed), mRealigns.load(std::memory_order_relaxed),
                mLateSkipFrames.load(std::memory_order_relaxed), mDrops.load(std::memory_order_relaxed),
                mRepeats.load(std::memory_order_relaxed)};
    }
    /* worker: range of (packet play time - steered timeline) since the last call, in ns
       (lo > hi: no packet); the next write starts a new range */
    void takeDeviation(int64_t *lo, int64_t *hi) {
        *lo = mPubDevLo.load(std::memory_order_relaxed);
        *hi = mPubDevHi.load(std::memory_order_relaxed);
        mPubDevLo.store(INT64_MAX, std::memory_order_relaxed);
        mPubDevHi.store(INT64_MIN, std::memory_order_relaxed);
        mDevReset.store(true, std::memory_order_release);
    }

    /* any thread: the steered offset of the timeline from frame counting (the median the
       reader is slewed to), ns */
    int64_t steeredOffset() const { return mSteered.load(std::memory_order_relaxed); }

    /* consumer: fill `frames` frames whose first one is presented at p0 (CLOCK_MONOTONIC).
       Not aligned (start, after an underrun, flush, hard anchor or realign): silence / skip
       to the exact frame. Aligned: drift corrections and hard realigns as in the header.
       Soft anchors keep the alignment (the drift correction slews to them). */
    void read(int16_t *out, int frames, int64_t p0) {
        uint64_t r = mR.load(std::memory_order_relaxed);
        const uint32_t fseq = mFlushSeq.load(std::memory_order_acquire);
        if (fseq != mSeenFlushSeq) {
            mSeenFlushSeq = fseq;
            const uint64_t fp = mFlushPos.load(std::memory_order_relaxed);
            if (fp > r) r = fp;
            uint32_t ar = mAR.load(std::memory_order_relaxed);
            const uint32_t aw = mAW.load(std::memory_order_acquire);
            while (ar != aw && mAnchors[ar % kAnchors].pos < fp) ar++;
            mAR.store(ar, std::memory_order_release);
            mCurValid = false;
            mAligned = false;
            mAfterUnderrun = false;
        }
        if (mRealignReq.exchange(false, std::memory_order_acq_rel)) {
            mAligned = false;
            mAfterUnderrun = false;
        }
        /* judged once per callback, at its first aligned sample: correction hysteresis,
           correction rate and the persistence of a large error */
        bool judged = false;
        int maxCorr = 0, corrSpacing = frames, corrections = 0, sinceCorr = 0;
        int done = 0;
        while (done < frames) {
            const uint64_t w = mW.load(std::memory_order_acquire);
            /* adopt anchors that start at or before the read position */
            uint32_t ar = mAR.load(std::memory_order_relaxed);
            const uint32_t aw = mAW.load(std::memory_order_acquire);
            while (ar != aw && mAnchors[ar % kAnchors].pos <= r) {
                mCur = mAnchors[ar % kAnchors];
                mCurValid = true;
                if (!mCur.soft) mAligned = false;  /* new timeline segment */
                ar++;
            }
            mAR.store(ar, std::memory_order_release);
            if (r == w) {
                silence(out, done, frames - done);
                if (mAligned) {
                    bump(mUnderruns);  /* data not there in time: late packets */
                    mAfterUnderrun = true;
                }
                mAligned = false;
                break;
            }
            uint64_t limit = w;
            if (ar != aw && mAnchors[ar % kAnchors].pos < limit) limit = mAnchors[ar % kAnchors].pos;
            const int64_t avail = (int64_t) (limit - r);
            if (!mCurValid) {  /* data written before any anchor: play as is */
                const int n = (int) std::min<int64_t>(frames - done, avail);
                r = get(r, out + (size_t) done * kCh, n);
                applyRamp(out + (size_t) done * kCh, n);
                done += n;
                continue;
            }
            const int64_t tr = mCur.t + framesToNs((int64_t) (r - mCur.pos));
            const int64_t p = p0 + framesToNs(done);
            const int64_t err = p - tr;  /* > 0: the sample is late */
            if (!mAligned) {
                const int64_t errFrames = nsToFrames(err);
                if (errFrames > 0) {  /* late: skip to the exact frame */
                    const int64_t skip = std::min<int64_t>(errFrames, avail);
                    r += (uint64_t) skip;
                    if (mAfterUnderrun) bump(mLateSkipFrames, (uint32_t) skip);
                    if (mReadRampLeft == 0) {  /* blend over the jump */
                        startReadRamp(done > 0 ? out + (size_t) (done - 1) * kCh : mLastOut, kDeclickFrames);
                    }
                    continue;
                }
                if (errFrames < 0) {  /* early: silence until it is due */
                    const int sil = (int) std::min<int64_t>(-errFrames, frames - done);
                    silence(out, done, sil);
                    done += sil;
                    continue;
                }
                mAligned = true;
                mAfterUnderrun = false;
                mCorrecting = false;
                mOverCallbacks = 0;
            }
            const int64_t aerr = err < 0 ? -err : err;
            if (!judged) {
                judged = true;
                mOverCallbacks = aerr > kHardErrNs ? mOverCallbacks + 1 : 0;
                if (aerr > kSoftStartNs) mCorrecting = true;
                maxCorr = aerr > kSoftFastNs ? frames / 100 + 1 : frames / 200 + 1;
                corrSpacing = std::max(frames / maxCorr, 1);
                sinceCorr = corrSpacing;
            }
            if (aerr > kHugeErrNs || mOverCallbacks >= kHardPersistCallbacks) {
                mAligned = false;  /* realign: the next pass skips / silences to the exact frame */
                mOverCallbacks = 0;
                bump(mRealigns);
                continue;
            }
            if (aerr < kSoftStopNs) mCorrecting = false;
            if (mCorrecting && corrections < maxCorr && sinceCorr >= corrSpacing) {
                int16_t *o = out + (size_t) done * kCh;
                if (err > 0 && avail >= 2) {
                    /* late: drop one frame (two ring frames become their average) */
                    const int16_t *a = frameAt(r), *b = frameAt(r + 1);
                    for (int c = 0; c < kCh; c++) o[c] = (int16_t) (((int32_t) a[c] + b[c]) >> 1);
                    r += 2;
                    done += 1;
                    corrections++;
                    sinceCorr = 0;
                    bump(mDrops);
                    continue;
                }
                if (err < 0) {
                    /* early: repeat one frame (the average of the last output and the next) */
                    const int16_t *prev = done > 0 ? o - kCh : mLastOut;
                    const int16_t *b = frameAt(r);
                    for (int c = 0; c < kCh; c++) o[c] = (int16_t) (((int32_t) prev[c] + b[c]) >> 1);
                    done += 1;
                    corrections++;
                    sinceCorr = 0;
                    bump(mRepeats);
                    continue;
                }
            }
            int n = (int) std::min<int64_t>(frames - done, avail);
            if (mCorrecting && corrections < maxCorr) n = std::min(n, corrSpacing - sinceCorr);
            n = std::max(n, 1);
            r = get(r, out + (size_t) done * kCh, n);
            applyRamp(out + (size_t) done * kCh, n);
            done += n;
            sinceCorr += n;
        }
        memcpy(mLastOut, out + (size_t) (frames - 1) * kCh, sizeof(mLastOut));
        mR.store(r, std::memory_order_release);
    }

private:
    struct Anchor { uint64_t pos; int64_t t; bool soft; };
    static constexpr uint32_t kAnchors = 64;

    /* producer: a new timeline (start, flush, sequence discontinuity, too much missing):
       position w plays at t; the timing median starts empty */
    void startTimeline(uint64_t w, int64_t t) {
        if (!publish(w, t, false)) return;  /* no room: the next packet starts it */
        mHaveAnchor = true;
        mBasePos = w;
        mBaseT = t;
        mPub = 0;
        mSteerT = t;
        mSteered.store(mPub, std::memory_order_relaxed);
        mDevN = mDevIdx = 0;
        mHavePrevDev = false;
        mStepSide = 0;
    }

    /* producer: the packet at w should play at t; steer the timeline by the median offset */
    void steer(uint64_t w, int64_t t) {
        const int64_t counted = mBaseT + framesToNs((int64_t) (w - mBasePos));
        const int64_t d = t - counted;
        if (mHavePrevDev && absNs(d - mPrevDev) > kJumpNs) bump(mTsJumps);
        mPrevDev = d;
        mHavePrevDev = true;
        const int64_t e = d - mPub;
        if (e < mDevLo) mDevLo = e;
        if (e > mDevHi) mDevHi = e;
        mPubDevLo.store(mDevLo, std::memory_order_relaxed);
        mPubDevHi.store(mDevHi, std::memory_order_relaxed);
        /* a real step: every packet far off on the same side for kStepHoldNs */
        const int side = e > kStepNs ? 1 : (e < -kStepNs ? -1 : 0);
        if (side == 0) {
            mStepSide = 0;
        } else if (side != mStepSide) {
            mStepSide = side;
            mStepStart = counted;
        } else if (counted - mStepStart >= kStepHoldNs && publish(w, t, false)) {
            mPub = d;
            mSteerT = counted;
            mSteered.store(mPub, std::memory_order_relaxed);
            mDevN = mDevIdx = 0;  /* the median starts again at the new offset */
            mStepSide = 0;
            bump(mHardAnchors);
        }
        mDev[mDevIdx] = d;
        mDevIdx = (mDevIdx + 1) % kDevWindow;
        if (mDevN < kDevWindow) mDevN++;
        if (mDevN < kDevMinSamples) return;
        std::copy(mDev, mDev + mDevN, mScratch);
        std::nth_element(mScratch, mScratch + mDevN / 2, mScratch + mDevN);
        const int64_t m = mScratch[mDevN / 2];
        const int64_t room = std::min((counted - mSteerT) * kSteerPpm / 1000000, 2 * kSoftAnchorMinNs);
        const int64_t next = mPub + std::clamp(m - mPub, -room, room);
        if (absNs(next - mPub) >= kSoftAnchorMinNs && publish(w, counted + next, true)) {
            mPub = next;  /* the reader slews to it */
            mSteered.store(mPub, std::memory_order_relaxed);
            mSteerT = counted;
            bump(mSoftAnchors);
        }
    }

    /* producer: false when the reader has not taken enough anchors yet (retried later) */
    bool publish(uint64_t pos, int64_t t, bool soft) {
        const uint32_t aw = mAW.load(std::memory_order_relaxed);
        if (aw - mAR.load(std::memory_order_acquire) >= kAnchors) return false;
        mAnchors[aw % kAnchors] = {pos, t, soft};
        mAW.store(aw + 1, std::memory_order_release);
        return true;
    }

    /* producer: spare-time statistics, published once per window */
    void noteSpare(int64_t spare, int64_t raw, int64_t now) {
        bump(mWrites);
        if (spare < 0) {
            bump(mLateWrites);
            mWinLate++;
        }
        if (spare < mWinSpareMin) mWinSpareMin = spare;
        if (raw < mWinRawMin) mWinRawMin = raw;
        if (mWinStartNs == 0) mWinStartNs = now;
        if (now - mWinStartNs >= kSpareWindowNs) {
            mPubSpareMin.store(mWinSpareMin, std::memory_order_relaxed);
            mPubRawMin.store(mWinRawMin, std::memory_order_relaxed);
            mPubLate.store(mWinLate, std::memory_order_relaxed);
            mPubSeq.fetch_add(1, std::memory_order_release);
            mWinSpareMin = mWinRawMin = INT64_MAX;
            mWinLate = 0;
            mWinStartNs = now;
        }
    }

    const int16_t *frameAt(uint64_t pos) const {
        return mBuf.get() + (size_t) (pos % kRingFrames) * kCh;
    }
    int16_t *slot(uint64_t pos) { return mBuf.get() + (size_t) (pos % kRingFrames) * kCh; }

    /* producer: blend the next `len` written frames in from `from` (null = silence) */
    void startRamp(const int16_t *from, int len) {
        for (int c = 0; c < kCh; c++) mRampFrom[c] = from ? from[c] : 0;
        mRampLen = mRampLeft = len;
    }

    /* producer: `frames` frames of concealment at w (published at once) */
    uint64_t conceal(uint64_t w, int frames) {
        const uint64_t r = mR.load(std::memory_order_acquire);
        const int64_t space = kRingFrames - (int64_t) (w - r);
        if (frames > space) frames = (int) std::max<int64_t>(space, 0);
        if (frames <= 0) return w;
        const int period = (int) std::min<uint64_t>(w, (uint64_t) kPlcPeriodFrames);
        const int body = std::min(frames, kPlcMaxFrames);
        const bool fadeOut = frames > kPlcMaxFrames;
        for (int i = 0; i < frames; i++) {
            int16_t *dst = slot(w + (uint64_t) i);
            if (period == 0 || i >= body) {
                for (int c = 0; c < kCh; c++) dst[c] = 0;
                continue;
            }
            /* the last `period` frames backwards, then forwards, ...: continuous at the turns */
            const int k = i % (2 * period);
            const int16_t *src = frameAt(k < period ? w - 1 - (uint64_t) k : w - (uint64_t) period + (uint64_t) (k - period));
            const float g = fadeOut ? 1.0f - (float) (i + 1) / (float) body : 1.0f;
            for (int c = 0; c < kCh; c++) dst[c] = (int16_t) ((float) src[c] * g);
        }
        if (fadeOut || period == 0) {
            startRamp(nullptr, kFadeInFrames);
        } else {
            startRamp(frameAt(w + (uint64_t) frames - 1), kDeclickFrames);
        }
        bump(mConcealFrames, (uint32_t) frames);
        w += (uint64_t) frames;
        mW.store(w, std::memory_order_release);
        return w;
    }

    uint64_t put(uint64_t w, const int16_t *src, int frames) {
        const uint64_t r = mR.load(std::memory_order_acquire);
        const int64_t space = kRingFrames - (int64_t) (w - r);
        if (frames > space) frames = (int) std::max<int64_t>(space, 0);  /* overflow: drop tail */
        const uint64_t start = w;
        int left = frames;
        while (left > 0) {
            const int idx = (int) (w % kRingFrames);
            const int n = std::min(left, kRingFrames - idx);
            memcpy(mBuf.get() + (size_t) idx * kCh, src, (size_t) n * kCh * sizeof(int16_t));
            src += (size_t) n * kCh;
            w += (uint64_t) n;
            left -= n;
        }
        /* blend in (still unpublished, so the consumer never sees the unblended frames) */
        for (int i = 0; i < frames && mRampLeft > 0; i++, mRampLeft--) {
            const float a = (float) (mRampLen - mRampLeft + 1) / (float) mRampLen;
            int16_t *d = slot(start + (uint64_t) i);
            for (int c = 0; c < kCh; c++) d[c] = (int16_t) ((float) mRampFrom[c] * (1.0f - a) + (float) d[c] * a);
        }
        mW.store(w, std::memory_order_release);
        return w;
    }

    uint64_t get(uint64_t r, int16_t *dst, int frames) {
        int left = frames;
        while (left > 0) {
            const int idx = (int) (r % kRingFrames);
            const int n = std::min(left, kRingFrames - idx);
            memcpy(dst, mBuf.get() + (size_t) idx * kCh, (size_t) n * kCh * sizeof(int16_t));
            dst += (size_t) n * kCh;
            r += (uint64_t) n;
            left -= n;
        }
        return r;
    }

    /* consumer: `n` frames of silence at out[done], faded out from the last output frame;
       the audio after it fades in */
    void silence(int16_t *out, int done, int n) {
        if (n <= 0) return;
        int16_t from[kCh];
        memcpy(from, done > 0 ? out + (size_t) (done - 1) * kCh : mLastOut, sizeof(from));
        int16_t *o = out + (size_t) done * kCh;
        for (int i = 0; i < n; i++) {
            const float g = i < kDecayFrames ? 1.0f - (float) (i + 1) / (float) kDecayFrames : 0.0f;
            for (int c = 0; c < kCh; c++) o[(size_t) i * kCh + c] = (int16_t) ((float) from[c] * g);
        }
        mReadRampFrom[0] = mReadRampFrom[1] = 0;
        mReadRampLen = mReadRampLeft = kFadeInFrames;
    }

    void startReadRamp(const int16_t *from, int len) {
        for (int c = 0; c < kCh; c++) mReadRampFrom[c] = from[c];
        mReadRampLen = mReadRampLeft = len;
    }

    /* consumer: blend freshly read frames in after a silence or a jump */
    void applyRamp(int16_t *o, int n) {
        for (int i = 0; i < n && mReadRampLeft > 0; i++, mReadRampLeft--) {
            const float a = (float) (mReadRampLen - mReadRampLeft + 1) / (float) mReadRampLen;
            for (int c = 0; c < kCh; c++) {
                o[(size_t) i * kCh + c] =
                    (int16_t) ((float) mReadRampFrom[c] * (1.0f - a) + (float) o[(size_t) i * kCh + c] * a);
            }
        }
    }

    std::unique_ptr<int16_t[]> mBuf;
    std::atomic<uint64_t> mW{0}, mR{0};
    Anchor mAnchors[kAnchors] = {};
    std::atomic<uint32_t> mAW{0}, mAR{0};
    std::atomic<uint64_t> mFlushPos{0};
    std::atomic<uint32_t> mFlushSeq{0};
    std::atomic<bool> mReanchor{false};
    std::atomic<bool> mRealignReq{false};
    std::atomic<bool> mDevReset{false};
    std::atomic<int64_t> mLastWriteNs{0};
    std::atomic<int64_t> mOutLeadNs{0};
    std::atomic<int64_t> mSteered{0};
    std::atomic<int64_t> mPubSpareMin{INT64_MAX}, mPubRawMin{INT64_MAX};
    std::atomic<int64_t> mPubDevLo{INT64_MAX}, mPubDevHi{INT64_MIN};
    std::atomic<uint32_t> mPubLate{0};
    std::atomic<uint32_t> mPubSeq{0};
    std::atomic<uint32_t> mWrites{0}, mLateWrites{0}, mConcealFrames{0};
    std::atomic<uint32_t> mSoftAnchors{0}, mHardAnchors{0}, mTsJumps{0};
    std::atomic<uint32_t> mUnderruns{0}, mRealigns{0}, mLateSkipFrames{0}, mDrops{0}, mRepeats{0};
    /* producer only */
    bool mHaveAnchor = false;
    uint64_t mBasePos = 0;
    int64_t mBaseT = 0;
    int64_t mPub = 0;  /* steered offset of the timeline from the frame-counted base */
    int64_t mSteerT = 0;  /* frame-counted time of the last steering move */
    int64_t mDev[kDevWindow] = {};
    int64_t mScratch[kDevWindow] = {};
    int mDevN = 0, mDevIdx = 0;
    int64_t mPrevDev = 0;
    bool mHavePrevDev = false;
    int mStepSide = 0;
    int64_t mStepStart = 0;
    int64_t mDevLo = INT64_MAX, mDevHi = INT64_MIN;
    int64_t mWinSpareMin = INT64_MAX, mWinRawMin = INT64_MAX;
    uint32_t mWinLate = 0;
    int64_t mWinStartNs = 0;
    int16_t mRampFrom[kCh] = {};
    int mRampLen = 1, mRampLeft = 0;
    /* consumer only */
    Anchor mCur = {};
    bool mCurValid = false;
    bool mAligned = false;
    bool mAfterUnderrun = false;
    bool mCorrecting = false;
    int mOverCallbacks = 0;
    int16_t mLastOut[kCh] = {};
    uint32_t mSeenFlushSeq = 0;
    int16_t mReadRampFrom[kCh] = {};
    int mReadRampLen = 1, mReadRampLeft = 0;
};

}  // namespace z9x_timeline

#endif /* Z9X_PCM_TIMELINE_H */
