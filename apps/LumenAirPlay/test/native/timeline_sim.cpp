/*
 * Host simulation of the AirPlay audio timeline (jni/glue/pcm_timeline.h): an AAC-ELD sender
 * (480 frames a packet, 44.1 kHz), Wi-Fi resends that hold later packets in the reorder
 * buffer, the AAudio callback of a test Z9X (2215 frames, presentation 165 ms after the
 * callback, output lead ~215 ms) and play times with the timing faults a sender can show.
 * Every scenario prints the device's 5 s statistics and checks them:
 *
 *   mac4k       2026-11-28: no packet lost, play times jump back and forth by up to
 *               250 ms several times a second (sync updates), 20 % resends. 1.0.11 played
 *               this with ~40 % concealed and ~10 realigns per 5 s; now nothing is concealed
 *               and the reader never realigns.
 *   jitter      every packet's play time off by up to +-60 ms.
 *   sawtooth    the play time drifts 200 ms within each second, then jumps back.
 *   clean, loss (2 % lost: exactly that is concealed), decdrop (1 % not decoded: the same),
 *   step        the sender's clock steps +300 ms once: one hard anchor, no storm,
 *   drift       the sender's clock runs 200 ppm fast: slewed, no realign.
 *
 * Build: test/native/run.sh (c++17); test/native/mutate.sh breaks the timeline rules one by one
 * and expects this simulation to fail. -DOLD_TIMELINE='"<file>"' builds the same scenarios
 * against a copy of the 1.0.11 PcmTimeline (writer API write(pcm, frames, T, lost, extra)): the
 * jitter scenario then shows the owner's numbers (concealed 2.1-2.8 s and 8-11 realigns per
 * 5 s with no packet lost).
 * Copyright (C) 2026 Z9X project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <random>
#include <string>
#include <vector>

static int64_t gNow = 0;
#define Z9X_TIMELINE_NOW() gNow

#ifdef OLD_TIMELINE
#include OLD_TIMELINE
#else
#ifdef TIMELINE_HEADER
#include TIMELINE_HEADER /* a mutant (mutate.sh) */
#else
#include "../../jni/glue/pcm_timeline.h"
#endif
using namespace z9x_timeline;
#endif

