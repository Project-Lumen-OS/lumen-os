/*
 * When a compressed mirroring frame leaves the FIFO for the decoder (no Android dependency:
 * test/native/pacing_sim.cpp replays decoders with uneven decode times against this header).
 *
 * A frame arrives about latency + extra (~280 ms) before its play time T. It waits in the FIFO
 * until T - lead, is decoded, and the picture is released with releaseOutputBufferAtTime(T); a
 * picture that comes out more than 34 ms after T is shown at once and counted late.
 *
 * 1.0.12 fed at T - (average decode latency + 15 ms): every frame that took 50 ms longer than the
 * average came out late, and a slow frame also made the ones behind it late (one hardware
 * decoder, frames in order). 4K H.264 from a Mac gave 10-30 % late frames at 30-46 fps on the
 * a test Z9X while the average decode latency read only 11-60 ms.
 *
 * Now:
 *  - lead = the longest decode latency of the last 1-2 s + 25 ms, at least 60 ms, at most 150 ms
 *    (frames wait ~280 ms anyway, so feeding them earlier costs no delay);
 *  - at most kMaxAhead frames are inside the decoder or decoded and waiting for their play time:
 *    codec2 gives the output surface about 7 buffers beyond what the decoder itself holds
 *    (kSmoothnessFactor 4 + kRenderingDepth 3), so the decoder never waits for a free buffer.
 *
 * Threads: noteLatency() on the codec output thread, everything else on the decoder worker.
 *
 * Copyright (C) 2026 Z9X project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#ifndef Z9X_VIDEO_PACING_H
#define Z9X_VIDEO_PACING_H

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <deque>

class FeedPacer {
public:
    static constexpr int64_t kMs = 1000000LL;
    static constexpr int64_t kMarginNs = 25 * kMs;
    static constexpr int64_t kLeadMinNs = 60 * kMs;
    static constexpr int64_t kLeadMaxNs = 150 * kMs;
    static constexpr int64_t kBucketNs = 1000 * kMs;  /* peak = max of this bucket and the last one */
    static constexpr size_t kMaxAhead = 6;

    /* output thread: one frame's decode latency (input queued -> picture dequeued) */
    void noteLatency(int64_t latNs, int64_t now) {
        if (mBucketStart == 0 || now - mBucketStart >= kBucketNs) {
            mPrevMax = (mBucketStart != 0 && now - mBucketStart < 2 * kBucketNs) ? mCurMax : 0;
            mCurMax = 0;
            mBucketStart = now;
        }
        mCurMax = std::max(mCurMax, latNs);
        mPeak.store(std::max(mCurMax, mPrevMax), std::memory_order_relaxed);
    }

    int64_t peakNs() const { return mPeak.load(std::memory_order_relaxed); }

    /* how long before its play time a frame goes to the decoder; fixedNs > 0 overrides */
    int64_t leadNs(int64_t fixedNs = 0) const {
        if (fixedNs > 0) return std::min(fixedNs, kLeadMaxNs);
        return std::clamp(peakNs() + kMarginNs, kLeadMinNs, kLeadMaxNs);
    }

    /* the earliest time the frame with play time `target` may be fed */
    int64_t feedAt(int64_t target, int64_t now, int64_t fixedNs = 0) {
        drop(now);
        int64_t t = target - leadNs(fixedNs);
        if (mAhead.size() >= kMaxAhead) t = std::max(t, mAhead.front());  /* wait for a free buffer */
        return t;
    }

    /* a frame went into the decoder */
    void noteFed(int64_t target, int64_t now) {
        drop(now);
        if (target > now) mAhead.push_back(target);
        mMaxAhead = std::max(mMaxAhead, mAhead.size());
    }

    /* a new decoder instance: the frames of the old one are gone */
    void reset() { mAhead.clear(); }

    /* frames in the decoder or waiting for their play time, and the most since takeMaxAhead() */
    size_t ahead() const { return mAhead.size(); }
    size_t takeMaxAhead() {
        const size_t m = std::max(mMaxAhead, mAhead.size());
        mMaxAhead = mAhead.size();
        return m;
    }

private:
    void drop(int64_t now) {
        while (!mAhead.empty() && mAhead.front() <= now) mAhead.pop_front();
    }

    /* output thread */
    int64_t mBucketStart = 0, mCurMax = 0, mPrevMax = 0;
    std::atomic<int64_t> mPeak{30 * kMs};
    /* worker */
    std::deque<int64_t> mAhead;  /* play times of fed frames not yet due, in feed order */
    size_t mMaxAhead = 0;
};

#endif /* Z9X_VIDEO_PACING_H */
