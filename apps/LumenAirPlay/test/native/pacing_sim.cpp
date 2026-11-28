/*
 * Host simulation of the mirroring video feed pacing (jni/glue/video_pacing.h): one hardware
 * decoder that takes frames in order, frames that arrive ~280 ms before their play time, the
 * decoder's input queue (4 frames) and the 34 ms late rule of video_decoder.cpp. Each scenario
 * runs the new pacer and the 1.0.12 rule (lead = average decode latency + 15 ms, 25..150 ms) on
 * the same frames and checks:
 *
 *   mac4k       2026-11-28, 4K H.264 from a Mac at ~46 fps: most frames decode in 8-14 ms,
 *               every 12th one (a larger P-frame) in 30-45 ms, 3 % (motion, keyframes) in 50-90 ms
 *               (decoder ~70 % busy): 1.0.12 ~5 % late, the pacer at most 1 %.
 *   mac4kheavy  the same with every 8th frame larger and 5 % slow ones (~80 % busy): 1.0.12 shows
 *               the owner's 10-30 %, the pacer at most 3 % and a quarter of that.
 *   steady      1080p at 60 fps, 5-8 ms a frame: nothing late either way.
 *   static      one frame every 500 ms (a still screen): nothing late, the pacer feeds 60 ms ahead.
 *   stall       a 200 ms Wi-Fi stall, then the held frames arrive in a burst with ~80 ms left:
 *               nothing late with the pacer.
 *   slowframes  60 fps with rare 140 ms frames: the pacer wants a 150 ms lead, and the kMaxAhead cap
 *               (6 frames = 100 ms at 60 fps) is what keeps the frames waiting for display at 6.
 * Every scenario: frames fed and not yet shown never exceed FeedPacer::kMaxAhead (the output
 * surface buffers).
 *
 * Build: test/native/run.sh; test/native/mutate.sh breaks the pacing rules one by one
 * (-DPACING_HEADER='"<copy>"') and expects this simulation to fail.
 * Copyright (C) 2026 Z9X project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <functional>
#include <random>
#include <string>
#include <vector>

#ifdef PACING_HEADER
#include PACING_HEADER /* a mutant (mutate.sh) */
#else
#include "../../jni/glue/video_pacing.h"
#endif

namespace {

constexpr int64_t kMs = 1000000LL;
constexpr int64_t kLate = 34 * kMs;        /* video_decoder.cpp kLateRenderNs */
constexpr int64_t kPlayLead = 280 * kMs;   /* play time - arrival on a test unit */
constexpr size_t kInputSlots = 4;          /* decoder input buffers */

struct Frame { int64_t arrive, target, service; };
struct Result { int frames = 0, late = 0; size_t maxWaiting = 0; int64_t leadMin = 0, leadMax = 0; };

/* lead rule: returns the feed time of a frame given the time the worker looks at it */
using LeadFn = std::function<int64_t(int64_t target, int64_t now)>;

Result run(const std::vector<Frame> &fr, bool old) {
    FeedPacer pacer;
    int64_t ema = 30 * kMs;  /* 1.0.12: mDecodeLatencyNs starts at 30 ms */
    const size_t n = fr.size();
    std::vector<int64_t> feed(n), start(n), finish(n), shown(n);
    size_t outDone = 0;  /* outputs already reported to the latency estimate */
    Result r;
    r.leadMin = INT64_MAX;
    int64_t workerFree = 0, decoderFree = 0;
    for (size_t i = 0; i < n; i++) {
        int64_t now = std::max(fr[i].arrive, workerFree);
        if (i >= kInputSlots) now = std::max(now, start[i - kInputSlots]);  /* input queue full */
        int64_t at = now;
        for (int pass = 0; pass < 8; pass++) {
            /* outputs that happened before this moment update the estimate */
            while (outDone < i && finish[outDone] <= at) {
                const int64_t lat = finish[outDone] - feed[outDone];
                if (old) ema += (lat - ema) / 8; else pacer.noteLatency(lat, finish[outDone]);
                outDone++;
            }
            int64_t want;
            if (old) {
                const int64_t lead = std::clamp(ema + 15 * kMs, 25 * kMs, 150 * kMs);
                want = fr[i].target - lead;
            } else {
                want = pacer.feedAt(fr[i].target, at);
            }
            const int64_t next = std::max(at, want);
            if (next == at) break;
            at = next;
        }
        const int64_t lead = fr[i].target - at;
        r.leadMin = std::min(r.leadMin, lead);
        r.leadMax = std::max(r.leadMax, lead);
        feed[i] = at;
        workerFree = at;
        if (!old) pacer.noteFed(fr[i].target, at);
        start[i] = std::max(at, decoderFree);
        finish[i] = start[i] + fr[i].service;
        decoderFree = finish[i];
        shown[i] = std::max(finish[i], fr[i].target);
        if (finish[i] - fr[i].target > kLate) r.late++;
        /* frames fed and not yet shown (decoder + output surface), at this feed time */
        size_t waiting = 0;
        for (size_t j = 0; j <= i; j++) if (feed[j] <= at && shown[j] > at) waiting++;
        r.maxWaiting = std::max(r.maxWaiting, waiting);
    }
    r.frames = (int) n;
    return r;
}

std::vector<Frame> make(double fps, double seconds, const std::function<int64_t(int, std::mt19937 &)> &service,
                        const std::function<int64_t(int, int64_t)> &arrivalDelay, unsigned seed) {
    std::mt19937 rng(seed);
    std::vector<Frame> v;
    const int64_t step = (int64_t) (1e9 / fps);
    const int count = (int) (seconds * fps);
    for (int i = 0; i < count; i++) {
        const int64_t sent = 1000 * kMs + i * step;
        Frame f;
        f.target = sent + kPlayLead;
        f.arrive = sent + arrivalDelay(i, sent);
        f.service = service(i, rng);
        v.push_back(f);
    }
    /* frames come off one TCP stream: in order */
    for (size_t i = 1; i < v.size(); i++) v[i].arrive = std::max(v[i].arrive, v[i - 1].arrive);
    return v;
}

int64_t uni(std::mt19937 &rng, int lo, int hi) {
    return std::uniform_int_distribution<int>(lo, hi)(rng) * kMs / 1;
}

int gFails = 0;
bool gQuiet = false;

void check(const char *sc, bool ok, const std::string &what) {
    if (!ok) {
        printf("FAIL %s: %s\n", sc, what.c_str());
        gFails++;
    }
}

void report(const char *sc, const Result &o, const Result &p) {
    if (gQuiet) return;
    printf("%-10s 1.0.12: %4d frames %4d late (%4.1f %%)   pacer: %4d late (%4.1f %%), lead %lld..%lld ms, "
           "waiting for display max %zu\n",
           sc, o.frames, o.late, 100.0 * o.late / o.frames, p.late, 100.0 * p.late / p.frames,
           (long long) (p.leadMin / kMs), (long long) (p.leadMax / kMs), p.maxWaiting);
}

void common(const char *sc, const Result &p) {
    check(sc, p.maxWaiting <= FeedPacer::kMaxAhead,
          "frames waiting for display " + std::to_string(p.maxWaiting) + " > " + std::to_string(FeedPacer::kMaxAhead));
}

}  // namespace