namespace {

constexpr int64_t MS = 1000000LL;
constexpr int kSpf = 480;
constexpr int kCb = 2215;               /* AAudio callback (burst) frames */
constexpr int64_t kPresentNs = 165 * MS; /* callback -> presentation of its first frame */
constexpr int64_t kPlayLeadNs = 430 * MS; /* latency 250 + extra 180: play time - sender time */

struct Scenario {
    const char *name;
    int model;          /* 0 none, 1 jumps, 2 per-packet jitter, 3 sawtooth, 4 one-sided outliers */
    double amp;         /* ms */
    double rate;        /* jumps / s, sawtooth Hz */
    double lossPct, resendPct, decDropPct;
    double stepMs;      /* at 10 s */
    double driftPpm;
    int seconds;
};

struct Totals { uint64_t frames = 0, conceal = 0, drops = 0, repeats = 0, realigns = 0, underruns = 0;
                uint64_t soft = 0, hard = 0, missing = 0; int64_t offset = 0; };

Totals run(const Scenario &sc, bool print) {
    std::mt19937 rng(1128);
    std::uniform_real_distribution<double> u01(0.0, 1.0);
    PcmTimeline tl;
    const int64_t pktNs = (int64_t) llround(kSpf * 1e9 / 44100.0 * (1.0 + sc.driftPpm * 1e-6));
    const int nPkts = (int) (sc.seconds * 1e9 / pktNs);
    /* timing error of the play times (model) at sender time s */
    double jumpOff = 0;
    double nextJump = 0;
    auto timingErr = [&](int64_t s, int k) -> int64_t {
        double ms = 0;
        if (sc.model == 1) {
            while (s / 1e6 >= nextJump) {
                jumpOff = (u01(rng) * 2 - 1) * sc.amp;
                nextJump += -std::log(1 - u01(rng)) * 1000.0 / sc.rate;
            }
            ms = jumpOff;
        } else if (sc.model == 2) {
            ms = (u01(rng) * 2 - 1) * sc.amp;
        } else if (sc.model == 3) {
            const double ph = std::fmod(s / 1e9 * sc.rate, 1.0);
            ms = -sc.amp / 2 + sc.amp * ph;
        } else if (sc.model == 4) {
            ms = u01(rng) * 100 < sc.rate ? sc.amp : 0;  /* rate = percent of the packets */
        }
        if (sc.stepMs != 0 && s >= 10 * 1000 * MS) ms += sc.stepMs;
        (void) k;
        return (int64_t) (ms * MS);
    };
    struct Pkt { int64_t write, t; bool lost, decDrop; };
    std::vector<Pkt> pk((size_t) nPkts);
    int64_t firstErr = INT64_MIN;  /* timing error of the first written packet: the start anchor */
    int64_t prevDeliver = 0;
    for (int k = 0; k < nPkts; k++) {
        const int64_t s = (int64_t) k * pktNs;
        int64_t arrive = s + 1 * MS;
        if (u01(rng) * 100 < sc.resendPct) arrive += (int64_t) ((10 + 50 * u01(rng)) * MS);
        const int64_t deliver = std::max(arrive, prevDeliver);  /* the reorder buffer is in order */
        prevDeliver = deliver;
        pk[(size_t) k] = {deliver + 2 * MS, s + kPlayLeadNs + timingErr(s, k), u01(rng) * 100 < sc.lossPct,
                          u01(rng) * 100 < sc.decDropPct};
    }
    std::vector<int16_t> pcm((size_t) kSpf * 2), out((size_t) kCb * 2);
    const int64_t cbNs = (int64_t) llround(kCb * 1e9 / 44100.0);
    int64_t nextCb = 300 * MS;
    int k = 0, pendingMissing = 0;
    uint64_t winMissing = 0;
    bool pendingLost = false;
    Totals tot;
    auto base = tl.stats();
    int64_t nextStat = 5000 * MS;
    const int64_t end = (int64_t) sc.seconds * 1000 * MS;
    for (gNow = 0; gNow < end; gNow += 1 * MS) {
        while (k < nPkts && pk[(size_t) k].write <= gNow) {
            const Pkt &p = pk[(size_t) k];
            if (p.lost || p.decDrop) {  /* never written: the next packet knows it is missing */
                pendingMissing += kSpf;
                pendingLost = pendingLost || p.lost;  /* 1.0.11 saw only the sequence gaps */
                winMissing += kSpf;
                k++;
                continue;
            }
            for (int i = 0; i < kSpf; i++) {
                const double n = (double) k * kSpf + i;
                const int16_t v = (int16_t) (8000 * std::sin(2 * M_PI * 441.0 * n / 44100.0));
                pcm[(size_t) i * 2] = pcm[(size_t) i * 2 + 1] = v;
            }
            if (firstErr == INT64_MIN) firstErr = p.t - ((int64_t) k * pktNs + kPlayLeadNs);
#ifdef OLD_TIMELINE
            tl.write(pcm.data(), kSpf, p.t, pendingLost, 180 * MS);
#else
            tl.write(pcm.data(), kSpf, p.t, pendingMissing, false, 180 * MS);
#endif
            pendingMissing = 0;
            pendingLost = false;
            k++;
        }
        if (gNow >= nextCb) {
            tl.setOutLead(kPresentNs + cbNs);
            tl.read(out.data(), kCb, gNow + kPresentNs);
            nextCb += cbNs;
        }
        if (gNow >= nextStat) {
            const auto s = tl.stats();
            const uint64_t concealMs = (uint64_t) ((s.concealFrames - base.concealFrames) * 1000ULL / 44100);
            if (print) {
                printf("  %-9s %2llds: concealed %4llu ms; drift corr -%u +%u, realign %u, underruns %u; ring %lld ms"
#ifndef OLD_TIMELINE
                       "; anchors soft %u hard %u, ts jumps %u"
#endif
                       "\n",
                       sc.name, (long long) (gNow / 1000 / MS), (unsigned long long) concealMs, s.drops - base.drops,
                       s.repeats - base.repeats, s.realigns - base.realigns, s.underruns - base.underruns,
                       (long long) (tl.bufferedFrames() * 1000 / 44100)
#ifndef OLD_TIMELINE
                       , s.softAnchors - base.softAnchors, s.hardAnchors - base.hardAnchors, s.tsJumps - base.tsJumps
#endif
                );
            }
            if (gNow > 5000 * MS) {  /* the first window holds the start-up */
                tot.conceal += s.concealFrames - base.concealFrames;
                tot.drops += s.drops - base.drops;
                tot.repeats += s.repeats - base.repeats;
                tot.realigns += s.realigns - base.realigns;
                tot.underruns += s.underruns - base.underruns;
#ifndef OLD_TIMELINE
                tot.soft += s.softAnchors - base.softAnchors;
                tot.hard += s.hardAnchors - base.hardAnchors;
#endif
                tot.frames += 5 * 44100;
                tot.missing += winMissing;
            }
            base = s;
            winMissing = 0;
            nextStat += 5000 * MS;
        }
    }
#ifndef OLD_TIMELINE
    tot.offset = tl.steeredOffset() + firstErr;  /* against the true play times */
#endif
    return tot;
}

int fails = 0;
void expect(bool ok, const char *sc, const std::string &what) {
    if (!ok) {
        printf("FAIL %s: %s\n", sc, what.c_str());
        fails++;
    }
}

}  // namespace

int main(int argc, char **argv) {
    const bool quiet = argc > 1 && !strcmp(argv[1], "-q");
    const Scenario all[] = {
        {"mac4k", 1, 250, 3, 0, 20, 0, 0, 0, 30},
        {"jitter", 2, 60, 0, 0, 20, 0, 0, 0, 30},
        {"sawtooth", 3, 200, 1, 0, 0, 0, 0, 0, 30},
        {"skewed", 4, 100, 25, 0, 20, 0, 0, 0, 30},
        {"clean", 0, 0, 0, 0, 0, 0, 0, 0, 30},
        {"loss", 0, 0, 0, 2, 20, 0, 0, 0, 30},
        {"decdrop", 0, 0, 0, 0, 0, 1, 0, 0, 30},
        {"step", 0, 0, 0, 0, 0, 0, 300, 0, 30},
        {"drift", 0, 0, 0, 0, 0, 0, 0, 200, 60},
    };
    for (const Scenario &sc : all) {
        const Totals t = run(sc, !quiet);
        const double pct = t.frames ? 100.0 * (double) (t.drops + t.repeats) / (double) t.frames : 0;
        printf("%-9s concealed %llu frames (missing %llu), drift corr %.3f %%, realign %llu, underruns %llu, "
               "anchors soft %llu hard %llu, offset %lld ms\n",
               sc.name, (unsigned long long) t.conceal, (unsigned long long) t.missing, pct,
               (unsigned long long) t.realigns, (unsigned long long) t.underruns, (unsigned long long) t.soft,
               (unsigned long long) t.hard, (long long) (t.offset / MS));
#ifndef OLD_TIMELINE
        const std::string n = sc.name;
        expect(t.realigns == 0, sc.name, "realigns " + std::to_string(t.realigns));
        expect(t.underruns == 0, sc.name, "underruns " + std::to_string(t.underruns));
        if (n == "loss" || n == "decdrop") {
            /* exactly the missing audio after the first window (missing counts from 0 s) */
            expect(t.missing > 0 && t.conceal + 2 * kSpf >= t.missing && t.conceal <= t.missing + 2 * kSpf, sc.name,
                   "concealed " + std::to_string(t.conceal) + " frames, missing " + std::to_string(t.missing));
        } else {
            expect(t.conceal == 0, sc.name, "concealed " + std::to_string(t.conceal) + " frames");
        }
        /* corrections stay inaudible; random jitter of single packets moves nothing audible */
        const double maxPct = (n == "jitter" || n == "skewed") ? 0.1 : 0.5;
        expect(pct < maxPct, sc.name, "drift corrections " + std::to_string(pct) + " %");
        if (n == "step") expect(t.hard == 1, sc.name, "hard anchors " + std::to_string(t.hard) + ", want 1");
        else expect(t.hard == 0, sc.name, "hard anchors " + std::to_string(t.hard));
        if (n == "drift") expect(t.soft > 0, sc.name, "the 200 ppm drift was never steered");
        /* the timeline ends where the play times say: the true offset (200 ppm of 60 s for the
           drift, the 300 ms step, 0 elsewhere; one-sided outliers must not pull it) */
        const int64_t want = n == "drift" ? 12 * MS : (n == "step" ? 300 * MS : 0);
        const int64_t tol = (n == "mac4k" || n == "sawtooth") ? 60 * MS : 6 * MS;
        expect(t.offset > want - tol && t.offset < want + tol, sc.name,
               "timeline offset " + std::to_string(t.offset / MS) + " ms, want " + std::to_string(want / MS));
#endif
    }
#ifndef OLD_TIMELINE
    if (fails) {
        printf("timeline_sim: %d FAILED\n", fails);
        return 1;
    }
    printf("timeline_sim: ALL PASSED\n");
#endif
    return 0;
}