int main(int argc, char **argv) {
    gQuiet = argc > 1 && strcmp(argv[1], "-q") == 0;
    const auto noDelay = [](int, int64_t) { return (int64_t) 3 * kMs; };

    /* mac4k: decoder ~70 % busy (old rule ~5 % late, pacer <= 1 %); mac4kheavy: ~80 % busy, the
       owner's 10-30 % with the old rule, the pacer at most a quarter of that and <= 3 % */
    const auto mac = [](const char *sc, int spikePct, int everyN, int oldMinPct, int newMaxPct) {
        const auto svc = [spikePct, everyN](int i, std::mt19937 &rng) {
            const int roll = std::uniform_int_distribution<int>(0, 99)(rng);
            if (roll < spikePct) return uni(rng, 50, 90);
            if (i % everyN == 0) return uni(rng, 30, 45);
            return uni(rng, 8, 14);
        };
        const auto net = [](int i, int64_t) { return (int64_t) (2 + (i * 7) % 9) * kMs; };
        const auto fr = make(46, 60, svc, net, 11);
        const Result o = run(fr, true), p = run(fr, false);
        report(sc, o, p);
        common(sc, p);
        check(sc, o.late * 100 >= oldMinPct * o.frames,
              "1.0.12 rule shows only " + std::to_string(o.late) + " late (scenario too light)");
        check(sc, p.late * 100 <= newMaxPct * p.frames && p.late * 4 <= o.late,
              std::to_string(p.late) + " late frames with the pacer");
    };
    mac("mac4k", 3, 12, 4, 1);
    mac("mac4kheavy", 5, 8, 10, 3);
    {   /* steady */
        const auto svc = [](int, std::mt19937 &rng) { return uni(rng, 5, 8); };
        const auto fr = make(60, 30, svc, noDelay, 12);
        const Result o = run(fr, true), p = run(fr, false);
        report("steady", o, p);
        common("steady", p);
        check("steady", o.late == 0 && p.late == 0, "late frames " + std::to_string(o.late) + " / " + std::to_string(p.late));
        check("steady", p.leadMax <= FeedPacer::kLeadMinNs, "lead above the 60 ms floor with 8 ms frames");
    }
    {   /* static */
        const auto svc = [](int, std::mt19937 &rng) { return uni(rng, 8, 12); };
        const auto fr = make(2, 60, svc, noDelay, 13);
        const Result o = run(fr, true), p = run(fr, false);
        report("static", o, p);
        common("static", p);
        check("static", p.late == 0, std::to_string(p.late) + " late frames");
    }
    {   /* stall: 200 ms without data, then the held frames arrive together */
        const auto svc = [](int, std::mt19937 &rng) { return uni(rng, 10, 18); };
        const auto net = [](int, int64_t sent) {
            const int64_t phase = (sent - 1000 * kMs) % (5000 * kMs);
            const int64_t stallStart = 2000 * kMs, stallEnd = 2200 * kMs;
            if (phase >= stallStart && phase < stallEnd) return stallEnd - phase + 3 * kMs;
            return (int64_t) 3 * kMs;
        };
        auto fr = make(46, 30, svc, net, 14);
        /* the burst leaves only ~80 ms: the stall eats 200 ms of the 280 ms lead */
        const Result o = run(fr, true), p = run(fr, false);
        report("stall", o, p);
        common("stall", p);
        check("stall", p.late == 0, std::to_string(p.late) + " late frames after a 200 ms stall");
    }
    {   /* slowframes */
        const auto svc = [](int i, std::mt19937 &rng) { return i % 97 == 50 ? uni(rng, 135, 145) : uni(rng, 6, 9); };
        const auto fr = make(60, 30, svc, noDelay, 15);
        const Result o = run(fr, true), p = run(fr, false);
        report("slowframes", o, p);
        common("slowframes", p);
        check("slowframes", p.maxWaiting == FeedPacer::kMaxAhead, "the cap was never reached (scenario does not test it)");
        check("slowframes", p.late < o.late, "not fewer late frames than the 1.0.12 rule");
    }
    if (gFails) {
        printf("pacing_sim: %d FAILED\n", gFails);
        return 1;
    }
    if (!gQuiet) printf("pacing_sim: all passed\n");
    return 0;
}
